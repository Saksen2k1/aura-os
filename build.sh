#!/usr/bin/env bash
# Builds AURA OS with the plain Android SDK tools (no Gradle). Linux, macOS and CI.
#
#   ./build.sh             debug APK, signed with a local debug key (created on first run)
#   ./build.sh --release   release APK, signed with the key from the environment (see docs/BUILD.md)
#
# Needs JDK 17 and an Android SDK with build-tools 34.0.0 and platforms;android-34 (ANDROID_HOME).
set -euo pipefail
cd "$(dirname "$0")"

BUILD_TOOLS=34.0.0
PLATFORM=android-34
MIN_SDK=26
TARGET_SDK=30
RELEASE=0
[ "${1:-}" = "--release" ] && RELEASE=1

SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
[ -n "$SDK" ] && [ -d "$SDK/build-tools/$BUILD_TOOLS" ] || { echo "Android SDK with build-tools $BUILD_TOOLS not found; set ANDROID_HOME" >&2; exit 1; }
BT="$SDK/build-tools/$BUILD_TOOLS"
JAR="$SDK/platforms/$PLATFORM/android.jar"
[ -f "$JAR" ] || { echo "Missing $PLATFORM; run: sdkmanager \"platforms;$PLATFORM\"" >&2; exit 1; }
JAVAC="${JAVA_HOME:+$JAVA_HOME/bin/}javac"
KEYTOOL="${JAVA_HOME:+$JAVA_HOME/bin/}keytool"

VERSION=$(sed -n 's/.*android:versionName="\([^"]*\)".*/\1/p' app/AndroidManifest.xml | head -n1)
echo "AURA OS $VERSION ($([ $RELEASE = 1 ] && echo release || echo debug))"

OUT=build
DIST=dist
rm -rf "$OUT"
mkdir -p "$OUT/gen" "$OUT/classes" "$OUT/dex" "$DIST"

"$BT/aapt2" compile --dir app/res -o "$OUT/res.zip"
"$BT/aapt2" link -o "$OUT/base.apk" -I "$JAR" --manifest app/AndroidManifest.xml --java "$OUT/gen" \
  --min-sdk-version "$MIN_SDK" --target-sdk-version "$TARGET_SDK" "$OUT/res.zip"
find app/src "$OUT/gen" -name '*.java' > "$OUT/sources.txt"
"$JAVAC" -encoding UTF-8 -nowarn -Xlint:-options --release 11 -classpath "$JAR" -d "$OUT/classes" @"$OUT/sources.txt"
"$BT/d8" --release --min-api "$MIN_SDK" --lib "$JAR" --output "$OUT/dex" $(find "$OUT/classes" -name '*.class')

# Fixed timestamps keep the unsigned APK identical between builds of the same source.
cp "$OUT/base.apk" "$OUT/unsigned.apk"
STAGE="$OUT/stage"
mkdir -p "$STAGE"
cp "$OUT/dex/classes.dex" "$STAGE/"
cp -R app/assets "$STAGE/assets"
find "$STAGE" -exec touch -t 202001010000 {} +
(cd "$STAGE" && zip -q -X -9 ../unsigned.apk classes.dex && find assets -type f | LC_ALL=C sort | zip -q -X -0 ../unsigned.apk -@)
"$BT/zipalign" -f -p 4 "$OUT/unsigned.apk" "$OUT/aligned.apk"

if [ $RELEASE = 1 ]; then
  : "${AURA_RELEASE_KEYSTORE:?set AURA_RELEASE_KEYSTORE}" "${AURA_RELEASE_KEY_ALIAS:?set AURA_RELEASE_KEY_ALIAS}" "${AURA_RELEASE_STORE_PASS:?set AURA_RELEASE_STORE_PASS}"
  APK="$DIST/AURA-OS-v$VERSION.apk"
  "$BT/apksigner" sign --ks "$AURA_RELEASE_KEYSTORE" --ks-key-alias "$AURA_RELEASE_KEY_ALIAS" \
    --ks-pass env:AURA_RELEASE_STORE_PASS --key-pass env:AURA_RELEASE_STORE_PASS --out "$APK" "$OUT/aligned.apk"
else
  KS=keys/debug.keystore
  if [ ! -f "$KS" ]; then
    mkdir -p keys
    "$KEYTOOL" -genkeypair -keystore "$KS" -storepass android -keypass android -alias aura-debug \
      -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=AURA OS debug" >/dev/null
  fi
  APK="$DIST/AURA-OS-v$VERSION-debug.apk"
  "$BT/apksigner" sign --ks "$KS" --ks-pass pass:android --out "$APK" "$OUT/aligned.apk"
fi
"$BT/apksigner" verify "$APK"
(cd "$DIST" && sha256sum "$(basename "$APK")" > "$(basename "$APK").sha256" 2>/dev/null || shasum -a 256 "$(basename "$APK")" > "$(basename "$APK").sha256")
echo "Built $APK ($(wc -c < "$APK" | tr -d ' ') bytes)"
cat "$APK.sha256"
