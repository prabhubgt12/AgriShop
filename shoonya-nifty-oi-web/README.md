# Shoonya NIFTY OI Web (Local)

Uses OAuth via [NorenApi-js](https://github.com/kambalatech/NorenApi-js)-style SDK with **Shoonya** endpoints (`OAuthlogin`, `NorenWClientAPI`) instead of legacy QuickAuth (`NorenWClientTP`).

## Setup

1. SDK is vendored in `./shoonya-sdk` (from NorenApi-js). To refresh: download `lib/RestApi.js`, `lib/WebSocket.js`, `lib/config.js` from the repo.

2. Copy `.env.example` to `.env` and set:
   - `SHOONYA_USERID` — your Shoonya user id
   - `SHOONYA_CLIENT_ID` — from Shoonya web → API → API Key → Generate
   - `SHOONYA_SECRET_CODE` — secret shown when you generate the API key
   - Optional: `SHOONYA_ACCESS_TOKEN` — if you already exchanged an auth code

## Run

- `npm install` (in this folder and in `shoonya-sdk` if needed)
- `npm start` or `start-web.cmd`

Open http://localhost:3000

## Login (OAuth)

**API-only accounts:** do not use `trade.shoonya.com` (shows “Access Restricted for API Only Users”). Get an auth code with Python **`GetAuthcode`** from Shoonya’s `Shoonya_oAuthAPI-py` sample, then paste it in the dashboard → **Login**. See [docs/API_ONLY_LOGIN.md](docs/API_ONLY_LOGIN.md).

1. Set `SHOONYA_CLIENT_ID` and `SHOONYA_SECRET_CODE` from the Shoonya API Key page.
2. Run `GetAuthcode` (or manual OAuth authorize from API docs) → copy the auth code.
3. Paste in dashboard → **Login** (token saved to `.data/oauthSession.json`).

Then click **Start**.
