/**
 * Shoonya API rejects MKT (ALGO_CHK). Convert to LMT with slippage vs LTP.
 */

function parseNum(x) {
  const n = Number(x);
  return Number.isFinite(n) ? n : null;
}

function roundToTick(price, tick, mode) {
  const t = tick > 0 ? tick : 0.05;
  const steps = price / t;
  if (mode === 'up') return Math.ceil(steps - 1e-9) * t;
  if (mode === 'down') return Math.floor(steps + 1e-9) * t;
  return Math.round(steps) * t;
}

function formatPrice(price, tick) {
  const t = tick > 0 ? tick : 0.05;
  const dec = t >= 1 ? 0 : Math.max(2, (String(t).split('.')[1] || '').length);
  return Number(price.toFixed(dec));
}

/** Reject NIFTY spot or other bad values masquerading as option LTP. */
function sanitizeOptionLtp(ltp, { entryPrice, underlyingLtp } = {}) {
  const n = parseNum(ltp);
  if (!n || n <= 0) return null;

  if (n > 3000) return null;

  if (isNum(underlyingLtp) && underlyingLtp > 5000 && Math.abs(n - underlyingLtp) < 250) {
    return null;
  }

  const entry = parseNum(entryPrice);
  if (isNum(entry) && entry > 0) {
    if (n > Math.max(500, entry * 15)) return null;
  } else if (n > 800) {
    return null;
  }

  return n;
}

function isNum(x) {
  return typeof x === 'number' && !Number.isNaN(x);
}

function findLegInSnapshot(snapshot, strike, optType) {
  const strikeNum = Number(strike);
  const row = (snapshot?.rows || []).find(
    (r) => r && (r.strike === strike || r.strike === strikeNum),
  );
  if (!row) return null;
  return optType === 'CE' ? row.ce : row.pe;
}

function findLegLtpFromSnapshot(latest, tsym) {
  if (!latest || !tsym) return null;
  for (const r of latest.rows || []) {
    if (r?.ce?.tsym === tsym && typeof r.ce.ltp === 'number') return r.ce.ltp;
    if (r?.pe?.tsym === tsym && typeof r.pe.ltp === 'number') return r.pe.ltp;
  }
  return null;
}

/** Option premium from chain; never returns index-scale LTP. */
function optionLtpFromSnapshot(snapshot, trade) {
  if (!snapshot || !trade) return null;
  const underlyingLtp = snapshot.underlying?.ltp;
  const ctx = { entryPrice: trade.entryPrice, underlyingLtp };

  if (trade.tsym) {
    const byTsym = sanitizeOptionLtp(findLegLtpFromSnapshot(snapshot, trade.tsym), ctx);
    if (isNum(byTsym)) return byTsym;
  }

  const leg = findLegInSnapshot(snapshot, trade.strike, trade.optType);
  return sanitizeOptionLtp(leg?.ltp, ctx);
}

/**
 * LTP for API limit orders. Never uses peakPrice (often polluted with index).
 * @param {boolean} opts.allowEntryFallback - sell may use entry if chain LTP missing
 */
function resolveOrderLtp(snapshot, trade, underlyingLtp, opts = {}) {
  const u = underlyingLtp ?? snapshot?.underlying?.ltp;
  let ltp = optionLtpFromSnapshot(snapshot, trade);
  if (!isNum(ltp) && opts.allowEntryFallback) {
    const entry = parseNum(trade?.entryPrice);
    if (isNum(entry) && entry > 0) ltp = entry;
  }
  return sanitizeOptionLtp(ltp, { entryPrice: trade?.entryPrice, underlyingLtp: u });
}

function apiLimitFromLtp(buyOrSell, ltp) {
  const slipPct = parseNum(process.env.SHOONYA_API_SLIPPAGE_PCT) ?? 3;
  const tick = parseNum(process.env.SHOONYA_NFO_TICK_SIZE) ?? 0.05;
  const base = parseNum(ltp);
  if (!base || base <= 0) {
    throw new Error('Valid LTP required for API order (MKT not allowed; use limit with slippage).');
  }
  const mult = buyOrSell === 'B' ? 1 + slipPct / 100 : 1 - slipPct / 100;
  const raw = base * mult;
  const mode = buyOrSell === 'B' ? 'up' : 'down';
  const price = formatPrice(roundToTick(raw, tick, mode), tick);
  return { price_type: 'LMT', price };
}

/** Map MKT → LMT using order.ltp; pass price_type: 'MKT' at call sites for clarity. */
function applyApiOrderPricing(order) {
  const pt = order.price_type || 'MKT';
  if (pt !== 'MKT' && pt !== 'SL-MKT') {
    const next = { ...order };
    delete next.ltp;
    delete next.entryPrice;
    delete next.underlyingLtp;
    return next;
  }

  const cleaned = sanitizeOptionLtp(order.ltp, {
    entryPrice: order.entryPrice,
    underlyingLtp: order.underlyingLtp,
  });

  if (!isNum(cleaned)) {
    throw new Error(
      `Valid option LTP required for API order (refused ${order.ltp ?? 'missing'} — spot/index-scale prices are not used).`,
    );
  }

  const { price_type, price } = apiLimitFromLtp(order.buy_or_sell, cleaned);
  const next = { ...order, price_type, price };
  delete next.ltp;
  delete next.entryPrice;
  delete next.underlyingLtp;
  return next;
}

function formatPlaceOrderError(resp) {
  if (!resp) return 'Unknown error';
  if (typeof resp === 'string') return resp;
  const msg = resp.emsg || resp.message || resp.error;
  if (msg) return String(msg);
  return JSON.stringify(resp);
}

module.exports = {
  applyApiOrderPricing,
  findLegLtpFromSnapshot,
  sanitizeOptionLtp,
  optionLtpFromSnapshot,
  resolveOrderLtp,
  formatPlaceOrderError,
};
