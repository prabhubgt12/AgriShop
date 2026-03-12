const { storeTrade } = require('./tradeStorage');

function isNum(x) {
  return typeof x === 'number' && !Number.isNaN(x);
}

function clamp(n, lo, hi) {
  return Math.max(lo, Math.min(hi, n));
}

function normalizeExitStyle(v) {
  const s = typeof v === 'string' ? v.trim().toUpperCase() : '';
  return s === 'TARGET' ? 'TARGET' : 'TRAILING';
}

function normalizeTargetPct(v) {
  const n = typeof v === 'number' ? v : Number(v);
  if (!Number.isFinite(n)) return 30;
  return clamp(Math.round(n), 20, 100);
}

function findLeg(snapshot, strike, optType) {
  const row = (snapshot?.rows || []).find((r) => r && r.strike === strike);
  if (!row) return null;
  return optType === 'CE' ? row.ce : row.pe;
}

function initialSl(entryPrice, mode) {
  if (!isNum(entryPrice) || entryPrice <= 0) return null;
  if (mode === 'EXPIRY') return entryPrice * 0.75;
  if (mode === 'NORMAL') return entryPrice * 0.75;
  if (mode === 'BIG_RALLY') return entryPrice * 0.60;
  return entryPrice * 0.70;
}

function updateTrailing(trade, mode, currentLtp) {
  if (!trade || trade.status !== 'OPEN' || !isNum(currentLtp)) return trade;
  const entry = trade.entryPrice;
  if (!isNum(entry) || entry <= 0) return trade;

  const peak = isNum(trade.peakPrice) ? Math.max(trade.peakPrice, currentLtp) : currentLtp;
  let sl = trade.slPrice;
  const mult = currentLtp / entry;

  if (mode === 'BIG_RALLY') {
    if (mult >= 2) sl = Math.max(sl, entry);
    if (mult >= 3) sl = Math.max(sl, peak * 0.5);
    if (mult >= 5) sl = Math.max(sl, peak * 0.6);
    if (mult >= 10) sl = Math.max(sl, peak * 0.7);
  } else {
    if (mult >= 1.3) sl = Math.max(sl, entry);
    if (mult >= 1.6) sl = Math.max(sl, peak * 0.8);
  }

  return { ...trade, peakPrice: peak, slPrice: sl };
}

function updatePeakOnly(trade, currentLtp) {
  if (!trade || trade.status !== 'OPEN' || !isNum(currentLtp)) return trade;
  const peak = isNum(trade.peakPrice) ? Math.max(trade.peakPrice, currentLtp) : currentLtp;
  return { ...trade, peakPrice: peak };
}

function shouldExit(trade, mode, currentLtp, exitStyle, targetPct, underlyingLtp) {
  if (!trade || trade.status !== 'OPEN' || !isNum(currentLtp)) return null;

  if (exitStyle === 'TARGET' && isNum(trade.entryPrice) && trade.entryPrice > 0) {
    const targetPrice = trade.entryPrice * (1 + targetPct / 100);
    console.log(`TARGET check: entryPrice=${trade.entryPrice}, targetPct=${targetPct}, targetPrice=${targetPrice}, currentLtp=${currentLtp}, hit=${currentLtp >= targetPrice}`);
    if (currentLtp >= targetPrice) return { reason: `TARGET_HIT_${targetPct}` };
  }

  // False breakout exit for NORMAL
  if (mode === 'NORMAL' && isNum(trade.breakoutLevel) && isNum(underlyingLtp)) {
    const buffer = 5;
    if (trade.optType === 'CE' && underlyingLtp < trade.breakoutLevel - buffer) {
      console.log('FALSE BREAKOUT CE', underlyingLtp, trade.breakoutLevel);
      return { reason: 'FALSE_BREAKOUT' };
    }
    if (trade.optType === 'PE' && underlyingLtp > trade.breakoutLevel + buffer) {
      console.log('FALSE BREAKOUT PE', underlyingLtp, trade.breakoutLevel);
      return { reason: 'FALSE_BREAKOUT' };
    }
  }

  if (isNum(trade.slPrice) && currentLtp <= trade.slPrice) return { reason: 'SL_HIT' };

  // Same extra trail-from-peak as paper for NORMAL/EXPIRY
  const entry = trade.entryPrice;
  const peak = trade.peakPrice;
  if (mode !== 'BIG_RALLY' && isNum(entry) && isNum(peak) && peak > 0) {
    const mult = peak / entry;
    if (mult >= 1.6 && currentLtp <= peak * 0.8) return { reason: 'TRAIL_FROM_PEAK' };
  }

  return null;
}

function createLiveTradeState() {
  return {
    enabled: true,
    current: null,
    history: [],
    lastDecision: null,
    lastExitTs: null,
    lastClosed: null,
    lastFailedTs: null,
  };
}

async function getNetQtyForSymbol(client, tsym, productType) {
  if (!client || typeof client.get_positions !== 'function') return null;
  const positions = await client.get_positions();
  if (!positions || !Array.isArray(positions)) return null;
  const pos = positions.find((p) => p && p.tsym === tsym && p.prd === productType);
  if (!pos) return 0;
  const q = typeof pos.netqty === 'number' ? pos.netqty : Number(pos.netqty);
  if (!Number.isFinite(q)) return null;
  return q;
}

async function stepLiveTrade(ctx) {
  const {
    client,
    paper,
    live,
    snapshotHistory,
    entryDecision,
  } = ctx;

  console.log('stepLiveTrade called, live.current:', live.current ? { status: live.current.status, id: live.current.id } : 'null');

  const latest = Array.isArray(snapshotHistory) ? snapshotHistory[snapshotHistory.length - 1] : null;
  if (!latest) return live;

  if (!paper || paper.tradeMode !== 'LIVE') return live;
  if (!paper.liveArmed) {
    return { ...live, lastDecision: { ts: latest.ts, action: 'NO_TRADE', reasons: ['LIVE not armed'] } };
  }

  // Convert ENTRY_PENDING → OPEN On Next Poll
  if (live.current && live.current.status === 'ENTRY_PENDING') {
    try {
      const orders = await client.get_orderbook();
      const order = orders.find(o => o.norenordno === live.current.entryOrderNo);

      if (order && (order.status === 'REJECTED' || order.status === 'CANCELLED')) {
        live.current = null;
        return { ...live, lastDecision: { ts: latest.ts, action: 'ERROR', reasons: ['Entry order rejected or cancelled by broker'] } };
      }

      if (order && order.status === 'COMPLETE') {
        const trades = await client.get_tradebook();
        const fill = trades.find(t => t.norenordno === live.current.entryOrderNo);

        if (fill) {
          const entryPrice = parseFloat(fill.avgprc);

          live.current = {
            ...live.current,
            status: 'OPEN',
            entryPrice,
            peakPrice: entryPrice,
            slPrice: initialSl(entryPrice, live.current.mode),
          };
        }
      }
    } catch (e) {
      console.log('Error checking pending order fill:', e.message);
    }
    return live;
  }

  // Convert EXITING → CLOSED On Next Poll
  if (live.current && live.current.status === 'EXITING') {
    try {
      const orders = await client.get_orderbook();
      const order = orders.find(o => o.norenordno === live.current.exitOrderNo);

      if (order && order.status === 'COMPLETE') {
        const trades = await client.get_tradebook();
        const fill = trades.find(t => t.norenordno === live.current.exitOrderNo);

        if (fill) {
          const exitPrice = parseFloat(fill.avgprc);
          const pnl = (isNum(exitPrice) && isNum(live.current.entryPrice)) ? (exitPrice - live.current.entryPrice) * live.current.qty : null;

          const closed = {
            ...live.current,
            status: 'CLOSED',
            exitPrice,
            pnl,
          };

          storeTrade(closed);

          live.current = null;
          live.lastClosed = closed;
          live.history = [...live.history, closed].slice(-50);
          live.lastExitTs = Date.now();
        }
      }
    } catch (e) {
      console.log('Error checking exit order fill:', e.message);
    }
    return live;
  }

  // Block new entries if ENTRY_PENDING
  if (live.current && live.current.status === 'ENTRY_PENDING') {
    return {
      ...live,
      lastDecision: { ts: latest.ts, action: 'BLOCKED', reasons: ['Trade already active or pending'] }
    };
  }

  const exitStyle = normalizeExitStyle(paper.exitStyle);
  const targetPct = normalizeTargetPct(paper.targetPct);

  console.log('Before exit if, live.current.status:', live.current?.status);

  if (live.current && (live.current.status === 'OPEN' || live.current.status === 'EXITING')) {
    console.log('Entering exit block for trade', live.current.id);
    const t = live.current;
    const legNow = findLeg(latest, t.strike, t.optType);
    const ltpNow = legNow?.ltp;
    console.log(`Exit check: trade ${t.id} status=${t.status}, entryPrice=${t.entryPrice}, slPrice=${t.slPrice}, currentLtp=${ltpNow}, strike=${t.strike}, optType=${t.optType}`);

    // If we already sent an exit order, don't place again.
    if (t.status === 'EXITING') {
      return { ...live, lastDecision: { ts: latest.ts, action: 'EXITING', reasons: [t.exitReason || 'EXITING'] } };
    }

    const updated = exitStyle === 'TRAILING'
      ? updateTrailing(t, t.mode, ltpNow)
      : updatePeakOnly(t, ltpNow);

    if (!updated.tsym || !updated.exchange) {
      const leg = findLeg(latest, updated.strike, updated.optType);
      updated.tsym = updated.tsym || leg?.tsym;
      updated.exchange = updated.exchange || 'NFO';
    }

    const exit = shouldExit(updated, updated.mode, ltpNow, exitStyle, targetPct, latest.underlying?.ltp);
    console.log(`Should exit result: ${JSON.stringify(exit)}`);

    if (!exit) {
      return {
        ...live,
        current: updated,
        lastDecision: { ts: latest.ts, action: 'HOLD', reasons: ['Holding live trade'] },
      };
    }

    if (!client || typeof client.place_order !== 'function') {
      return { ...live, current: updated, lastDecision: { ts: latest.ts, action: 'ERROR', reasons: ['place_order not available for exit'] } };
    }

    const productType = typeof paper.productType === 'string' ? paper.productType : 'M';
    const expectedQty = updated.qty;
    let netQty = null;
    try {
      netQty = await getNetQtyForSymbol(client, updated.tsym, productType);
    } catch (_e) {
      netQty = null;
    }
    console.log(`Exit netQty check: tsym=${updated.tsym}, productType=${productType}, netQty=${netQty}, expectedQty=${expectedQty}`);

    if (typeof netQty === 'number' && Number.isFinite(netQty) && netQty <= 0) {
      console.warn(`Position lookup returned netqty=${netQty} for ${updated.tsym}. Attempting exit anyway.`);
    }

    console.log(`Placing exit order for ${updated.tsym}, qty=${updated.qty}, reason=${exit.reason}`);

    console.log("EXIT PAYLOAD:", {
      tsym: updated.tsym,
      exchange: updated.exchange
    });

    const exitingBeforePlace = {
      ...updated,
      status: 'EXITING',
      exitReason: exit.reason,
      exitTs: latest.ts,
      exitPrice: isNum(ltpNow) ? ltpNow : null,
      pnl: (isNum(ltpNow) && isNum(updated.entryPrice)) ? (ltpNow - updated.entryPrice) * updated.qty : null,
    };

    const exitPayload = {
      buy_or_sell: 'S',
      product_type: productType,
      exchange: updated.exchange,
      tradingsymbol: updated.tsym,
      quantity: updated.qty,
      discloseqty: 0,
      price_type: 'MKT',
      price: 0,
      trigger_price: 0,
      retention: 'DAY',
      remarks: `exit_${exit.reason}`,
    };

    console.log("Exit order payload:", exitPayload);

    const resExit = await client.place_order(exitPayload);

    console.log('Exit order placed:', resExit);

    if (!resExit || resExit.stat !== 'Ok') {
      return {
        ...live,
        current: exitingBeforePlace,
        lastDecision: { ts: latest.ts, action: 'ERROR', reasons: ['Exit place_order failed', JSON.stringify(resExit)] },
      };
    }

    const exitOrderNo = resExit.norenordno || resExit.orderno || resExit.order_no || null;
    const exiting = {
      ...exitingBeforePlace,
      exitOrderNo,
    };

    // Set to EXITING, confirm complete on next poll
    return {
      ...live,
      current: exiting,
      lastDecision: { ts: latest.ts, action: 'EXITING', reasons: [exit.reason] },
    };
  }

  if (!entryDecision || !entryDecision.ok) {
    return { ...live, lastDecision: { ts: latest.ts, action: 'NO_TRADE', reasons: entryDecision?.reasons || ['No entry'] } };
  }

  // Cooldown after failed entry
  const cooldownMs = parseInt(process.env.TRADE_COOLDOWN_MS || '30000', 10);
  if (live.lastFailedTs && Date.now() - live.lastFailedTs < cooldownMs) {
    return {
      ...live,
      lastDecision: { ts: latest.ts, action: 'COOLDOWN', reasons: ['Waiting after failed entry'] }
    };
  }

  if (!client || typeof client.place_order !== 'function') {
    return { ...live, lastDecision: { ts: latest.ts, action: 'ERROR', reasons: ['Shoonya client.place_order not available'] } };
  }

  const inst = entryDecision.inst;
  const exchange = 'NFO';
  const row = (latest?.rows || []).find((r) => r && r.strike === inst.strike);
  const leg = inst.type === 'CE' ? row?.ce : row?.pe;
  const tradingsymbol = leg?.tsym;
  if (!tradingsymbol) {
    return { ...live, lastDecision: { ts: latest.ts, action: 'ERROR', reasons: [`TradingSymbol missing for ${inst.strike} ${inst.type}`] } };
  }

  const entryLtp = leg?.ltp;

  const buyOrSell = 'B';
  const qty = typeof paper.orderQty === 'number' ? paper.orderQty : Number(paper.orderQty);
  const quantity = Number.isFinite(qty) ? qty : 1;
  const productType = typeof paper.productType === 'string' ? paper.productType : 'M';

  const res = await client.place_order({
    buy_or_sell: buyOrSell,
    product_type: productType,
    exchange,
    tradingsymbol,
    quantity,
    discloseqty: 0,
    price_type: 'MKT',
    price: 0,
    trigger_price: 0,
    retention: 'DAY',
    remarks: `auto_${entryDecision.mode || 'LIVE'}`,
  });

  console.log('Live entry order placed:', res);

  if (!res || res.stat !== 'Ok') {
    return {
      ...live,
      lastDecision: { ts: latest.ts, action: 'ERROR', reasons: ['place_order failed', JSON.stringify(res)] },
      lastFailedTs: Date.now(),
    };
  }

  const orderno = res.norenordno || res.orderno || res.order_no || null;

  console.log("ENTRY DEBUG:", {
    strike: inst.strike,
    type: inst.type,
    tradingsymbol,
    exchange,
  });

  const pendingTrade = {
    id: `live_${latest.ts}`,
    status: 'ENTRY_PENDING',
    mode: entryDecision.mode,
    direction: entryDecision.direction,
    strike: inst.strike,
    optType: inst.type,
    tsym: tradingsymbol,
    exchange: exchange,
    breakoutLevel: (entryDecision && isNum(entryDecision.breakoutLevel))
      ? entryDecision.breakoutLevel
      : (inst.type === 'CE'
        ? latest?.candle5m?.high ?? latest?.underlying?.ltp
        : latest?.candle5m?.low ?? latest?.underlying?.ltp),
    breakoutSource: entryDecision?.breakoutSource || '5M_CANDLE',
    qty: quantity,
    entryTs: latest.ts,
    entryOrderNo: orderno,
  };

  return {
    ...live,
    current: pendingTrade,
    lastDecision: { ts: latest.ts, action: 'ENTRY_PENDING', reasons: ['Waiting for broker fill'] }
  };
}

module.exports = {
  createLiveTradeState,
  stepLiveTrade,
};
