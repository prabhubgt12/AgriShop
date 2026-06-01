# oAuth REST API.pdf — summary (Shoonya)

Source: `shoonya-nifty-oi-web/oAuth REST API .pdf`

## Authentication flow (pages 7–9)

1. **Redirect** the user to the **login page** (URL is not in this PDF; use trade OAuth login or Shoonya portal — see [API_ONLY_LOGIN.md](./API_ONLY_LOGIN.md)).
2. After successful login, read **`code`** from the **redirect URL**.
3. **POST** to **`/NorenWClientAPI/GenAcsTok`** with:
   - `code` — from redirect URL
   - `checksum` — **SHA-256** of `Client_id + secret_code + code` **with no spaces**  
     Example: client `ABC`, secret `123`, code `x1y2z3` → hash `ABC123x1y2z3`
4. Response includes **`access_token`** (and `refresh_token`, `expires_in`, etc.).
5. Send **`Authorization: Bearer <access_token>`** on every API call.

Live host (your app): `https://api.shoonya.com/NorenWClientAPI/GenAcsTok`  
PDF test example: `http://apitest.kambala.co.in/NorenWClientAPI/GenAcsTok`

## GenAcsTok success fields (relevant)

| Field | Use |
|-------|-----|
| `access_token` | Bearer token for REST + WebSocket |
| `refresh_token` | Store for renewal (if Shoonya supports refresh flow) |
| `expires_in` | Token expiry epoch |
| `USERID` / `uid` | User id |
| `actid` | Account id |

Failure example: `"emsg": "Invalid Input : INVALID_VERIFIER"` — wrong code, checksum, or expired code.

## What this PDF does **not** include

- The exact **OAuth login page URL** (no `OAuthlogin` / `trade.shoonya.com` link in extracted text).
- **Annexure** (end of PDF) = order report/status enums only, not OAuth URLs.

For login URL, use what works in practice:

- Python **GetAuthcode**: `https://trade.shoonya.com/OAuthlogin/investor-entry-level/login?api_key={CLIENT_ID}&route_to={USER_ID}`
- Dashboard **Auto login (TOTP)** — same URL, automated via Playwright

## How this maps to our Node app

| PDF step | Our code |
|----------|----------|
| Get `code` | `POST /api/login` (Playwright + TOTP, automated) |
| Checksum | `shoonya-sdk/lib/RestApi.js` → `SHA256(client_id + secret + code)` |
| GenAcsTok | `getAccessToken()` → `POST .../GenAcsTok` |
| Bearer header | `injectOAuthHeader()` on all API calls |
| Session file | `.data/oauthSession.json` |

## Other APIs in PDF

Same as standard Shoonya/Noren REST: SearchScrip, GetQuotes, PlaceOrder, OrderBook, TradeBook, PositionBook, Holdings, Limits, etc. — already wrapped in `shoonya-sdk/lib/RestApi.js` where implemented.
