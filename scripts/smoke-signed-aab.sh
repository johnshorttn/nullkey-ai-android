#!/usr/bin/env bash
# Builds a Play-shaped signed AAB using a throwaway keystore in a temp directory.
# Never reads production secrets. Do not upload this AAB to Google Play.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

if ! command -v keytool >/dev/null 2>&1; then
  echo "FAIL: keytool not found (JDK required)" >&2
  exit 1
fi

TMP="$(mktemp -d "${TMPDIR:-/tmp}/nullkey-aab-smoke.XXXXXX")"
cleanup() {
  rm -rf "$TMP"
}
trap cleanup EXIT

KEYSTORE="$TMP/ci-smoke.p12"
AAB="$ROOT/app/build/outputs/bundle/release/app-release.aab"
SMOKE_PASS='android-ci-smoke'

echo "Generating throwaway PKCS12 keystore (not for Play upload)..."
keytool -genkeypair \
  -keystore "$KEYSTORE" \
  -storetype PKCS12 \
  -alias upload \
  -keyalg RSA \
  -keysize 2048 \
  -validity 1 \
  -storepass "$SMOKE_PASS" \
  -keypass "$SMOKE_PASS" \
  -dname "CN=NullKey CI Smoke, OU=CI, O=NullKey, C=US" \
  -noprompt >/dev/null

export KEYSTORE_FILE="$KEYSTORE"
export KEYSTORE_PASSWORD="$SMOKE_PASS"
export KEY_ALIAS=upload
export KEY_PASSWORD="$SMOKE_PASS"

echo "Confirming the release minify opt-out still resolves to off..."
# Ignore a caller-set NULLKEY_RELEASE_MINIFY so this smoke always checks the flag.
OFF_STATUS="$(env -u NULLKEY_RELEASE_MINIFY ./gradlew --no-daemon --console=plain :app:printReleaseSigningStatus -Pnullkey.releaseMinify=false)"
grep -q 'minifyRelease=false' <<<"$OFF_STATUS" || {
  echo "FAIL: -Pnullkey.releaseMinify=false did not disable R8" >&2
  echo "$OFF_STATUS" >&2
  exit 1
}
grep -q 'shrinkResourcesRelease=false' <<<"$OFF_STATUS" || {
  echo "FAIL: resource shrinking stayed on when minify was disabled" >&2
  echo "$OFF_STATUS" >&2
  exit 1
}
grep -E '^(minifyRelease|shrinkResourcesRelease)=' <<<"$OFF_STATUS"

echo "bundleRelease + assembleRelease with throwaway signing (R8 on)..."
env -u NULLKEY_RELEASE_MINIFY ./gradlew --no-daemon --console=plain :app:printReleaseSigningStatus :app:bundleRelease :app:assembleRelease | tee "$TMP/release-build.log"

grep -q 'minifyRelease=true' "$TMP/release-build.log" || {
  echo "FAIL: release build did not report minifyRelease=true" >&2
  exit 1
}
grep -q 'shrinkResourcesRelease=true' "$TMP/release-build.log" || {
  echo "FAIL: release build did not report shrinkResourcesRelease=true" >&2
  exit 1
}

[[ -f "$AAB" ]] || {
  echo "FAIL: AAB not produced at $AAB" >&2
  exit 1
}

SIZE="$(wc -c < "$AAB" | tr -d ' ')"
[[ "$SIZE" -gt 10000 ]] || {
  echo "FAIL: AAB is implausibly small ($SIZE bytes)" >&2
  exit 1
}

echo "Verifying AAB jar signature..."
jarsigner -verify "$AAB" >/dev/null

APK="$ROOT/app/build/outputs/apk/release/app-release.apk"
[[ -f "$APK" ]] || {
  echo "FAIL: release APK not produced at $APK" >&2
  exit 1
}
APK_SIZE="$(wc -c < "$APK" | tr -d ' ')"
[[ "$APK_SIZE" -gt 10000 ]] || {
  echo "FAIL: release APK is implausibly small ($APK_SIZE bytes)" >&2
  exit 1
}

echo "Checking R8 mapping, kept IME/Room/enum names, and release permissions..."
bash "$ROOT/scripts/check-r8-mapping.sh"

echo "OK: signed AAB smoke produced $AAB ($SIZE bytes) and release APK ($APK_SIZE bytes)"
echo "Do not upload this artifact to Google Play; it uses a throwaway keystore."
echo "Keep app/build/outputs/mapping/release/mapping.txt with any Play upload of a minified AAB."
