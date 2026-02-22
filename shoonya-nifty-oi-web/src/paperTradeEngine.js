function isNum(x) {
  return typeof x === 'number' && !Number.isNaN(x);
}

 function passFailSpan(ok) {
   return `<span class="${ok ? 'pass' : 'fail'}">${ok ? 'yes' : 'no'}</span>`;
 }

function normalizeOrderQty(v) {
  const n = typeof v === 'number' ? v : Number(v);
  if (!Number.isFinite(n)) return 1;
  return Math.max(1, Math.round(n));
}

function normalizeProductType(v) {
  const s = typeof v === 'string' ? v.trim().toUpperCase() : '';
  return s || 'M';
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

function dayKeyFromTs(ts) {
  if (!isNum(ts)) return null;
  const d = new Date(ts);
  if (Number.isNaN(d.getTime())) return null;
  const y = d.getFullYear();
  const m = String(d.getMonth() + 1).padStart(2, '0');
  const dd = String(d.getDate()).padStart(2, '0');
  return `${y}-${m}-${dd}`;
}

function findLeg(snapshot, strike, type) {
  if (!snapshot || !Array.isArray(snapshot.rows)) return null;
  const row = snapshot.rows.find((r) => r && r.strike === strike);
  if (!row) return null;
  return type === 'CE' ? row.ce : row.pe;
}

function strikeStepFromSnapshot(snapshot) {
  if (!snapshot || !Array.isArray(snapshot.rows) || snapshot.rows.length < 2) return 50;
  const strikes = snapshot.rows.map((r) => r.strike).filter(isNum).sort((a, b) => a - b);
  for (let i = 1; i < strikes.length; i += 1) {
    const d = strikes[i] - strikes[i - 1];
    if (d > 0) return d;
  }
  return 50;
}

function selectInstrument(snapshot, mode, direction) {
  const step = strikeStepFromSnapshot(snapshot);
  const atm = snapshot?.atmStrike;
  if (!isNum(atm)) return null;

  if (mode === 'BIG_RALLY') {
    // 2-step OTM option based on direction
    if (direction === 'BEAR') {
      const strike = atm - 2 * step;
      return { strike, type: 'PE' };
    }
    const strike = atm + 2 * step;
    return { strike, type: 'CE' };
  }

  if (mode === 'NORMAL') {
    // 1-step OTM option based on direction
    if (direction === 'BEAR') {
      const strike = atm - 1 * step;
      return { strike, type: 'PE' };
    }
    const strike = atm + 1 * step;
    return { strike, type: 'CE' };
  }

  // EXPIRY: trade ATM in the direction
  return { strike: atm, type: direction === 'BULL' ? 'CE' : 'PE' };
}

function pctChange(now, then) {
  if (!isNum(now) || !isNum(then) || then <= 0) return null;
  return (now - then) / then;
}

function fmtNum(x, digits = 2) {
  if (!isNum(x)) return '-';
  const d = Number(digits);
  if (!Number.isFinite(d) || d < 0) return String(x);
  return x.toFixed(d);
}

function fmtPct(p, digits = 1) {
  if (!isNum(p)) return '-';
  return `${(p * 100).toFixed(digits)}%`;
}

function pickSnapshotAtOrBefore(history, ts) {
  if (!Array.isArray(history) || history.length === 0 || !isNum(ts)) return null;
  for (let i = history.length - 1; i >= 0; i -= 1) {
    const s = history[i];
    if (s && isNum(s.ts) && s.ts <= ts) return s;
  }
  return history[0] || null;
}

function computeDirectionSignal(history, mode, state) {
  const latest = history[history.length - 1];
  const under = latest?.underlying?.ltp;
  const atm = latest?.atmStrike;
  if (!isNum(under) || !isNum(atm)) {
    return { direction: null, reasons: ['Underlying/ATM missing'] };
  }

  const reasons = [];

  const ov = typeof state?.directionOverride === 'string' ? state.directionOverride.trim().toUpperCase() : '';
  if (ov === 'BULL' || ov === 'BEAR') {
    reasons.push(`Direction override: ${ov}`);
    return { direction: ov, reasons };
  }

  // Use the existing levels as a lightweight direction hint
  const resist = latest?.levels?.resistanceStrike;
  const support = latest?.levels?.supportStrike;

  // Simple heuristic: if price is above ATM => bullish bias, below ATM => bearish bias.
  if (under >= atm) {
    reasons.push('Underlying >= ATM');
    if (isNum(resist) && Math.abs(resist - under) <= 50) reasons.push('Near resistance');
    return { direction: 'BULL', reasons };
  }

  reasons.push('Underlying < ATM');
  if (isNum(support) && Math.abs(under - support) <= 50) reasons.push('Near support');
  return { direction: 'BEAR', reasons };
}

function shouldEnter(history, mode, state) {
  const latest = history[history.length - 1];
  const nowTs = latest?.ts;
  if (!isNum(nowTs)) return { ok: false, reasons: ['Snapshot timestamp missing'] };

  // Percent-change based checks from the chat notes.
  const lookbackMs = mode === 'EXPIRY' ? 5 * 60_000 : 5 * 60_000;
  const pastSnap = pickSnapshotAtOrBefore(history, nowTs - lookbackMs);

  const { direction, reasons: dirReasons } = computeDirectionSignal(history, mode, state);
  if (!direction) return { ok: false, reasons: dirReasons };

  const inst = selectInstrument(latest, mode, direction);
  if (!inst) return { ok: false, reasons: ['Instrument selection failed'] };

  const legNow = findLeg(latest, inst.strike, inst.type);
  const legPast = pastSnap ? findLeg(pastSnap, inst.strike, inst.type) : null;

  const ltpNow = legNow?.ltp;
  const ltpPast = legPast?.ltp;
  const chg = pctChange(ltpNow, ltpPast);

  const reasons = [...dirReasons];
  reasons.push(`Mode: ${mode}`);
  reasons.push(`Instrument: ${inst.strike} ${inst.type}`);

  // Entry thresholds from notes
  if (mode === 'EXPIRY') {
    // Require ~50% move in 5 min
    if (chg === null) return { ok: false, reasons: [...reasons, 'Not enough data for 5-min price change'] };
    if (chg >= 0.5) return { ok: true, direction, inst, reasons: [...reasons, `5m change ${(chg * 100).toFixed(1)}% >= 50%`] };
    return { ok: false, reasons: [...reasons, `5m change ${(chg * 100).toFixed(1)}% < 50%`] };
  }

  if (mode === 'NORMAL') {
    const underNow = latest?.underlying?.ltp;
    const atmStrike = latest?.atmStrike;

    if (!isNum(underNow)) return { ok: false, reasons: [...reasons, 'Underlying LTP missing'] };

    // Prefer TPS-derived 5m candle extremes (last completed candle).
    // Fallback to snapshot-derived extremes when TPS is not available.
    let extremeHigh5 = null;
    let extremeLow5 = null;
    let extremeSource = 'snapshot';
    if (latest && latest.candle5m && isNum(latest.candle5m.high) && isNum(latest.candle5m.low)) {
      extremeHigh5 = latest.candle5m.high;
      extremeLow5 = latest.candle5m.low;
      extremeSource = 'tps';
    } else {
      let hi = -Infinity;
      let lo = Infinity;
      for (let i = history.length - 2; i >= 0; i -= 1) {
        const s = history[i];
        if (!s || !isNum(s.ts) || s.ts < nowTs - 5 * 60_000) break;
        const ul = s.underlying?.ltp;
        if (!isNum(ul)) continue;
        hi = Math.max(hi, ul);
        lo = Math.min(lo, ul);
      }
      extremeHigh5 = hi;
      extremeLow5 = lo;
    }

    reasons.push(`5m high/low source: ${extremeSource === 'tps' ? 'TPSeries (last completed 5m candle)' : 'snapshot fallback'}`);

    const breaksHigh = underNow > extremeHigh5;
    const breaksLow = underNow < extremeLow5;

    const past2 = pickSnapshotAtOrBefore(history, nowTs - 2 * 60_000);

    const ceNow = isNum(atmStrike) ? findLeg(latest, atmStrike, 'CE') : null;
    const peNow = isNum(atmStrike) ? findLeg(latest, atmStrike, 'PE') : null;
    const cePast = (past2 && isNum(atmStrike)) ? findLeg(past2, atmStrike, 'CE') : null;
    const pePast = (past2 && isNum(atmStrike)) ? findLeg(past2, atmStrike, 'PE') : null;

    const ceChg = pctChange(ceNow?.ltp, cePast?.ltp);
    const peChg = pctChange(peNow?.ltp, pePast?.ltp);

    const ceCond = ceChg !== null && ceChg >= 0.12;
    const peCond = peChg !== null && peChg >= 0.12;

    reasons.push(`Breaks 5m high: ${passFailSpan(breaksHigh)} (LTP ${fmtNum(underNow, 2)} vs high ${fmtNum(extremeHigh5, 2)})`);
    reasons.push(`ATM CE >=12% (2m): ${passFailSpan(ceCond)} (${fmtPct(ceChg, 1)} | now ${fmtNum(ceNow?.ltp, 2)} vs 2m ${fmtNum(cePast?.ltp, 2)})`);
    reasons.push(`Breaks 5m low: ${passFailSpan(breaksLow)} (LTP ${fmtNum(underNow, 2)} vs low ${fmtNum(extremeLow5, 2)})`);
    reasons.push(`ATM PE >=12% (2m): ${passFailSpan(peCond)} (${fmtPct(peChg, 1)} | now ${fmtNum(peNow?.ltp, 2)} vs 2m ${fmtNum(pePast?.ltp, 2)})`);

    if (breaksHigh && ceCond) {
      const direction = 'BULL';
      const inst = selectInstrument(latest, mode, direction);
      const trade = {
        id: `paper_${latest.ts}`,
        status: 'OPEN',
        mode,
        strike: inst.strike,
        optType: inst.type,
        qty: normalizeOrderQty(state.orderQty),
        entryPrice: entryPrice,
        entryTs: latest.ts,
        peakPrice: entryPrice,
        slPrice: initialSl(entryPrice, mode),
        exitPrice: null,
        exitTs: null,
        exitReason: null,
        pnl: 0,
        reasons: reasons,
        breakoutLevel: extremeHigh5,
      };
      return { ok: true, direction, inst, reasons };
    } else if (breaksLow && peCond) {
      const direction = 'BEAR';
      const inst = selectInstrument(latest, mode, direction);
      const trade = {
        id: `paper_${latest.ts}`,
        status: 'OPEN',
        mode,
        strike: inst.strike,
        optType: inst.type,
        qty: normalizeOrderQty(state.orderQty),
        entryPrice: entryPrice,
        entryTs: latest.ts,
        peakPrice: entryPrice,
        slPrice: initialSl(entryPrice, mode),
        exitPrice: null,
        exitTs: null,
        exitReason: null,
        pnl: 0,
        reasons: reasons,
        breakoutLevel: extremeLow5,
      };
      return { ok: true, direction, inst, reasons };
    } else {
      return { ok: false, reasons: [...reasons, 'Conditions not met'] };
    }
  }

  if (mode === 'BIG_RALLY') {
    // BIG_RALLY entry: allow entry when selected 2-step OTM option doubles in 10 minutes.
    const past10 = pickSnapshotAtOrBefore(history, nowTs - 10 * 60_000);
    const legPast10 = past10 ? findLeg(past10, inst.strike, inst.type) : null;
    const chg10 = pctChange(legNow?.ltp, legPast10?.ltp);
    if (chg10 === null) return { ok: false, reasons: [...reasons, 'Not enough data for 10-min change'] };
    if (chg10 >= 1.0) return { ok: true, direction, inst, reasons: [...reasons, `10m change ${(chg10 * 100).toFixed(1)}% >= 100%`] };
    return { ok: false, reasons: [...reasons, `10m change ${(chg10 * 100).toFixed(1)}% < 100%`] };
  }

  return { ok: false, reasons: [...reasons, 'Unknown mode'] };
}

function computeAutoMode(latest, history, state) {
  const now = latest?.ts;
  const under = latest?.underlying?.ltp;
  if (!isNum(now) || !isNum(under)) return 'NORMAL';

  // Expiry detection: not implemented (needs expiry calendar). Keep manual selection for now.
  // BIG_RALLY detection: basic check from notes.
  const open = state?.dayOpenPrice;
  const pctFromOpen = isNum(open) && open > 0 ? Math.abs(under - open) / open : null;

  // Condition: move > 1% from open
  const condMove = pctFromOpen !== null && pctFromOpen > 0.01;

  // Condition: 2-step OTM option (CE/PE based on direction) doubled in last 10 mins
  const step = strikeStepFromSnapshot(latest);
  const atm = latest?.atmStrike;
  const { direction } = computeDirectionSignal(history, 'NORMAL', state);
  const strike = isNum(atm) ? (direction === 'BEAR' ? (atm - 2 * step) : (atm + 2 * step)) : null;
  const optType = direction === 'BEAR' ? 'PE' : 'CE';
  const past10 = pickSnapshotAtOrBefore(history, now - 10 * 60_000);
  const legNow = isNum(strike) ? findLeg(latest, strike, optType) : null;
  const legPast10 = isNum(strike) && past10 ? findLeg(past10, strike, optType) : null;
  const chg10 = pctChange(legNow?.ltp, legPast10?.ltp);
  const condDouble = chg10 !== null && chg10 >= 1.0;

  if ((condMove ? 1 : 0) + (condDouble ? 1 : 0) >= 2) return 'BIG_RALLY';
  return 'NORMAL';
}

function updateTrailing(trade, mode, currentLtp) {
  if (!trade || trade.status !== 'OPEN' || !isNum(currentLtp)) return trade;

  const entry = trade.entryPrice;
  if (!isNum(entry) || entry <= 0) return trade;

  const peak = isNum(trade.peakPrice) ? Math.max(trade.peakPrice, currentLtp) : currentLtp;
  let sl = trade.slPrice;

  const mult = currentLtp / entry;

  if (mode === 'BIG_RALLY') {
    // Step-based trailing
    if (mult >= 2) sl = Math.max(sl, entry);
    if (mult >= 3) sl = Math.max(sl, peak * 0.5);
    if (mult >= 5) sl = Math.max(sl, peak * 0.6);
    if (mult >= 10) sl = Math.max(sl, peak * 0.7);
  } else {
    // Normal/expiry
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

function maybeExit(trade, mode, currentLtp, exitCfg) {
  if (!trade || trade.status !== 'OPEN' || !isNum(currentLtp)) return null;

  const exitStyle = normalizeExitStyle(exitCfg?.exitStyle || 'TRAILING');
  const targetPct = normalizeTargetPct(exitCfg?.targetPct);

  // Target profit exit (only when configured)
  if (exitStyle === 'TARGET' && isNum(trade.entryPrice) && trade.entryPrice > 0) {
    const targetPrice = trade.entryPrice * (1 + targetPct / 100);
    if (currentLtp >= targetPrice) return { reason: `TARGET_HIT_${targetPct}` };
  }

  // False breakout exit for NORMAL
  if (trade.mode === 'NORMAL' && isNum(trade.breakoutLevel)) {
    if (trade.optType === 'CE' && currentLtp < trade.breakoutLevel - 10) {
      return { reason: 'FALSE_BREAKOUT' };
    }
    if (trade.optType === 'PE' && currentLtp > trade.breakoutLevel + 10) {
      return { reason: 'FALSE_BREAKOUT' };
    }
  }

  // SL hit
  if (isNum(trade.slPrice) && currentLtp <= trade.slPrice) {
    return { reason: 'SL_HIT' };
  }

  // Peak trail exit for normal/expiry (20% from peak after >=60% profit)
  const entry = trade.entryPrice;
  const peak = trade.peakPrice;
  if (mode !== 'BIG_RALLY' && isNum(entry) && isNum(peak) && peak > 0) {
    const mult = peak / entry;
    if (mult >= 1.6 && currentLtp <= peak * 0.8) {
      return { reason: 'TRAIL_FROM_PEAK' };
    }
  }

  return null;
}

function initialSl(entryPrice, mode) {
  if (!isNum(entryPrice) || entryPrice <= 0) return null;
  if (mode === 'EXPIRY') return entryPrice * 0.75; // ~25%
  if (mode === 'NORMAL') return entryPrice * 0.70; // ~30%
  if (mode === 'BIG_RALLY') return entryPrice * 0.60; // looser
  return entryPrice * 0.70;
}

function stepPaperTrade(state, snapshotHistory, selectedMode) {
  const latest = snapshotHistory[snapshotHistory.length - 1];
  if (!latest) return state;

  // reset daily counters
  const todayKey = dayKeyFromTs(latest.ts);
  if (todayKey && state.tradesDate !== todayKey) {
    state.tradesDate = todayKey;
    state.tradesToday = 0;
  }

  // initialize day open
  if (!isNum(state.dayOpenPrice) && isNum(latest?.underlying?.ltp)) {
    state.dayOpenPrice = latest.underlying.ltp;
  }

  const mode = selectedMode === 'AUTO'
    ? computeAutoMode(latest, snapshotHistory, state)
    : selectedMode;

  const currentTrade = state.currentTrade;

  // Update existing trade
  if (currentTrade && currentTrade.status === 'OPEN') {
    const legNow = findLeg(latest, currentTrade.strike, currentTrade.optType);
    const ltpNow = legNow?.ltp;

    const exitStyle = normalizeExitStyle(state.exitStyle);
    const updated = exitStyle === 'TRAILING'
      ? updateTrailing(currentTrade, mode, ltpNow)
      : updatePeakOnly(currentTrade, ltpNow);
    const exit = maybeExit(updated, mode, ltpNow, { exitStyle: state.exitStyle, targetPct: state.targetPct });

    if (exit) {
      const exitPrice = isNum(ltpNow) ? ltpNow : updated.entryPrice;
      const pnl = (exitPrice - updated.entryPrice) * updated.qty;
      const closed = {
        ...updated,
        status: 'CLOSED',
        exitPrice,
        exitTs: latest.ts,
        exitReason: exit.reason,
        pnl,
      };
      return {
        ...state,
        selectedMode,
        effectiveMode: mode,
        currentTrade: closed,
        tradeHistory: [...state.tradeHistory, closed].slice(-50),
      };
    }

    return { ...state, selectedMode, effectiveMode: mode, currentTrade: updated };
  }

  // Entry
  const entryCheck = shouldEnter(snapshotHistory, mode, state);

  const maxTrades = isNum(state.maxTradesPerDay) ? state.maxTradesPerDay : 3;
  const tradesToday = isNum(state.tradesToday) ? state.tradesToday : 0;
  if (tradesToday >= maxTrades) {
    return {
      ...state,
      selectedMode,
      effectiveMode: mode,
      lastDecision: {
        ts: latest.ts,
        mode,
        action: 'NO_TRADE',
        reasons: [`Max trades/day reached: ${tradesToday}/${maxTrades}`],
      },
    };
  }

  if (!entryCheck.ok) {
    return {
      ...state,
      selectedMode,
      effectiveMode: mode,
      lastDecision: { ts: latest.ts, mode, action: 'NO_TRADE', reasons: entryCheck.reasons },
    };
  }

  const inst = entryCheck.inst;
  const legNow = findLeg(latest, inst.strike, inst.type);
  const entryPrice = legNow?.ltp;
  if (!isNum(entryPrice) || entryPrice <= 0) {
    return {
      ...state,
      selectedMode,
      effectiveMode: mode,
      lastDecision: { ts: latest.ts, mode, action: 'NO_TRADE', reasons: [...entryCheck.reasons, 'Entry price missing'] },
    };
  }

  const trade = {
    id: `paper_${latest.ts}`,
    status: 'OPEN',
    mode,
    strike: inst.strike,
    optType: inst.type,
    qty: normalizeOrderQty(state.orderQty),
    entryPrice,
    entryTs: latest.ts,
    peakPrice: entryPrice,
    slPrice: initialSl(entryPrice, mode),
    exitPrice: null,
    exitTs: null,
    exitReason: null,
    pnl: 0,
    reasons: entryCheck.reasons,
  };

  return {
    ...state,
    selectedMode,
    effectiveMode: mode,
    tradesToday: tradesToday + 1,
    currentTrade: trade,
    lastDecision: { ts: latest.ts, mode, action: 'ENTER', reasons: entryCheck.reasons },
  };
}

function createPaperTradeState() {
  return {
    enabled: true,
    tradeMode: 'PAPER',
    liveArmed: false,
    productType: 'M',
    qtyMode: 'QTY',
    lots: 1,
    qtyPerLot: 65,
    orderQty: 1,
    directionOverride: 'AUTO',
    selectedMode: 'AUTO',
    effectiveMode: 'NORMAL',
    exitStyle: 'TRAILING',
    targetPct: 30,
    maxTradesPerDay: 3,
    tradesDate: null,
    tradesToday: 0,
    dayOpenPrice: null,
    currentTrade: null,
    tradeHistory: [],
    lastDecision: null,
  };
}

function forceEnterPaperTrade(state, snapshotHistory) {
  const latest = Array.isArray(snapshotHistory) ? snapshotHistory[snapshotHistory.length - 1] : null;
  if (!latest) return { ok: false, error: 'No snapshot available' };
  if (state.currentTrade && state.currentTrade.status === 'OPEN') {
    return { ok: false, error: 'Trade already open' };
  }

  const todayKey = dayKeyFromTs(latest.ts);
  const tradesDate = todayKey || state.tradesDate;
  const tradesToday = (todayKey && state.tradesDate !== todayKey) ? 0 : (isNum(state.tradesToday) ? state.tradesToday : 0);
  const maxTrades = isNum(state.maxTradesPerDay) ? state.maxTradesPerDay : 3;
  if (tradesToday >= maxTrades) {
    return { ok: false, error: `Max trades/day reached: ${tradesToday}/${maxTrades}` };
  }

  const selectedMode = state.selectedMode || 'AUTO';
  const effectiveMode = selectedMode === 'AUTO'
    ? computeAutoMode(latest, snapshotHistory, state)
    : selectedMode;

  const { direction } = computeDirectionSignal(snapshotHistory, effectiveMode, state);
  const inst = selectInstrument(latest, effectiveMode, direction || 'BULL');
  if (!inst) return { ok: false, error: 'Instrument selection failed' };

  const legNow = findLeg(latest, inst.strike, inst.type);
  const entryPrice = legNow?.ltp;
  if (!isNum(entryPrice) || entryPrice <= 0) return { ok: false, error: 'Entry price missing' };

  const trade = {
    id: `paper_forced_${latest.ts}`,
    status: 'OPEN',
    mode: effectiveMode,
    strike: inst.strike,
    optType: inst.type,
    qty: normalizeOrderQty(state.orderQty),
    entryPrice,
    entryTs: latest.ts,
    peakPrice: entryPrice,
    slPrice: initialSl(entryPrice, effectiveMode),
    exitPrice: null,
    exitTs: null,
    exitReason: null,
    pnl: 0,
    reasons: ['FORCED_ENTRY'],
  };

  const nextState = {
    ...state,
    effectiveMode,
    tradesDate,
    tradesToday: tradesToday + 1,
    currentTrade: trade,
    lastDecision: { ts: latest.ts, mode: effectiveMode, action: 'FORCED_ENTRY', reasons: ['FORCED_ENTRY'] },
  };

  return { ok: true, state: nextState };
}

function computeEntryDecision(state, snapshotHistory) {
  const latest = Array.isArray(snapshotHistory) ? snapshotHistory[snapshotHistory.length - 1] : null;
  if (!latest) return { ok: false, reasons: ['No snapshot available'] };

  const todayKey = dayKeyFromTs(latest.ts);
  const tradesToday = (todayKey && state.tradesDate !== todayKey) ? 0 : (isNum(state.tradesToday) ? state.tradesToday : 0);
  const maxTrades = isNum(state.maxTradesPerDay) ? state.maxTradesPerDay : 3;
  if (tradesToday >= maxTrades) {
    return { ok: false, reasons: [`Max trades/day reached: ${tradesToday}/${maxTrades}`] };
  }

  const selectedMode = state.selectedMode || 'AUTO';
  const effectiveMode = selectedMode === 'AUTO'
    ? computeAutoMode(latest, snapshotHistory, state)
    : selectedMode;

  const entryCheck = shouldEnter(snapshotHistory, effectiveMode, state);
  if (!entryCheck.ok) return { ok: false, mode: effectiveMode, reasons: entryCheck.reasons };

  return {
    ok: true,
    mode: effectiveMode,
    inst: entryCheck.inst,
    direction: entryCheck.direction,
    reasons: entryCheck.reasons,
  };
}

function forceExitPaperTrade(state, snapshotHistory, reason = 'FORCED_EXIT') {
  const latest = Array.isArray(snapshotHistory) ? snapshotHistory[snapshotHistory.length - 1] : null;
  if (!latest) return { ok: false, error: 'No snapshot available' };
  const t = state.currentTrade;
  if (!t || t.status !== 'OPEN') return { ok: false, error: 'No open trade to exit' };

  const legNow = findLeg(latest, t.strike, t.optType);
  const ltpNow = legNow?.ltp;
  const exitPrice = isNum(ltpNow) ? ltpNow : t.entryPrice;
  const pnl = (exitPrice - t.entryPrice) * (t.qty || 1);

  const closed = {
    ...t,
    status: 'CLOSED',
    exitPrice,
    exitTs: latest.ts,
    exitReason: reason,
    pnl,
  };

  const nextState = {
    ...state,
    currentTrade: closed,
    tradeHistory: [...state.tradeHistory, closed].slice(-50),
    lastDecision: { ts: latest.ts, mode: state.effectiveMode, action: 'FORCED_EXIT', reasons: [reason] },
  };

  return { ok: true, state: nextState };
}

module.exports = {
  createPaperTradeState,
  stepPaperTrade,
  forceEnterPaperTrade,
  forceExitPaperTrade,
  computeEntryDecision,
  computeDirectionSignal,
  selectInstrument,
  normalizeOrderQty,
  normalizeProductType,
};
