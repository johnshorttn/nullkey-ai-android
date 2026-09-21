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

echo "bundleRelease with throwaway signing..."
./gradlew --no-daemon :app:printReleaseSigningStatus :app:bundleRelease

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

echo "OK: signed AAB smoke produced $AAB ($SIZE bytes)"
echo "Do not upload this artifact to Google Play; it uses a throwaway keystore."
