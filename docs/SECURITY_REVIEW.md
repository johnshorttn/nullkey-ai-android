# NullKey AI — performance & security review

**Date:** 2026-09-21  
**Branch:** `rewrite/v2` (this PR)  
**Scope:** On-device review of keyboard, Vault, clipboard monitor, backup/sync, and the `targetSdk` / `compileSdk` **36** bump. No Play upload. No secrets inspected beyond public tree.

This is a checklist review with small, safe fixes applied in the same PR. It does **not** claim issue #17 complete.

## Method

- Manifest, permissions, exported components, backup flags
- IME (`NullKeyImeService`) and clipboard foreground service
- Vault crypto, portable backup, Room, asset store
- Sync transport (offline-only in this tree)
- Obvious IME/Vault performance hotspots (no speculative rewrites)

## Findings fixed in this PR

| ID | Severity | Finding | Fix |
| --- | --- | --- | --- |
| S1 | P1 | `android:allowBackup="true"` would let Auto Backup / `adb backup` copy Vault DB, assets, and prefs. | `allowBackup="false"` plus `fullBackupContent` / `dataExtractionRules` excludes for database, shared prefs, and `vault/` files. |
| S2 | P1 | API 34+ `startForeground()` should pass `FOREGROUND_SERVICE_TYPE_SPECIAL_USE` to match the manifest. Missing type is a crash risk on `targetSdk` 34–36. | Typed `startForeground` on API 34+; helper covered by unit tests. |
| S3 | P1 | Android 15+ draws activities edge-to-edge; Android 16 **removed** the opt-out. Untreated, setup/Vault/IME chrome can sit under status/nav bars. On the 320x640 CI emulator that also hid search/setup controls. | `SystemBarInsets` pads activity content and the IME root. Vault home is a `ScrollView` so those controls stay reachable. |
| S4 | P2 | `usesCleartextTraffic` was implicit. | Explicit `android:usesCleartextTraffic="false"`. |

Compile restore (literal `\n` in vault-swipe Kotlin/XML) is included so this branch builds independently. Swipe *behavior* is unchanged; cancel/confirm UX remains PR #18.

## Confirmed OK (no code change)

- **No `INTERNET`.** Offline default holds. Sync is an in-memory / provider-agnostic interface with no production host.
- **IME** is `exported="true"` only with `BIND_INPUT_METHOD` (system-only bind).
- **Clipboard monitor** is `exported="false"` with `foregroundServiceType="specialUse"` and a `PROPERTY_SPECIAL_USE_FGS_SUBTYPE` explanation.
- **Internal activities** (`ClipDetailActivity`, `TrashActivity`, `ClipboardLabActivity`) are `exported="false"`.
- **FileProvider** is not exported; share/open uses `FLAG_GRANT_READ_URI_PERMISSION` and `VaultAssetStore.resolve` rejects path traversal.
- **Vault protection** uses Android Keystore AES-GCM (non-exportable). Portable Secure Backup is PBKDF2-HMAC-SHA256 (210k) + AES-GCM, separate from the device key.
- **Plain JSON export** keeps protected payloads as ciphertext and refuses device-bound restore (use `.nkbackup`). Covered by `ClipBackupSecurityTest`.
- **POST_NOTIFICATIONS** is declared and requested at runtime before the monitor is useful on API 33+.
- **`USE_BIOMETRIC` / `USE_FINGERPRINT`** come from `androidx.biometric` for protected-clip unlock. Not network-related.
- **PendingIntent** for the monitor notification is `FLAG_IMMUTABLE`.
- **16 KB page size:** no JNI/native libs in this module.
- **Clipboard Capture Lab** uses generated tokens and restores the previous clip; it does not log existing clipboard text.

## Deferred (not blocking this slice)

| ID | Severity | Finding | Recommended next |
| --- | --- | --- | --- |
| D1 | P2 | Release R8 is now **on** by default (`isMinifyEnabled` + `isShrinkResources`), with keep rules for IME components, Room `*_Impl`, and persisted enum names. Debug stays off. | Residual: instrumented CI still uses the debug APK. Install a minified release build on a device before the first Play upload. Opt out with `-Pnullkey.releaseMinify=false`. Upload `mapping.txt`. |
| D2 | P2 | Unprotected JSON **Export** writes clip plaintext by design (user-initiated). | Keep; listing/privacy copy (PR #23) should say Export is user-driven and not Auto Backup. |
| D3 | P3 | `WordSuggester.learn` persists the whole frequency map on every committed word (`MAX_WORDS = 2000`). Fine at current size; a debounce would be a later polish. | Leave until swipe-typing / spelling work lands. |
| D4 | P3 | IME `refreshClips()` runs a Room query on every search keystroke (no debounce). | Optional later; vault search is local. |
| D5 | P3 | Clipboard FGS is sticky and user-started. API 15+ `specialUse` is the correct type; Play may still ask for a justification at Console time. | Keep subtype string; document in Play form (PR #23). |
| D6 | P3 | `POST_NOTIFICATIONS` denial still allows Start monitor; the FGS notification may be hidden and the service can be killed. | Optional: disable Start monitor until granted. Not changed here to avoid UX scope creep. |
| D7 | — | Instrumented CI runs **API 34** and **API 36** `google_apis` x86_64 emulators. `targetSdk` 36 is also asserted via `applicationInfo` on the API-34 image. | Keep both; API 34 is the prior green baseline. |
| D8 | — | Incognito/no-learn, TalkBack virtual views, theme height, and Play/AAB docs live in open PRs #18–#23, not this tree. | Merge/land wave; do not regress those control sets. |

## targetSdk / compileSdk 36 notes

- Play Console requires **target API 36** for new phone listings/updates after 31 August 2026.
- Tooling: **AGP 8.9.1** (minimum for API 36) and **Gradle 8.11.1**. Kotlin/KSP stay on 1.9.24.
- Behavior changes that apply to NullKey: edge-to-edge (S3), typed FGS (S2), backup (S1). No photo/media permissions, no `dataSync` FGS timeout, no exported broadcast receivers.
- Robolectric unit tests pin `sdk=34` (`app/src/test/resources/robolectric.properties`) because Robolectric 4.12.2 does not emulate API 36. Contract tests still read `targetSdkVersion == 36`.

## Performance (short)

- Vault image previews already go through `SampledBitmapDecoder` (inSampleSize). Keep it; do not decode full-resolution bitmaps on the detail screen.
- Gesture ranking scans at most `MAX_WORDS` (2000). Acceptable; PR #19 may replace the scorer — do not rewrite here.
- No network, no periodic WorkManager, no analytics SDKs.

## Tests added with this review

- `TargetSdkContractTest` — target 36, no INTERNET, IME bind permission, specialUse FGS, backup flag, backup XML resources.
- `TargetSdkInstrumentedTest` — same package contract on device plus Start monitor → running FGS.
- `SystemBarInsetsTest` — inset union used for padding.
- `ClipSwipePreferencesTest` — newline/`labelRes` compile guard.

## Recommended next milestone for #17

Do **not** claim 100%. D1 (release R8) is enabled on this tree with the residual device pass noted above. The next #17 milestone is a **public HTTPS privacy-policy URL** (host [PRIVACY.md](PRIVACY.md)). OCR/spelling stay deferred unless they can ship with no `INTERNET` permission. Promoting `rewrite/v2` to `main` (#16) waits on that product decision.
