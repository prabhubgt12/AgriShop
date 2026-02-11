const path = require('path');
const express = require('express');
const dotenv = require('dotenv');

dotenv.config({ path: path.join(__dirname, '.env') });

const { createShoonyaClient } = require('./src/shoonyaClient');
const { buildNiftyChainSnapshot } = require('./src/optionChain');
const { computeSuggestion } = require('./src/signalEngine');
const {
  createPaperTradeState,
  stepPaperTrade,
  forceEnterPaperTrade,
  forceExitPaperTrade,
  computeEntryDecision,
  normalizeOrderQty,
  normalizeProductType,
} = require('./src/paperTradeEngine');

const { stepLiveTrade, createLiveTradeState } = require('./src/liveTradeEngine');

const app = express();
app.use(express.json());
app.use(express.static(path.join(__dirname, 'public')));

const port = parseInt(process.env.PORT || '3000', 10);
const pollSeconds = parseInt(process.env.POLL_SECONDS || '5', 10);
const windowSeconds = parseInt(process.env.WINDOW_SECONDS || '60', 10);

const state = {
  client: null,
  loggedIn: false,
  lastSnapshot: null,
  snapshotHistory: [],
  paper: createPaperTradeState(),
  live: createLiveTradeState(),
  lastError: null,
  polling: false,
  pollTimer: null,
  auth: {
    userid: process.env.SHOONYA_USERID,
    password: process.env.SHOONYA_PASSWORD,
    vendor_code: process.env.SHOONYA_VENDOR_CODE,
    api_secret: process.env.SHOONYA_API_SECRET,
    imei: process.env.SHOONYA_IMEI,
  },
};

function ensureClient() {
  if (state.client) return;
  state.client = createShoonyaClient({});
}

async function loginWithOtp(otp) {
  ensureClient();
  const params = {
    userid: state.auth.userid,
    password: state.auth.password,
    twoFA: otp,
    vendor_code: state.auth.vendor_code,
    api_secret: state.auth.api_secret,
    imei: state.auth.imei,
  };

  const missing = Object.entries(params)
    .filter(([k, v]) => !v && k !== 'twoFA')
    .map(([k]) => k);
  if (missing.length) {
    throw new Error(`Missing env vars for login: ${missing.join(', ')}`);
  }
  if (!otp) {
    throw new Error('OTP is required (Shoonya factor2)');
  }

  const res = await state.client.login(params);
  if (!res || res.stat !== 'Ok') {
    state.loggedIn = false;
    throw new Error(`Shoonya login failed: ${JSON.stringify(res)}`);
  }

  state.loggedIn = true;
}

async function updateOnce() {
  if (!state.loggedIn) throw new Error('Not logged in. Call /api/login with OTP.');

  const snapshot = await buildNiftyChainSnapshot(state.client, {
    strikeStep: parseInt(process.env.STRIKE_STEP || '50', 10),
    strikesEachSide: parseInt(process.env.STRIKES_EACH_SIDE || '8', 10),
  }, state.lastSnapshot);

  // Maintain history for window-based suggestion
  state.snapshotHistory.push(snapshot);
  const maxKeep = Math.max(2, Math.ceil((Math.max(1, windowSeconds) / Math.max(1, pollSeconds)) * 2));
  if (state.snapshotHistory.length > maxKeep) {
    state.snapshotHistory = state.snapshotHistory.slice(-maxKeep);
  }

  snapshot.suggestion = computeSuggestion(state.snapshotHistory, {
    windowMs: Math.max(1, windowSeconds) * 1000,
    widthStrikes: 4,
    minScore: 2,
  });

  if (state.paper && state.paper.enabled) {
    state.paper = stepPaperTrade(state.paper, state.snapshotHistory, state.paper.selectedMode);
  }

  // LIVE execution (safe gated by env + UI)
  const liveEnabled = String(process.env.ENABLE_LIVE_TRADING || '').toLowerCase() === 'true';
  if (liveEnabled && state.paper && state.paper.tradeMode === 'LIVE') {
    const entryDecision = computeEntryDecision(state.paper, state.snapshotHistory);
    state.live = await stepLiveTrade({
      client: state.client,
      paper: state.paper,
      live: state.live,
      snapshotHistory: state.snapshotHistory,
      entryDecision,
    });
  }

  state.lastSnapshot = snapshot;
  state.lastError = null;
}

async function startPolling() {
  if (state.polling) return;
  if (!state.loggedIn) throw new Error('Not logged in. Call /api/login with OTP first.');
  state.polling = true;

  const loop = async () => {
    try {
      await updateOnce();
    } catch (e) {
      state.lastError = e && e.message ? e.message : String(e);
    }
  };

  await loop();
  state.pollTimer = setInterval(loop, Math.max(1, pollSeconds) * 1000);
}

function stopPolling() {
  if (state.pollTimer) {
    clearInterval(state.pollTimer);
    state.pollTimer = null;
  }
  state.polling = false;
}

app.get('/api/health', (_req, res) => {
  res.json({ ok: true, loggedIn: state.loggedIn, polling: state.polling, lastError: state.lastError });
});

app.post('/api/login', async (req, res) => {
  try {
    const otp = req.body && typeof req.body.otp === 'string' ? req.body.otp.trim() : '';
    await loginWithOtp(otp);
    res.json({ ok: true });
  } catch (e) {
    res.status(500).json({ ok: false, error: e && e.message ? e.message : String(e) });
  }
});

app.post('/api/start', async (_req, res) => {
  try {
    await startPolling();
    res.json({ ok: true });
  } catch (e) {
    res.status(500).json({ ok: false, error: e && e.message ? e.message : String(e) });
  }
});

app.post('/api/stop', (_req, res) => {
  try {
    stopPolling();
    res.json({ ok: true });
  } catch (e) {
    res.status(500).json({ ok: false, error: e && e.message ? e.message : String(e) });
  }
});

app.get('/api/paper', (_req, res) => {
  res.json({ ok: true, paper: state.paper });
});

app.post('/api/paper/risk-config', (req, res) => {
  const maxTradesRaw = req.body ? req.body.maxTradesPerDay : undefined;
  const maxTradesNum = typeof maxTradesRaw === 'number' ? maxTradesRaw : Number(maxTradesRaw);
  const maxTradesPerDay = Number.isFinite(maxTradesNum) ? Math.max(1, Math.min(50, Math.round(maxTradesNum))) : state.paper.maxTradesPerDay;

  const modeRaw = req.body && typeof req.body.tradeMode === 'string' ? req.body.tradeMode.trim().toUpperCase() : state.paper.tradeMode;
  const tradeMode = modeRaw === 'LIVE' ? 'LIVE' : 'PAPER';
  const liveEnabled = String(process.env.ENABLE_LIVE_TRADING || '').toLowerCase() === 'true';
  if (tradeMode === 'LIVE' && !liveEnabled) {
    res.status(400).json({ ok: false, error: 'LIVE trading is disabled. Set ENABLE_LIVE_TRADING=true in .env to allow it.', paper: state.paper });
    return;
  }

  state.paper.maxTradesPerDay = maxTradesPerDay;
  state.paper.tradeMode = tradeMode;
  res.json({ ok: true, paper: state.paper, liveEnabled });
});

app.post('/api/paper/order-config', (req, res) => {
  const body = req.body || {};
  const productRaw = body.productType;

  const qtyModeRaw = typeof body.qtyMode === 'string' ? body.qtyMode.trim().toUpperCase() : state.paper.qtyMode;
  const qtyMode = qtyModeRaw === 'LOTS' ? 'LOTS' : 'QTY';

  const lotsRaw = body.lots;
  const qtyPerLotRaw = body.qtyPerLot;
  const qtyRaw = body.orderQty;

  const lots = normalizeOrderQty(lotsRaw);
  const qtyPerLot = normalizeOrderQty(qtyPerLotRaw);

  const effectiveQty = qtyMode === 'LOTS'
    ? normalizeOrderQty(lots * qtyPerLot)
    : normalizeOrderQty(qtyRaw);

  state.paper.qtyMode = qtyMode;
  state.paper.lots = lots;
  state.paper.qtyPerLot = qtyPerLot;
  state.paper.orderQty = effectiveQty;
  state.paper.productType = normalizeProductType(productRaw);
  res.json({ ok: true, paper: state.paper });
});

app.post('/api/paper/direction', (req, res) => {
  const dir = req.body && typeof req.body.direction === 'string' ? req.body.direction.trim().toUpperCase() : 'AUTO';
  const allowed = new Set(['AUTO', 'BULL', 'BEAR']);
  if (!allowed.has(dir)) {
    res.status(400).json({ ok: false, error: 'Invalid direction. Use AUTO/BULL/BEAR', paper: state.paper });
    return;
  }
  state.paper.directionOverride = dir;
  res.json({ ok: true, paper: state.paper });
});

app.post('/api/paper/arm-live', (req, res) => {
  const liveEnabled = String(process.env.ENABLE_LIVE_TRADING || '').toLowerCase() === 'true';
  if (!liveEnabled) {
    res.status(400).json({ ok: false, error: 'LIVE trading is disabled. Set ENABLE_LIVE_TRADING=true in .env to allow it.', paper: state.paper });
    return;
  }
  const armed = req.body && typeof req.body.armed === 'boolean' ? req.body.armed : false;
  state.paper.liveArmed = armed;
  res.json({ ok: true, paper: state.paper });
});

app.post('/api/paper/mode', (req, res) => {
  const mode = req.body && typeof req.body.mode === 'string' ? req.body.mode.trim().toUpperCase() : '';
  const allowed = new Set(['AUTO', 'EXPIRY', 'NORMAL', 'BIG_RALLY']);
  if (!allowed.has(mode)) {
    res.status(400).json({ ok: false, error: 'Invalid mode. Use AUTO/EXPIRY/NORMAL/BIG_RALLY' });
    return;
  }
  state.paper.selectedMode = mode;
  res.json({ ok: true, paper: state.paper });
});

app.post('/api/paper/exit-config', (req, res) => {
  const exitStyle = req.body && typeof req.body.exitStyle === 'string' ? req.body.exitStyle.trim().toUpperCase() : '';
  const allowed = new Set(['TRAILING', 'TARGET']);
  if (!allowed.has(exitStyle)) {
    res.status(400).json({ ok: false, error: 'Invalid exitStyle. Use TRAILING or TARGET' });
    return;
  }

  const targetPctRaw = req.body ? req.body.targetPct : undefined;
  const targetPctNum = typeof targetPctRaw === 'number' ? targetPctRaw : Number(targetPctRaw);
  const targetPct = Number.isFinite(targetPctNum) ? Math.max(20, Math.min(100, Math.round(targetPctNum))) : state.paper.targetPct;

  state.paper.exitStyle = exitStyle;
  state.paper.targetPct = targetPct;
  res.json({ ok: true, paper: state.paper });
});

app.post('/api/paper/force-enter', (_req, res) => {
  if (state.paper.tradeMode === 'LIVE') {
    res.status(400).json({
      ok: false,
      error: 'Force Entry is PAPER-only. Switch Exec to PAPER to use Force Entry, or Arm LIVE and wait for a signal in LIVE mode.',
      paper: state.paper,
    });
    return;
  }
  const out = forceEnterPaperTrade(state.paper, state.snapshotHistory);
  if (!out.ok) {
    res.status(400).json({ ok: false, error: out.error || 'Force enter failed', paper: state.paper });
    return;
  }
  state.paper = out.state;
  res.json({ ok: true, paper: state.paper });
});

app.post('/api/paper/force-exit', (_req, res) => {
  if (state.paper.tradeMode === 'LIVE') {
    res.status(400).json({
      ok: false,
      error: 'Force Exit is PAPER-only. Switch Exec to PAPER to use Force Exit. LIVE exits are handled automatically by SL/Target, or you can add a dedicated LIVE manual exit.',
      paper: state.paper,
    });
    return;
  }
  const out = forceExitPaperTrade(state.paper, state.snapshotHistory, 'FORCED_EXIT');
  if (!out.ok) {
    res.status(400).json({ ok: false, error: out.error || 'Force exit failed', paper: state.paper });
    return;
  }
  state.paper = out.state;
  res.json({ ok: true, paper: state.paper });
});

app.get('/api/debug/search', async (req, res) => {
  try {
    if (!state.loggedIn) {
      res.status(401).json({ ok: false, error: 'Not logged in' });
      return;
    }
    const exch = typeof req.query.exch === 'string' ? req.query.exch : 'NSE';
    const text = typeof req.query.text === 'string' ? req.query.text : 'NIFTY';
    const reply = await state.client.searchscrip(exch, text);
    res.json({ ok: true, reply });
  } catch (e) {
    res.status(500).json({ ok: false, error: e && e.message ? e.message : String(e) });
  }
});

app.get('/api/snapshot', (_req, res) => {
  if (!state.loggedIn) {
    res.status(401).json({ ok: false, error: 'Not logged in. POST /api/login with factor2 code.' });
    return;
  }

  if (!state.lastSnapshot) {
    res.status(503).json({ ok: false, error: state.lastError || 'No snapshot yet. Call /api/start.' });
    return;
  }

  res.json({ ok: true, snapshot: state.lastSnapshot, lastError: state.lastError, paper: state.paper, live: state.live });
});

app.listen(port, () => {
  console.log(`Shoonya NIFTY OI web app running at http://localhost:${port}`);
  console.log('Create a .env from .env.example, then POST /api/start (UI does this automatically).');
});
