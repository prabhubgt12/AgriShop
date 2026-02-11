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
  if (!leg) return 0;
  if (!isNum(leg.dLtp) || !isNum(leg.dOi)) return 0;
  if (leg.dLtp > 0 && leg.dOi > 0) return 1;
  return 0;
}

function scoreUnwind(leg) {
  // Unwinding: dOi < 0 (preferably with price increase)
  if (!leg) return 0;
  if (!isNum(leg.dOi)) return 0;
  if (leg.dOi < 0) return 0.5;
  return 0;
}

function computeSuggestion(snapshotHistory, opts = {}) {
  const windowMs = isNum(opts.windowMs) ? opts.windowMs : 60_000;
  const widthStrikes = isNum(opts.widthStrikes) ? opts.widthStrikes : 4;
  const minScore = isNum(opts.minScore) ? opts.minScore : 2;

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

  let ceBuildup = 0;
  let peBuildup = 0;
  let ceUnwind = 0;
  let peUnwind = 0;

  for (const s of windowSnaps) {
    const rows = nearAtmRows(s, widthStrikes);
    ceBuildup += sum(rows.map((r) => scoreBuildup(r.ce)));
    peBuildup += sum(rows.map((r) => scoreBuildup(r.pe)));
    ceUnwind += sum(rows.map((r) => scoreUnwind(r.ce)));
    peUnwind += sum(rows.map((r) => scoreUnwind(r.pe)));
  }

  // Bullish bias when CE buildup is stronger and PE is unwinding
  const bullishScore = ceBuildup + peUnwind - peBuildup;
  const bearishScore = peBuildup + ceUnwind - ceBuildup;

  const under = latest?.underlying?.ltp;
  const atm = latest?.atmStrike;
  const support = latest?.levels?.supportStrike;
  const resist = latest?.levels?.resistanceStrike;

  const reasons = [];
  reasons.push(`Window snapshots: ${windowSnaps.length}`);
  reasons.push(`Near-ATM CE long buildup count: ${ceBuildup}`);
  reasons.push(`Near-ATM PE long buildup count: ${peBuildup}`);
  reasons.push(`Under: ${isNum(under) ? under.toFixed(2) : under} • ATM: ${isNum(atm) ? atm : atm}`);

  let action = 'NO_TRADE';
  let confidence = 0;

  if (bullishScore >= minScore && under >= atm) {
    // Guardrail: avoid buying CE right at resistance
    const nearResist = resist !== null && isNum(under) && Math.abs(resist - under) <= 50;
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
    const nearSupport = support !== null && isNum(under) && Math.abs(under - support) <= 50;
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
    reasons,
    window: { count: windowSnaps.length, fromTs, toTs, windowMs },
  };
}

module.exports = {
  computeSuggestion,
};
