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

function computeSuggestion(snapshot, prevSnapshot) {
  if (!snapshot || !prevSnapshot) {
    return { action: 'NO_TRADE', confidence: 0, reasons: ['Need at least two snapshots for ΔOI-based signals'] };
  }

  const rows = nearAtmRows(snapshot, 4);

  const ceBuildup = sum(rows.map((r) => scoreBuildup(r.ce)));
  const peBuildup = sum(rows.map((r) => scoreBuildup(r.pe)));
  const ceUnwind = sum(rows.map((r) => scoreUnwind(r.ce)));
  const peUnwind = sum(rows.map((r) => scoreUnwind(r.pe)));

  // Bullish bias when CE buildup is stronger and PE is unwinding
  const bullishScore = ceBuildup + peUnwind - peBuildup;
  const bearishScore = peBuildup + ceUnwind - ceBuildup;

  const under = snapshot.underlying?.ltp;
  const atm = snapshot.atmStrike;
  const support = snapshot.levels?.supportStrike;
  const resist = snapshot.levels?.resistanceStrike;

  const reasons = [];
  reasons.push(`Near-ATM CE long buildup count: ${ceBuildup}`);
  reasons.push(`Near-ATM PE long buildup count: ${peBuildup}`);

  const minScore = 2; // tune later

  let action = 'NO_TRADE';
  let confidence = 0;

  if (bullishScore >= minScore && under >= atm) {
    // Guardrail: avoid buying CE right at resistance
    const nearResist = resist !== null && Math.abs(resist - under) <= 50;
    if (!nearResist) {
      action = 'BUY_CE';
      confidence = Math.min(100, Math.round(40 + bullishScore * 15));
      reasons.push(`Bullish score: ${bullishScore.toFixed(2)}`);
      if (support !== null) reasons.push(`Support (max Put OI): ${support}`);
      if (resist !== null) reasons.push(`Resistance (max Call OI): ${resist}`);
    } else {
      reasons.push('Skipped BUY_CE: underlying too close to resistance');
    }
  } else if (bearishScore >= minScore && under <= atm) {
    // Guardrail: avoid buying PE right at support
    const nearSupport = support !== null && Math.abs(under - support) <= 50;
    if (!nearSupport) {
      action = 'BUY_PE';
      confidence = Math.min(100, Math.round(40 + bearishScore * 15));
      reasons.push(`Bearish score: ${bearishScore.toFixed(2)}`);
      if (support !== null) reasons.push(`Support (max Put OI): ${support}`);
      if (resist !== null) reasons.push(`Resistance (max Call OI): ${resist}`);
    } else {
      reasons.push('Skipped BUY_PE: underlying too close to support');
    }
  } else {
    reasons.push(`Bullish score: ${bullishScore.toFixed(2)}`);
    reasons.push(`Bearish score: ${bearishScore.toFixed(2)}`);
  }

  return { action, confidence, reasons };
}

module.exports = {
  computeSuggestion,
};
