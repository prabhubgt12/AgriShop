const path = require('path');
const express = require('express');
const dotenv = require('dotenv');

dotenv.config({ path: path.join(__dirname, '.env') });

const { createShoonyaClient } = require('./src/shoonyaClient');
const { buildNiftyChainSnapshot } = require('./src/optionChain');
const { computeSuggestion } = require('./src/signalEngine');

const app = express();
app.use(express.json());
app.use(express.static(path.join(__dirname, 'public')));

const port = parseInt(process.env.PORT || '3000', 10);
const pollSeconds = parseInt(process.env.POLL_SECONDS || '5', 10);

const state = {
  client: null,
  loggedIn: false,
  lastSnapshot: null,
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

  snapshot.suggestion = computeSuggestion(snapshot, state.lastSnapshot);

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

  res.json({ ok: true, snapshot: state.lastSnapshot, lastError: state.lastError });
});

app.listen(port, () => {
  console.log(`Shoonya NIFTY OI web app running at http://localhost:${port}`);
  console.log('Create a .env from .env.example, then POST /api/start (UI does this automatically).');
});
