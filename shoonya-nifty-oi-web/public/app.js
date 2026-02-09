const startBtn = document.getElementById('startBtn');
const stopBtn = document.getElementById('stopBtn');
const refreshBtn = document.getElementById('refreshBtn');

const otpInput = document.getElementById('otpInput');
const loginBtn = document.getElementById('loginBtn');
const loginStatus = document.getElementById('loginStatus');

const elSuggestion = document.getElementById('suggestion');
const elConfidence = document.getElementById('confidence');
const elReasons = document.getElementById('reasons');
const elError = document.getElementById('error');

const elUnderLtp = document.getElementById('underLtp');
const elUnderSym = document.getElementById('underSym');
const elUnderToken = document.getElementById('underToken');
const elAtm = document.getElementById('atm');
const elSupport = document.getElementById('support');
const elResistance = document.getElementById('resistance');

const elCeItmOiSum = document.getElementById('ceItmOiSum');
const elPeItmOiSum = document.getElementById('peItmOiSum');
const elCePeRatio = document.getElementById('cePeRatio');

const tableBody = document.querySelector('#chainTable tbody');

async function postStart() {
  const res = await fetch('/api/start', { method: 'POST' });
  const data = await res.json();
  if (!data.ok) throw new Error(data.error || 'Failed to start');
}

async function postStop() {
  const res = await fetch('/api/stop', { method: 'POST' });
  const data = await res.json();
  if (!data.ok) throw new Error(data.error || 'Failed to stop');
}

async function postLogin(otp) {
  const res = await fetch('/api/login', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ otp }),
  });
  const data = await res.json();
  if (!data.ok) throw new Error(data.error || 'Login failed');
}

async function fetchHealth() {
  const res = await fetch('/api/health');
  return res.json();
}

function setLoginStatus(text, kind) {
  loginStatus.textContent = text;
  loginStatus.classList.remove('ok', 'bad');
  if (kind) loginStatus.classList.add(kind);
}

function fmtNum(x) {
  if (x === null || x === undefined) return '-';
  const n = Number(x);
  if (Number.isNaN(n)) return String(x);
  return n.toLocaleString('en-IN');
}

function fmtSigned(x) {
  if (x === null || x === undefined) return '-';
  const n = Number(x);
  if (Number.isNaN(n)) return String(x);
  const s = n >= 0 ? '+' : '';
  return s + n.toLocaleString('en-IN');
}

function render(snapshot, lastError) {
  elError.textContent = lastError || '';

  const sug = snapshot?.suggestion;
  elSuggestion.textContent = sug?.action || '-';
  elConfidence.textContent = sug ? `Confidence: ${fmtNum(sug.confidence)}` : '-';
  elReasons.innerHTML = sug?.reasons?.length ? sug.reasons.map(r => `<div>${r}</div>`).join('') : '';

  elUnderLtp.textContent = fmtNum(snapshot?.underlying?.ltp);
  elUnderSym.textContent = snapshot?.underlying?.tsym || '-';
  elUnderToken.textContent = snapshot?.underlying?.token || '-';
  elAtm.textContent = fmtNum(snapshot?.atmStrike);
  elSupport.textContent = fmtNum(snapshot?.levels?.supportStrike);
  elResistance.textContent = fmtNum(snapshot?.levels?.resistanceStrike);

  const itm = snapshot?.itmOiStats;
  elCeItmOiSum.textContent = itm && itm.ceItmOiSum !== null ? fmtNum(itm.ceItmOiSum) : '-';
  elPeItmOiSum.textContent = itm && itm.peItmOiSum !== null ? fmtNum(itm.peItmOiSum) : '-';
  elCePeRatio.textContent = itm && itm.ceOverPe !== null ? itm.ceOverPe.toFixed(2) : '-';

  tableBody.innerHTML = '';
  const rows = snapshot?.rows || [];
  for (const row of rows) {
    const tr = document.createElement('tr');
    if (row.strike === snapshot.atmStrike) tr.classList.add('highlight');

    tr.innerHTML = `
      <td>${fmtNum(row.strike)}</td>
      <td>${fmtNum(row.ce?.ltp)}</td>
      <td>${fmtNum(row.ce?.oi)}</td>
      <td>${fmtSigned(row.ce?.dOi)}</td>
      <td>${fmtSigned(row.pe?.dOi)}</td>
      <td>${fmtNum(row.pe?.oi)}</td>
      <td>${fmtNum(row.pe?.ltp)}</td>
    `;

    tableBody.appendChild(tr);
  }
}

async function refresh() {
  const health = await fetchHealth();
  setLoginStatus(health.loggedIn ? 'Logged in' : 'Not logged in', health.loggedIn ? 'ok' : null);

  if (!health.loggedIn) {
    elError.textContent = 'Not logged in. Enter factor2 code and click Login.';
    return;
  }

  if (!health.polling) {
    elError.textContent = 'Polling is stopped. Click Start to begin updates.';
    return;
  }

  const res = await fetch('/api/snapshot');
  const data = await res.json();
  if (!data.ok) {
    elError.textContent = data.error || 'No snapshot';
    return;
  }
  render(data.snapshot, data.lastError);
}

loginBtn.addEventListener('click', async () => {
  try {
    const otp = (otpInput.value || '').trim();
    await postLogin(otp);
    otpInput.value = '';
    const health = await fetchHealth();
    if (health.loggedIn) {
      setLoginStatus('Login successful', 'ok');
    } else {
      setLoginStatus('Login failed', 'bad');
    }
    elError.textContent = '';
  } catch (e) {
    setLoginStatus('Login failed', 'bad');
    elError.textContent = e && e.message ? e.message : String(e);
  }
});

startBtn.addEventListener('click', async () => {
  try {
    const health = await fetchHealth();
    setLoginStatus(health.loggedIn ? 'Logged in' : 'Not logged in', health.loggedIn ? 'ok' : null);
    if (!health.loggedIn) {
      throw new Error('Not logged in. Enter OTP and click Login first.');
    }
    await postStart();
    await refresh();
  } catch (e) {
    elError.textContent = e && e.message ? e.message : String(e);
  }
});

stopBtn.addEventListener('click', async () => {
  try {
    await postStop();
    elError.textContent = 'Polling stopped.';
  } catch (e) {
    elError.textContent = e && e.message ? e.message : String(e);
  }
});

refreshBtn.addEventListener('click', refresh);

(async () => {
  try {
    const health = await fetchHealth();
    setLoginStatus(health.loggedIn ? 'Logged in' : 'Not logged in', health.loggedIn ? 'ok' : null);
  } catch (_) {
  }

  await refresh();
  setInterval(refresh, 5000);
})();
