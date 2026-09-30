@echo off
rem Roblox Images Fix (RF) - set Android Private DNS via ADB
rem Usage: set-private-dns.bat quad9|opendns|adguard|off|status|<hostname>

setlocal
where adb >nul 2>&1
if %errorlevel% neq 0 (
    echo [ERROR] adb not found. Install Android platform-tools:
    echo https://developer.android.com/tools/releases/platform-tools
    exit /b 1
)

set ARG=%~1
if "%ARG%"=="" goto :usage

if /i "%ARG%"=="status" goto :status
if /i "%ARG%"=="off"    goto :off

set "SPEC="
if /i "%ARG%"=="quad9"   set "SPEC=dns.quad9.net"
if /i "%ARG%"=="opendns" set "SPEC=dns.opendns.com"
if /i "%ARG%"=="adguard" set "SPEC=dns.adguard-dns.com"
if "%SPEC%"=="" set "SPEC=%ARG%"

adb shell settings put global private_dns_mode hostname
adb shell settings put global private_dns_specifier %SPEC%
echo [OK] Private DNS = %SPEC%
echo Now FULLY close Roblox (swipe away) and reopen it.
goto :eof

:off
adb shell settings put global private_dns_mode off
echo [OK] Private DNS disabled.
goto :eof

:status
echo mode:
adb shell settings get global private_dns_mode
echo hostname:
adb shell settings get global private_dns_specifier
goto :eof

:usage
echo Usage: %~nx0 quad9^|opendns^|adguard^|off^|status^|<hostname>
echo Example: %~nx0 opendns
exit /b 1
