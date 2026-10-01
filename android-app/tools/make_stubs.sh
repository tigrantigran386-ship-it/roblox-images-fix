#!/usr/bin/env bash
# Генерирует API-стабы для локальной проверки компиляции (javac) без Android SDK.
# Использование: ./make_stubs.sh /tmp/rbxstubs
DEST="${1:-/tmp/rbxstubs}"
mkdir -p "$DEST"
cp -r "$(dirname "$0")/stubs/"* "$DEST"/
echo "стабы в $DEST"
