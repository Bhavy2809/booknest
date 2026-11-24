@echo off
rem Starts the React app on http://localhost:5173   (start the backend first)
cd /d "%~dp0frontend"

where node >nul 2>nul
if errorlevel 1 (
  echo Node.js was not found. Install it from https://nodejs.org then run this file again.
    pause
    exit /b 1
)

if not exist node_modules (
  echo Installing packages, this happens only the first time...
  call npm install
)

call npm run dev
pause
