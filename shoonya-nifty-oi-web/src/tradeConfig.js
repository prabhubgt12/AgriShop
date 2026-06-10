/** NIFTY index points above/below breakoutLevel before FALSE_BREAKOUT exit (NORMAL mode). */
function getFalseBreakoutBufferPoints() {
  const raw = process.env.FALSE_BREAKOUT_BUFFER_POINTS;
  const n = raw != null && String(raw).trim() !== '' ? parseInt(raw, 10) : 15;
  if (!Number.isFinite(n) || n < 0) return 15;
  return n;
}

/**
 * Time-based trading restrictions — applies to all modes, all days.
 *
 * Configurable via .env:
 *   TRADE_ENTRY_START_HHMM=0930   no new entries before 9:30 AM IST
 *   TRADE_ENTRY_END_HHMM=1500     no new entries at or after 3:00 PM IST
 *   TRADE_EXIT_END_HHMM=1515      force exit open trades at or after 3:15 PM IST
 *   TRADE_FORCE_EXIT_EOD=false    set true to enable force exit (default off)
 *
 * Returns { blockEntry, forceExit, istMins }
 */
function getTimeRestriction(nowTs) {
  if (!nowTs || !Number.isFinite(nowTs)) return { blockEntry: false, forceExit: false, istMins: null };

  // Convert UTC epoch ms → IST minutes since midnight
  const istMins = Math.floor(nowTs / 60000 + 330) % (24 * 60);

  const toMins = (hhmm) => {
    const n = parseInt(hhmm, 10);
    return Math.floor(n / 100) * 60 + (n % 100);
  };

  const entryStart = toMins(process.env.TRADE_ENTRY_START_HHMM || '0930');
  const entryEnd   = toMins(process.env.TRADE_ENTRY_END_HHMM   || '1500');
  const exitEnd    = toMins(process.env.TRADE_EXIT_END_HHMM    || '1515');
  const forceExitEnabled = String(process.env.TRADE_FORCE_EXIT_EOD || 'false').toLowerCase() === 'true';

  const blockEntry = istMins < entryStart || istMins >= entryEnd;
  const forceExit  = forceExitEnabled && istMins >= exitEnd;

  return { blockEntry, forceExit, istMins };
}

module.exports = {
  getFalseBreakoutBufferPoints,
  getTimeRestriction,
};
