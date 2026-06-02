/** NIFTY index points above/below breakoutLevel before FALSE_BREAKOUT exit (NORMAL mode). */
function getFalseBreakoutBufferPoints() {
  const raw = process.env.FALSE_BREAKOUT_BUFFER_POINTS;
  const n = raw != null && String(raw).trim() !== '' ? parseInt(raw, 10) : 15;
  if (!Number.isFinite(n) || n < 0) return 15;
  return n;
}

module.exports = {
  getFalseBreakoutBufferPoints,
};
