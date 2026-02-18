const startBtn = document.getElementById('startBtn');
const stopBtn = document.getElementById('stopBtn');
const refreshBtn = document.getElementById('refreshBtn');

const otpInput = document.getElementById('otpInput');
const loginBtn = document.getElementById('loginBtn');
const loginStatus = document.getElementById('loginStatus');

const elSuggestion = document.getElementById('suggestion');
const elConfidence = document.getElementById('confidence');
const elSnapshotMeta = document.getElementById('snapshotMeta');
const elTradeMode = document.getElementById('tradeMode');
const elTradeInstrument = document.getElementById('tradeInstrument');
const elTradeEntry = document.getElementById('tradeEntry');
const elTradeLtp = document.getElementById('tradeLtp');
const elTradePeak = document.getElementById('tradePeak');
const elTradeSl = document.getElementById('tradeSl');
const elTradePnl = document.getElementById('tradePnl');
const elTradeUpdated = document.getElementById('tradeUpdated');
const elTradeNote = document.getElementById('tradeNote');
const elReasons = document.getElementById('reasons');
const elError = document.getElementById('error');

const modeAutoBtn = document.getElementById('modeAutoBtn');
const modeNormalBtn = document.getElementById('modeNormalBtn');
const modeExpiryBtn = document.getElementById('modeExpiryBtn');
const modeBigBtn = document.getElementById('modeBigBtn');

const forceEnterBtn = document.getElementById('forceEnterBtn');
const forceExitBtn = document.getElementById('forceExitBtn');
const liveResyncBtn = document.getElementById('liveResyncBtn');

const exitStyleSel = document.getElementById('exitStyleSel');
const targetPctRange = document.getElementById('targetPctRange');
const targetPctLabel = document.getElementById('targetPctLabel');

const tradeModeSel = document.getElementById('tradeModeSel');
const maxTradesInput = document.getElementById('maxTradesInput');
const tradesTodayLabel = document.getElementById('tradesTodayLabel');

const qtyModeSel = document.getElementById('qtyModeSel');
const orderQtyInput = document.getElementById('orderQtyInput');
const lotsInput = document.getElementById('lotsInput');
const qtyPerLotInput = document.getElementById('qtyPerLotInput');
const productTypeInput = document.getElementById('productTypeInput');
const armLiveBtn = document.getElementById('armLiveBtn');

const directionSel = document.getElementById('directionSel');

const elUnderLtp = document.getElementById('underLtp');
const elUnderVwap = document.getElementById('underVwap');
const elUnderSym = document.getElementById('underSym');
const elUnderToken = document.getElementById('underToken');
const elAtm = document.getElementById('atm');
const elSupport = document.getElementById('support');
const elResistance = document.getElementById('resistance');

const elCeItmOiSum = document.getElementById('ceItmOiSum');
const elPeItmOiSum = document.getElementById('peItmOiSum');
const elCePeRatio = document.getElementById('cePeRatio');
const elCeItmDOiSum = document.getElementById('ceItmDOiSum');
const elPeItmDOiSum = document.getElementById('peItmDOiSum');
const elCePeDOiRatio = document.getElementById('cePeDOiRatio');
const elItmOiSignal = document.getElementById('itmOiSignal');
const elConditionStatus = document.getElementById('conditionStatus');

const tableBody = document.querySelector('#chainTable tbody');

async function postStart() {
  const res = await fetch('/api/start', { method: 'POST' });
  const data = await res.json();
  if (!data.ok) throw new Error(data.error || 'Failed to start');
}

function collectOrderConfigPayload() {
  const qtyMode = qtyModeSel ? qtyModeSel.value : 'QTY';
  const orderQty = orderQtyInput ? Number(orderQtyInput.value) : 1;
  const lots = lotsInput ? Number(lotsInput.value) : 1;
  const qtyPerLot = qtyPerLotInput ? Number(qtyPerLotInput.value) : 50;
  const productType = productTypeInput ? productTypeInput.value : 'M';
  return { qtyMode, orderQty, lots, qtyPerLot, productType };
}

async function postDirection(direction) {
  const res = await fetch('/api/paper/direction', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ direction }),
  });
  const data = await res.json();
  if (!data.ok) throw new Error(data.error || 'Failed to set direction');
  return data.paper;
}

async function postOrderConfig(payload) {
  const res = await fetch('/api/paper/order-config', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
  });
  const data = await res.json();
  if (!data.ok) throw new Error(data.error || 'Failed to set order config');
  return data.paper;
}

async function postArmLive(armed) {
  const res = await fetch('/api/paper/arm-live', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ armed }),
  });
  const data = await res.json();
  if (!data.ok) throw new Error(data.error || 'Failed to arm live');
  return data.paper;
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

function fmtTime(ts) {
  if (!ts) return '-';
  const d = new Date(ts);
  if (Number.isNaN(d.getTime())) return '-';
  return d.toLocaleTimeString('en-IN');
}

function setModeButtons(selectedMode) {
  const mode = (selectedMode || '').toUpperCase();
  const all = [modeAutoBtn, modeNormalBtn, modeExpiryBtn, modeBigBtn];
  all.forEach((b) => b && b.classList.add('secondary'));
  const active = {
    AUTO: modeAutoBtn,
    NORMAL: modeNormalBtn,
    EXPIRY: modeExpiryBtn,
    BIG_RALLY: modeBigBtn,
  }[mode];
  if (active) active.classList.remove('secondary');
}

async function postPaperMode(mode) {
  const res = await fetch('/api/paper/mode', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ mode }),
  });
  const data = await res.json();
  if (!data.ok) throw new Error(data.error || 'Failed to set mode');
  return data.paper;
}

async function postRiskConfig(tradeMode, maxTradesPerDay) {
  const res = await fetch('/api/paper/risk-config', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ tradeMode, maxTradesPerDay }),
  });
  const data = await res.json();
  if (!data.ok) throw new Error(data.error || 'Failed to set risk config');
  return data.paper;
}

function setRiskControls(paper) {
  const tm = typeof paper?.tradeMode === 'string' ? paper.tradeMode.toUpperCase() : 'PAPER';
  const maxT = typeof paper?.maxTradesPerDay === 'number' ? paper.maxTradesPerDay : Number(paper?.maxTradesPerDay);
  const maxNorm = Number.isFinite(maxT) ? maxT : 3;
  const today = typeof paper?.tradesToday === 'number' ? paper.tradesToday : Number(paper?.tradesToday);
  const todayNorm = Number.isFinite(today) ? today : 0;

  if (tradeModeSel) tradeModeSel.value = tm === 'LIVE' ? 'LIVE' : 'PAPER';
  if (maxTradesInput) maxTradesInput.value = String(maxNorm);
  if (tradesTodayLabel) tradesTodayLabel.textContent = `${todayNorm}/${maxNorm}`;

  const isLive = tm === 'LIVE';
  if (forceEnterBtn) forceEnterBtn.disabled = false;
  if (forceExitBtn) forceExitBtn.disabled = false;

  const qty = typeof paper?.orderQty === 'number' ? paper.orderQty : Number(paper?.orderQty);
  const qtyNorm = Number.isFinite(qty) ? qty : 1;
  const qm = typeof paper?.qtyMode === 'string' ? paper.qtyMode.toUpperCase() : 'QTY';
  const lots = typeof paper?.lots === 'number' ? paper.lots : Number(paper?.lots);
  const lotsNorm = Number.isFinite(lots) ? lots : 1;
  const qpl = typeof paper?.qtyPerLot === 'number' ? paper.qtyPerLot : Number(paper?.qtyPerLot);
  const qplNorm = Number.isFinite(qpl) ? qpl : 50;
  const pt = typeof paper?.productType === 'string' ? paper.productType : 'M';
  if (qtyModeSel) qtyModeSel.value = qm === 'LOTS' ? 'LOTS' : 'QTY';
  if (orderQtyInput) orderQtyInput.value = String(qtyNorm);
  if (lotsInput) lotsInput.value = String(lotsNorm);
  if (qtyPerLotInput) qtyPerLotInput.value = String(qplNorm);
  if (productTypeInput) productTypeInput.value = pt;

  const armed = !!paper?.liveArmed;
  if (armLiveBtn) armLiveBtn.textContent = armed ? 'LIVE ARMED' : 'Arm LIVE';

  const dir = typeof paper?.directionOverride === 'string' ? paper.directionOverride.toUpperCase() : 'AUTO';
  if (directionSel) directionSel.value = ['AUTO', 'BULL', 'BEAR'].includes(dir) ? dir : 'AUTO';
}

async function postForceEnter() {
  const res = await fetch('/api/paper/force-enter', { method: 'POST' });
  const data = await res.json();
  if (!data.ok) throw new Error(data.error || 'Force entry failed');
  return data;
}

async function postForceExit() {
  const res = await fetch('/api/paper/force-exit', { method: 'POST' });
  const data = await res.json();
  if (!data.ok) throw new Error(data.error || 'Force exit failed');
  return data;
}

async function postLiveResync() {
  const res = await fetch('/api/live/resync', { method: 'POST' });
  const data = await res.json();
  if (!data.ok) throw new Error(data.error || 'LIVE resync failed');
  return data;
}

async function postExitConfig(exitStyle, targetPct) {
  const res = await fetch('/api/paper/exit-config', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ exitStyle, targetPct }),
  });
  const data = await res.json();
  if (!data.ok) throw new Error(data.error || 'Failed to set exit config');
  return data.paper;
}

function setExitControls(paper) {
  const style = (paper?.exitStyle || 'TRAILING').toUpperCase();
  const pct = typeof paper?.targetPct === 'number' ? paper.targetPct : Number(paper?.targetPct);
  const pctNorm = Number.isFinite(pct) ? pct : 30;

  if (exitStyleSel) exitStyleSel.value = style;
  if (targetPctRange) targetPctRange.value = String(pctNorm);
  if (targetPctLabel) targetPctLabel.textContent = `${pctNorm}%`;
  if (targetPctRange) targetPctRange.disabled = style !== 'TARGET';
}

function render(snapshot, lastError, paper, live) {
  elError.textContent = lastError || '';

  const sug = snapshot?.suggestion;
  elSuggestion.textContent = sug?.action || '-';
  elConfidence.textContent = sug ? `Confidence: ${fmtNum(sug.confidence)}` : '-';
  if (sug && sug.window) {
    elSnapshotMeta.textContent = `Updated: ${fmtTime(snapshot?.ts)} • Window: ${fmtNum(sug.window.count)} snaps (${fmtTime(sug.window.fromTs)} - ${fmtTime(sug.window.toTs)})`;
  } else {
    elSnapshotMeta.textContent = `Updated: ${fmtTime(snapshot?.ts)}`;
  }
  elReasons.innerHTML = sug?.reasons?.length ? sug.reasons.map(r => `<div>${String(r || '')}</div>`).join('') : '';

  if (paper) {
    setModeButtons(paper.selectedMode);
    setRiskControls(paper);
    setExitControls(paper);
    elTradeMode.textContent = `${paper.selectedMode} (effective: ${paper.effectiveMode})`;

    if (paper.tradeMode === 'LIVE') {
      const liveOpen = !!(live && live.current && live.current.status === 'OPEN');
      if (forceEnterBtn) forceEnterBtn.disabled = liveOpen;
      if (forceExitBtn) forceExitBtn.disabled = !liveOpen;
    }

    const t = paper.tradeMode === 'LIVE' ? live?.current : paper.currentTrade;
    if (t && t.status === 'OPEN') {
      const ltp = (() => {
        const row = (snapshot?.rows || []).find(r => r.strike === t.strike);
        const leg = t.optType === 'CE' ? row?.ce : row?.pe;
        return leg?.ltp;
      })();
      const pnl = (typeof ltp === 'number' && typeof t.entryPrice === 'number') ? (ltp - t.entryPrice) * (t.qty || 1) : null;

      elTradeInstrument.textContent = `${t.strike} ${t.optType}`;
      elTradeEntry.textContent = fmtNum(t.entryPrice);
      if (elTradeLtp) elTradeLtp.textContent = fmtNum(ltp);
      elTradePeak.textContent = fmtNum(t.peakPrice);
      elTradeSl.textContent = fmtNum(t.slPrice);
      elTradePnl.textContent = pnl === null ? '-' : fmtSigned(pnl);
      elTradeUpdated.textContent = fmtTime(snapshot?.ts);

      elTradePnl.classList.remove('pnlPos', 'pnlNeg', 'pnlZero');
      if (typeof pnl === 'number' && pnl > 0) elTradePnl.classList.add('pnlPos');
      else if (typeof pnl === 'number' && pnl < 0) elTradePnl.classList.add('pnlNeg');
      else elTradePnl.classList.add('pnlZero');

      elTradeNote.innerHTML = '';
      if (paper.tradeMode === 'LIVE' && live && live.current) {
        elTradeNote.innerHTML = `<div><b>LIVE Order</b>: ${live.current.entryOrderNo || '-'}</div>`;
      }
      if (paper.tradeMode === 'LIVE' && live && live.lastDecision) {
        elTradeNote.innerHTML += `<div style="margin-top:6px;"><b>LIVE</b>: ${live.lastDecision.action || '-'}</div>`;
      }
    } else if (t && t.status === 'CLOSED') {
      elTradeInstrument.textContent = `${t.strike} ${t.optType}`;
      elTradeEntry.textContent = fmtNum(t.entryPrice);
      if (elTradeLtp) elTradeLtp.textContent = '-';
      elTradePeak.textContent = fmtNum(t.peakPrice);
      elTradeSl.textContent = fmtNum(t.slPrice);
      elTradePnl.textContent = fmtNum(t.pnl);
      elTradeUpdated.textContent = fmtTime(t.exitTs);

      elTradePnl.classList.remove('pnlPos', 'pnlNeg', 'pnlZero');
      if (typeof t.pnl === 'number' && t.pnl > 0) elTradePnl.classList.add('pnlPos');
      else if (typeof t.pnl === 'number' && t.pnl < 0) elTradePnl.classList.add('pnlNeg');
      else elTradePnl.classList.add('pnlZero');

      elTradeNote.innerHTML = t.exitReason ? `<div><b>Exit</b>: ${t.exitReason}</div>` : '';
      if (paper.tradeMode === 'LIVE' && live && live.current) {
        elTradeNote.innerHTML += `<div style="margin-top:6px;"><b>LIVE Order</b>: ${live.current.entryOrderNo || '-'}</div>`;
      }
    } else {
      elTradeInstrument.textContent = '-';
      elTradeEntry.textContent = '-';
      if (elTradeLtp) elTradeLtp.textContent = '-';
      elTradePeak.textContent = '-';
      elTradeSl.textContent = '-';
      elTradePnl.textContent = '-';
      elTradeUpdated.textContent = '-';
      elTradePnl.classList.remove('pnlPos', 'pnlNeg', 'pnlZero');

      const dec = paper.lastDecision;
      const decText = dec && dec.reasons && dec.reasons.length
        ? `<div><b>Last Decision</b>: ${dec.action || '-'} (${dec.mode || '-'})</div>`
          + dec.reasons.map(r => `<div>${String(r || '')}</div>`).join('')
        : '';
      elTradeNote.innerHTML = `No trade yet`;
      if (paper.tradeMode === 'LIVE' && live && live.lastDecision) {
        elTradeNote.innerHTML += `<div style="margin-top:6px;"><b>LIVE</b>: ${live.lastDecision.action || '-'}</div>`;
      }
    }
  } else {
    setRiskControls({ tradeMode: 'PAPER', maxTradesPerDay: 3, tradesToday: 0 });
    setExitControls({ exitStyle: 'TRAILING', targetPct: 30 });
    elTradeMode.textContent = '-';
    elTradeInstrument.textContent = '-';
    elTradeEntry.textContent = '-';
    if (elTradeLtp) elTradeLtp.textContent = '-';
    elTradePeak.textContent = '-';
    elTradeSl.textContent = '-';
    elTradePnl.textContent = '-';
    elTradeUpdated.textContent = '-';
    elTradePnl.classList.remove('pnlPos', 'pnlNeg', 'pnlZero');
    elTradeNote.innerHTML = '';
  }

  if (elConditionStatus) {
    const dec = paper?.lastDecision;
    const arr = Array.isArray(dec?.reasons) ? dec.reasons : [];
    elConditionStatus.innerHTML = arr.length
      ? arr.map((r) => `<div>${String(r || '')}</div>`).join('')
      : '<div>-</div>';
  }

  elUnderLtp.textContent = fmtNum(snapshot?.underlying?.ltp);
  if (elUnderVwap) elUnderVwap.textContent = fmtNum(snapshot?.underlying?.vwap);
  elUnderSym.textContent = snapshot?.underlying?.tsym || '-';
  elUnderToken.textContent = snapshot?.underlying?.token || '-';
  elAtm.textContent = fmtNum(snapshot?.atmStrike);
  elSupport.textContent = fmtNum(snapshot?.levels?.supportStrike);
  elResistance.textContent = fmtNum(snapshot?.levels?.resistanceStrike);

  const itm = snapshot?.itmOiStats;
  if (elItmOiSignal) {
    const sig = itm && typeof itm.signal === 'string' ? itm.signal : '';
    elItmOiSignal.textContent = sig || '-';
    elItmOiSignal.classList.remove('signalBull', 'signalBear', 'signalNeutral');
    if (sig === 'BULLISH') elItmOiSignal.classList.add('signalBull');
    else if (sig === 'BEARISH') elItmOiSignal.classList.add('signalBear');
    else if (sig) elItmOiSignal.classList.add('signalNeutral');
  }
  elCeItmOiSum.textContent = itm && itm.ceItmOiSum !== null ? fmtNum(itm.ceItmOiSum) : '-';
  elPeItmOiSum.textContent = itm && itm.peItmOiSum !== null ? fmtNum(itm.peItmOiSum) : '-';
  elCePeRatio.textContent = itm && itm.ceOverPe !== null ? itm.ceOverPe.toFixed(2) : '-';
  if (elCeItmDOiSum) elCeItmDOiSum.textContent = itm && itm.ceItmDOiSum !== null ? fmtSigned(itm.ceItmDOiSum) : '-';
  if (elPeItmDOiSum) elPeItmDOiSum.textContent = itm && itm.peItmDOiSum !== null ? fmtSigned(itm.peItmDOiSum) : '-';
  if (elCePeDOiRatio) elCePeDOiRatio.textContent = itm && itm.ceDOiOverPeDOi !== null ? itm.ceDOiOverPeDOi.toFixed(2) : '-';

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
  render(data.snapshot, data.lastError, data.paper, data.live);
}

modeAutoBtn?.addEventListener('click', async () => { try { await postPaperMode('AUTO'); await refresh(); } catch (e) { elError.textContent = e && e.message ? e.message : String(e); } });
modeNormalBtn?.addEventListener('click', async () => { try { await postPaperMode('NORMAL'); await refresh(); } catch (e) { elError.textContent = e && e.message ? e.message : String(e); } });
modeExpiryBtn?.addEventListener('click', async () => { try { await postPaperMode('EXPIRY'); await refresh(); } catch (e) { elError.textContent = e && e.message ? e.message : String(e); } });
modeBigBtn?.addEventListener('click', async () => { try { await postPaperMode('BIG_RALLY'); await refresh(); } catch (e) { elError.textContent = e && e.message ? e.message : String(e); } });

forceEnterBtn?.addEventListener('click', async () => {
  try {
    elError.textContent = '';
    const out = await postForceEnter();
    elError.textContent = out && out.order ? `LIVE entry order placed: ${(out.order.norenordno || out.order.orderno || out.order.order_no || '-')}` : 'Force entry placed.';
    await refresh();
  } catch (e) {
    elError.textContent = e && e.message ? e.message : String(e);
  }
});
forceExitBtn?.addEventListener('click', async () => {
  try {
    elError.textContent = '';
    const out = await postForceExit();
    elError.textContent = out && out.order ? `LIVE exit order placed: ${(out.order.norenordno || out.order.orderno || out.order.order_no || '-')}` : 'Force exit placed.';
    await refresh();
  } catch (e) {
    elError.textContent = e && e.message ? e.message : String(e);
  }
});

liveResyncBtn?.addEventListener('click', async () => {
  try {
    elError.textContent = '';
    await postLiveResync();
    elError.textContent = 'LIVE trade resynced from saved state.';
    await refresh();
  } catch (e) {
    elError.textContent = e && e.message ? e.message : String(e);
  }
});

exitStyleSel?.addEventListener('change', async () => {
  try {
    const style = exitStyleSel.value;
    const pct = targetPctRange ? Number(targetPctRange.value) : 30;
    await postExitConfig(style, pct);
    await refresh();
  } catch (e) {
    elError.textContent = e && e.message ? e.message : String(e);
  }
});

targetPctRange?.addEventListener('input', () => {
  if (targetPctLabel) targetPctLabel.textContent = `${targetPctRange.value}%`;
});

targetPctRange?.addEventListener('change', async () => {
  try {
    const style = exitStyleSel ? exitStyleSel.value : 'TRAILING';
    const pct = Number(targetPctRange.value);
    await postExitConfig(style, pct);
    await refresh();
  } catch (e) {
    elError.textContent = e && e.message ? e.message : String(e);
  }
});

tradeModeSel?.addEventListener('change', async () => {
  try {
    const tm = tradeModeSel.value;
    const maxT = maxTradesInput ? Number(maxTradesInput.value) : 3;
    await postRiskConfig(tm, maxT);
    await refresh();
  } catch (e) {
    elError.textContent = e && e.message ? e.message : String(e);
  }
});

maxTradesInput?.addEventListener('change', async () => {
  try {
    const tm = tradeModeSel ? tradeModeSel.value : 'PAPER';
    const maxT = Number(maxTradesInput.value);
    await postRiskConfig(tm, maxT);
    await refresh();
  } catch (e) {
    elError.textContent = e && e.message ? e.message : String(e);
  }
});

orderQtyInput?.addEventListener('change', async () => {
  try {
    await postOrderConfig(collectOrderConfigPayload());
    await refresh();
  } catch (e) {
    elError.textContent = e && e.message ? e.message : String(e);
  }
});

qtyModeSel?.addEventListener('change', async () => {
  try {
    await postOrderConfig(collectOrderConfigPayload());
    await refresh();
  } catch (e) {
    elError.textContent = e && e.message ? e.message : String(e);
  }
});

lotsInput?.addEventListener('change', async () => {
  try {
    await postOrderConfig(collectOrderConfigPayload());
    await refresh();
  } catch (e) {
    elError.textContent = e && e.message ? e.message : String(e);
  }
});

qtyPerLotInput?.addEventListener('change', async () => {
  try {
    await postOrderConfig(collectOrderConfigPayload());
    await refresh();
  } catch (e) {
    elError.textContent = e && e.message ? e.message : String(e);
  }
});

productTypeInput?.addEventListener('change', async () => {
  try {
    await postOrderConfig(collectOrderConfigPayload());
    await refresh();
  } catch (e) {
    elError.textContent = e && e.message ? e.message : String(e);
  }
});

armLiveBtn?.addEventListener('click', async () => {
  try {
    const currentlyArmed = armLiveBtn.textContent === 'LIVE ARMED';
    await postArmLive(!currentlyArmed);
    await refresh();
  } catch (e) {
    elError.textContent = e && e.message ? e.message : String(e);
  }
});

directionSel?.addEventListener('change', async () => {
  try {
    await postDirection(directionSel.value);
    await refresh();
  } catch (e) {
    elError.textContent = e && e.message ? e.message : String(e);
  }
});

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

document.getElementById('generate-report').addEventListener('click', async () => {
  const interval = document.getElementById('report-interval').value;
  let from = '';
  let to = '';
  const now = new Date();
  if (interval === 'day') {
    from = new Date(now.getTime() - 24 * 60 * 60 * 1000).toISOString().slice(0, 10);
    to = now.toISOString().slice(0, 10);
  } else if (interval === 'week') {
    from = new Date(now.getTime() - 7 * 24 * 60 * 60 * 1000).toISOString().slice(0, 10);
    to = now.toISOString().slice(0, 10);
  } else if (interval === 'month') {
    from = new Date(now.getTime() - 30 * 24 * 60 * 60 * 1000).toISOString().slice(0, 10);
    to = now.toISOString().slice(0, 10);
  }
  // Set the inputs
  document.getElementById('report-from').value = from;
  document.getElementById('report-to').value = to;
  const params = new URLSearchParams();
  if (from) params.append('from', from);
  if (to) params.append('to', to);
  try {
    const res = await fetch('/api/trades/report?' + params);
    const data = await res.json();
    if (data.ok) {
      let html = '<table><thead><tr><th>Date</th><th>Strike</th><th>Buy Price</th><th>Sell Price</th><th>P/L</th></tr></thead><tbody>';
      let totalPL = 0;
      for (const trade of data.trades) {
        const date = new Date(trade.exitTs).toLocaleDateString();
        const strike = trade.strike;
        const buyPrice = trade.entryPrice;
        const sellPrice = trade.exitPrice;
        const pnl = trade.pnl;
        totalPL += pnl || 0;
        const pnlClass = pnl > 0 ? 'profit' : pnl < 0 ? 'loss' : '';
        html += `<tr><td>${date}</td><td>${strike}</td><td>${buyPrice}</td><td>${sellPrice}</td><td class="${pnlClass}">${pnl}</td></tr>`;
      }
      html += `</tbody></table><div>Total P/L: ${totalPL}</div>`;
      document.getElementById('report-results').innerHTML = html;
    } else {
      document.getElementById('report-results').innerHTML = 'Error: ' + (data.error || 'Unknown error');
    }
  } catch (e) {
    document.getElementById('report-results').innerHTML = 'Fetch error: ' + e.message;
  }
});

(async () => {
  try {
    const health = await fetchHealth();
    setLoginStatus(health.loggedIn ? 'Logged in' : 'Not logged in', health.loggedIn ? 'ok' : null);
  } catch (_) {
  }

  await refresh();
  setInterval(refresh, 5000);
})();
