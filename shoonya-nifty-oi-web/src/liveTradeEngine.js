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
  if (mode === 'NORMAL') return entryPrice * 0.70;
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

function shouldExit(trade, mode, currentLtp, exitStyle, targetPct) {
  if (!trade || trade.status !== 'OPEN' || !isNum(currentLtp)) return null;

  if (exitStyle === 'TARGET' && isNum(trade.entryPrice) && trade.entryPrice > 0) {
    const targetPrice = trade.entryPrice * (1 + targetPct / 100);
    if (currentLtp >= targetPrice) return { reason: `TARGET_HIT_${targetPct}` };
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

  const latest = Array.isArray(snapshotHistory) ? snapshotHistory[snapshotHistory.length - 1] : null;
  if (!latest) return live;

  if (!paper || paper.tradeMode !== 'LIVE') return live;
  if (!paper.liveArmed) {
    return { ...live, lastDecision: { ts: latest.ts, action: 'NO_TRADE', reasons: ['LIVE not armed'] } };
  }

  const exitStyle = normalizeExitStyle(paper.exitStyle);
  const targetPct = normalizeTargetPct(paper.targetPct);

  if (live.current && (live.current.status === 'OPEN' || live.current.status === 'EXITING')) {
    const t = live.current;
    const legNow = findLeg(latest, t.strike, t.optType);
    const ltpNow = legNow?.ltp;

    // If we already sent an exit order, don't place again.
    if (t.status === 'EXITING') {
      return { ...live, lastDecision: { ts: latest.ts, action: 'EXITING', reasons: [t.exitReason || 'EXITING'] } };
    }

    const updated = exitStyle === 'TRAILING'
      ? updateTrailing(t, t.mode, ltpNow)
      : updatePeakOnly(t, ltpNow);

    const exit = shouldExit(updated, updated.mode, ltpNow, exitStyle, targetPct);
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

    if (netQty === 0) {
      return {
        ...live,
        current: updated,
        lastDecision: { ts: latest.ts, action: 'SKIP_EXIT', reasons: ['No open position (netqty=0), skipping exit'] },
      };
    }

    if (typeof netQty === 'number' && Number.isFinite(netQty) && Number.isFinite(expectedQty) && netQty !== expectedQty) {
      return {
        ...live,
        current: updated,
        lastDecision: {
          ts: latest.ts,
          action: 'BLOCK_EXIT',
          reasons: [`Position mismatch for ${updated.tsym} (${productType}): netqty=${netQty}, expected=${expectedQty}. Manual lots detected; refusing to auto-exit.`],
        },
      };
    }

    const exitingBeforePlace = {
      ...updated,
      status: 'EXITING',
      exitReason: exit.reason,
      exitTs: latest.ts,
      exitPrice: isNum(ltpNow) ? ltpNow : null,
      pnl: (isNum(ltpNow) && isNum(updated.entryPrice)) ? (ltpNow - updated.entryPrice) * updated.qty : null,
    };

    const resExit = await client.place_order({
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
    });

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

    // For now we mark it CLOSED immediately after sending MKT exit.
    const closed = { ...exiting, status: 'CLOSED' };
    storeTrade(closed);
    return {
      ...live,
      current: null,
      lastClosed: closed,
      history: [...live.history, closed].slice(-50),
      lastExitTs: Date.now(),
      lastDecision: { ts: latest.ts, action: 'EXIT', reasons: [exit.reason] },
    };
  }

  if (!entryDecision || !entryDecision.ok) {
    return { ...live, lastDecision: { ts: latest.ts, action: 'NO_TRADE', reasons: entryDecision?.reasons || ['No entry'] } };
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
      lastRejectTs: Date.now(),
    };
  }

  const orderno = res.norenordno || res.orderno || res.order_no || null;

  // Confirm order fill
  if (orderno) {
    await new Promise(resolve => setTimeout(resolve, 2000)); // Wait 2 seconds for potential fill
    try {
      const trades = await client.get_tradebook();
      const entryTrades = trades.filter(t => t.norenordno === orderno);
      console.log('Fill check: trades length', trades ? trades.length : 'null', 'entryTrades length', entryTrades.length);
      if (!trades || !Array.isArray(trades) || entryTrades.length === 0) {
        console.log('Order not filled, setting lastRejectTs');
        return {
          ...live,
          lastDecision: { ts: latest.ts, action: 'ERROR', reasons: ['Entry order placed but not filled within 2s', `OrderNo: ${orderno}`] },
          lastRejectTs: Date.now(),
        };
      }
    } catch (e) {
      console.log('Fill check error:', e.message);
      return {
        ...live,
        lastDecision: { ts: latest.ts, action: 'ERROR', reasons: ['Failed to confirm entry order fill', e && e.message ? e.message : String(e)] },
        lastRejectTs: Date.now(),
      };
    }
  }
  console.log('Creating live trade for order', orderno);
  const opened = {
    id: `live_${latest.ts}`,
    status: 'OPEN',
    mode: entryDecision.mode,
    direction: entryDecision.direction,
    strike: inst.strike,
    optType: inst.type,
    qty: quantity,
    entryPrice,
    entryTs: latest.ts,
    peakPrice: entryPrice,
    slPrice: sl,
    exitPrice: null,
    exitTs: null,
    exitReason: null,
    pnl: 0,
    entryOrderNo: orderno,
    reasons: entryDecision.reasons,
  };

  console.log('Live trade entered:', opened);

  return {
    ...live,
    current: trade,
    lastDecision: { ts: latest.ts, action: 'ENTER', reasons: entryDecision.reasons },
  };
}

module.exports = {
  createLiveTradeState,
  stepLiveTrade,
};
