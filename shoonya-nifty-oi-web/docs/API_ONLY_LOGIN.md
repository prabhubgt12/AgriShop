# Shoonya login (automated only)

Login supports **two ways** in the dashboard:

1. **Auto login (TOTP)** — same as Python `GetAuthcode` (Playwright + `.env` credentials).
2. **Login with code** — paste the auth code from `python GetAuthcode` or the redirect URL (no browser automation).

## One-time setup

```bash
cd shoonya-nifty-oi-web
npm install
npx playwright install chromium
```

## `.env` (required)

```env
SHOONYA_USERID=
SHOONYA_CLIENT_ID=
SHOONYA_SECRET_CODE=
SHOONYA_PASSWORD=
SHOONYA_TOTP_SECRET=
```

Optional:

```env
SHOONYA_AUTO_LOGIN_URL=https://trade.shoonya.com/OAuthlogin/investor-entry-level/login
SHOONYA_AUTO_LOGIN_HEADLESS=true
```

Whitelist your **public IP** in Shoonya API Key settings.

## Use

1. `npm start`
2. Open http://localhost:3000
3. Either:
   - **Auto login (TOTP)** — automated GetAuthcode flow, or
   - **Login with code** — paste auth code from Python `GetAuthcode`
4. Click **Start**

Session is saved to `.data/oauthSession.json`.

## Clear session (to test login again)

**Dashboard:** click **Logout / clear session** (deletes the file and logs out in memory).

**Manual:**

```powershell
cd shoonya-nifty-oi-web
Remove-Item .data\oauthSession.json -ErrorAction SilentlyContinue
```

Then restart `npm start` if the server was already running.

If `.env` has `SHOONYA_ACCESS_TOKEN`, remove or comment it out before restart — otherwise the app will log in again from env on startup.

## API-only accounts

If you see “Access Restricted for API Only Users” on `trade.shoonya.com` in a normal browser, the automated login still uses the **GetAuthcode** OAuth URL (`api_key` + `route_to`) and usually works.

## Troubleshooting

| Issue | Fix |
|-------|-----|
| Login button disabled | Set all five `.env` fields above |
| Playwright error | `npx playwright install chromium` |
| Timeout / no auth code | Check TOTP secret, password, Client ID, IP whitelist |
| Token errors | Run Login again (codes expire quickly) |

See also [OAUTH_REST_API_PDF.md](./OAUTH_REST_API_PDF.md) for GenAcsTok / checksum details.
