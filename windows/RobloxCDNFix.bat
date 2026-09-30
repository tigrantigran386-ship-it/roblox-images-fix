@echo off
rem Roblox Images Fix (RF) - launcher
rem Self-elevates to Administrator and opens the interactive menu.
cd /d "%~dp0"
net session >nul 2>&1
if %errorlevel% neq 0 (
    echo Requesting administrator rights...
    powershell -NoProfile -Command "Start-Process -FilePath '%~f0' -Verb RunAs"
    exit /b
)
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0RobloxCDNFix.ps1" %*
if not "%1"=="" (
    echo.
    pause
)
