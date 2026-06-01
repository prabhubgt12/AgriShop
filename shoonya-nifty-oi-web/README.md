# Shoonya NIFTY OI Web (Local)



NIFTY OI dashboard with Shoonya OAuth (NorenWClientAPI) and automated login.



## Setup



1. SDK: `./shoonya-sdk` (NorenApi-js style).

2. Copy `.env.example` → `.env` and set all login fields (see below).

3. `npm install` and `npx playwright install chromium`



## Run



```bash

npm start

```



Open http://localhost:3000



## Login



- **Auto login (TOTP)** — GetAuthcode-style automation (Playwright + `.env`).
- **Login with code** — paste auth code from Python `GetAuthcode` (what you used successfully).



Required in `.env`:



- `SHOONYA_USERID`, `SHOONYA_CLIENT_ID`, `SHOONYA_SECRET_CODE`

- `SHOONYA_PASSWORD`, `SHOONYA_TOTP_SECRET`



Details: [docs/API_ONLY_LOGIN.md](docs/API_ONLY_LOGIN.md)



Session is saved to `.data/oauthSession.json`. Then click **Start**.


