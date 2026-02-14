const fs = require('fs');
const path = require('path');
const axios = require('axios');
const AdmZip = require('adm-zip');

const CACHE_DIR = path.join(__dirname, '..', '.cache');
const NFO_URL = 'https://api.shoonya.com/NFO_symbols.txt.zip';
const NSE_URL = 'https://api.shoonya.com/NSE_symbols.txt.zip';

function ensureCacheDir() {
  if (!fs.existsSync(CACHE_DIR)) fs.mkdirSync(CACHE_DIR, { recursive: true });
}

async function downloadZip(url, outPath) {
  const res = await axios.get(url, { responseType: 'arraybuffer', timeout: 20000 });
  fs.writeFileSync(outPath, Buffer.from(res.data));
}

function unzipFirstFile(zipPath) {
  const zip = new AdmZip(zipPath);
  const entries = zip.getEntries();
  if (!entries.length) throw new Error(`No entries in zip: ${zipPath}`);
  return entries[0].getData().toString('utf-8');
}

function parseCsv(text) {
  const lines = text.split(/\r?\n/).filter(Boolean);
  if (!lines.length) return [];

  const header = lines[0].split(',').map((h) => h.trim());
  const rows = [];

  for (let i = 1; i < lines.length; i += 1) {
    const parts = lines[i].split(',');
    if (parts.length < 3) continue;

    const row = {};
    for (let j = 0; j < header.length && j < parts.length; j += 1) {
      row[header[j]] = parts[j];
    }
    rows.push(row);
  }

  return rows;
}

function parseExpiryToDate(exp) {
  if (!exp) return null;

  // Common formats seen: DD-MMM-YYYY, YYYY-MM-DD, DDMMMYY
  const d1 = new Date(exp);
  if (!Number.isNaN(d1.getTime())) return d1;

  const m = exp.match(/^(\d{2})([A-Za-z]{3})(\d{2,4})$/);
  if (m) {
    const dd = parseInt(m[1], 10);
    const monStr = m[2].toLowerCase();
    const yyRaw = m[3];
    const year = yyRaw.length === 2 ? 2000 + parseInt(yyRaw, 10) : parseInt(yyRaw, 10);
    const monthMap = { jan: 0, feb: 1, mar: 2, apr: 3, may: 4, jun: 5, jul: 6, aug: 7, sep: 8, oct: 9, nov: 10, dec: 11 };
    const month = monthMap[monStr];
    if (month !== undefined) return new Date(year, month, dd);
  }

  return null;
}

async function loadSymbolMaster(kind) {
  ensureCacheDir();
  const url = kind === 'NFO' ? NFO_URL : NSE_URL;
  const zipPath = path.join(CACHE_DIR, `${kind}_symbols.zip`);

  // Refresh daily
  const refreshMs = 12 * 60 * 60 * 1000;
  const shouldDownload = !fs.existsSync(zipPath) || (Date.now() - fs.statSync(zipPath).mtimeMs) > refreshMs;
  if (shouldDownload) await downloadZip(url, zipPath);

  const csvText = unzipFirstFile(zipPath);
  return parseCsv(csvText);
}

async function getNiftyIndexToken(api) {
  const norm = (s) => String(s || '').trim().replace(/\s+/g, ' ').toUpperCase();

  const pickFromSearch = (values) => {
    const exact50 = values.find((v) => norm(v.tsym) === 'NIFTY 50' || norm(v.tsym) === 'NIFTY50');
    if (exact50) return { token: String(exact50.token), tsym: String(exact50.tsym || '') };

    const exactNifty = values.find((v) => norm(v.tsym) === 'NIFTY');
    if (exactNifty) return { token: String(exactNifty.token), tsym: String(exactNifty.tsym || '') };

    return null;
  };

  // Prefer searchscrip exact match to avoid picking unrelated indices.
  const reply = await api.searchscrip('NSE', 'NIFTY 50');
  if (reply && reply.stat === 'Ok' && Array.isArray(reply.values) && reply.values.length) {
    const picked = pickFromSearch(reply.values);
    if (picked) return picked;
  }

  const reply2 = await api.searchscrip('NSE', 'NIFTY');
  if (reply2 && reply2.stat === 'Ok' && Array.isArray(reply2.values) && reply2.values.length) {
    const picked = pickFromSearch(reply2.values);
    if (picked) return picked;
  }

  // Fallback to symbol master (best-effort). Kept last to avoid wrong picks.
  const rows = await loadSymbolMaster('NSE');
  const isIndex = (r) => norm(r.Instrument || r.instname).includes('INDEX');
  const tsymU = (r) => norm(r.tsym || r.Tsym || r.TradingSymbol);
  const symU = (r) => norm(r.Symbol || r.symname || r.Sym);
  const tokenOf = (r) => String(r.Token || r.token || '').trim();

  const preferred = rows.find((r) => isIndex(r) && (tsymU(r) === 'NIFTY 50' || tsymU(r) === 'NIFTY50'))
    || rows.find((r) => isIndex(r) && tsymU(r) === 'NIFTY')
    || rows.find((r) => isIndex(r) && symU(r) === 'NIFTY 50');

  if (preferred && tokenOf(preferred)) {
    return { token: tokenOf(preferred), tsym: String(preferred.tsym || preferred.Tsym || preferred.TradingSymbol || '') };
  }

  const hints = [];
  if (reply2 && Array.isArray(reply2.values)) {
    for (const v of reply2.values.slice(0, 5)) {
      hints.push(`${String(v.tsym || '')}:${String(v.token || '')}`);
    }
  }
  throw new Error(`Unable to resolve NIFTY index token. searchscrip hints: ${hints.join(', ')}`);
}

async function loadNiftyOptionUniverse(expiryIso) {
  const rows = await loadSymbolMaster('NFO');
  const niftyRows = rows.filter((r) => {
    const sym = (r.Symbol || r.symname || r.Sym || '').toUpperCase();
    const inst = (r.Instrument || r.instname || '').toUpperCase();
    return sym === 'NIFTY' && (inst.includes('OPT') || inst.includes('OPTIDX'));
  });

  const enriched = niftyRows.map((r) => {
    const expiryRaw = r.Expiry || r.expiry || r.ExpDate || r.expdate;
    const expiryDate = parseExpiryToDate(expiryRaw);
    const strikeRaw = r.StrikePrice || r.strike || r.Strk || r.strprc;
    const strike = strikeRaw ? Number(strikeRaw) : null;
    const optType = (r.OptionType || r.opttype || r.OptType || '').toUpperCase();
    const token = String(r.Token || r.token || '');
    const tsym = String(r.TradingSymbol || r.tsym || '');

    return { raw: r, expiryRaw, expiryDate, strike, optType, token, tsym };
  }).filter((x) => x.token && x.expiryDate && x.strike && (x.optType === 'CE' || x.optType === 'PE'));

  const targetExpiry = expiryIso ? new Date(expiryIso) : null;

  let expiryDate;
  if (targetExpiry && !Number.isNaN(targetExpiry.getTime())) {
    expiryDate = targetExpiry;
  } else {
    const today = new Date();
    today.setHours(0, 0, 0, 0);
    const futureExpiries = [...new Set(enriched.map((x) => x.expiryDate.getTime()))]
      .map((t) => new Date(t))
      .filter((d) => d >= today)
      .sort((a, b) => a - b);

    if (!futureExpiries.length) throw new Error('No future NIFTY expiries found in symbol master');
    expiryDate = futureExpiries[0];
  }

  const expiryKey = expiryDate.toDateString();
  const filtered = enriched.filter((x) => x.expiryDate.toDateString() === expiryKey);

  return { expiryDate, universe: filtered };
}

module.exports = {
  loadSymbolMaster,
  getNiftyIndexToken,
  loadNiftyOptionUniverse,
};
