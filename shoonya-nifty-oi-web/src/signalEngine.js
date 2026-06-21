function isNum(x) {
  return typeof x === 'number' && !Number.isNaN(x);
}

function sum(values) {
  return values.reduce((a, b) => a + b, 0);
}

function nearAtmRows(snapshot, widthStrikes = 4) {
  const atm = snapshot.atmStrike;
  const step = 50;
  const min = atm - widthStrikes * step;
  const max = atm + widthStrikes * step;
  return (snapshot.rows || []).filter((r) => r.strike >= min && r.strike <= max);
}

function scoreBuildup(leg) {
  // Long buildup: dLtp > 0 and dOi > 0
  // Volume confirmation: +0.5 bonus if dVol > 0 (genuine activity, not rollover)
  if (!leg) return 0;
  if (!isNum(leg.dLtp) || !isNum(leg.dOi)) return 0;
  if (leg.dLtp > 0 && leg.dOi > 0) {
    const volBonus = isNum(leg.dVol) && leg.dVol > 0 ? 0.5 : 0;
    return 1 + volBonus; // 1.0 without volume, 1.5 with volume confirmation
  }
  return 0;
}

function scoreUnwind(leg) {
  // Unwinding: dOi < 0
  // Volume confirmation: +0.3 bonus if volume rising during unwind (stronger exit signal)
  if (!leg) return 0;
  if (!isNum(leg.dOi)) return 0;
  if (leg.dOi < 0) {
    const volBonus = isNum(leg.dVol) && leg.dVol > 0 ? 0.3 : 0;
    return 0.5 + volBonus; // 0.5 without volume, 0.8 with volume confirmation
  }
  return 0;
}

function computeSuggestion(snapshotHistory, opts = {}) {
  const windowMs = isNum(opts.windowMs) ? opts.windowMs : 120_000;  // 2 min window (was 60s)
  const widthStrikes = isNum(opts.widthStrikes) ? opts.widthStrikes : 4;
  const minScore = isNum(opts.minScore) ? opts.minScore : 4;  // higher threshold (was 2)

  if (!Array.isArray(snapshotHistory) || snapshotHistory.length < 2) {
    return {
      action: 'NO_TRADE',
      confidence: 0,
      reasons: ['Need at least two snapshots for ΔOI-based signals'],
      window: { count: Array.isArray(snapshotHistory) ? snapshotHistory.length : 0, fromTs: null, toTs: null, windowMs },
    };
  }

  const latest = snapshotHistory[snapshotHistory.length - 1];
  const toTs = latest?.ts ?? null;
  const fromCutoff = isNum(toTs) ? (toTs - windowMs) : null;
  const windowSnaps = fromCutoff !== null
    ? snapshotHistory.filter((s) => isNum(s?.ts) && s.ts >= fromCutoff)
    : snapshotHistory;

  if (windowSnaps.length < 2) {
    return {
      action: 'NO_TRADE',
      confidence: 0,
      reasons: ['Need more snapshots inside window'],
      window: { count: windowSnaps.length, fromTs: null, toTs, windowMs },
    };
  }

  // ── Window start vs end scoring ─────────────────────────────────────────────
  // Compare first snapshot in window vs latest for total OI/LTP/Vol change.
  // More accurate than summing noisy 2-second deltas across all snapshots.
  const first = windowSnaps[0];
  const firstRows = nearAtmRows(first, widthStrikes);
  const latestRows = nearAtmRows(latest, widthStrikes);

  let ceBuildup = 0;
  let peBuildup = 0;
  let ceUnwind = 0;
  let peUnwind = 0;
  let dOiActive = false; // true if any meaningful OI change detected

  for (let i = 0; i < latestRows.length; i++) {
    const rowNow   = latestRows[i];
    const rowFirst = firstRows.find(r => r.strike === rowNow.strike);
    if (!rowFirst) continue;

    const ce = rowNow.ce;
    const ceF = rowFirst.ce;
    const pe = rowNow.pe;
    const peF = rowFirst.pe;

    // CE: compare window start vs end
    if (ce && ceF && isNum(ce.ltp) && isNum(ceF.ltp) && isNum(ce.oi) && isNum(ceF.oi)) {
      const ltpChg = ce.ltp - ceF.ltp;
      const oiChg  = ce.oi  - ceF.oi;
      const volChg = isNum(ce.vol) && isNum(ceF.vol) ? ce.vol - ceF.vol : 0;
      if (Math.abs(oiChg) > 0) dOiActive = true;
      if (ltpChg > 0 && oiChg > 0) {
        ceBuildup += 1 + (volChg > 0 ? 0.5 : 0); // long buildup confirmed
      } else if (oiChg < 0) {
        ceUnwind += 0.5 + (volChg > 0 ? 0.3 : 0); // unwinding
      }
    }

    // PE: compare window start vs end
    if (pe && peF && isNum(pe.ltp) && isNum(peF.ltp) && isNum(pe.oi) && isNum(peF.oi)) {
      const ltpChg = pe.ltp - peF.ltp;
      const oiChg  = pe.oi  - peF.oi;
      const volChg = isNum(pe.vol) && isNum(peF.vol) ? pe.vol - peF.vol : 0;
      if (Math.abs(oiChg) > 0) dOiActive = true;
      if (ltpChg > 0 && oiChg > 0) {
        peBuildup += 1 + (volChg > 0 ? 0.5 : 0);
      } else if (oiChg < 0) {
        peUnwind += 0.5 + (volChg > 0 ? 0.3 : 0);
      }
    }
  }
  // ─────────────────────────────────────────────────────────────────────────────

  // Bullish bias when CE buildup is stronger and PE is unwinding
  const bullishScore = ceBuildup + peUnwind - peBuildup;
  const bearishScore = peBuildup + ceUnwind - ceBuildup;

  const under = latest?.underlying?.ltp;
  const atm = latest?.atmStrike;
  const support = latest?.levels?.supportStrike;
  const resist = latest?.levels?.resistanceStrike;

  const reasons = [];
  reasons.push(`Window snapshots: ${windowSnaps.length} (${dOiActive ? 'OI active' : 'OI static — no change in window'})`);
  reasons.push(`Near-ATM CE buildup: ${ceBuildup.toFixed(1)} | CE unwind: ${ceUnwind.toFixed(1)}`);
  reasons.push(`Near-ATM PE buildup: ${peBuildup.toFixed(1)} | PE unwind: ${peUnwind.toFixed(1)}`);
  reasons.push(`Under: ${isNum(under) ? under.toFixed(2) : under} • ATM: ${isNum(atm) ? atm : atm}`);

  let action = 'NO_TRADE';
  let confidence = 0;

  if (bullishScore >= minScore && under >= atm) {
    // Guardrail: avoid buying CE right at resistance
    const nearResist = resist !== null && isNum(under) && Math.abs(resist - under) <= 20;
    if (!nearResist) {
      action = 'BUY_CE';
      confidence = Math.min(100, Math.round(40 + bullishScore * 10));
      reasons.push(`Bullish score: ${bullishScore.toFixed(2)}`);
      if (support !== null) reasons.push(`Support (max Put OI): ${support}`);
      if (resist !== null) reasons.push(`Resistance (max Call OI): ${resist}`);
    } else {
      reasons.push('Skipped BUY_CE: underlying too close to resistance');
    }
  } else if (bearishScore >= minScore && under <= atm) {
    // Guardrail: avoid buying PE right at support
    const nearSupport = support !== null && isNum(under) && Math.abs(under - support) <= 20;
    if (!nearSupport) {
      action = 'BUY_PE';
      confidence = Math.min(100, Math.round(40 + bearishScore * 10));
      reasons.push(`Bearish score: ${bearishScore.toFixed(2)}`);
      if (support !== null) reasons.push(`Support (max Put OI): ${support}`);
      if (resist !== null) reasons.push(`Resistance (max Call OI): ${resist}`);
    } else {
      reasons.push('Skipped BUY_PE: underlying too close to support');
    }
  } else {
    reasons.push(`Bullish score: ${bullishScore.toFixed(2)}`);
    reasons.push(`Bearish score: ${bearishScore.toFixed(2)}`);
    if (bullishScore >= minScore && !(under >= atm)) reasons.push('BUY_CE blocked: underlying is below ATM');
    if (bearishScore >= minScore && !(under <= atm)) reasons.push('BUY_PE blocked: underlying is above ATM');
  }

  const fromTs = windowSnaps[0]?.ts ?? null;
  return {
    action,
    confidence,
    dOiActive,  // true if OI changed in window — false = signal based on static OI only
    reasons,
    window: { count: windowSnaps.length, fromTs, toTs, windowMs },
  };
}

module.exports = {
  computeSuggestion,
};
