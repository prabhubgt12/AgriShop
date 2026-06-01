# Shoonya API-only account login

If you see **"Access Restricted for API Only Users"** on `trade.shoonya.com`, your account is **API-only**. You must **not** use Option B (trade portal login).

## What to do

### 1. Whitelist your IP (required since Apr 2026)

1. Log in where API settings are allowed (often **https://api.shoonya.com/OAuthlogin** or Shoonya app).
2. Open **API** / **API Key** settings.
3. Add your **public IP** (the IP your Node server uses on the internet).
4. Save / Update.

Without whitelisting, token exchange or API calls may fail even with a valid `code`.

### 2. Get the OAuth `code` (recommended: Python `GetAuthcode`)

**Works for API-only accounts** (bypasses trade portal “Access Restricted” error).

1. Use Shoonya’s sample repo: `Shoonya_oAuthAPI-py-main`.
2. Fill `cred.yml` with `client_id`, `Secret_Code`, `UID`, etc. (match your `.env`).
3. Edit `GetAuthcode` config: `CLIENT_ID`, `USER_ID`, `PASSWORD`, `TOTP_SECRET`, `SECRET_CODE`.
4. Install deps: `pip install -r requirements.txt` (includes `NorenRestApiOAuth`, `selenium`, `pyotp`).
5. Run: `python GetAuthcode` — it prints **Auth Code: …**
6. Paste that code in the NIFTY OI dashboard → **Login**.

The Node app exchanges the code via `GenAcsTok` and saves the token in `.data/oauthSession.json` (usually no need to run `GetAuthcode` again until the token expires).

**Alternative (manual browser):** use the OAuth authorize URL from [Shoonya API documentation](https://shoonya.com/api-documentation) → Authentication appendix, then copy `code=` from the redirect URL.

Optional in `.env`:

```env
SHOONYA_OAUTH_AUTHORIZE_URL=<paste full URL from Shoonya docs>
```

### 3. Configure `.env` (this project)

```env
SHOONYA_OAUTH_USE_TRADE_LOGIN=false
SHOONYA_OAUTH_URL=https://api.shoonya.com/OAuthlogin
SHOONYA_OAUTH_APPEND_CLIENT_ID=true
SHOONYA_USERID=FA41572
SHOONYA_CLIENT_ID=<from API Key page>
SHOONYA_SECRET_CODE=<from API Key page>
```

Restart: `npm start`

### 4. Dashboard

1. **Open API login** → should open `api.shoonya.com`, not `trade.shoonya.com`.
2. Complete authorize flow; paste `code` → **Login**.

## Still stuck?

- Email **apisupport@shoonya.com** — confirm API is enabled for your client code.
- Prism chat: https://prism.shoonya.com/api (per Shoonya support).
