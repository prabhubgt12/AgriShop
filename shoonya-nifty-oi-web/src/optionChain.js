const { nanoid } = require('nanoid');
const { getNiftyIndexToken, loadNiftyOptionUniverse } = require('./symbolMaster');

function roundToStep(x, step) {
  return Math.round(x / step) * step;
}

function buildStrikeList(atm, step, eachSide) {
  const strikes = [];
  for (let i = -eachSide; i <= eachSide; i += 1) {
    strikes.push(atm + i * step);
  }
  return strikes;
}

function pickLeg(universe, strike, type) {
  return universe.find((x) => x.strike === strike && x.optType === type) || null;
}

function safeNum(x) {
  if (x === null || x === undefined) return null;
  const n = Number(x);
  if (Number.isNaN(n)) return null;
  return n;
}

async function getQuote(api, exch, token) {
  const q = await api.get_quotes(exch, token);
  if (!q || q.stat !== 'Ok') return null;
  return q;
}

async function getQuotesChunked(api, exch, tokens, chunkSize = 10) {
  const out = new Map();

  for (let i = 0; i < tokens.length; i += chunkSize) {
    const chunk = tokens.slice(i, i + chunkSize);
    const replies = await Promise.all(chunk.map((t) => getQuote(api, exch, t)));
    for (let j = 0; j < chunk.length; j += 1) {
      out.set(chunk[j], replies[j]);
    }
  }

  return out;
}

function extractQuoteFields(q) {
  // Shoonya field names from README table include: lp, oi, v, c, o, h, l, ...
  const ltp = safeNum(q.lp ?? q.ltp ?? q.LTP);
  const oi = safeNum(q.oi ?? q.OI);
  const vol = safeNum(q.v ?? q.vol ?? q.volume);

  return { ltp, oi, vol };
}

function computeDelta(now, prev) {
  if (!now || !prev) return { dOi: null, dLtp: null, dVol: null };
  const dOi = (now.oi !== null && prev.oi !== null) ? now.oi - prev.oi : null;
  const dLtp = (now.ltp !== null && prev.ltp !== null) ? now.ltp - prev.ltp : null;
  const dVol = (now.vol !== null && prev.vol !== null) ? now.vol - prev.vol : null;
  return { dOi, dLtp, dVol };
}

function computeItmOiStats(rows, underlyingLtp, topN = 10) {
  const ltp = Number(underlyingLtp);
  if (!Number.isFinite(ltp)) {
    return { topN, ceItmOiSum: null, peItmOiSum: null, ceOverPe: null, peOverCe: null };
  }

  const ceItm = rows
    .filter((r) => Number.isFinite(r.strike) && r.strike < ltp && r.ce && r.ce.oi !== null)
    .sort((a, b) => Math.abs(ltp - a.strike) - Math.abs(ltp - b.strike))
    .slice(0, topN);

  const peItm = rows
    .filter((r) => Number.isFinite(r.strike) && r.strike > ltp && r.pe && r.pe.oi !== null)
    .sort((a, b) => Math.abs(ltp - a.strike) - Math.abs(ltp - b.strike))
    .slice(0, topN);

  const ceItmOiSum = ceItm.reduce((s, r) => s + (Number(r.ce.oi) || 0), 0);
  const peItmOiSum = peItm.reduce((s, r) => s + (Number(r.pe.oi) || 0), 0);

  const ceOverPe = peItmOiSum > 0 ? ceItmOiSum / peItmOiSum : null;
  const peOverCe = ceItmOiSum > 0 ? peItmOiSum / ceItmOiSum : null;

  return { topN, ceItmOiSum, peItmOiSum, ceOverPe, peOverCe };
}

async function buildNiftyChainSnapshot(api, opts, prevSnapshot) {
  const strikeStep = opts.strikeStep;
  const strikesEachSide = opts.strikesEachSide;

  const expiryIso = process.env.EXPIRY_ISO || '';
  const { expiryDate, universe } = await loadNiftyOptionUniverse(expiryIso || null);

  const indexInfo = await getNiftyIndexToken(api);
  const indexToken = typeof indexInfo === 'string' ? indexInfo : indexInfo.token;
  const indexTsym = typeof indexInfo === 'string' ? '' : (indexInfo.tsym || '');

  const underQuoteRaw = await getQuote(api, 'NSE', indexToken);
  if (!underQuoteRaw) throw new Error('Failed to fetch underlying quote');

  const underQuote = extractQuoteFields(underQuoteRaw);
  if (underQuote.ltp === null) throw new Error('Underlying LTP missing');

  const atmStrike = roundToStep(underQuote.ltp, strikeStep);
  const strikes = buildStrikeList(atmStrike, strikeStep, strikesEachSide);

  const legs = [];
  for (const strike of strikes) {
    const ce = pickLeg(universe, strike, 'CE');
    const pe = pickLeg(universe, strike, 'PE');
    if (ce) legs.push({ strike, type: 'CE', token: ce.token, tsym: ce.tsym });
    if (pe) legs.push({ strike, type: 'PE', token: pe.token, tsym: pe.tsym });
  }

  const tokenList = legs.map((l) => l.token);
  const quoteMap = await getQuotesChunked(api, 'NFO', tokenList, 10);

  const prevMap = new Map();
  if (prevSnapshot && Array.isArray(prevSnapshot.rows)) {
    for (const row of prevSnapshot.rows) {
      if (row.ce && row.ce.token) prevMap.set(row.ce.token, row.ce);
      if (row.pe && row.pe.token) prevMap.set(row.pe.token, row.pe);
    }
  }

  const rows = strikes.map((strike) => {
    const ceLeg = legs.find((l) => l.strike === strike && l.type === 'CE') || null;
    const peLeg = legs.find((l) => l.strike === strike && l.type === 'PE') || null;

    const ceRaw = ceLeg ? quoteMap.get(ceLeg.token) : null;
    const peRaw = peLeg ? quoteMap.get(peLeg.token) : null;

    const ceNow = ceRaw ? extractQuoteFields(ceRaw) : null;
    const peNow = peRaw ? extractQuoteFields(peRaw) : null;

    const cePrev = ceLeg ? prevMap.get(ceLeg.token) : null;
    const pePrev = peLeg ? prevMap.get(peLeg.token) : null;

    const ceDelta = computeDelta(ceNow, cePrev);
    const peDelta = computeDelta(peNow, pePrev);

    const ce = ceLeg && ceNow ? { ...ceNow, ...ceDelta, token: ceLeg.token, tsym: ceLeg.tsym } : null;
    const pe = peLeg && peNow ? { ...peNow, ...peDelta, token: peLeg.token, tsym: peLeg.tsym } : null;

    return { strike, ce, pe };
  });

  // Levels: max OI strikes
  let supportStrike = null;
  let resistanceStrike = null;
  let maxPutOi = -Infinity;
  let maxCallOi = -Infinity;

  for (const r of rows) {
    if (r.pe && r.pe.oi !== null && r.pe.oi > maxPutOi) {
      maxPutOi = r.pe.oi;
      supportStrike = r.strike;
    }
    if (r.ce && r.ce.oi !== null && r.ce.oi > maxCallOi) {
      maxCallOi = r.ce.oi;
      resistanceStrike = r.strike;
    }
  }

  const itmOiStats = computeItmOiStats(rows, underQuote.ltp, 10);

  return {
    id: nanoid(),
    ts: Date.now(),
    expiry: expiryDate.toISOString().slice(0, 10),
    underlying: { ltp: underQuote.ltp, token: indexToken, tsym: indexTsym },
    atmStrike,
    levels: { supportStrike, resistanceStrike },
    itmOiStats,
    rows,
  };
}

module.exports = {
  buildNiftyChainSnapshot,
};
