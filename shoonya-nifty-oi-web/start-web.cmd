@echo off
setlocal

echo Starting Shoonya NIFTY OI Web...
echo.
echo If this is first run, it will install dependencies.
echo.

npm install
if errorlevel 1 (
  echo npm install failed
  exit /b 1
)

npm start
