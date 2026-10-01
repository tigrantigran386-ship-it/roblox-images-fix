#!/usr/bin/env bash
# Roblox Images Fix (RF) — set Android Private DNS via ADB
# Usage: ./set-private-dns.sh quad9|opendns|adguard|off|status|<hostname>
set -euo pipefail

if ! command -v adb >/dev/null 2>&1; then
  echo "[ERROR] adb not found. Install Android platform-tools first." >&2
  exit 1
fi

ARG="${1:-}"
[ -z "$ARG" ] && { echo "Usage: $0 quad9|opendns|adguard|off|status|<hostname>"; exit 1; }

SPEC=""
case "$(echo "$ARG" | tr '[:upper:]' '[:lower:]')" in
  status)
    echo "mode:     $(adb shell settings get global private_dns_mode)"
    echo "hostname: $(adb shell settings get global private_dns_specifier)"
    exit 0
    ;;
  off)
    adb shell settings put global private_dns_mode off
    echo "[OK] Private DNS disabled."
    exit 0
    ;;
  quad9)   SPEC="dns.quad9.net" ;;
  opendns) SPEC="dns.opendns.com" ;;
  adguard) SPEC="dns.adguard-dns.com" ;;
  *)       SPEC="$ARG" ;;
esac

adb shell settings put global private_dns_mode hostname
adb shell settings put global private_dns_specifier "$SPEC"
echo "[OK] Private DNS = $SPEC"
echo "Now FULLY close Roblox (swipe away) and reopen it."
