#!/usr/bin/env bash
# Собирает release APK, переименовывает в grabbit-<abi>-release.apk и ставит на устройство.
# Требуется: подключённое через adb устройство (192.168.240.112:5555).
set -euo pipefail

DEVICE="${DEVICE:-192.168.240.112:5555}"
ABI="${ABI:-x86_64}"
APK="app/build/outputs/apk/release/app-${ABI}-release.apk"
RENAMED="app/build/outputs/apk/release/grabbit-${ABI}-release.apk"

echo "==> Сборка release ($ABI)"
./gradlew :app:assembleRelease --console=plain

if [[ ! -f "$APK" ]]; then
    echo "ERROR: APK не найден: $APK" >&2
    exit 1
fi

echo "==> Переименование: $APK -> $RENAMED"
mv -f "$APK" "$RENAMED"

echo "==> Установка на $DEVICE"
adb connect "$DEVICE" >/dev/null 2>&1 || true
adb -s "$DEVICE" install -r -d "$RENAMED"

echo "==> Готово: $RENAMED"
