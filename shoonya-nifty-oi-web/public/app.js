const startBtn = document.getElementById('startBtn');
const stopBtn = document.getElementById('stopBtn');
const refreshBtn = document.getElementById('refreshBtn');
const elTradeQty = document.getElementById('tradeQty');
const elTradeCapital = document.getElementById('tradeCapital');

const authCodeInput = document.getElementById('authCodeInput');
const autoLoginBtn = document.getElementById('autoLoginBtn');
const loginWithCodeBtn = document.getElementById('loginWithCodeBtn');
const logoutBtn = document.getElementById('logoutBtn');
const loginStatus = document.getElementById('loginStatus');

const elSuggestion = document.getElementById('suggestion');
const elConfidence = document.getElementById('confidence');
const elMarketSentimentSummary = document.getElementById('marketSentimentSummary');

// ── Suggestion stability buffer ───────────────────────────────────────────────
// Only update displayed suggestion after CONFIRM consecutive polls agree.
// Prevents BUY_CE → NEUTRAL → BUY_PE flicker on every 2s poll.
const _sugBuf = { values: [], lastStable: '-', lastStableConfidence: 0 };
const SUGGEST_CONFIRM_POLLS = 3; // 3 polls × 2s = 6s hold
function getStableSuggestion(action, confidence) {
  const val = action || 'NO_TRADE';
  _sugBuf.values.push({ val, confidence: confidence || 0 });
  if (_sugBuf.values.length > SUGGEST_CONFIRM_POLLS)
    _sugBuf.values.shift();
  const full = _sugBuf.values.length === SUGGEST_CONFIRM_POLLS;
  const allSame = full && _sugBuf.values.every(v => v.val === _sugBuf.values[0].val);
  if (allSame) {
    _sugBuf.lastStable = _sugBuf.values[0].val;
    _sugBuf.lastStableConfidence = _sugBuf.values[0].confidence;
  }
  return { action: _sugBuf.lastStable, confidence: _sugBuf.lastStableConfidence };
}
// ─────────────────────────────────────────────────────────────────────────────
const elSnapshotMeta = document.getElementById('snapshotMeta');
const elTradeMode = document.getElementById('tradeMode');
const elTradeInstrument = document.getElementById('tradeInstrument');
const elTradeEntry = document.getElementById('tradeEntry');
const elTradeLtp = document.getElementById('tradeLtp');
const elTradePeak = document.getElementById('tradePeak');
const elTradeSl = document.getElementById('tradeSl');
const elTradeBreakoutLevel = document.getElementById('tradeBreakoutLevel');
const elTradeBreakoutSource = document.getElementById('tradeBreakoutSource');
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
const liveClearBtn = document.getElementById('liveClearBtn');

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
const amountInput = document.getElementById('amountInput');
const calcLotsBtn = document.getElementById('calcLotsBtn');
const amountHint = document.getElementById('amountHint');

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

function sentimentBadge(type, text) {
  let color = '#ffd54f'; // yellow

  switch (type) {
    case 'BULLISH':
    case 'CALM':
      color = '#00c853';
      break;

    case 'BEARISH':
    case 'HIGH':
      color = '#ff5252';
      break;

    case 'VOLATILE':
      color = '#ff9800';
      break;

    case 'NORMAL':
      color = '#42a5f5';
      break;
  }

  return `
    <span style="
      display:inline-block;
      margin-right:8px;
      color:${color};
      font-weight:600;
    ">
      ● ${text}
    </span>
  `;
}
function collectOrderConfigPayload() {
  const qtyMode = qtyModeSel ? qtyModeSel.value : 'QTY';
  const orderQty = orderQtyInput ? Number(orderQtyInput.value) : 1;
  const lots = lotsInput ? Number(lotsInput.value) : 1;
  const qtyPerLot = qtyPerLotInput ? Number(qtyPerLotInput.value) : 50;
  const productType = productTypeInput ? productTypeInput.value : 'M';
  const amount = amountInput ? Number(amountInput.value) || 0 : 0;
  return { qtyMode, orderQty, lots, qtyPerLot, productType, amount };
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

async function postLoginWithCode(authCode) {
  const res = await fetch('/api/login', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ authCode }),
  });
  const data = await res.json();
  if (!data.ok) throw new Error(data.error || 'Login failed');
  return data;
}

async function postAutoLogin() {
  const res = await fetch('/api/login/auto', { method: 'POST' });
  const data = await res.json();
  if (!data.ok) throw new Error(data.error || 'Auto login failed');
  return data;
}

async function postLogout() {
  const res = await fetch('/api/logout', { method: 'POST' });
  const data = await res.json();
  if (!data.ok) throw new Error(data.error || 'Logout failed');
  return data;
}

function parseAuthCodeInput(raw) {
  const s = (raw || '').trim();
  if (!s) return '';
  try {
    if (s.includes('code=')) {
      const u = s.includes('://') ? new URL(s) : new URL(`https://x?${s.replace(/^\?/, '')}`);
      return u.searchParams.get('code') || s;
    }
  } catch (_) {
    /* use raw */
  }
  return s;
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

function findLeg(snapshot, strike, optType) {
  const row = (snapshot?.rows || []).find((r) => r && r.strike === strike);
  if (!row) return null;
  return optType === 'CE' ? row.ce : row.pe;
}

function sanitizeTradeLtp(ltp, trade, snapshot) {
  const n = Number(ltp);
  if (!Number.isFinite(n) || n <= 0) return null;
  if (n > 3000) return null;

  const under = Number(snapshot?.underlying?.ltp);
  if (Number.isFinite(under) && under > 5000 && Math.abs(n - under) < 250) return null;

  const entry = Number(trade?.entryPrice);
  if (Number.isFinite(entry) && entry > 0) {
    if (n > Math.max(500, entry * 15)) return null;
  } else if (n > 800) {
    return null;
  }

  return n;
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

function formatExitReason(reason) {
  if (!reason) return '';
  const labels = {
    FALSE_BREAKOUT: 'False breakout (NIFTY moved back through breakout level)',
    SL_HIT: 'Stop loss hit',
    TRAIL_FROM_PEAK: 'Trail from peak (gave back profit)',
    EXIT_FILLED: 'Exit order filled',
    FORCED_EXIT: 'Manual force exit',
    FORCE_EXIT_EOD: 'Auto exit — outside trading hours',
    RECONCILED_NO_POSITION: 'Closed — no open position at broker',
  };
  if (labels[reason]) return labels[reason];
  if (String(reason).startsWith('TARGET_HIT_')) {
    const pct = String(reason).replace('TARGET_HIT_', '');
    return `Target profit hit (${pct}%)`;
  }
  return String(reason).replace(/_/g, ' ');
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

async function postLiveClearCurrent() {
  const res = await fetch('/api/live/clear-current', { method: 'POST' });
  const data = await res.json();
  if (!data.ok) throw new Error(data.error || 'Clear LIVE state failed');
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

function isLiveActiveTrade(live) {
  const s = live?.current?.status;
  return s === 'OPEN' || s === 'ENTRY_PENDING' || s === 'EXITING';
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
  if (lastError) elError.textContent = lastError;

  const engineDec =
    paper?.tradeMode === 'LIVE' ? live?.lastDecision : paper?.lastDecision;

  const sug = snapshot?.suggestion;
  const stableSug = getStableSuggestion(sug?.action, sug?.confidence);
  const dOiLabel = sug && !sug.dOiActive ? ' (static OI)' : '';
  elSuggestion.textContent = (stableSug.action || '-') + dOiLabel;
  elConfidence.textContent = sug ? `Confidence: ${fmtNum(stableSug.confidence)}` : '-';
  const ms = snapshot?.marketSentiment;

	if (elMarketSentimentSummary) {
	  if (!ms) {
		elMarketSentimentSummary.textContent = '-';
	  } else {
		elMarketSentimentSummary.innerHTML =
		  sentimentBadge(
			ms.vixSignal,
			`VIX ${ms.vixSignal} (${Number(ms.vix).toFixed(2)})`
		  ) +

		  sentimentBadge(
			ms.futureSignal,
			`FUT ${ms.futureSignal}`
		  ) +

		  sentimentBadge(
			ms.ceVwapSignal,
			ms.ceVwapSignal === 'BULLISH'
			  ? 'Calls Above VWAP'
			  : 'Calls Below VWAP'
		  ) +

		  sentimentBadge(
			ms.peVwapSignal,
			ms.peVwapSignal === 'BULLISH'
			  ? 'Puts Above VWAP'
			  : 'Puts Below VWAP'
		  );
	  }
	}
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
      const liveActive = isLiveActiveTrade(live);
      if (forceEnterBtn) forceEnterBtn.disabled = liveActive;
      if (forceExitBtn) forceExitBtn.disabled = !liveOpen;
    }

    const t = paper.tradeMode === 'LIVE'
      ? (live?.current || live?.lastClosed)
      : paper.currentTrade;

    const isOpenLike =
      t && (t.status === 'OPEN' || t.status === 'ENTRY_PENDING' || t.status === 'EXITING');

    if (isOpenLike) {
      const rawLtp = findLeg(snapshot, t.strike, t.optType)?.ltp;
      const ltp = rawLtp //sanitizeTradeLtp(rawLtp, t, snapshot);
      const pnl =
        typeof ltp === 'number' && typeof t.entryPrice === 'number'
          ? (ltp - t.entryPrice) * (t.qty || 1)
          : null;

	  const qty = Number(t.qty || 0);
	  const capital = Number(t.entryPrice || 0) * qty;

	  if (elTradeQty) {
	    elTradeQty.textContent = qty > 0
		  ? qty.toLocaleString('en-IN')
		  : '-';
	  }
	  if (elTradeCapital) {
		elTradeCapital.textContent = capital > 0
		  ? `₹${Math.round(capital).toLocaleString('en-IN')}`
		  : '-';
	  }
      elTradeInstrument.textContent = `${t.strike} ${t.optType} (${t.status})`;
      elTradeEntry.textContent =
        t.entryPrice == null
          ? (t.status === 'ENTRY_PENDING' ? 'pending…' : 'awaiting fill…')
          : fmtNum(t.entryPrice);
      if (elTradeLtp) elTradeLtp.textContent = fmtNum(ltp);
      elTradePeak.textContent = fmtNum(t.peakPrice);
      elTradeSl.textContent = fmtNum(t.slPrice);
      if (elTradeBreakoutLevel) elTradeBreakoutLevel.textContent = fmtNum(t.breakoutLevel);
      if (elTradeBreakoutSource) elTradeBreakoutSource.textContent = t.breakoutSource || '-';
      elTradePnl.textContent =
        t.status === 'ENTRY_PENDING' ? '-' : pnl === null ? '-' : fmtSigned(Math.round(pnl));
      elTradeUpdated.textContent = fmtTime(snapshot?.ts);

      elTradePnl.classList.remove('pnlPos', 'pnlNeg', 'pnlZero');
      if (typeof pnl === 'number' && pnl > 0) elTradePnl.classList.add('pnlPos');
      else if (typeof pnl === 'number' && pnl < 0) elTradePnl.classList.add('pnlNeg');
      else elTradePnl.classList.add('pnlZero');

      const orderNo = t.entryOrderNo || t.exitOrderNo || '-';
      elTradeNote.innerHTML = '';
      if (paper.tradeMode === 'LIVE') {
        elTradeNote.innerHTML = `<div><b>LIVE</b> order: ${orderNo}</div>`;
        if (t.status === 'ENTRY_PENDING') {
          elTradeNote.innerHTML += `<div style="margin-top:6px;">Waiting for entry fill (limit order). Poll will update when complete.</div>`;
        }
        if (t.status === 'EXITING') {
          elTradeNote.innerHTML += `<div style="margin-top:6px;">Exit in progress${t.exitReason ? `: ${formatExitReason(t.exitReason)}` : ''}</div>`;
        }
      }
      if (paper.tradeMode === 'LIVE' && live?.lastDecision) {
        const eng = live.lastDecision.action || '-';
        elTradeNote.innerHTML += `<div style="margin-top:6px;"><b>Engine action</b>: ${eng}${eng === 'HOLD' ? ' (monitoring; trade status is ' + t.status + ')' : ''}</div>`;
      }
      if (paper.tradeMode === 'LIVE' && t.status === 'OPEN' && t.entryPrice == null) {
        elTradeNote.innerHTML += `<div style="margin-top:6px;">Entry price will appear after the next poll reads your broker fill.</div>`;
      }
      if (rawLtp != null && ltp == null) {
        elTradeNote.innerHTML += `<div style="margin-top:6px;color:#888;font-size:0.9em;">Ignored one invalid LTP tick while calculating P/L.</div>`;
      }
    } else if (t && t.status === 'CLOSED') {
      elTradeInstrument.textContent = `${t.strike} ${t.optType}`;
      elTradeEntry.textContent = fmtNum(t.entryPrice);
      if (elTradeLtp) elTradeLtp.textContent = '-';
      elTradePeak.textContent = fmtNum(t.peakPrice);
      elTradeSl.textContent = fmtNum(t.slPrice);
      if (elTradeBreakoutLevel) elTradeBreakoutLevel.textContent = fmtNum(t.breakoutLevel);
      if (elTradeBreakoutSource) elTradeBreakoutSource.textContent = t.breakoutSource || '-';
      elTradePnl.textContent = fmtNum(Math.round(t.pnl));
      elTradeUpdated.textContent = fmtTime(t.exitTs);

      elTradePnl.classList.remove('pnlPos', 'pnlNeg', 'pnlZero');
      if (typeof t.pnl === 'number' && t.pnl > 0) elTradePnl.classList.add('pnlPos');
      else if (typeof t.pnl === 'number' && t.pnl < 0) elTradePnl.classList.add('pnlNeg');
      else elTradePnl.classList.add('pnlZero');
	  const qty = Number(t.qty || 0);
	  const capital = Number(t.entryPrice || 0) * qty;

		if (elTradeQty) {
		  elTradeQty.textContent = qty > 0
			? qty.toLocaleString('en-IN')
			: '-';
		}

		if (elTradeCapital) {
		  elTradeCapital.textContent = capital > 0
			? `₹${Math.round(capital).toLocaleString('en-IN')}`
			: '-';
		}
      if (t.exitReason) {
        elTradeNote.innerHTML = `<div><b>Exit reason</b>: ${formatExitReason(t.exitReason)}</div>`;
        if (paper.tradeMode === 'LIVE' && live?.lastDecision?.reasons?.length > 1) {
          const reconcile = live.lastDecision.reasons.find((r) => r.startsWith('RECONCILED'));
          if (reconcile && reconcile !== t.exitReason) {
            elTradeNote.innerHTML += `<div style="margin-top:4px;color:#888;font-size:0.9em;">${formatExitReason(reconcile)}</div>`;
          }
        }
      } else {
        elTradeNote.innerHTML = '';
      }
      if (paper.tradeMode === 'LIVE' && live && live.current) {
        elTradeNote.innerHTML += `<div style="margin-top:6px;"><b>LIVE Order</b>: ${live.current.entryOrderNo || '-'}</div>`;
      }
    } else {
      elTradeInstrument.textContent = '-';
      elTradeEntry.textContent = '-';
      if (elTradeLtp) elTradeLtp.textContent = '-';
      elTradePeak.textContent = '-';
      elTradeSl.textContent = '-';
      if (elTradeBreakoutLevel) elTradeBreakoutLevel.textContent = '-';
      if (elTradeBreakoutSource) elTradeBreakoutSource.textContent = '-';
      elTradePnl.textContent = '-';
      if (elTradeQty) elTradeQty.textContent = '-';
      if (elTradeCapital) elTradeCapital.textContent = '-';
      elTradeUpdated.textContent = '-';
      elTradePnl.classList.remove('pnlPos', 'pnlNeg', 'pnlZero');

      elTradeNote.innerHTML = `No trade yet`;
      if (engineDec?.reasons?.length) {
        elTradeNote.innerHTML +=
          `<div style="margin-top:6px;"><b>Last Decision</b>: ${engineDec.action || '-'} (${engineDec.mode || '-'})</div>` +
          engineDec.reasons.map((r) => `<div>${String(r || '')}</div>`).join('');
      } else if (paper.tradeMode === 'LIVE' && live?.lastDecision) {
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
    if (elTradeQty) elTradeQty.textContent = '-';
    if (elTradeCapital) elTradeCapital.textContent = '-';
    elTradeSl.textContent = '-';
    if (elTradeBreakoutLevel) elTradeBreakoutLevel.textContent = '-';
    if (elTradeBreakoutSource) elTradeBreakoutSource.textContent = '-';
    elTradePnl.textContent = '-';
	if (elTradeQty) elTradeQty.textContent = '-';
	if (elTradeCapital) elTradeCapital.textContent = '-';
    elTradeUpdated.textContent = '-';
    elTradePnl.classList.remove('pnlPos', 'pnlNeg', 'pnlZero');
    elTradeNote.innerHTML = '';
  }

  if (elConditionStatus) {
    const arr = Array.isArray(engineDec?.reasons) ? engineDec.reasons : [];
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

  if (tableBody) {
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
}

async function refresh() {
  try {
    const health = await fetchHealth();
    setLoginStatus(health.loggedIn ? 'Logged in' : 'Not logged in', health.loggedIn ? 'ok' : null);

    if (!health.loggedIn) {
      elError.textContent = 'Not logged in. Click Login (requires .env credentials).';
      return;
    }

    const res = await fetch('/api/snapshot');
    const data = await res.json();
    if (!data.ok) {
      elError.textContent = data.error || 'No snapshot';
      return;
    }

    if (!health.polling) {
      elError.textContent = 'Polling is stopped. Click Start to refresh chain and trade LTP.';
    } else if (!data.lastError) {
      elError.textContent = '';
    }

    render(data.snapshot, data.lastError, data.paper, data.live);
  } catch (e) {
    console.error('refresh failed:', e);
    elError.textContent = e && e.message ? e.message : String(e);
  }
}

modeAutoBtn?.addEventListener('click', async () => { try { await postPaperMode('AUTO'); await refresh(); } catch (e) { elError.textContent = e && e.message ? e.message : String(e); } });
modeNormalBtn?.addEventListener('click', async () => { try { await postPaperMode('NORMAL'); await refresh(); } catch (e) { elError.textContent = e && e.message ? e.message : String(e); } });
modeExpiryBtn?.addEventListener('click', async () => { try { await postPaperMode('EXPIRY'); await refresh(); } catch (e) { elError.textContent = e && e.message ? e.message : String(e); } });
modeBigBtn?.addEventListener('click', async () => { try { await postPaperMode('BIG_RALLY'); await refresh(); } catch (e) { elError.textContent = e && e.message ? e.message : String(e); } });

forceEnterBtn?.addEventListener('click', async () => {
  try {
    elError.textContent = '';
    const out = await postForceEnter();
    const ord = out?.order?.norenordno || out?.order?.orderno || out?.order?.order_no || '-';
    if (out?.warning) {
      elError.textContent = `Order ${ord} placed. ${out.warning}`;
    } else if (out?.order) {
      elError.textContent = `LIVE entry order placed: ${ord}`;
    } else {
      elError.textContent = 'Force entry placed.';
    }
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

liveClearBtn?.addEventListener('click', async () => {
  try {
    elError.textContent = '';
    const data = await postLiveClearCurrent();
    elError.textContent = data.cleared
      ? `Cleared stuck LIVE state (was ${data.previousStatus || 'unknown'}).`
      : 'No local LIVE trade was set.';
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

// ── Amount → Lots calculator (client-side only) ──────────────────────────────
// Uses the ATM option LTP from the chain table to estimate lots.
// Formula: lots = floor(amount / (ltp * qtyPerLot)), minimum 1.
function calcLotsFromAmount() {
  const amount = Number(amountInput?.value);
  if (!amount || amount <= 0) {
    if (amountHint) amountHint.textContent = '-';
    return;
  }

  // Read ATM LTP from the highlighted row in the chain table (CE or PE, lower of two)
  let ltp = null;
  const highlightRow = document.querySelector('#chainTable tbody tr.highlight');
  if (highlightRow) {
    const cells = highlightRow.querySelectorAll('td');
    const ceLtp = parseFloat((cells[1]?.textContent || '').replace(/,/g, ''));
    const peLtp = parseFloat((cells[6]?.textContent || '').replace(/,/g, ''));
    const valid = [ceLtp, peLtp].filter(x => Number.isFinite(x) && x > 0);
    if (valid.length) ltp = Math.max(...valid);
  }

  if (!ltp || ltp <= 0) {
    if (amountHint) amountHint.textContent = 'No LTP yet';
    return;
  }

  const qpl = qtyPerLotInput ? Math.max(1, Number(qtyPerLotInput.value) || 65) : 65;
  const MAX_QTY = 1800; // Shoonya single-order limit
  const maxLots = Math.floor(MAX_QTY / qpl); // e.g. 27 for qpl=65 -> 1755 qty
  const rawLots = Math.max(1, Math.floor(amount / (ltp * qpl)));
  const capped = rawLots > maxLots;
  const lots = capped ? maxLots : rawLots;
  const approxCost = lots * ltp * qpl;

  const cappedNote = capped ? ` ⚠ capped at ${maxLots} lots (max ${lots * qpl} qty)` : '';
  if (amountHint) amountHint.textContent = `~${lots} lot${lots > 1 ? 's' : ''} (₹${Math.round(approxCost).toLocaleString('en-IN')})${cappedNote}`;

  // Auto-fill lots and units qty fields
  if (lotsInput) lotsInput.value = String(lots);
  if (orderQtyInput) orderQtyInput.value = String(lots * qpl);
}

amountInput?.addEventListener('input', calcLotsFromAmount);

calcLotsBtn?.addEventListener('click', async () => {
  try {
    calcLotsFromAmount();
    await postOrderConfig(collectOrderConfigPayload());
    await refresh();
  } catch (e) {
    elError.textContent = e && e.message ? e.message : String(e);
  }
});
// ─────────────────────────────────────────────────────────────────────────────

directionSel?.addEventListener('change', async () => {
  try {
    await postDirection(directionSel.value);
    await refresh();
  } catch (e) {
    elError.textContent = e && e.message ? e.message : String(e);
  }
});

autoLoginBtn.addEventListener('click', async () => {
  try {
    autoLoginBtn.disabled = true;
    setLoginStatus('Auto login running…', null);
    elError.textContent = '';
    await postAutoLogin();
    const health = await fetchHealth();
    if (health.loggedIn) {
      setLoginStatus('Logged in', 'ok');
      const apiSection = document.getElementById('api-testing-section');
      if (apiSection) apiSection.style.display = 'block';
    } else {
      setLoginStatus('Login failed', 'bad');
    }
  } catch (e) {
    setLoginStatus('Login failed', 'bad');
    elError.textContent = e && e.message ? e.message : String(e);
  } finally {
    autoLoginBtn.disabled = false;
  }
});

logoutBtn?.addEventListener('click', async () => {
  try {
    logoutBtn.disabled = true;
    elError.textContent = '';
    await postLogout();
    setLoginStatus('Not logged in (session cleared)', null);
    const apiSection = document.getElementById('api-testing-section');
    if (apiSection) apiSection.style.display = 'none';
    await refresh();
  } catch (e) {
    elError.textContent = e && e.message ? e.message : String(e);
  } finally {
    logoutBtn.disabled = false;
  }
});

loginWithCodeBtn.addEventListener('click', async () => {
  try {
    const authCode = parseAuthCodeInput(authCodeInput.value);
    if (!authCode) {
      throw new Error('Paste the auth code from GetAuthcode or the redirect URL.');
    }
    loginWithCodeBtn.disabled = true;
    setLoginStatus('Exchanging auth code…', null);
    elError.textContent = '';
    await postLoginWithCode(authCode);
    authCodeInput.value = '';
    const health = await fetchHealth();
    if (health.loggedIn) {
      setLoginStatus('Logged in', 'ok');
      const apiSection = document.getElementById('api-testing-section');
      if (apiSection) apiSection.style.display = 'block';
    } else {
      setLoginStatus('Login failed', 'bad');
    }
  } catch (e) {
    setLoginStatus('Login failed', 'bad');
    elError.textContent = e && e.message ? e.message : String(e);
  } finally {
    loginWithCodeBtn.disabled = false;
  }
});

startBtn.addEventListener('click', async () => {
  try {
    const health = await fetchHealth();
    setLoginStatus(health.loggedIn ? 'Logged in' : 'Not logged in', health.loggedIn ? 'ok' : null);
    if (!health.loggedIn) {
      throw new Error('Not logged in. Click Login first.');
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
      let html = '<table><thead><tr><th>Entry Date/Time</th><th>Strike</th><th>Qty</th><th>Buy Price</th><th>Sell Price</th><th>P/L</th></tr></thead><tbody>';
      let totalPL = 0;
      for (const trade of data.trades) {
        const date = trade.entryTs && trade.entryTs > 0 ? new Date(trade.entryTs).toLocaleString() : 'N/A';
        const strike = trade.strike;
        const buyPrice = Math.round(trade.entryPrice);
        const sellPrice = Math.round(trade.exitPrice);
        const pnl = trade.pnl;
        totalPL += pnl || 0;
        const pnlStyle = pnl > 0 ? 'style="color: limegreen;"' : pnl < 0 ? 'style="color: red;"' : '';
        html += `<tr><td>${date}</td><td>${strike} ${trade.optType}</td><td>${trade.qty}</td><td>${buyPrice}</td><td>${sellPrice}</td><td ${pnlStyle}>${Math.round(pnl)}</td></tr>`;
      }
      const totalStyle = totalPL > 0 ? 'style="color: limegreen;"' : totalPL < 0 ? 'style="color: red;"' : '';
      html += `</tbody></table><div ${totalStyle}>Total P/L: ${Math.round(totalPL)}</div>`;
      document.getElementById('report-results').innerHTML = html;
    } else {
      document.getElementById('report-results').innerHTML = 'Error: ' + (data.error || 'Unknown error');
    }
  } catch (e) {
    document.getElementById('report-results').innerHTML = 'Fetch error: ' + e.message;
  }
});

// ── Generic API Tester ───────────────────────────────────────────────────────
{
  // Preset params for each method — helps remember common param shapes
  const API_PRESETS = {
    get_quotes: { exchange: 'NSE', token: '26000' },
    searchscrip: { exchange: 'NSE', stext: 'India VIX' },
    get_time_price_series: { exchange: 'NSE', token: '26000', starttime: '', endtime: '', interval: '5' },
    get_security_info: { exchange: 'NSE', token: '26000' },
    get_option_chain: { exchange: 'NFO', tradingsymbol: 'NIFTY', strikeprice: '24000', count: '5' },
    get_positions: {},
    get_holdings: {},
    get_order_book: {},
    get_trade_book: {},
    get_limits: {},
    get_pending_gtt_orders: {},
  };

  const API_HINTS = {
    get_quotes: 'exchange: NSE/NFO/BSE, token: numeric token',
    searchscrip: 'exchange: NSE/NFO, stext: partial name e.g. "India VIX"',
    get_time_price_series: 'starttime/endtime: "YYYY-MM-DD HH:MM:SS" IST or epoch seconds',
    get_security_info: 'exchange + token for full scrip details',
    get_option_chain: 'tradingsymbol: NIFTY/BANKNIFTY, strikeprice, count: number of strikes each side',
    get_positions: 'No params needed',
    get_holdings: 'No params needed',
    get_order_book: 'No params needed',
    get_trade_book: 'No params needed',
    get_limits: 'No params needed',
  };

  const methodSel = document.getElementById('api-method-sel');
  const methodCustom = document.getElementById('api-method-custom');
  const paramsEl = document.getElementById('api-params');
  const responseEl = document.getElementById('api-response');
  const statusEl = document.getElementById('api-status');
  const hintEl = document.getElementById('api-preset-hints');
  const testBtn = document.getElementById('test-api-btn');
  const clearBtn = document.getElementById('api-clear-btn');
  const copyBtn = document.getElementById('api-copy-btn');

  function getMethod() {
    const sel = methodSel ? methodSel.value : 'get_time_price_series';
    if (sel === 'custom') return (methodCustom ? methodCustom.value.trim() : '') || '';
    return sel;
  }

  function setPreset(method) {
    if (!paramsEl) return;
    const preset = API_PRESETS[method];
    if (preset) {
      // Fill in live timestamps for TPS
      if (method === 'get_time_price_series') {
        const now = new Date();
        const fmt = d => {
          const p = n => String(n).padStart(2,'0');
          return `${d.getFullYear()}-${p(d.getMonth()+1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}:00`;
        };
        preset.starttime = fmt(new Date(now.getTime() - 15 * 60000));
        preset.endtime = fmt(now);
      }
      paramsEl.value = JSON.stringify(preset, null, 2);
    } else {
      paramsEl.value = '{}';
    }
    if (hintEl) hintEl.textContent = API_HINTS[method] ? '💡 ' + API_HINTS[method] : '';
  }

  methodSel?.addEventListener('change', () => {
    const sel = methodSel.value;
    if (methodCustom) methodCustom.style.display = sel === 'custom' ? 'block' : 'none';
    if (sel !== 'custom') setPreset(sel);
    if (responseEl) responseEl.textContent = '';
    if (statusEl) statusEl.textContent = '';
  });

  // Init with default preset
  setPreset(methodSel ? methodSel.value : 'get_time_price_series');

  testBtn?.addEventListener('click', async () => {
    const method = getMethod();
    if (!method) { if (statusEl) statusEl.textContent = 'Enter a method name'; return; }
    if (statusEl) statusEl.textContent = 'Calling...';
    if (responseEl) responseEl.textContent = '';

    let params = paramsEl ? paramsEl.value.trim() : '{}';

    // Auto-convert IST datetime strings to epoch seconds for TPS
    try {
      const obj = JSON.parse(params || '{}');
      const toEpochSec = (s) => {
        if (typeof s !== 'string') return null;
        const t = s.trim();
        if (!t) return null;
        if (/^\d+$/.test(t)) return t;
        if (!t.includes(' ')) return null;
        const ms = new Date(t.replace(' ', 'T') + '+05:30').getTime();
        if (!Number.isFinite(ms)) return null;
        return String(Math.floor(ms / 1000));
      };
      if (obj.starttime) { const v = toEpochSec(obj.starttime); if (v) obj.starttime = v; }
      if (obj.endtime)   { const v = toEpochSec(obj.endtime);   if (v) obj.endtime   = v; }
      params = JSON.stringify(obj);
    } catch (_e) {}

    try {
      const res = await fetch(`/api/debug/call?api=${encodeURIComponent(method)}&params=${encodeURIComponent(params)}`);
      const data = await res.json();
      if (responseEl) responseEl.textContent = JSON.stringify(data, null, 2);
      if (statusEl) statusEl.textContent = data.ok ? '✅ OK' : '❌ Error';
    } catch (e) {
      if (responseEl) responseEl.textContent = 'Fetch error: ' + e.message;
      if (statusEl) statusEl.textContent = '❌ Failed';
    }
  });

  clearBtn?.addEventListener('click', () => {
    if (responseEl) responseEl.textContent = '';
    if (statusEl) statusEl.textContent = '';
    setPreset(getMethod());
  });

  copyBtn?.addEventListener('click', () => {
    const text = responseEl ? responseEl.textContent : '';
    if (text) navigator.clipboard.writeText(text).then(() => {
      if (statusEl) { statusEl.textContent = 'Copied!'; setTimeout(() => statusEl.textContent = '', 1500); }
    });
  });
}
// ─────────────────────────────────────────────────────────────────────────────

(async () => {
  try {
    const health = await fetchHealth();
    setLoginStatus(health.loggedIn ? 'Logged in' : 'Not logged in', health.loggedIn ? 'ok' : null);
    if (autoLoginBtn) {
      autoLoginBtn.disabled = !health.autoLoginAvailable || health.autoLoginBusy;
      if (!health.autoLoginAvailable) {
        autoLoginBtn.title =
          'Add SHOONYA_PASSWORD and SHOONYA_TOTP_SECRET to .env (see docs/API_ONLY_LOGIN.md)';
      }
    }
  } catch (_) {
  }

  await refresh();
  setInterval(() => {
    refresh().catch((e) => console.error('poll refresh:', e));
  }, 2000);
})();

// Market sentiment patch
