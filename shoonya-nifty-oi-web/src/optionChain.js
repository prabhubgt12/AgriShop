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

async function getVwap(api, exch, token) {
  try {
    if (!api || typeof api.get_time_price_series !== 'function') {
      console.log('[VWAP] get_time_price_series not available on client');
      return null;
    }

    const now = new Date();
    const today = new Date(now.getFullYear(), now.getMonth(), now.getDate());
    const marketOpen = new Date(today.getTime() + (9 * 60 + 15) * 60 * 1000);
    const starttime = Math.floor(marketOpen.getTime() / 1000);
    const endtime = Math.floor(now.getTime() / 1000);

    const res = await api.get_time_price_series({
      exchange: exch,
      token,
      starttime,
      endtime,
      interval: '1',
    });
    if (!res) {
      console.log('[VWAP] TPSeries response is null/undefined');
      return null;
    }

    if (res.stat !== 'Ok') {
      console.log('[VWAP] TPSeries stat not Ok:', res.stat, 'msg:', res.emsg || res.message || '');
      return null;
    }

    if (!Array.isArray(res.values)) {
      console.log('[VWAP] TPSeries values missing/not array. Keys:', Object.keys(res || {}));
      return null;
    }

    if (res.values.length === 0) {
      console.log('[VWAP] TPSeries values empty');
      return null;
    }

    const sample = res.values[0];
    if (sample && typeof sample === 'object') {
      console.log('[VWAP] TPSeries sample candle keys:', Object.keys(sample));
      console.log('[VWAP] TPSeries sample candle:', JSON.stringify(sample));
    } else {
      console.log('[VWAP] TPSeries sample candle not object:', sample);
    }

    let sumPriceVol = 0;
    let sumVol = 0;
    for (const candle of res.values) {
      const vwap = safeNum(candle.intvwap);
      const vol = safeNum(candle.intv);
      if (vwap !== null && vol !== null && vol > 0) {
        sumPriceVol += vwap * vol;
        sumVol += vol;
      }
    }

    if (sumVol === 0) {
      console.log('[VWAP] sumVol is 0. vwap field used: intvwap, vol field used: intv');
      return null;
    }
    return sumPriceVol / sumVol;
  } catch (_e) {
    console.log('[VWAP] exception:', _e && _e.message ? _e.message : String(_e));
    return null;
  }
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
    if (i + chunkSize < tokens.length) await new Promise(r => setTimeout(r, 200)); // Delay 200ms between chunks
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

function computeItmOiStats(rows, underlyingLtp, eachSide = 5) {
  const ltp = Number(underlyingLtp);
  if (!Number.isFinite(ltp) || !Array.isArray(rows) || rows.length === 0) {
    const topN = eachSide * 2 + 1;
    return {
      topN,
      ceItmOiSum: null,
      peItmOiSum: null,
      ceOverPe: null,
      peOverCe: null,
      ceItmDOiSum: null,
      peItmDOiSum: null,
      ceDOiOverPeDOi: null,
      peDOiOverCeDOi: null,
      signal: null,
    };
  }

  const sorted = rows
    .filter((r) => Number.isFinite(r.strike))
    .slice()
    .sort((a, b) => a.strike - b.strike);

  if (sorted.length === 0) {
    const topN = eachSide * 2 + 1;
    return {
      topN,
      ceItmOiSum: null,
      peItmOiSum: null,
      ceOverPe: null,
      peOverCe: null,
      ceItmDOiSum: null,
      peItmDOiSum: null,
      ceDOiOverPeDOi: null,
      peDOiOverCeDOi: null,
      signal: null,
    };
  }

  let atmIdx = 0;
  let best = Infinity;
  for (let i = 0; i < sorted.length; i += 1) {
    const d = Math.abs(sorted[i].strike - ltp);
    if (d < best) {
      best = d;
      atmIdx = i;
    }
  }

  const start = Math.max(0, atmIdx - eachSide);
  const end = Math.min(sorted.length - 1, atmIdx + eachSide);
  const window = sorted.slice(start, end + 1);

  const ceItmOiSum = window.reduce((s, r) => s + (r.ce && r.ce.oi !== null ? (Number(r.ce.oi) || 0) : 0), 0);
  const peItmOiSum = window.reduce((s, r) => s + (r.pe && r.pe.oi !== null ? (Number(r.pe.oi) || 0) : 0), 0);

  const ceItmDOiSum = window.reduce((s, r) => s + (r.ce && r.ce.dOi !== null ? (Number(r.ce.dOi) || 0) : 0), 0);
  const peItmDOiSum = window.reduce((s, r) => s + (r.pe && r.pe.dOi !== null ? (Number(r.pe.dOi) || 0) : 0), 0);

  const ceOverPe = peItmOiSum > 0 ? ceItmOiSum / peItmOiSum : null;
  const peOverCe = ceItmOiSum > 0 ? peItmOiSum / ceItmOiSum : null;

  const ceDOiOverPeDOi = peItmDOiSum !== 0 ? ceItmDOiSum / peItmDOiSum : null;
  const peDOiOverCeDOi = ceItmDOiSum !== 0 ? peItmDOiSum / ceItmDOiSum : null;
  const topN = window.length;

  // Overall signal heuristic (ΔOI first, fallback to total OI):
  // - If PE ΔOI dominates -> bullish (put writing / support build-up)
  // - If CE ΔOI dominates -> bearish (call writing / resistance build-up)
  // - If ΔOI is too small/noisy, use total OI dominance.
  let signal = 'NEUTRAL';
  const dOiDiff = peItmDOiSum - ceItmDOiSum;
  const dOiAbsMax = Math.max(Math.abs(peItmDOiSum), Math.abs(ceItmDOiSum));
  const dOiThreshold = Math.max(25000, dOiAbsMax * 0.25);
  if (Number.isFinite(dOiDiff) && Math.abs(dOiDiff) >= dOiThreshold) {
    signal = dOiDiff > 0 ? 'BULLISH' : 'BEARISH';
  } else {
    const oiDiff = peItmOiSum - ceItmOiSum;
    const oiAbsMax = Math.max(Math.abs(peItmOiSum), Math.abs(ceItmOiSum));
    const oiThreshold = Math.max(100000, oiAbsMax * 0.15);
    if (Number.isFinite(oiDiff) && Math.abs(oiDiff) >= oiThreshold) {
      signal = oiDiff > 0 ? 'BULLISH' : 'BEARISH';
    }
  }

  return {
    topN,
    ceItmOiSum,
    peItmOiSum,
    ceOverPe,
    peOverCe,
    ceItmDOiSum,
    peItmDOiSum,
    ceDOiOverPeDOi,
    peDOiOverCeDOi,
    signal,
  };
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

  // const vwap = await getVwap(api, 'NSE', indexToken);
  const vwap = null;

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

  const itmOiStats = computeItmOiStats(rows, underQuote.ltp, 5);

  return {
    id: nanoid(),
    ts: Date.now(),
    expiry: expiryDate.toISOString().slice(0, 10),
    underlying: { ltp: underQuote.ltp, token: indexToken, tsym: indexTsym, vwap },
    atmStrike,
    levels: { supportStrike, resistanceStrike },
    itmOiStats,
    rows,
  };
}

module.exports = {
  buildNiftyChainSnapshot,
};
