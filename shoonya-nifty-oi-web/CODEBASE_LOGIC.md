# Complete Codebase Logic Documentation

## Overview
The Shoonya NIFTY OI web application is a Node.js-based trading app for NIFTY options on the NFO exchange. It supports PAPER and LIVE trading modes, with automated strategies and manual controls. Built with Express.js, it fetches market data, computes signals, and places orders via Shoonya API.

Key components:
- `server.js`: Main server with APIs, state management, polling.
- `src/paperTradeEngine.js`: PAPER trading logic, strategies, backtesting.
- `src/liveTradeEngine.js`: LIVE trading logic, order placement, monitoring.
- `src/optionChain.js`: Market data fetching and snapshot building.
- `src/shoonyaClient.js`: Shoonya API wrapper.
- `public/`: Frontend HTML/CSS/JS.

## Server.js Logic
Main entry point. Sets up Express app, state, and APIs.

### State Management
Global `state` object:
- `loggedIn`, `client`: Shoonya auth.
- `lastSnapshot`, `snapshotHistory`: Market data.
- `paper`: PAPER trade config and state.
- `live`: LIVE trade state.
- `polling`: Background update loop.

### Initialization
- Loads env vars for Shoonya creds, ENABLE_LIVE_TRADING.
- Creates Shoonya client.
- Loads persisted live state if exists.

### APIs
- `GET /api/health`: Status (loggedIn, polling, lastError).
- `POST /api/login`: OTP login, starts polling.
- `POST /api/start`: Starts update loop.
- `POST /api/stop`: Stops loop.
- `GET /api/paper`: Returns paper state.
- `POST /api/paper/risk-config`: Sets maxTradesPerDay, tradeMode (PAPER/LIVE).
- `POST /api/paper/order-config`: Sets qtyMode, lots, qtyPerLot, orderQty, productType.
- `POST /api/paper/direction`: Sets directionOverride (AUTO/BULL/BEAR).
- `POST /api/paper/arm-live`: Sets liveArmed (requires tradeMode=LIVE).
- `POST /api/paper/mode`: Sets selectedMode (AUTO/NORMAL/EXPIRY/BIG_RALLY).
- `POST /api/paper/exit-config`: Sets exitStyle (TRAILING/TARGET), targetPct.
- `POST /api/paper/force-enter`: Places forced entry order (LIVE only if armed).
- `POST /api/paper/force-exit`: Places forced exit order (LIVE only if open trade).
- `GET /api/debug/search`: Searches scrips via Shoonya.
- `GET /api/snapshot`: Returns latest snapshot, paper, live states.
- `POST /api/live/resync`: Reconstructs live state from Shoonya orders/trades.

### Polling Loop
`startPolling()` runs `updateOnce()` every pollSeconds (default 1s).
- Fetches snapshot via `buildNiftyChainSnapshot()`.
- Steps PAPER trade: `stepPaperTrade()`.
- If LIVE enabled and armed: Computes entry decision, steps LIVE trade: `stepLiveTrade()`.
- Persists live state if open/exiting trade.

### Persistence
- `persistLiveState()`: Saves live.current to JSON file.
- `tryRestoreLiveState()`: Loads from file, validates.
- Resync: Queries orderbook/tradebook to reconstruct state without file.

### Helpers
- `isMarketOpen()`: Checks NFO hours (9:15-15:30 IST).
- Error handling: Catches and logs errors.

## Paper Trade Engine (paperTradeEngine.js)
Simulates trading without real orders.

### State Structure
- enabled, tradeMode, liveArmed, productType, qtyMode, lots, qtyPerLot, orderQty.
- directionOverride, selectedMode, effectiveMode, exitStyle, targetPct, maxTradesPerDay.
- tradesDate, tradesToday, dayOpenPrice, currentTrade, tradeHistory, lastDecision.

### Direction Computation
`computeDirectionSignal()`: Based on LTP vs ATM, override.
- LTP >= ATM: BULL.
- LTP < ATM: BEAR.

### Instrument Selection
`selectInstrument()`: Chooses strike/type based on mode/direction.
- NORMAL/BIG_RALLY: 2-strike OTM CE/PE.
- EXPIRY: ATM CE/PE.

### Entry Decision
`shouldEnter()`: Checks if entry conditions met.
- Fetches 5/10-min past snapshot.
- Computes price change %.
- NORMAL: >=25% in 5min.
- EXPIRY: >=50% in 5min.
- BIG_RALLY: >=100% in 10min, plus rally conditions.

### Trade Step
`stepPaperTrade()`: Updates state.
- Resets daily counters.
- Computes effective mode (AUTO uses computeAutoMode).
- If open trade: Updates trailing/peak, checks exits.
- Else: Checks entry, opens trade if conditions met.

### Exits
`maybeExit()`: Checks SL hit, target, trail from peak.
`updateTrailing()`: Adjusts SL based on profit mult.
`updatePeakOnly()`: Tracks peak.

### Force Actions
`forceEnterPaperTrade()`: Bypasses checks, opens trade.
`forceExitPaperTrade()`: Closes trade with reason.

## Live Trade Engine (liveTradeEngine.js)
Handles real trading.

### State Structure
- enabled, current (trade object), history, lastDecision.

### Trade Object
- id, status (OPEN/EXITING/CLOSED), mode, strike, optType, tsym, exchange, qty.
- entryTs, entryOrderNo, entryPrice, peakPrice, slPrice.
- exitTs, exitPrice, exitReason, exitOrderNo, pnl.

### Step Function
`stepLiveTrade()`: Manages live trades.
- If open trade: Monitors LTP, checks exits.
- If entry decision: Places entry order, confirms fill, sets OPEN.
- Uses cooldown for failed entries.

### Entry
`placeLiveEntry()`: Places buy order, waits 2s, checks tradebook.
- If filled: Sets entryPrice from avgprc, sets trade OPEN.
- If not: Sets lastEntryAttemptTs, status ERROR, no trade.

### Exit
`placeLiveExit()`: Places sell order, waits 2s, checks tradebook.
- If filled: Sets exitPrice/pnl from avgprc, sets CLOSED.
- If not: Error, keeps OPEN.

### Monitoring
Updates peak/SL, places exit orders when conditions met.

## Option Chain (optionChain.js)
Fetches and builds NIFTY option chain snapshot.

### Snapshot Structure
- id, ts, expiry, underlying, atmStrike, levels, itmOiStats, rows (strikes with ce/pe data).

### Building
`buildNiftyChainSnapshot()`: Calls Shoonya searchscrip for strikes, fetches LTP/OI/vol.
- Computes atmStrike: Nearest strike to LTP.
- Builds rows: CE/PE data per strike.
- Computes OI stats, levels (resistance/support).

## Shoonya Client (shoonyaClient.js)
Wrapper for Shoonya API.

### Methods
- login(params): Authenticates.
- searchscrip(exch, text): Searches symbols.
- get_orderbook(): Fetches orders.
- get_tradebook(): Fetches trades.
- place_order(orderObj): Places order (snake_case fields).

## Frontend (public/)
Simple HTML/JS/CSS UI.
- Displays snapshot, paper/live states.
- Buttons for actions (start/stop, force enter/exit, resync).
- Updates via /api/snapshot polling.
- Disabled states for buttons based on conditions.

## Resync Logic
`tryResyncFromShoonya()`: Reconstructs live state from Shoonya data.
- Filters app orders (forced_/auto_), COMPLETE, B.
- Finds entry order, checks for exit order (exit_*).
- If exit found: Sets CLOSED with pnl.
- If not, checks manual sell orders: Sets MANUAL_EXIT if sell on same tsym.
- Restores paper config from order details.

## Error Handling and Safety
- Market open checks before orders.
- Tradebook confirmations for fills.
- Cooldowns prevent spam.
- Live mode gated by env var.
- Fallbacks for missing data.
