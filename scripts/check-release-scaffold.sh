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
RULES="$ROOT/app/proguard-rules.pro"
APP_GRADLE="$ROOT/app/build.gradle.kts"
RELEASE_DOC="$ROOT/docs/RELEASE_AAB.md"
SMOKE="$ROOT/scripts/smoke-signed-aab.sh"
R8_CHECK="$ROOT/scripts/check-r8-mapping.sh"

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

# Library manifests (for example ML Kit telemetry) may inject INTERNET.
# The app manifest must strip it and must not request it for real.
if grep -E 'android\.permission\.INTERNET' "$MANIFEST" | grep -v 'tools:node="remove"' | grep -q .; then
  fail "app AndroidManifest requests INTERNET; update docs/privacy.md and Play Data Safety before adding network"
fi
grep -Fq 'android.permission.INTERNET" tools:node="remove"' "$MANIFEST" \
  || fail "app AndroidManifest must strip library-injected INTERNET"
grep -Fq 'android.permission.ACCESS_NETWORK_STATE" tools:node="remove"' "$MANIFEST" \
  || fail "app AndroidManifest must strip library-injected ACCESS_NETWORK_STATE"

PRIVACY_URL="https://johnshorttn.github.io/nullkey-ai-android/privacy.html"
PRIVACY_DOC="$ROOT/docs/privacy.md"
[[ -f "$PRIVACY_DOC" ]] || fail "docs/privacy.md missing"
grep -Fq "$PRIVACY_URL" "$PRIVACY_DOC" || fail "docs/privacy.md must cite $PRIVACY_URL"
grep -Fq 'does **not** include the `INTERNET` permission' "$PRIVACY_DOC" || fail "docs/privacy.md must state the app has no INTERNET permission"
grep -Fq "$PRIVACY_URL" "$ROOT/docs/PRIVACY.md" || fail "docs/PRIVACY.md must point at the canonical privacy.html URL"
if grep -Fq 'allowBackup is currently enabled' "$ROOT/docs/PRIVACY.md"; then
  fail "docs/PRIVACY.md must not claim Auto Backup is enabled"
fi
for doc in "$ROOT/docs/PLAY_STORE.md" "$ROOT/docs/RELEASE_AAB.md" "$ROOT/README.md" \
  "$ROOT/app/src/main/java/com/nullverse/nullkeyai/ui/AboutSupportActivity.kt"; do
  grep -Fq "$PRIVACY_URL" "$doc" || fail "$doc must cite $PRIVACY_URL"
done

[[ -f "$RULES" ]] || fail "app/proguard-rules.pro missing"
[[ -f "$R8_CHECK" ]] || fail "scripts/check-r8-mapping.sh missing"
[[ -x "$R8_CHECK" ]] || fail "scripts/check-r8-mapping.sh must be executable"

for token in \
  'NullKeyImeService' \
  'ClipboardMonitorService' \
  'NullKeyKeyboardView' \
  'NullKeyDatabase_Impl' \
  'androidx.room.RoomDatabase' \
  '-keep enum com.nullverse.nullkeyai.**'
do
  grep -F -q -e "$token" "$RULES" || fail "proguard-rules.pro must mention $token"
done

grep -Fq 'isMinifyEnabled = minifyRelease' "$APP_GRADLE" || fail "release minify must follow minifyRelease"
grep -Fq 'isShrinkResources = minifyRelease' "$APP_GRADLE" || fail "release resource shrinking must follow minifyRelease"
grep -Fq 'NULLKEY_RELEASE_MINIFY' "$APP_GRADLE" || fail "release minify must honor NULLKEY_RELEASE_MINIFY"
grep -Fq 'isMinifyEnabled = false' "$APP_GRADLE" || fail "debug minify must stay off"

grep -Fq 'nullkey.releaseMinify' "$RELEASE_DOC" || fail "docs/RELEASE_AAB.md must document the R8 opt-out"
grep -Fq 'mapping.txt' "$RELEASE_DOC" || fail "docs/RELEASE_AAB.md must mention mapping.txt"
if grep -Fq 'R8/minify stays' "$RELEASE_DOC"; then
  fail "docs/RELEASE_AAB.md still says R8 stays off"
fi

grep -Fq 'check-r8-mapping.sh' "$SMOKE" || fail "smoke-signed-aab.sh must run check-r8-mapping.sh"
grep -Fq 'assembleRelease' "$SMOKE" || fail "smoke-signed-aab.sh must assembleRelease"
grep -Fq 'bundleRelease' "$SMOKE" || fail "smoke-signed-aab.sh must bundleRelease"

ok "release signing scaffold (placeholders, gitignore, no INTERNET permission)"
ok "release R8 rules, opt-out flag, and AAB docs"
