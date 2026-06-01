# Trading Strategies and Logic

## Overview
This document outlines the trading strategies and logic implemented in the Shoonya NIFTY OI web application. The app supports PAPER and LIVE trading modes, with strategies for NIFTY options on the NFO exchange.

Strategies: NORMAL, EXPIRY, BIG_RALLY.

## Direction Computation
Direction (BULL/BEAR) is computed based on underlying LTP vs ATM and direction override.
- If LTP >= ATM, direction = BULL (bullish bias).
- If LTP < ATM, direction = BEAR (bearish bias).
- Can be overridden via UI to AUTO/BULL/BEAR.

## Instrument Selection
Instruments are selected based on mode, direction, and strike step (typically 50).

### NORMAL
- Picks 2-strike OTM option based on direction.
- BULL: CE at ATM + 2*step (e.g., 25550 CE if ATM=25450, step=50).
- BEAR: PE at ATM - 2*step (e.g., 25350 PE).

### BIG_RALLY
- Same as NORMAL: 2-strike OTM CE/PE based on direction.

### EXPIRY
- Picks ATM CE/PE based on direction.

## Entry Conditions
Entry checks use 5-min price change for NORMAL/EXPIRY, 10-min for BIG_RALLY.

### NORMAL
- Requires 25% price increase in 5 min for the selected option.

### EXPIRY
- Requires 50% price increase in 5 min for the selected option.

### BIG_RALLY
- Requires 100% price increase in 10 min for the selected option.
- Additional conditions: move >1% from open, or 2-step OTM doubled in 10 min.

## Exit Logic
Exits use trailing SL or target profit.

### Trailing Stop-Loss (TRAILING)
- Initial SL: 70% for NORMAL/EXPIRY, 60% for BIG_RALLY.
- Adjusts based on profit multiplier:
  - >=1.3x: SL = entry price.
  - >=1.6x: SL = 80% of peak.
  - BIG_RALLY: >=2x entry, >=3x peak*0.5, >=5x peak*0.6, >=10x peak*0.7.

### Target Profit (TARGET)
- Exit at targetPct% profit (e.g., 30%).

### Additional Exits
- SL hit: current LTP <= SL.
- Trail from peak: After >=60% profit, exit if LTP <= 80% of peak (NORMAL/EXPIRY).

## LIVE Trading
- Mirrors PAPER logic.
- Adds order placement via Shoonya API.
- Tradebook confirmation after entry/exit orders.
- Cooldown for failed entries.
- Market open checks (9:15-15:30 IST).

## Resync Logic
- Queries Shoonya orderbook/tradebook to reconstruct live trades.
- Looks for app-placed orders (remarks 'forced_'/'auto_').
- Sets status OPEN/CLOSED based on entry/exit orders.
- Detects manual exits via sell orders on same tsym.

## Force Entry/Exit
- Manual entry/exit via UI.
- Bypasses strategy checks.
- For LIVE: places market orders, confirms fill via tradebook.

## Persistence and Limits
- Max 3 trades/day.
- Persists live trades to file.
- Resets daily counters.
