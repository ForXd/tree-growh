#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
. scripts/env.sh
: "${ANDROID_HOME:?Set ANDROID_HOME}"
: "${QINGYA_KEYSTORE:=$HOME/.local/share/qingya-signing/release.jks}"
: "${QINGYA_PASSWORD_FILE:=$HOME/.local/share/qingya-signing/password}"
: "${QINGYA_KEY_ALIAS:=qingya}"
[ -f "$QINGYA_KEYSTORE" ] && [ -f "$QINGYA_PASSWORD_FILE" ] || { echo 'Release signing material is missing; see docs/releasing.md.'; exit 1; }
mkdir -p artifacts
if [ "$#" -eq 2 ]; then
    ./gradlew testDebugUnitTest lintRelease assembleRelease "-PreleaseVersion=$1" "-PreleaseCode=$2"
elif [ "$#" -eq 0 ]; then
    ./gradlew testDebugUnitTest lintRelease assembleRelease
else
    echo 'Usage: scripts/release.sh [fixture-version fixture-code]'; exit 1
fi
BUILD_TOOLS="$ANDROID_HOME/build-tools/37.0.0"
UNSIGNED='app/build/outputs/apk/release/app-release-unsigned.apk'
"$BUILD_TOOLS/aapt" dump badging "$UNSIGNED" > artifacts/apk-badging.txt
VERSION=$(python3 -c 'import re; print(re.search(r"versionName=\x27([^\x27]+)",open("artifacts/apk-badging.txt").read()).group(1))')
CODE=$(python3 -c 'import re; print(re.search(r"versionCode=\x27([^\x27]+)",open("artifacts/apk-badging.txt").read()).group(1))')
APK="artifacts/qingya-$VERSION.apk"
"$BUILD_TOOLS/zipalign" -f -p 4 "$UNSIGNED" artifacts/aligned.apk
"$BUILD_TOOLS/apksigner" sign --ks "$QINGYA_KEYSTORE" --ks-key-alias "$QINGYA_KEY_ALIAS" --ks-pass "file:$QINGYA_PASSWORD_FILE" --out "$APK" artifacts/aligned.apk
"$BUILD_TOOLS/apksigner" verify --verbose --print-certs "$APK" > "artifacts/signature-$VERSION.txt"
python3 scripts/release-manifest.py "$VERSION" "$CODE" "$APK"
rm artifacts/aligned.apk
printf 'Signed %s (versionCode %s)\n' "$APK" "$CODE"
