#!/usr/bin/env bash
# Verifies Play/AAB signing scaffolding: placeholders, gitignore, and no
# tracked secrets. Does not read keystore.properties or print passwords.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

fail() {
  echo "FAIL: $*" >&2
  exit 1
}

ok() {
  echo "OK: $*"
}

GITIGNORE="$ROOT/.gitignore"
EXAMPLE="$ROOT/keystore.properties.example"
MANIFEST="$ROOT/app/src/main/AndroidManifest.xml"

[[ -f "$GITIGNORE" ]] || fail ".gitignore missing"
[[ -f "$EXAMPLE" ]] || fail "keystore.properties.example missing"
[[ -f "$MANIFEST" ]] || fail "AndroidManifest.xml missing"

for pattern in 'keystore.properties' '*.jks' '*.aab' '*.keystore'; do
  grep -Fq "$pattern" "$GITIGNORE" || fail ".gitignore must mention $pattern"
done

grep -q 'CHANGE_ME' "$EXAMPLE" || fail "keystore.properties.example must use CHANGE_ME placeholders"
grep -q 'KEYSTORE_FILE' "$EXAMPLE" || fail "keystore.properties.example must document KEYSTORE_FILE"
grep -q 'KEYSTORE_PASSWORD' "$EXAMPLE" || fail "keystore.properties.example must document KEYSTORE_PASSWORD"
grep -q 'bundleRelease' "$EXAMPLE" || fail "keystore.properties.example must mention bundleRelease"

store_password="$(grep -E '^storePassword=' "$EXAMPLE" | head -1 | cut -d= -f2- || true)"
key_password="$(grep -E '^keyPassword=' "$EXAMPLE" | head -1 | cut -d= -f2- || true)"
[[ "$store_password" == "CHANGE_ME" ]] || fail "example storePassword must be CHANGE_ME"
[[ "$key_password" == "CHANGE_ME" ]] || fail "example keyPassword must be CHANGE_ME"

if git rev-parse --is-inside-work-tree >/dev/null 2>&1; then
  if git ls-files --error-unmatch keystore.properties >/dev/null 2>&1; then
    fail "keystore.properties is tracked by git"
  fi
  tracked_keys="$(git ls-files -- '*.jks' '*.keystore' '*.p12' '*.pkcs12' | grep -v 'debug.keystore' || true)"
  [[ -z "$tracked_keys" ]] || fail "tracked keystore material: $tracked_keys"
fi

if grep -Eq 'android\.permission\.INTERNET' "$MANIFEST"; then
  fail "app AndroidManifest requests INTERNET; update docs/PRIVACY.md and Play Data Safety before adding network"
fi

ok "release signing scaffold (placeholders, gitignore, no INTERNET permission)"
