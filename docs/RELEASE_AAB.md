# Signed AAB / Play upload path

This is the issue #17 release-build path. It produces a signed Android App Bundle locally or in CI. **It does not upload to Google Play.** Never commit keystores, `keystore.properties`, or passwords.

## What is already wired

| Piece | Location |
| --- | --- |
| Optional release signing | `app/build.gradle.kts` |
| Placeholder properties | `keystore.properties.example` |
| Git-ignored secrets | `.gitignore` (`keystore.properties`, `*.jks`, `*.p12`, `*.aab`) |
| Production CI (secrets) | `.github/workflows/release.yml` |
| Throwaway AAB smoke | `scripts/smoke-signed-aab.sh` |
| Scaffold check | `scripts/check-release-scaffold.sh` / `./gradlew :app:checkReleaseScaffold` |
| Release R8 (minify + shrink) | `app/build.gradle.kts` release build type, `app/proguard-rules.pro` |
| R8 mapping check | `scripts/check-r8-mapping.sh` (also run by the smoke script) |

If `KEYSTORE_FILE` / `keystore.properties` is missing, `release` stays **unsigned** so `assembleDebug` and unit tests keep working. Environment variables override the properties file so CI and the smoke script cannot pick up a developer keystore by accident.

## Local signed AAB

1. Generate an **upload** keystore (keep this file offline; back it up). Do not commit it.

```bash
keytool -genkeypair -v \
  -keystore upload-keystore.jks \
  -keyalg RSA -keysize 2048 -validity 10000 \
  -alias upload
```

2. Copy `keystore.properties.example` to `keystore.properties` (git-ignored) and replace `CHANGE_ME` with the real store/key passwords. Point `storeFile` at the keystore path relative to the repo root, or use an absolute path.

3. Build the release AAB. **R8 minify and resource shrinking are on for `release`** (debug and unit tests stay unminified):

```bash
./gradlew :app:printReleaseSigningStatus
./gradlew :app:bundleRelease
# APK if needed (same R8 pass):
./gradlew :app:assembleRelease
```

`printReleaseSigningStatus` prints `minifyRelease=true` and `shrinkResourcesRelease=true` when R8 is on. It never prints passwords.

To build a diagnostic **unminified** release AAB (not the Play default):

```bash
./gradlew :app:bundleRelease -Pnullkey.releaseMinify=false
# or:
NULLKEY_RELEASE_MINIFY=false ./gradlew :app:bundleRelease
```

Environment variables override `-P`. Signing env vars are unchanged.

Outputs (git-ignored):

- `app/build/outputs/bundle/release/app-release.aab`
- `app/build/outputs/apk/release/app-release.apk`
- `app/build/outputs/mapping/release/mapping.txt` (R8 deobfuscation file; upload this to Play with the AAB)

4. Confirm signing (APK):

```bash
apksigner verify --print-certs app/build/outputs/apk/release/app-release.apk
```

The AAB is a signed zip; `jarsigner -verify app/build/outputs/bundle/release/app-release.aab` is enough for a local check.

## GitHub Actions (production key)

Configure repository **Actions secrets** (never put these in source):

| Secret | Value |
| --- | --- |
| `KEYSTORE_BASE64` | `base64 -w0 upload-keystore.jks` |
| `KEYSTORE_PASSWORD` | keystore store password |
| `KEY_ALIAS` | key alias (`upload` in the example) |
| `KEY_PASSWORD` | key password |

Then:

- Push a `v*` tag, or
- Run **Release (signed)** via `workflow_dispatch`

The workflow decodes the keystore into the runner temp dir, sets `KEYSTORE_FILE` / `KEYSTORE_PASSWORD` / `KEY_ALIAS` / `KEY_PASSWORD`, runs `:app:bundleRelease` and `:app:assembleRelease` **with release R8 on**, verifies the APK with `apksigner`, and uploads the AAB, APK, and `mapping.txt`. If `KEYSTORE_BASE64` is unset, it runs the throwaway smoke instead of failing, so the AAB path stays proven. Do not upload the smoke AAB. Do upload `mapping.txt` for any minified AAB you send to Play.

## Throwaway smoke (no Play key)

```bash
./scripts/check-release-scaffold.sh
./scripts/smoke-signed-aab.sh
```

This generates a 1-day PKCS12 in `$TMPDIR`, builds a signed AAB and release APK with R8 on, verifies the jar signature, and runs `scripts/check-r8-mapping.sh`. That check requires:

- `mapping.txt` renames ordinary app classes (for example `WordSuggester`). Kotlin objects such as `VaultArchive` and `GestureWordRanker` may be inlined instead; their original names must not remain in the release dex
- seeds/dex still contain the IME service, clipboard monitor, activities, keyboard view, and Room `NullKeyDatabase_Impl`
- persisted enum names (`DARK_VAULT`, `INFERRED`, `TOMBSTONE`, and the rest of the vault/prefs/JSON set) are not renamed
- the release APK does not request `INTERNET` and still targets API 36

It also checks that `-Pnullkey.releaseMinify=false` reports minify and resource shrinking off, without building that unminified AAB. **Do not upload the smoke AAB to Play** — Play app signing is bound to the first upload key.

## Play Console upload (manual)

1. Use the **production** upload keystore, not the smoke keystore.
2. In Play Console: **Release → Production / Testing → Create release → Upload AAB**.
3. Enable **Play App Signing**. Keep the upload key; Google holds the app-signing key.
4. Complete Data Safety, IME, and privacy-policy fields using [PLAY_STORE.md](PLAY_STORE.md).
5. Privacy policy URL for Play Console: **https://johnshorttn.github.io/nullkey-ai-android/privacy.html** (source [privacy.md](privacy.md)). GitHub Pages already publishes `docs/` from `rewrite/v2`. Do not paste a repo-only markdown path.

This repository does not call `fastlane supply`, Play Developer API, or `r0adkll/upload-google-play`.

## SDK target

`targetSdk` / `compileSdk` are **36**.

## R8

Release minify and resource shrinking are **on** by default. Debug is not minified, so `assembleDebug` and `testDebugUnitTest` are unchanged.

Keep rules in `app/proguard-rules.pro` cover:

- IME service, clipboard foreground service, activities, and `NullKeyKeyboardView` (layout / `xml/method.xml` class names)
- Room database, generated `NullKeyDatabase_Impl` (`Class.forName`), entities, and DAOs
- Enum constant names stored in Room columns, SharedPreferences, and JSON (`ClipContentType`, swipe actions, keyboard theme, sync state)

Library consumer rules (Room, Kotlin, coroutines, AndroidX) still apply on top of those.

### Residual risk

CI proves the release AAB/APK build, the mapping file, and that those entry points and enum names are present in the minified dex. It does **not** install the minified release build on a device or run instrumented tests against it (instrumented CI stays on the debug APK). Before the first Play upload, install the minified release build on a phone and exercise: enable the IME, type and swipe, open Vault, pin/protect/trash, and export/import a backup. If that pass fails, ship an unminified AAB with `-Pnullkey.releaseMinify=false` and keep the mapping from the failing build. This does not close issue #17.
