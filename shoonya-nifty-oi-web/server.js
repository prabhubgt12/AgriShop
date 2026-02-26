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
const TPS_POLL_MS = parseInt(process.env.TPS_POLL_MS || '60000', 10);
const TPS_SHIFT_DAYS = parseInt(process.env.TPS_SHIFT_DAYS || '0', 10);
 
const LIVE_STATE_PATH = path.join(__dirname, '.data', 'liveState.json');

let hasResyncedOnStart = false;
 
const state = {
  client: null,
  loggedIn: false,
  lastSnapshot: null,
  snapshotHistory: [],
  tps5m: {
    lastFetchTs: 0,
    lastCompleted: null,
    lastError: null,
    lastParams: null,
  },
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

function storeTrade(trade) {
  const filePath = path.join(__dirname, '.data', 'trades.json');
  let trades = [];
  if (fs.existsSync(filePath)) {
    try {
      trades = JSON.parse(fs.readFileSync(filePath, 'utf8'));
    } catch (_e) {
      trades = [];
    }
  }
  trades.push(trade);
  try {
    fs.writeFileSync(filePath, JSON.stringify(trades, null, 2));
  } catch (_e) {
    console.error('Failed to store trade:', _e);
  }
}

function getKey(dateStr, interval) {
  if (interval === 'all') return 'total';
  if (interval === 'day') return dateStr.slice(0, 10);
  if (interval === 'month') return dateStr.slice(0, 7);
  if (interval === 'week') return getWeekStart(dateStr);
  return 'total';
}

function getWeekStart(dateStr) {
  const d = new Date(dateStr + 'T00:00:00');
  const day = d.getDay();
  const diff = d.getDate() - day + (day === 0 ? -6 : 1);
  const weekStart = new Date(d.setDate(diff));
  return weekStart.toISOString().slice(0, 10);
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
  try {
    const getOrderId = (o) => (o && (o.norenordno || o.orderno || o.order_no)) || null;
    const toMs = (t) => {
      const v = t ? new Date(t).getTime() : NaN;
      return Number.isFinite(v) ? v : null;
    };

    const orders = await state.client.get_orderbook();
    if (!orders || !Array.isArray(orders)) {
      return false;
    }

    const trades = await state.client.get_tradebook();
    const existingTrades = safeReadJson(path.join(__dirname, '.data', 'trades.json')) || [];

    // If we have a current LIVE trade in memory, first try to close it using broker exit linkage
    // (works even if you currently have manual positions in the same tsym).
    if (state.live && state.live.current && (state.live.current.status === 'OPEN' || state.live.current.status === 'EXITING')) {
      const cur = state.live.current;
      const entryOrderNo = cur.entryOrderNo;
      if (entryOrderNo) {
        const entryTsMs = Number.isFinite(cur.entryTs) ? cur.entryTs : null;
        const exitOrder = orders.find((o) => {
          const parent = o && o.parentorderno ? String(o.parentorderno) : '';
          const remarks = o && o.remarks ? String(o.remarks) : '';
          const status = o && o.status ? String(o.status) : '';
          const isExitRemark = remarks.startsWith('exit_') || remarks === 'forced_exit';
          return isExitRemark && status === 'COMPLETE' && parent === String(entryOrderNo);
        }) || orders.find((o) => {
          // Fallback: Some brokers don't set parentorderno reliably.
          // Match by symbol+qty and time after entry.
          const remarks = o && o.remarks ? String(o.remarks) : '';
          const status = o && o.status ? String(o.status) : '';
          const trantype = o && o.trantype ? String(o.trantype) : '';
          const tsym = o && o.tsym ? String(o.tsym) : '';
          const qty = typeof o.qty === 'number' ? o.qty : Number(o.qty);
          const isExitRemark = remarks.startsWith('exit_') || remarks === 'forced_exit';
          const orderTs = toMs(o.norentm);
          const afterEntry = entryTsMs === null || orderTs === null ? true : orderTs >= entryTsMs;
          return isExitRemark && status === 'COMPLETE' && trantype === 'S' && tsym === String(cur.tsym) && qty === Number(cur.qty) && afterEntry;
        });

        if (exitOrder) {
          const exitOrderId = getOrderId(exitOrder);
          let exitPrice = null;
          if (trades && Array.isArray(trades) && exitOrderId) {
            const exitTrades = trades.filter(t => t && t.norenordno === exitOrderId);
            if (exitTrades.length > 0) {
              const p = parseFloat(exitTrades[0].avgprc);
              if (p > 0) exitPrice = p;
            }
          }

          const closed = {
            ...cur,
            status: 'CLOSED',
            exitTs: new Date(exitOrder.norentm || Date.now()).getTime(),
            exitReason: (() => {
              const r = String(exitOrder.remarks || '');
              if (r === 'forced_exit') return 'FORCED_EXIT';
              return r.replace('exit_', '') || 'EXIT';
            })(),
            exitOrderNo: exitOrderId,
            exitPrice,
            pnl: (typeof exitPrice === 'number' && typeof cur.entryPrice === 'number' && typeof cur.qty === 'number')
              ? (exitPrice - cur.entryPrice) * cur.qty
              : cur.pnl,
          };

          // Only store if not already stored.
          if (!existingTrades.some(t => t && t.entryOrderNo === closed.entryOrderNo)) {
            storeTrade(closed);
          }

          state.live.current = null;
          state.live.history = [...(state.live.history || []), closed].slice(-50);
          state.live.lastDecision = { ts: Date.now(), action: 'RESYNC_CLOSED', reasons: ['Exit order COMPLETE found for current trade'] };
          state.live.lastExitTs = Date.now();
          persistLiveState();
          return true;
        }
      }
    }

    const appOrders = orders.filter(o => o.remarks && (o.remarks.startsWith('forced_') || o.remarks.startsWith('auto_')));
    if (appOrders.length === 0) {
      return false;
    }
    let syncedAny = false;

    for (const entryOrder of appOrders) {
      const orderId = getOrderId(entryOrder);
      // Check if already synced (skip if we have a trade with this entryOrderNo)
      if (existingTrades.some(t => t.entryOrderNo === orderId)) continue;

      const entryTrades = trades.filter(t => t.norenordno === orderId);
      if (!trades || !Array.isArray(trades) || entryTrades.length === 0) continue;
      const entryTrade = entryTrades[0];
      const entryPrice = parseFloat(entryTrade.avgprc);
      if (!entryPrice || entryPrice <= 0) continue;

      let exitPrice = null;
      let pnl = null;
      let status = 'OPEN';
      let exitTs = null;
      let exitReason = null;
      let exitOrderNo = null;

      // Check for app-placed exit order
      const exitOrder = orders.find(o => o.remarks && o.remarks.startsWith('exit_') && o.status === 'COMPLETE' && o.parentorderno === entryOrder.norenordno);
      if (exitOrder) {
        const exitOrderId = exitOrder.norenordno || exitOrder.orderno || exitOrder.order_no;
        status = 'CLOSED';
        exitTs = new Date(exitOrder.norentm).getTime();
        exitReason = exitOrder.remarks.replace('exit_', '');
        exitOrderNo = exitOrderId;
        const exitTrades = trades.filter(t => t.norenordno === exitOrderId);
        if (exitTrades.length > 0) {
          const exitTrade = exitTrades[0];
          exitPrice = parseFloat(exitTrade.avgprc);
          if (exitPrice > 0) {
            pnl = (exitPrice - entryPrice) * parseInt(entryOrder.qty);
          }
        }
      } else {
        // Check for manual exits: sell trades on same tsym
        const sellTrades = trades.filter(t => t.tsym === entryOrder.tsym && t.trantype === 'S');
        if (sellTrades.length > 0) {
          const manualExitTrade = sellTrades[0]; // Take the first one, assuming bulk exit
          exitPrice = parseFloat(manualExitTrade.avgprc);
          if (exitPrice > 0) {
            pnl = (exitPrice - entryPrice) * parseInt(entryOrder.qty);
            status = 'CLOSED';
            exitTs = new Date(manualExitTrade.norentm || entryOrder.norentm || Date.now()).getTime();
            exitReason = 'MANUAL_EXIT';
            exitOrderNo = manualExitTrade.norenordno;
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
      if (!tsymMatch) continue;
      const optType = tsymMatch[1] === 'C' ? 'CE' : 'PE';
      const strike = parseInt(tsymMatch[2]);
      const qty = parseInt(entryOrder.qty);
      const tradeObj = {
        id: `resynced_${orderId}_${Date.now()}`,
        status,
        mode,
        strike,
        optType,
        tsym: entryOrder.tsym,
        exchange: entryOrder.exch,
        qty,
        entryTs: new Date(entryOrder.norentm || Date.now()).getTime(),
        entryOrderNo: orderId,
        entryPrice,
        peakPrice: entryPrice,
        slPrice,
        exitTs,
        exitPrice,
        exitReason,
        exitOrderNo,
        pnl,
      };

      if (status === 'CLOSED') {
        storeTrade(tradeObj);
        syncedAny = true;
      } else if (status === 'OPEN') {
        state.live.current = tradeObj;
      }
    }

    if (state.live.current) {
      state.paper.tradeMode = 'LIVE';
    }

    return syncedAny || !!state.live.current;
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

function istEpochSeconds(d) {
  // Convert a JS Date to epoch seconds, treating the wall-clock as IST.
  // (Used only for TPS calls)
  if (!(d instanceof Date) || Number.isNaN(d.getTime())) return null;
  const yyyy = d.getFullYear();
  const mm = String(d.getMonth() + 1).padStart(2, '0');
  const dd = String(d.getDate()).padStart(2, '0');
  const hh = String(d.getHours()).padStart(2, '0');
  const mi = String(d.getMinutes()).padStart(2, '0');
  const ss = String(d.getSeconds()).padStart(2, '0');
  const iso = `${yyyy}-${mm}-${dd}T${hh}:${mi}:${ss}+05:30`;
  return Math.floor(new Date(iso).getTime() / 1000);
}

function clampToMarketHoursIst(d) {
  // Clamp wall-clock time to NSE cash market hours (approx) 09:15–15:30 IST.
  if (!(d instanceof Date) || Number.isNaN(d.getTime())) return d;
  const hh = d.getHours();
  const mm = d.getMinutes();
  const mins = hh * 60 + mm;
  const open = 9 * 60 + 15;
  const close = 15 * 60 + 30;

  if (mins < open) {
    d.setHours(9, 20, 0, 0);
    return d;
  }
  if (mins > close) {
    d.setHours(15, 25, 0, 0);
    return d;
  }
  return d;
}

function extractCandleHighLow(c) {
  if (!c || typeof c !== 'object') return null;
  const hi = Number(c.inth ?? c.high ?? c.h);
  const lo = Number(c.intl ?? c.low ?? c.l);
  if (!Number.isFinite(hi) || !Number.isFinite(lo)) return null;
  return { high: hi, low: lo, raw: c };
}

async function maybeUpdateTps5m(api, token) {
  if (!api || typeof api.get_time_price_series !== 'function') return;
  const now = Date.now();
  if (now - (state.tps5m.lastFetchTs || 0) < TPS_POLL_MS) return;
  state.tps5m.lastFetchTs = now;

  try {
    const nowIst = new Date();
    const shiftDays = Number.isFinite(TPS_SHIFT_DAYS) ? TPS_SHIFT_DAYS : 0;
    if (shiftDays) {
      nowIst.setDate(nowIst.getDate() - shiftDays);
      clampToMarketHoursIst(nowIst);
    }
    const endSec = istEpochSeconds(nowIst);
    const startSec = endSec !== null ? (endSec - 60 * 60) : null; // last 60m is enough to get a few 5m candles
    if (!Number.isFinite(startSec) || !Number.isFinite(endSec)) return;

    const reqParams = {
      exchange: 'NSE',
      token,
      starttime: String(startSec),
      endtime: String(endSec),
      interval: '5',
    };
    state.tps5m.lastParams = { ...reqParams, _shiftDays: shiftDays };

    // console.log('[TPS-5m] calling get_time_price_series:', reqParams);
    const res = await api.get_time_price_series(reqParams);
    // console.log('[TPS-5m] response:', res);

    if (!res || !Array.isArray(res) || res.length < 2) {
      state.tps5m.lastError = 'No data';
      return;
    }

    // Use the second candle (last completed, as res[0] is in-progress)
    const lastCompletedRaw = res[1];
    const hl = extractCandleHighLow(lastCompletedRaw);
    if (!hl) {
      state.tps5m.lastError = 'Candle high/low missing';
      return;
    }

    state.tps5m.lastCompleted = {
      high: hl.high,
      low: hl.low,
      fetchedAt: now,
    };
    state.tps5m.lastError = null;
    console.log(`TPS 5m updated: high=${hl.high}, low=${hl.low}, fetchedAt=${new Date(now).toISOString()}`);
  } catch (e) {
    state.tps5m.lastError = e && e.message ? e.message : String(e);
  }
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

  // Update TPS 5m candle cache (once per minute) and attach last completed candle HL to snapshot
  const underToken = snapshot && snapshot.underlying ? snapshot.underlying.token : null;
  if (underToken) {
    await maybeUpdateTps5m(state.client, underToken);
    if (state.tps5m.lastCompleted) {
      snapshot.candle5m = { ...state.tps5m.lastCompleted };
    }
  }
 
  if (state.paper && state.paper.enabled) {
    state.paper = stepPaperTrade(state.paper, state.snapshotHistory, state.paper.selectedMode);
  }

  // Resync once on start
  if (!hasResyncedOnStart) {
    await tryResyncFromShoonya();
    hasResyncedOnStart = true;
  }

  // LIVE execution (safe gated by env + UI)
  const liveEnabled = String(process.env.ENABLE_LIVE_TRADING || '').toLowerCase() === 'true';
  if (liveEnabled && state.paper && state.paper.tradeMode === 'LIVE') {
    const cooldownMs = parseInt(process.env.TRADE_COOLDOWN_MS || '30000', 10);
    let entryDecision;
    if (state.live.current && (state.live.current.status === 'OPEN' || state.live.current.status === 'EXITING')) {
      entryDecision = { ok: false, reasons: ['Live trade already open'] };
    } else if (state.live.lastExitTs && Date.now() - state.live.lastExitTs < cooldownMs) {
      entryDecision = { ok: false, reasons: ['Cooldown active'] };
    } else {
      entryDecision = computeEntryDecision(state.paper, state.snapshotHistory);
    }
    if (entryDecision.ok && state.live.current && (state.live.current.status === 'OPEN' || state.live.current.status === 'EXITING')) {
      entryDecision = { ok: false, reasons: ['Live trade already open'] };
    }
    if (entryDecision.ok && state.live.lastRejectTs && Date.now() - state.live.lastRejectTs < cooldownMs) {
      entryDecision = { ok: false, reasons: ['Cooldown after rejection'] };
    }
    console.log('Live entryDecision:', entryDecision);
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
 
  let updateInFlight = false;
 
  const loop = async () => {
    if (updateInFlight) return;
    updateInFlight = true;
    try {
      await updateOnce();
    } catch (e) {
      state.lastError = e && e.message ? e.message : String(e);
    } finally {
      updateInFlight = false;
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

app.get('/api/debug/tps5m', (_req, res) => {
  res.json({
    ok: true,
    tps5m: {
      lastFetchTs: state.tps5m.lastFetchTs,
      lastCompleted: state.tps5m.lastCompleted,
      lastError: state.tps5m.lastError,
      lastParams: state.tps5m.lastParams,
      shiftDays: Number.isFinite(TPS_SHIFT_DAYS) ? TPS_SHIFT_DAYS : 0,
    },
  });
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
          if (effectiveMode === 'NORMAL') return entryPrice * 0.8;
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
        storeTrade(closed);
        state.live.current = closed;
        state.live.history = [...(state.live.history || []), closed].slice(-50);
        state.live.lastDecision = { ts: Date.now(), action: 'FORCED_EXIT', reasons: ['FORCED_EXIT'] };
        state.live.lastExitTs = Date.now();
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

app.get('/api/debug/call', async (req, res) => {
  try {
    if (!state.loggedIn) {
      res.status(401).json({ ok: false, error: 'Not logged in' });
      return;
    }
    const api = req.query.api || req.body.api;
    const paramsStr = req.query.params || req.body.params;
    const params = paramsStr ? JSON.parse(paramsStr) : {};
    if (!state.client || typeof state.client[api] !== 'function') {
      res.status(400).json({ ok: false, error: 'Invalid API name or client not available' });
      return;
    }
    params.uid = state.auth.userid;
    // Note: get_time_price_series works with original parameter names in automated code
    // Keep debug/call generic; caller controls exact params shape.
    const reply = await state.client[api](params);
    res.json({ ok: true, reply });
  } catch (e) {
    res.status(500).json({ ok: false, error: e && e.message ? e.message : String(e) });
  }
});

app.get('/api/snapshot', (_req, res) => {
  res.set('Cache-Control', 'no-cache, no-store, must-revalidate');
  res.set('Pragma', 'no-cache');
  res.set('Expires', '0');

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

app.get('/api/trades/report', (req, res) => {
  const trades = safeReadJson(path.join(__dirname, '.data', 'trades.json')) || [];
  let filtered = trades.filter(t => t.status === 'CLOSED'); // Only closed trades
  const from = req.query.from;
  const to = req.query.to;
  if (from) {
    const fromTs = new Date(from + 'T00:00:00').getTime();
    filtered = filtered.filter(t => t.exitTs >= fromTs);
  }
  if (to) {
    const toTs = new Date(to + 'T23:59:59').getTime();
    filtered = filtered.filter(t => t.exitTs <= toTs);
  }
  res.json({ ok: true, trades: filtered });
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
