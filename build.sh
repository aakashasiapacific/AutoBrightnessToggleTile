#!/usr/bin/env bash
# Builds a signed, installable APK without Gradle or Android Studio:
#   aapt2 (resources) -> javac (Java 8 bytecode) -> dx (dex) -> zipalign -> apksigner (v2 + v3)
#
# Requirements (Debian/Ubuntu):
#   sudo apt install aapt apksigner zipalign dalvik-exchange default-jdk-headless curl zip
#
# Environment overrides:
#   SDK_JAR   path to an android.jar (API 34). Downloaded to .sdk/ on first run if unset.
#   KS        signing keystore (default: keystore/release.jks, created on first run)
#   KS_PASS   keystore password (default: "android")
set -euo pipefail
unset JAVA_TOOL_OPTIONS

ROOT="$(cd "$(dirname "$0")" && pwd)"
OUT="$ROOT/build"
SDK_JAR="${SDK_JAR:-$ROOT/.sdk/android-34.jar}"
SDK_URL="https://raw.githubusercontent.com/Sable/android-platforms/master/android-34/android.jar"
KS="${KS:-$ROOT/keystore/release.jks}"
KS_PASS="${KS_PASS:-android}"
VERSION="$(sed -n 's/.*android:versionName="\([^"]*\)".*/\1/p' "$ROOT/AndroidManifest.xml")"
APK_NAME="AutoBrightnessTile-v$VERSION.apk"

if [ ! -f "$SDK_JAR" ]; then
    echo "Downloading android.jar (API 34)"
    mkdir -p "$(dirname "$SDK_JAR")"
    curl -fsSL -o "$SDK_JAR" "$SDK_URL"
fi

rm -rf "$OUT"
mkdir -p "$OUT/gen" "$OUT/classes" "$OUT/compiled" "$(dirname "$KS")"

echo "[1/6] Compiling resources"
aapt2 compile --dir "$ROOT/res" -o "$OUT/compiled/res.zip"

echo "[2/6] Linking resources + manifest"
aapt2 link -o "$OUT/base.apk" \
    -I "$SDK_JAR" \
    --manifest "$ROOT/AndroidManifest.xml" \
    --java "$OUT/gen" \
    -A "$ROOT/assets" \
    "$OUT/compiled/res.zip"

echo "[3/6] Compiling Java"
javac -nowarn -Xlint:-options -encoding UTF-8 -source 8 -target 8 \
    -bootclasspath "$SDK_JAR" \
    -d "$OUT/classes" \
    $(find "$ROOT/src" "$OUT/gen" -name '*.java')

echo "[4/6] Dexing"
dalvik-exchange --dex --min-sdk-version=26 --output="$OUT/classes.dex" "$OUT/classes"

echo "[5/6] Packaging + aligning"
cp "$OUT/base.apk" "$OUT/unaligned.apk"
(cd "$OUT" && zip -q -X unaligned.apk classes.dex)
zipalign -f -p 4 "$OUT/unaligned.apk" "$OUT/aligned.apk"

echo "[6/6] Signing"
if [ ! -f "$KS" ]; then
    echo "No keystore found, creating a new local signing key at $KS"
    keytool -genkeypair -keystore "$KS" -storetype PKCS12 \
        -storepass "$KS_PASS" -keypass "$KS_PASS" -alias autobrightness \
        -keyalg RSA -keysize 4096 -validity 36500 \
        -dname "CN=Auto Brightness Tile" >/dev/null
fi
apksigner sign --ks "$KS" --ks-pass "pass:$KS_PASS" --ks-key-alias autobrightness \
    --out "$OUT/$APK_NAME" "$OUT/aligned.apk"
apksigner verify "$OUT/$APK_NAME"
echo "Built: $OUT/$APK_NAME"
