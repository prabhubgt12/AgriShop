const path = require('path');
const fs = require('fs');
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
  computeDirectionSignal,
  selectInstrument,
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
 
const LIVE_STATE_PATH = path.join(__dirname, '.data', 'liveState.json');
 
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
 
function safeReadJson(filePath) {
  try {
    if (!fs.existsSync(filePath)) return null;
    const raw = fs.readFileSync(filePath, 'utf8');
    if (!raw) return null;
    return JSON.parse(raw);
  } catch (_e) {
    return null;
  }
}
 
function safeWriteJson(filePath, obj) {
  try {
    fs.mkdirSync(path.dirname(filePath), { recursive: true });
    fs.writeFileSync(filePath, JSON.stringify(obj, null, 2), 'utf8');
    return true;
  } catch (_e) {
    return false;
  }
}
 
function persistLiveState() {
  safeWriteJson(LIVE_STATE_PATH, { live: state.live, savedAt: Date.now() });
}
 
function tryRestoreLiveState() {
  const data = safeReadJson(LIVE_STATE_PATH);
  if (!data || !data.live) return false;
  if (!data.live.current || (data.live.current.status !== 'OPEN' && data.live.current.status !== 'EXITING')) return false;
  state.live = { ...createLiveTradeState(), ...data.live };
  return true;
}
 
tryRestoreLiveState();
 
async function tryResyncFromShoonya() {
  if (!state.loggedIn || !state.client) return false;
  try {
    const orders = await state.client.get_orderbook();
    if (!orders || !Array.isArray(orders)) return false;
    const appOrders = orders.filter(o => o.remarks && (o.remarks.startsWith('forced_') || o.remarks.startsWith('auto_')) && o.status === 'COMPLETE' && o.trantype === 'B');
    if (appOrders.length === 0) return false;

    // Assume first one for entry
    const entryOrder = appOrders[0];

    // Check for a corresponding exit order
    const exitOrder = orders.find(o => o.remarks && o.remarks.startsWith('exit_') && o.status === 'COMPLETE' && o.parentorderno === entryOrder.norenordno);

    const trades = await state.client.get_tradebook();
    const entryTrades = trades.filter(t => t.norenordno === entryOrder.norenordno);
    if (!trades || !Array.isArray(trades) || entryTrades.length === 0) return false;
    const entryTrade = entryTrades[0];
    const entryPrice = parseFloat(entryTrade.avgprc);
    if (!entryPrice || entryPrice <= 0) return false;

    let exitPrice = null;
    let pnl = null;
    let status = 'OPEN';
    let exitTs = null;
    let exitReason = null;
    let exitOrderNo = null;

    if (exitOrder) {
      status = 'CLOSED';
      exitTs = Date.now(); // Use current time for resync exit time
      exitReason = exitOrder.remarks.replace('exit_', '');
      exitOrderNo = exitOrder.norenordno;
      const exitTrades = trades.filter(t => t.norenordno === exitOrder.norenordno);
      if (exitTrades.length > 0) {
        const exitTrade = exitTrades[0];
        exitPrice = parseFloat(exitTrade.avgprc);
        if (exitPrice > 0) {
          pnl = (exitPrice - entryPrice) * parseInt(entryOrder.qty);
        }
      }
    }

    // Check for manual exits: any sell order COMPLETE on same tsym
    if (status === 'OPEN') {
      const sellOrders = orders.filter(o => o.tsym === entryOrder.tsym && o.trantype === 'S' && o.status === 'COMPLETE');
      if (sellOrders.length > 0) {
        const manualExitOrder = sellOrders[0];
        const manualExitTrades = trades.filter(t => t.norenordno === manualExitOrder.norenordno);
        if (manualExitTrades.length > 0) {
          const manualExitTrade = manualExitTrades[0];
          exitPrice = parseFloat(manualExitTrade.avgprc);
          if (exitPrice > 0) {
            pnl = (exitPrice - entryPrice) * parseInt(entryOrder.qty);
            status = 'CLOSED';
            exitTs = Date.now();
            exitReason = 'MANUAL_EXIT';
            exitOrderNo = manualExitOrder.norenordno;
          }
        }
      }
    }

    const mode = (entryOrder.remarks.split('_')[1] || 'NORMAL').toUpperCase();
    let slPrice;
    if (mode === 'EXPIRY') slPrice = entryPrice * 0.75;
    else if (mode === 'NORMAL') slPrice = entryPrice * 0.7;
    else if (mode === 'BIG_RALLY') slPrice = entryPrice * 0.6;
    else slPrice = entryPrice * 0.7;
    const tsymMatch = entryOrder.tsym.match(/([CP])(\d+)$/);
    if (!tsymMatch) return false;
    const optType = tsymMatch[1] === 'C' ? 'CE' : 'PE';
    const strike = parseInt(tsymMatch[2]);
    const qty = parseInt(entryOrder.qty);
    const tradeObj = {
      id: `resynced_${Date.now()}`,
      status,
      mode,
      strike,
      optType,
      tsym: entryOrder.tsym,
      exchange: entryOrder.exch,
      qty,
      entryTs: Date.now(),
      entryOrderNo: entryOrder.norenordno,
      entryPrice,
      peakPrice: entryPrice,
      slPrice,
      exitTs,
      exitPrice,
      exitReason,
      exitOrderNo,
      pnl,
    };
    state.live = { ...createLiveTradeState(), current: tradeObj };
    // Restore paper config to match
    state.paper.tradeMode = 'LIVE';
    state.paper.selectedMode = mode;
    state.paper.orderQty = qty;
    state.paper.productType = entryOrder.prd;
    state.paper.qtyMode = 'QTY';
    state.paper.lots = 1;
    state.paper.qtyPerLot = qty;
    state.paper.directionOverride = 'AUTO';
    state.paper.exitStyle = 'TRAILING';
    state.paper.targetPct = 30;
    state.paper.maxTradesPerDay = 3;
    return true;
  } catch (e) {
    console.error('Resync from Shoonya failed:', e);
    return false;
  }
}
 
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
 
    if (state.live && state.live.current && (state.live.current.status === 'OPEN' || state.live.current.status === 'EXITING')) {
      persistLiveState();
    }
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
 
app.post('/api/live/resync', async (_req, res) => {
  const ok = await tryResyncFromShoonya();
  if (!ok) {
    res.status(404).json({ ok: false, error: 'No app-placed LIVE trade found to resync from Shoonya.', live: state.live, paper: state.paper });
    return;
  }
  res.json({ ok: true, live: state.live, paper: state.paper });
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
    (async () => {
      try {
        if (state.live && state.live.current && state.live.current.status === 'OPEN') {
          res.status(400).json({ ok: false, error: 'LIVE trade already OPEN. Force Exit it before forcing a new entry.', paper: state.paper, live: state.live });
          return;
        }
        const liveEnabled = String(process.env.ENABLE_LIVE_TRADING || '').toLowerCase() === 'true';
        if (!liveEnabled) {
          res.status(400).json({ ok: false, error: 'LIVE trading is disabled. Set ENABLE_LIVE_TRADING=true in .env to allow it.', paper: state.paper });
          return;
        }
        if (!state.paper.liveArmed) {
          res.status(400).json({ ok: false, error: 'LIVE not armed. Click Arm LIVE first.', paper: state.paper });
          return;
        }
        if (!state.client || typeof state.client.place_order !== 'function') {
          res.status(400).json({ ok: false, error: 'Shoonya client.place_order not available.', paper: state.paper });
          return;
        }

        const latest = Array.isArray(state.snapshotHistory) ? state.snapshotHistory[state.snapshotHistory.length - 1] : null;
        if (!latest) {
          res.status(400).json({ ok: false, error: 'No snapshot available.', paper: state.paper });
          return;
        }

        const selectedMode = state.paper.selectedMode || 'AUTO';
        const effectiveMode = selectedMode === 'AUTO' ? (state.paper.effectiveMode || 'NORMAL') : selectedMode;
        const { direction } = computeDirectionSignal(state.snapshotHistory, effectiveMode, state.paper);
        const inst = selectInstrument(latest, effectiveMode, direction || 'BULL');
        if (!inst) {
          res.status(400).json({ ok: false, error: 'Instrument selection failed.', paper: state.paper });
          return;
        }

        const row = (latest?.rows || []).find((r) => r && r.strike === inst.strike);
        const leg = inst.type === 'CE' ? row?.ce : row?.pe;
        const tradingsymbol = leg?.tsym;
        if (!tradingsymbol) {
          res.status(400).json({ ok: false, error: `TradingSymbol missing for ${inst.strike} ${inst.type}`, paper: state.paper });
          return;
        }

        const qty = typeof state.paper.orderQty === 'number' ? state.paper.orderQty : Number(state.paper.orderQty);
        const quantity = Number.isFinite(qty) ? qty : 1;
        const productType = typeof state.paper.productType === 'string' ? state.paper.productType : 'M';

        const resp = await state.client.place_order({
          buy_or_sell: 'B',
          product_type: productType,
          exchange: 'NFO',
          tradingsymbol,
          quantity,
          discloseqty: 0,
          price_type: 'MKT',
          price: 0,
          trigger_price: 0,
          retention: 'DAY',
          remarks: `forced_${effectiveMode}`,
        });

        if (!resp || resp.stat !== 'Ok') {
          res.status(500).json({ ok: false, error: `LIVE entry order failed: ${JSON.stringify(resp || 'Unknown error')}`, paper: state.paper });
          return;
        }

        const orderno = resp.norenordno || resp.orderno || resp.order_no || null;

        // Confirm order fill
        if (orderno) {
          await new Promise(resolve => setTimeout(resolve, 2000)); // Wait 2 seconds for potential fill
          try {
            const trades = await state.client.get_tradebook();
            const entryTrades = trades.filter(t => t.norenordno === orderno);
            if (!trades || !Array.isArray(trades) || entryTrades.length === 0) {
              res.status(500).json({ ok: false, error: 'Order placed but not filled yet. Check Shoonya app for status.', paper: state.paper });
              return;
            }
          } catch (e) {
            res.status(500).json({ ok: false, error: 'Failed to confirm order fill: ' + (e && e.message ? e.message : String(e)), paper: state.paper });
            return;
          }
        }

        const entryPrice = typeof leg?.ltp === 'number' ? leg.ltp : null;
        const slPrice = (() => {
          if (typeof entryPrice !== 'number' || entryPrice <= 0) return null;
          if (effectiveMode === 'EXPIRY') return entryPrice * 0.75;
          if (effectiveMode === 'NORMAL') return entryPrice * 0.70;
          if (effectiveMode === 'BIG_RALLY') return entryPrice * 0.60;
          return entryPrice * 0.70;
        })();
        const opened = {
          id: `live_forced_${Date.now()}`,
          status: 'OPEN',
          mode: effectiveMode,
          strike: inst.strike,
          optType: inst.type,
          tsym: tradingsymbol,
          exchange: 'NFO',
          qty: quantity,
          entryTs: Date.now(),
          entryOrderNo: orderno,
          entryPrice,
          peakPrice: entryPrice,
          slPrice,
          exitTs: null,
          exitPrice: null,
          exitReason: null,
          exitOrderNo: null,
          pnl: null,
        };

        state.live.current = opened;
        state.live.history = [...(state.live.history || []), opened].slice(-50);
        state.live.lastDecision = { ts: Date.now(), action: 'FORCED_ENTRY', reasons: ['FORCED_ENTRY'] };
        persistLiveState();
        res.json({ ok: true, paper: state.paper, live: state.live, order: resp });
      } catch (e) {
        res.status(500).json({
          ok: false,
          error: e && e.message ? e.message : String(e || 'Unknown error'),
          stack: e && e.stack ? e.stack : null,
          paper: state.paper,
        });
      }
    })();
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
    (async () => {
      try {
        const liveEnabled = String(process.env.ENABLE_LIVE_TRADING || '').toLowerCase() === 'true';
        if (!liveEnabled) {
          res.status(400).json({ ok: false, error: 'LIVE trading is disabled. Set ENABLE_LIVE_TRADING=true in .env to allow it.', paper: state.paper });
          return;
        }
        if (!state.client || typeof state.client.place_order !== 'function') {
          res.status(400).json({ ok: false, error: 'Shoonya client.place_order not available.', paper: state.paper });
          return;
        }
        if (!state.live.current || state.live.current.status !== 'OPEN') {
          res.status(400).json({ ok: false, error: 'No open LIVE trade to exit.', paper: state.paper, live: state.live });
          return;
        }

        const t = state.live.current;
        const resp = await state.client.place_order({
          buy_or_sell: 'S',
          product_type: state.paper.productType || 'M',
          exchange: t.exchange || 'NFO',
          tradingsymbol: t.tsym,
          quantity: t.qty,
          discloseqty: 0,
          price_type: 'MKT',
          price: 0,
          trigger_price: 0,
          retention: 'DAY',
          remarks: 'forced_exit',
        });

        if (!resp || resp.stat !== 'Ok') {
          res.status(500).json({ ok: false, error: `LIVE exit order failed: ${JSON.stringify(resp || 'Unknown error')}`, paper: state.paper, live: state.live });
          return;
        }

        const closed = {
          ...t,
          status: 'CLOSED',
          exitTs: Date.now(),
          exitReason: 'FORCED_EXIT',
          exitOrderNo: resp.norenordno || resp.orderno || resp.order_no || null,
        };
        state.live.current = closed;
        state.live.history = [...(state.live.history || []), closed].slice(-50);
        state.live.lastDecision = { ts: Date.now(), action: 'FORCED_EXIT', reasons: ['FORCED_EXIT'] };
        persistLiveState();
        res.json({ ok: true, paper: state.paper, live: state.live, order: resp });
      } catch (e) {
        res.status(500).json({
          ok: false,
          error: e && e.message ? e.message : String(e || 'Unknown error'),
          stack: e && e.stack ? e.stack : null,
          paper: state.paper,
        });
      }
    })();
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
  console.log('Registered routes:');
  app._router.stack.forEach((r) => {
    if (r.route && r.route.path) {
      const methods = Object.keys(r.route.methods).join(',').toUpperCase();
      console.log(`  ${methods} ${r.route.path}`);
    }
  });
});
