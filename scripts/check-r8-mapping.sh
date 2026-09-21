#!/usr/bin/env bash
# Confirms a release build actually ran R8 and that IME, Room, and persisted
# enum names survived. Does not read keystores or print secrets.
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

MAP_DIR="$ROOT/app/build/outputs/mapping/release"
MAPPING="$MAP_DIR/mapping.txt"
SEEDS="$MAP_DIR/seeds.txt"
RESOURCES="$MAP_DIR/resources.txt"
CONFIG="$MAP_DIR/configuration.txt"
APK="$ROOT/app/build/outputs/apk/release/app-release.apk"

[[ -f "$MAPPING" ]] || fail "missing $MAPPING (release minify did not run)"
[[ -f "$SEEDS" ]] || fail "missing $SEEDS"
[[ -f "$RESOURCES" ]] || fail "missing $RESOURCES (resource shrinking did not run)"
[[ -f "$CONFIG" ]] || fail "missing $CONFIG"
[[ -f "$APK" ]] || fail "missing $APK (assembleRelease did not produce a release APK)"

grep -q 'proguard-rules.pro' "$CONFIG" || fail "R8 configuration did not include proguard-rules.pro"

python3 - "$MAPPING" "$SEEDS" "$APK" <<'PY'
import pathlib, sys, zipfile

mapping_path, seeds_path, apk_path = map(pathlib.Path, sys.argv[1:4])
mapping = mapping_path.read_text(encoding="utf-8", errors="replace")
seeds = seeds_path.read_text(encoding="utf-8", errors="replace")

entry_points = [
    "com.nullverse.nullkeyai.ime.NullKeyImeService",
    "com.nullverse.nullkeyai.clipboard.ClipboardMonitorService",
    "com.nullverse.nullkeyai.ui.MainActivity",
    "com.nullverse.nullkeyai.ui.ClipDetailActivity",
    "com.nullverse.nullkeyai.ui.TrashActivity",
    "com.nullverse.nullkeyai.diagnostics.ClipboardLabActivity",
    "com.nullverse.nullkeyai.ime.engine.NullKeyKeyboardView",
    "com.nullverse.nullkeyai.db.NullKeyDatabase",
    "com.nullverse.nullkeyai.db.NullKeyDatabase_Impl",
    "com.nullverse.nullkeyai.db.Clip",
    "com.nullverse.nullkeyai.db.Tag",
    "com.nullverse.nullkeyai.db.ClipTagCrossRef",
]
enums = {
    "com.nullverse.nullkeyai.db.ClipContentType",
    "com.nullverse.nullkeyai.db.ClipCaptureMethod",
    "com.nullverse.nullkeyai.db.ClipSourceConfidence",
    "com.nullverse.nullkeyai.clipboard.ClipSwipeAction",
    "com.nullverse.nullkeyai.ime.engine.KeyboardThemeId",
    "com.nullverse.nullkeyai.sync.SyncRecordState",
}
# Names written into Room columns, prefs, JSON, and SQL defaults.
enum_constants = {
    "TEXT", "IMAGE", "FILE", "URI", "RICH",
    "CLIPBOARD", "IME", "SHARE", "MANUAL", "OCR", "IMPORT", "REMOTE", "UNKNOWN",
    "CONFIRMED", "INFERRED",
    "PIN", "PROTECT", "DELETE", "TAG",
    "DARK_VAULT", "LIGHT", "SYSTEM",
    "LOCAL", "PENDING", "SYNCED", "CONFLICT", "TOMBSTONE",
    "LETTERS", "SYMBOLS", "PORTRAIT", "LANDSCAPE", "OFF", "ON", "LOCKED",
}
# Used by release code, so R8 should rename them rather than keep the source name.
obfuscation_samples = [
    "com.nullverse.nullkeyai.ime.WordSuggester",
    "com.nullverse.nullkeyai.clipboard.VaultArchive",
    "com.nullverse.nullkeyai.ime.GestureWordRanker",
]

renamed_app = []
class_map = {}
current = None
for raw in mapping.splitlines():
    if raw.startswith("#") or not raw.strip():
        continue
    if not raw.startswith(" ") and raw.endswith(":") and " -> " in raw:
        left, right = raw[:-1].split(" -> ", 1)
        current = left
        class_map[left] = right
        if left.startswith("com.nullverse.nullkeyai.") and left != right:
            renamed_app.append(left)
        continue
    if current in enums and raw.startswith("    ") and " -> " in raw:
        member = raw.strip()
        left_m, right_m = member.split(" -> ", 1)
        left_name = left_m.split()[-1].split(".")[-1]
        right_name = right_m.split()[-1].split(".")[-1].rstrip(":")
        if left_name in enum_constants and left_name != right_name:
            raise SystemExit(
                f"FAIL: persisted enum constant renamed: {current}.{left_name} -> {right_name}"
            )

if not renamed_app:
    raise SystemExit("FAIL: mapping.txt did not rename any com.nullverse.nullkeyai class")

for name in entry_points:
    if name not in seeds:
        raise SystemExit(f"FAIL: seeds.txt does not keep {name}")
    mapped = class_map.get(name)
    if mapped is not None and mapped != name:
        raise SystemExit(f"FAIL: entry point renamed: {name} -> {mapped}")

for name in enums:
    mapped = class_map.get(name)
    if mapped is not None and mapped != name:
        raise SystemExit(f"FAIL: persisted enum class renamed: {name} -> {mapped}")

renamed_samples = []
for name in obfuscation_samples:
    mapped = class_map.get(name)
    if mapped is None:
        raise SystemExit(f"FAIL: expected {name} in mapping.txt (renamed or removed unexpectedly)")
    if mapped == name:
        raise SystemExit(f"FAIL: {name} kept its source name; R8 obfuscation looks off")
    renamed_samples.append(f"{name} -> {mapped}")

with zipfile.ZipFile(apk_path) as zf:
    dex = b"".join(zf.read(n) for n in zf.namelist() if n.endswith(".dex"))

def descriptor(class_name: str) -> bytes:
    return ("L" + class_name.replace(".", "/") + ";").encode()

missing_dex = [name for name in entry_points if descriptor(name) not in dex]
if missing_dex:
    raise SystemExit("FAIL: release dex missing entry points: " + ", ".join(missing_dex))

for literal in ("DARK_VAULT", "INFERRED", "TOMBSTONE"):
    if literal.encode() not in dex:
        raise SystemExit(f"FAIL: release dex missing persisted enum name {literal}")

print(f"renamed_app_classes={len(renamed_app)}")
print("obfuscated=" + "; ".join(renamed_samples))
PY

# Release APK must stay offline. aapt is in the SDK build-tools AGP just used.
SDK="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}"
if [[ -z "$SDK" && -f "$ROOT/local.properties" ]]; then
  SDK="$(grep -E '^sdk\.dir=' "$ROOT/local.properties" | head -1 | cut -d= -f2- | tr -d '\r')"
fi
AAPT=""
if [[ -n "$SDK" && -d "$SDK/build-tools" ]]; then
  AAPT="$(find "$SDK/build-tools" -name aapt -type f | sort | tail -1 || true)"
fi
if [[ -z "$AAPT" ]]; then
  fail "aapt not found; cannot verify release APK permissions"
fi

PERMS="$("$AAPT" dump permissions "$APK")"
if grep -q 'android.permission.INTERNET' <<<"$PERMS"; then
  fail "release APK requests INTERNET"
fi
BADGING="$("$AAPT" dump badging "$APK")"
grep -q "package: name='com.nullverse.nullkeyai'" <<<"$BADGING" || fail "release APK package name changed"
grep -q "targetSdkVersion:'36'" <<<"$BADGING" || fail "release APK targetSdk is not 36"

ok "R8 mapping keeps IME, Room, and persisted enum names; app code is obfuscated"
ok "release APK has no INTERNET permission and targetSdk 36"
