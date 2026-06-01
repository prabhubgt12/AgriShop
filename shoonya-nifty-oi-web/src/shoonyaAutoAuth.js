/**
 * Headless OAuth login — mirrors Shoonya_oAuthAPI-py GetAuthcode (Selenium + TOTP).
 * Requires: playwright, otplib, Chrome/Chromium via `npx playwright install chromium`
 */

function getAuthenticator() {
  try {
    const { authenticator } = require('otplib');
    return authenticator;
  } catch (_) {
    throw new Error('otplib is not installed. Run: npm install');
  }
}

function buildTradeLoginUrl(clientId, userId) {
  const base =
    process.env.SHOONYA_AUTO_LOGIN_URL ||
    'https://trade.shoonya.com/OAuthlogin/investor-entry-level/login';
  const sep = base.includes('?') ? '&' : '?';
  return `${base}${sep}api_key=${encodeURIComponent(clientId)}&route_to=${encodeURIComponent(userId)}`;
}

function extractCodeFromUrl(url) {
  if (!url || !url.includes('code=')) return null;
  try {
    const parsed = new URL(url);
    const code = parsed.searchParams.get('code');
    return code && code.trim() ? code.trim() : null;
  } catch (_) {
    const m = url.match(/[?&]code=([^&]+)/);
    return m ? decodeURIComponent(m[1]) : null;
  }
}

function watchForAuthCode(onCode) {
  return (url) => {
    const code = extractCodeFromUrl(url);
    if (code) onCode(code);
  };
}

async function fetchAuthCodeViaBrowser({ clientId, userId, password, totpSecret }) {
  const authenticator = getAuthenticator();
  let playwright;
  try {
    playwright = require('playwright');
  } catch (_) {
    throw new Error(
      'Playwright is not installed. Run: npm install && npx playwright install chromium'
    );
  }

  const loginUrl = buildTradeLoginUrl(clientId, userId);
  let authCode = null;
  const capture = (code) => {
    authCode = code;
  };
  const onUrl = watchForAuthCode(capture);

  const browser = await playwright.chromium.launch({
    headless: process.env.SHOONYA_AUTO_LOGIN_HEADLESS !== 'false',
  });

  try {
    const page = await browser.newPage();
    page.on('request', (req) => onUrl(req.url()));
    page.on('response', (res) => onUrl(res.url()));

    await page.goto(loginUrl, { waitUntil: 'domcontentloaded', timeout: 90000 });
    await page.waitForSelector('input[type="password"]', { timeout: 45000 });
    await page.waitForTimeout(800);

    const visible = page.locator(
      'input:not([type="hidden"]):not([type="checkbox"]):not([type="radio"]):visible'
    );
    await visible.nth(0).click();
    await visible.nth(0).fill(userId);
    await visible.nth(1).fill(password);

    let otpValue = authenticator.generate(totpSecret.replace(/\s/g, ''));
    await visible.nth(2).fill(otpValue);

    const loginBtn = page.getByRole('button', { name: /^LOGIN$/i });
    await loginBtn.click();

    const deadline = Date.now() + 90000;
    let lastOtpRetry = Date.now();

    while (!authCode && Date.now() < deadline) {
      await page.waitForTimeout(500);
      onUrl(page.url());

      if (Date.now() - lastOtpRetry > 55000) {
        const newOtp = authenticator.generate(totpSecret.replace(/\s/g, ''));
        if (newOtp !== otpValue) {
          await visible.nth(2).fill(newOtp);
          await loginBtn.click();
          otpValue = newOtp;
          lastOtpRetry = Date.now();
        }
      }
    }

    if (!authCode) {
      throw new Error(
        'Timed out waiting for OAuth auth code. Check TOTP secret, password, Client ID, and IP whitelist.'
      );
    }

    return authCode;
  } finally {
    await browser.close();
  }
}

function isAutoLoginConfigured() {
  return Boolean(
    process.env.SHOONYA_USERID &&
      process.env.SHOONYA_CLIENT_ID &&
      process.env.SHOONYA_PASSWORD &&
      process.env.SHOONYA_TOTP_SECRET
  );
}

module.exports = {
  fetchAuthCodeViaBrowser,
  isAutoLoginConfigured,
  buildTradeLoginUrl,
};
