const { storeTrade } = require('./tradeStorage');
const {
  applyApiOrderPricing,
  optionLtpFromSnapshot,
  resolveOrderLtp,
} = require('./orderPricing');
const { getFalseBreakoutBufferPoints } = require('./tradeConfig');
const { normalizeNorenList, matchOrderNo, orderStatusUpper, confirmOrderFill } = require('./norenApi');

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
  const strikeNum = Number(strike);
  const row = (snapshot?.rows || []).find(
    (r) => r && (r.strike === strike || r.strike === strikeNum),
  );
  if (!row) return null;
  return optType === 'CE' ? row.ce : row.pe;
}

/** Fix peak/SL polluted when index LTP was stored as option price. */
function repairCorruptedPeak(trade, underlyingLtp) {
  if (!trade || !isNum(trade.entryPrice) || trade.entryPrice <= 0) return trade;
  const peak = trade.peakPrice;
  if (!isNum(peak) || peak <= 0) return trade;
  const looksLikeIndex =
    peak > 500 &&
    trade.entryPrice < 500 &&
    (!isNum(underlyingLtp) || Math.abs(peak - underlyingLtp) < 200);
  if (!looksLikeIndex) return trade;
  const entryPrice = trade.entryPrice;
  return {
    ...trade,
    peakPrice: entryPrice,
    slPrice: initialSl(entryPrice, trade.mode),
  };
}

/*function initialSl(entryPrice, mode) {
  if (!isNum(entryPrice) || entryPrice <= 0) return null;
  if (mode === 'EXPIRY') return entryPrice * 0.75;
  if (mode === 'NORMAL') return entryPrice * 0.75;
  if (mode === 'BIG_RALLY') return entryPrice * 0.60;
  return entryPrice * 0.70;
}*/

function initialSl(entryPrice, mode) {
  if (!isNum(entryPrice) || entryPrice <= 0) return null;
  const pct = {
    EXPIRY:    parseFloat(process.env.SL_PCT_EXPIRY    || '0.60'),  // 40% loss
    NORMAL:    parseFloat(process.env.SL_PCT_NORMAL    || '0.75'),  // 25% loss
    BIG_RALLY: parseFloat(process.env.SL_PCT_BIG_RALLY || '0.60'),  // 40% loss
  }[mode] ?? parseFloat(process.env.SL_PCT_DEFAULT || '0.70');
  return entryPrice * pct;
}

/**
 * Priority 1: Time-based SL tightening.
 *
 * If the trade has been open for a while and never developed momentum,
 * tighten the SL toward breakeven rather than letting it bleed the full
 * initial 25% loss. Returns the tightened SL price (never lower than baseSl).
 *
 * Thresholds (all configurable via .env):
 *   DEAD_TRADE_GRACE_MS   — how long to wait before judging (default 3 min)
 *   DEAD_TRADE_MOMENTUM   — minimum peak/entry ratio to be considered "alive" (default 1.10)
 *   DEAD_TRADE_SL_PCT     — tightened SL as fraction of entry (default 0.97 = -3%)
 */
function computeDynamicSl(trade, nowTs) {
  const entry = trade.entryPrice;
  const peak  = trade.peakPrice;
  const baseSl = trade.slPrice;

  if (!isNum(entry) || entry <= 0) return baseSl;
  if (!isNum(nowTs) || !isNum(trade.entryTs)) return baseSl;

  const graceMs       = parseInt(process.env.DEAD_TRADE_GRACE_MS  || '180000', 10); // 3 min
  const momentumRatio = parseFloat(process.env.DEAD_TRADE_MOMENTUM || '1.10');
  const tightPct      = parseFloat(process.env.DEAD_TRADE_SL_PCT   || '0.97');

  const ageMs = nowTs - trade.entryTs;
  if (ageMs < graceMs) return baseSl; // still within grace period

  const gotMomentum = isNum(peak) && peak >= entry * momentumRatio;
  if (gotMomentum) return baseSl; // trade proved itself, keep normal SL

  // Dead trade: tighten SL to near breakeven
  const tightSl = entry * tightPct;
  return Math.max(baseSl, tightSl);
}

function updateTrailing(trade, mode, currentLtp) {
  if (!trade || trade.status !== 'OPEN' || !isNum(currentLtp)) return trade;
  const entry = trade.entryPrice;
  if (!isNum(entry) || entry <= 0) return trade;

  const peak = isNum(trade.peakPrice) ? Math.max(trade.peakPrice, currentLtp) : currentLtp;
  let sl = trade.slPrice;
  const mult = currentLtp / entry;

  if (mode === 'BIG_RALLY') {
    // Wide gaps — violent moves need room to breathe.
    // Earlier breakeven at 1.5× vs old 2× to protect against sharp reversals.
    if (mult >= 1.5) sl = Math.max(sl, entry);            // breakeven lock (earlier than before)
    if (mult >= 2)   sl = Math.max(sl, entry);            // keep existing step
    if (mult >= 3)   sl = Math.max(sl, peak * 0.5);       // 50% gap
    if (mult >= 5)   sl = Math.max(sl, peak * 0.6);       // 40% gap
    if (mult >= 10)  sl = Math.max(sl, peak * 0.7);       // 30% gap
  } else if (mode === 'EXPIRY') {
    // 0DTE ATM options — faster breakeven needed due to theta decay.
    // Trail tightens progressively as profit grows (opposite of old paper logic).
    if (mult >= 1.20) sl = Math.max(sl, entry);           // breakeven at 20% (earlier than NORMAL)
    if (mult >= 1.35) sl = Math.max(sl, peak * 0.88);     // 12% gap
    if (mult >= 1.60) sl = Math.max(sl, peak * 0.90);     // tighten to 10% gap
    if (mult >= 2.00) sl = Math.max(sl, peak * 0.92);     // tighten to 8% gap
  } else {
    // NORMAL — progressive trailing that tightens as profit grows.
    // Each step uses peak (not currentLtp) so SL only moves up, never down.
    if (mult >= 1.3) sl = Math.max(sl, entry);            // breakeven lock
    if (mult >= 1.4) sl = Math.max(sl, peak * 0.83);      // protect ~11% if peak=140
    if (mult >= 1.5) sl = Math.max(sl, peak * 0.85);      // protect ~17% if peak=150
    if (mult >= 1.8) sl = Math.max(sl, peak * 0.88);      // protect ~36% if peak=180
    if (mult >= 2.5) sl = Math.max(sl, peak * 0.90);      // protect ~125% if peak=250
    if (mult >= 4.0) sl = Math.max(sl, peak * 0.92);      // protect ~268% if peak=400
  }

  return { ...trade, peakPrice: peak, slPrice: sl };
}

function updatePeakOnly(trade, currentLtp) {
  if (!trade || trade.status !== 'OPEN' || !isNum(currentLtp)) return trade;
  const peak = isNum(trade.peakPrice) ? Math.max(trade.peakPrice, currentLtp) : currentLtp;
  return { ...trade, peakPrice: peak };
}

function shouldExit(trade, mode, currentLtp, exitStyle, targetPct, underlyingLtp, nowTs) {
  if (!trade || trade.status !== 'OPEN' || !isNum(currentLtp)) return null;

  if (exitStyle === 'TARGET' && isNum(trade.entryPrice) && trade.entryPrice > 0) {
    const targetPrice = trade.entryPrice * (1 + targetPct / 100);
    console.log(`TARGET check: entryPrice=${trade.entryPrice}, targetPct=${targetPct}, targetPrice=${targetPrice}, currentLtp=${currentLtp}, hit=${currentLtp >= targetPrice}`);
    if (currentLtp >= targetPrice) return { reason: `TARGET_HIT_${targetPct}` };
  }

  // False breakout exit for NORMAL
  /*if (mode === 'NORMAL' && isNum(trade.breakoutLevel) && isNum(underlyingLtp)) {
    const buffer = getFalseBreakoutBufferPoints();
    if (trade.optType === 'CE' && underlyingLtp < trade.breakoutLevel - buffer) {
      console.log('FALSE BREAKOUT CE', underlyingLtp, trade.breakoutLevel, 'buffer', buffer);
      return { reason: 'FALSE_BREAKOUT', useSlMkt: true };
    }
    if (trade.optType === 'PE' && underlyingLtp > trade.breakoutLevel + buffer) {
      console.log('FALSE BREAKOUT PE', underlyingLtp, trade.breakoutLevel, 'buffer', buffer);
      return { reason: 'FALSE_BREAKOUT', useSlMkt: true };
    }
  }
   // New False breakout exit for NORMAL
	if (
	  mode === 'NORMAL' &&
	  isNum(trade.breakoutLevel) &&
	  isNum(underlyingLtp)
	) {
	  const ageMs = nowTs - trade.entryTs;

	  // Give breakout 60 seconds to establish itself
	  if (ageMs > 60 * 1000) {
		const buffer = getFalseBreakoutBufferPoints();

		if (
		  trade.optType === 'CE' &&
		  underlyingLtp < trade.breakoutLevel - buffer
		) {
		  console.log(
			'FALSE BREAKOUT CE',
			underlyingLtp,
			trade.breakoutLevel,
			'buffer',
			buffer,
			'ageMs',
			ageMs
		  );
		  return { reason: 'FALSE_BREAKOUT', useSlMkt: true };
		}

		if (
		  trade.optType === 'PE' &&
		  underlyingLtp > trade.breakoutLevel + buffer
		) {
		  console.log(
			'FALSE BREAKOUT PE',
			underlyingLtp,
			trade.breakoutLevel,
			'buffer',
			buffer,
			'ageMs',
			ageMs
		  );
		  return { reason: 'FALSE_BREAKOUT', useSlMkt: true };
		}
	  }
	}
  */
  // Dynamic (time-tightened) SL — NORMAL mode only.
  // EXPIRY: options recover fast (tightened SL fired prematurely in practice).
  // BIG_RALLY: expects 2x-10x moves; -3% tightened SL fires on normal fluctuation.
  // Both modes rely on initial SL + updateTrailing only.
  const effectiveSl = (mode === 'NORMAL')
    ? computeDynamicSl(trade, nowTs)
    : trade.slPrice;
  if (isNum(effectiveSl) && currentLtp <= effectiveSl) {
    const isDynamic = mode === 'NORMAL' && isNum(trade.slPrice) && effectiveSl > trade.slPrice;
    console.log(`SL check: mode=${mode}, effectiveSl=${effectiveSl}, baseSl=${trade.slPrice}, dynamic=${isDynamic}, ltp=${currentLtp}`);
    return { reason: isDynamic ? 'SL_HIT_TIGHTENED' : 'SL_HIT', useSlMkt: true };
  }

  // NOTE: TRAIL_FROM_PEAK removed — updateTrailing already moves slPrice to
  // peak * 0.8 at the 1.6x threshold so SL_HIT above handles it. Having both
  // was redundant and could cause confusing double-fire.

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
  const positions = normalizeNorenList(await client.get_positions());
  if (!positions.length) return null;
  const pos = positions.find((p) => p && p.tsym === tsym && p.prd === productType);
  if (!pos) return 0;
  const q = typeof pos.netqty === 'number' ? pos.netqty : Number(pos.netqty);
  if (!Number.isFinite(q)) return null;
  return q;
}

/** Backfill entry/peak/SL from tradebook, orderbook, or open position average. */
async function resolveEntryFromBroker(client, trade, paper) {
  if (!trade || !client) return trade;
  if (isNum(trade.entryPrice) && trade.entryPrice > 0) return trade;

  let entryPrice = null;

  if (trade.entryOrderNo) {
    try {
      const fillCheck = await confirmOrderFill(client, trade.entryOrderNo, { waitMs: 0 });
      if (isNum(fillCheck.entryPrice) && fillCheck.entryPrice > 0) {
        entryPrice = fillCheck.entryPrice;
      }
    } catch (e) {
      console.log('resolveEntryFromBroker order fill:', e.message);
    }
  }

  if (!isNum(entryPrice) && trade.tsym) {
    try {
      const productType = typeof paper?.productType === 'string' ? paper.productType : 'M';
      const positions = normalizeNorenList(await client.get_positions());
      const pos = positions.find(
        (p) => p && p.tsym === trade.tsym && String(p.prd) === String(productType) && Number(p.netqty) > 0,
      );
      if (pos) {
        const avg = parseFloat(pos.dayavgprc ?? pos.netavgprc ?? pos.uploadprice);
        if (isNum(avg) && avg > 0) entryPrice = avg;
      }
    } catch (e) {
      console.log('resolveEntryFromBroker positions:', e.message);
    }
  }

  if (!isNum(entryPrice) || entryPrice <= 0) return trade;

  const repaired = repairCorruptedPeak(trade, null);
  let peakPrice = entryPrice;
  if (isNum(repaired.peakPrice) && repaired.peakPrice > 0) {
    const plausiblePeak =
      repaired.peakPrice < 500 &&
      repaired.peakPrice <= entryPrice * 10 &&
      repaired.peakPrice >= entryPrice * 0.5;
    if (plausiblePeak) peakPrice = Math.max(repaired.peakPrice, entryPrice);
  }
  return {
    ...trade,
    status: trade.status === 'ENTRY_PENDING' ? 'OPEN' : trade.status,
    entryPrice,
    peakPrice,
    slPrice: isNum(trade.slPrice) ? trade.slPrice : initialSl(entryPrice, trade.mode),
  };
}

async function resolveExitPriceFromBroker(client, trade) {
  if (isNum(trade.exitPrice)) return trade.exitPrice;
  if (!client || !trade.exitOrderNo) return null;
  try {
    const orders = normalizeNorenList(await client.get_orderbook());
    const order = orders.find((o) => matchOrderNo(o, trade.exitOrderNo));
    if (order && orderStatusUpper(order) === 'COMPLETE') {
      const p = parseFloat(order.avgprc || order.prc);
      if (isNum(p)) return p;
    }
    const trades = normalizeNorenList(await client.get_tradebook());
    const fill = trades.find((tr) => matchOrderNo(tr, trade.exitOrderNo));
    if (fill?.avgprc != null) {
      const p = parseFloat(fill.avgprc);
      if (isNum(p)) return p;
    }
  } catch (e) {
    console.log('resolveExitPriceFromBroker:', e.message);
  }
  return null;
}

/** Drop stale EXITING/ENTRY_PENDING when broker has no matching order/position. */
async function reconcileLiveTradeWithBroker(client, live, paper) {
  const t = live?.current;
  if (!t || !client) return live;

  const productType = typeof paper?.productType === 'string' ? paper.productType : 'M';
  let netQty = null;
  try {
    netQty = await getNetQtyForSymbol(client, t.tsym, productType);
  } catch (_e) {
    netQty = null;
  }

  const closeAsReconciled = async (reconcileNote) => {
    const exitPrice = (await resolveExitPriceFromBroker(client, t)) ?? t.exitPrice ?? null;
    const exitReason = t.exitReason || reconcileNote;
    const closed = {
      ...t,
      status: 'CLOSED',
      exitTs: t.exitTs || Date.now(),
      exitReason,
      exitPrice,
      pnl:
        isNum(exitPrice) && isNum(t.entryPrice)
          ? (exitPrice - t.entryPrice) * t.qty
          : t.pnl ?? null,
    };
    storeTrade(closed);
    const reasons = t.exitReason ? [t.exitReason, reconcileNote] : [reconcileNote];
    return {
      ...live,
      current: null,
      lastClosed: closed,
      history: [...(live.history || []), closed].slice(-50),
      lastExitTs: Date.now(),
      lastDecision: {
        ts: Date.now(),
        action: t.exitReason || 'RECONCILED',
        reasons,
      },
    };
  };

  const reopen = (reason) => ({
    ...live,
    current: {
      ...t,
      status: 'OPEN',
      exitOrderNo: null,
      exitReason: null,
      exitTs: null,
      exitPrice: null,
    },
    lastDecision: { ts: Date.now(), action: 'REOPEN', reasons: [reason] },
  });

  if (t.status === 'EXITING') {
    if (netQty === 0) {
      return await closeAsReconciled('RECONCILED_NO_POSITION');
    }

    if (!t.exitOrderNo) {
      return reopen('EXITING without exit order number (stale local state)');
    }

    try {
      const orders = normalizeNorenList(await client.get_orderbook());
      const order = orders.find((o) => matchOrderNo(o, t.exitOrderNo));
      if (!order) {
        if (netQty === 0) return await closeAsReconciled('RECONCILED_NO_POSITION');
        return reopen('Exit order not found in order book');
      }
      const st = orderStatusUpper(order);
      if (st === 'COMPLETE') {
        const exitPrice = parseFloat(order.avgprc || order.prc);
        const closed = {
          ...t,
          status: 'CLOSED',
          exitPrice: Number.isFinite(exitPrice) ? exitPrice : t.exitPrice,
          exitTs: t.exitTs || Date.now(),
          exitReason: t.exitReason || 'EXIT_FILLED',
          pnl:
            Number.isFinite(exitPrice) && isNum(t.entryPrice)
              ? (exitPrice - t.entryPrice) * t.qty
              : t.pnl,
        };
        storeTrade(closed);
        return {
          ...live,
          current: null,
          lastClosed: closed,
          history: [...(live.history || []), closed].slice(-50),
          lastExitTs: Date.now(),
        };
      }
      if (st === 'REJECTED' || st === 'CANCELLED') {
        return reopen(`Exit order ${st}`);
      }
    } catch (e) {
      console.log('reconcile EXITING:', e.message);
    }
    return live;
  }

  if (t.status === 'ENTRY_PENDING') {
    if (netQty != null && netQty > 0) {
      const current = await resolveEntryFromBroker(client, { ...t, status: 'OPEN' }, paper);
      return {
        ...live,
        current,
        lastDecision: {
          ts: Date.now(),
          action: 'RECONCILED',
          reasons: [
            current.entryPrice != null ? 'Position open at broker' : 'Position open; entry price pending',
          ],
        },
      };
    }

    if (!t.entryOrderNo) {
      return { ...live, current: null, lastDecision: { ts: Date.now(), action: 'RECONCILED', reasons: ['Cleared stale ENTRY_PENDING'] } };
    }

    try {
      const orders = normalizeNorenList(await client.get_orderbook());
      const order = orders.find((o) => matchOrderNo(o, t.entryOrderNo));
      if (!order) {
        return { ...live, current: null, lastDecision: { ts: Date.now(), action: 'RECONCILED', reasons: ['Entry order not in order book'] } };
      }
      const st = orderStatusUpper(order);
      if (st === 'REJECTED' || st === 'CANCELLED') {
        return { ...live, current: null, lastDecision: { ts: Date.now(), action: 'RECONCILED', reasons: [`Entry ${st}`] } };
      }
    } catch (e) {
      console.log('reconcile ENTRY_PENDING:', e.message);
    }
  }

  if (t.status === 'OPEN' && !isNum(t.entryPrice)) {
    const current = await resolveEntryFromBroker(client, t, paper);
    if (current.entryPrice != null) {
      return {
        ...live,
        current,
        lastDecision: { ts: Date.now(), action: 'RECONCILED', reasons: ['Entry price backfilled from broker'] },
      };
    }
  }

  return live;
}

async function stepLiveTrade(ctx) {
  const {
    client,
    paper,
    snapshotHistory,
    entryDecision,
  } = ctx;
  let { live } = ctx;

  console.log('stepLiveTrade called, live.current:', live.current ? { status: live.current.status, id: live.current.id } : 'null');

  const latest = Array.isArray(snapshotHistory) ? snapshotHistory[snapshotHistory.length - 1] : null;
  if (!latest) return live;

  if (!paper || paper.tradeMode !== 'LIVE') return live;
  if (!paper.liveArmed) {
    return { ...live, lastDecision: { ts: latest.ts, action: 'NO_TRADE', reasons: ['LIVE not armed'] } };
  }

  live = await reconcileLiveTradeWithBroker(client, live, paper);

  if (live.current && !isNum(live.current.entryPrice)) {
    live.current = await resolveEntryFromBroker(client, live.current, paper);
  }

  // Convert ENTRY_PENDING → OPEN on next poll (or backfill OPEN missing entry)
  if (live.current && live.current.status === 'ENTRY_PENDING') {
    try {
      const orders = normalizeNorenList(await client.get_orderbook());
      const order = orders.find((o) => matchOrderNo(o, live.current.entryOrderNo));
      const st = order ? orderStatusUpper(order) : '';

      if (st === 'REJECTED' || st === 'CANCELLED') {
        live.current = null;
        return { ...live, lastDecision: { ts: latest.ts, action: 'ERROR', reasons: ['Entry order rejected or cancelled by broker'] } };
      }

      if (st === 'COMPLETE' || !isNum(live.current.entryPrice)) {
        live.current = await resolveEntryFromBroker(client, live.current, paper);
      }
    } catch (e) {
      console.log('Error checking pending order fill:', e.message);
    }
    if (live.current?.status === 'ENTRY_PENDING') {
      return live;
    }
  }

  // Convert EXITING → CLOSED On Next Poll
  if (live.current && live.current.status === 'EXITING') {
    try {
      const orders = normalizeNorenList(await client.get_orderbook());
      const order = orders.find((o) => String(o.norenordno) === String(live.current.exitOrderNo));

      if (order && orderStatusUpper(order) === 'COMPLETE') {
        let exitPrice = parseFloat(order.avgprc || order.prc);
        const trades = normalizeNorenList(await client.get_tradebook());
        const fill = trades.find((tr) => matchOrderNo(tr, live.current.exitOrderNo));
        if (fill?.avgprc != null) exitPrice = parseFloat(fill.avgprc);
        if (!Number.isFinite(exitPrice)) exitPrice = live.current.exitPrice;

        const closed = {
          ...live.current,
          status: 'CLOSED',
          exitTs: live.current.exitTs || latest.ts,
          exitReason: live.current.exitReason || 'EXIT_FILLED',
          exitPrice,
          pnl:
            isNum(exitPrice) && isNum(live.current.entryPrice)
              ? (exitPrice - live.current.entryPrice) * live.current.qty
              : null,
        };

        storeTrade(closed);

        live.current = null;
        live.lastClosed = closed;
        live.history = [...live.history, closed].slice(-50);
        live.lastExitTs = Date.now();
        live.lastDecision = {
          ts: latest.ts,
          action: closed.exitReason,
          reasons: [closed.exitReason],
        };
      } else if (order && ['REJECTED', 'CANCELLED'].includes(orderStatusUpper(order))) {
        live.current = {
          ...live.current,
          status: 'OPEN',
          exitOrderNo: null,
          exitReason: null,
          exitTs: null,
          exitPrice: null,
        };
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
    let t = repairCorruptedPeak(live.current, latest.underlying?.ltp);
    const ltpNow = optionLtpFromSnapshot(latest, t);
    console.log(`Exit check: trade ${t.id} status=${t.status}, entryPrice=${t.entryPrice}, slPrice=${t.slPrice}, peak=${t.peakPrice}, currentLtp=${ltpNow}, strike=${t.strike}, optType=${t.optType}`);

    // If we already sent an exit order, don't place again.
    if (t.status === 'EXITING') {
      return { ...live, lastDecision: { ts: latest.ts, action: 'EXITING', reasons: [t.exitReason || 'EXITING'] } };
    }

    const updated = exitStyle === 'TRAILING'
      ? updateTrailing(t, t.mode, ltpNow)
      : updatePeakOnly(t, ltpNow);
    live = { ...live, current: updated };
    t = updated;

    if (!updated.tsym || !updated.exchange) {
      const leg = findLeg(latest, updated.strike, updated.optType);
      updated.tsym = updated.tsym || leg?.tsym;
      updated.exchange = updated.exchange || 'NFO';
    }

    const exit = shouldExit(updated, updated.mode, ltpNow, exitStyle, targetPct, latest.underlying?.ltp, latest.ts);
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

    const orderLtp = resolveOrderLtp(latest, updated, latest.underlying?.ltp, {
      allowEntryFallback: true,
    });
    if (!isNum(orderLtp)) {
      console.warn(`Deferring auto exit for ${updated.tsym}: no valid option LTP (chain may be stale)`);
      return {
        ...live,
        current: updated,
        lastDecision: {
          ts: latest.ts,
          action: 'WAIT_LTP',
          reasons: [`Exit ${exit.reason} pending — waiting for valid option LTP before placing sell`],
        },
      };
    }

    console.log(`Placing exit order for ${updated.tsym}, qty=${updated.qty}, reason=${exit.reason}, orderLtp=${orderLtp}, useSlMkt=${!!exit.useSlMkt}`);

    const exitingBeforePlace = {
      ...updated,
      status: 'EXITING',
      exitReason: exit.reason,
      exitTs: latest.ts,
      exitPrice: orderLtp,
      pnl: isNum(updated.entryPrice) ? (orderLtp - updated.entryPrice) * updated.qty : null,
    };

    let exitPayload;
    try {
      exitPayload = applyApiOrderPricing({
        buy_or_sell: 'S',
        product_type: productType,
        exchange: updated.exchange,
        tradingsymbol: updated.tsym,
        quantity: updated.qty,
        discloseqty: 0,
        // SL exits (SL_HIT, SL_HIT_TIGHTENED, FALSE_BREAKOUT) use SL-MKT for
        // guaranteed fill even when price moves fast between polls.
        // Target/trail exits use MKT (→ LMT with slippage) since price is
        // moving in our favour and a limit order will fill fine.
        price_type: 'MKT',
        price: 0,
        trigger_price: 0,
        retention: 'DAY',
        remarks: `exit_${exit.reason}`,
        ltp: orderLtp,
        entryPrice: updated.entryPrice,
        underlyingLtp: latest.underlying?.ltp,
      });
    } catch (pricingErr) {
      return {
        ...live,
        current: updated,
        lastDecision: {
          ts: latest.ts,
          action: 'ERROR',
          reasons: [pricingErr?.message || String(pricingErr)],
        },
      };
    }

    console.log('Exit order payload:', { tsym: updated.tsym, price: exitPayload.price, price_type: exitPayload.price_type });

    const resExit = await client.place_order(exitPayload);

    console.log('Exit order placed:', resExit);

    if (!resExit || resExit.stat !== 'Ok') {
      return {
        ...live,
        current: updated,
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

  const entryLtp = resolveOrderLtp(latest, { tsym: tradingsymbol, strike: inst.strike, optType: inst.type }, latest.underlying?.ltp);
  if (!isNum(entryLtp)) {
    return {
      ...live,
      lastDecision: { ts: latest.ts, action: 'ERROR', reasons: ['Valid option LTP missing for entry order'] },
      lastFailedTs: Date.now(),
    };
  }

  const buyOrSell = 'B';
  const qty = typeof paper.orderQty === 'number' ? paper.orderQty : Number(paper.orderQty);
  const quantity = Number.isFinite(qty) ? qty : 1;
  const productType = typeof paper.productType === 'string' ? paper.productType : 'M';

  let entryPayload;
  try {
    entryPayload = applyApiOrderPricing({
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
      ltp: entryLtp,
      underlyingLtp: latest.underlying?.ltp,
    });
  } catch (pricingErr) {
    return {
      ...live,
      lastDecision: { ts: latest.ts, action: 'ERROR', reasons: [pricingErr?.message || String(pricingErr)] },
      lastFailedTs: Date.now(),
    };
  }

  const res = await client.place_order(entryPayload);

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
  reconcileLiveTradeWithBroker,
};
