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

3. Build:

```bash
./gradlew :app:printReleaseSigningStatus
./gradlew :app:bundleRelease
# APK if needed:
./gradlew :app:assembleRelease
```

Outputs (git-ignored):

- `app/build/outputs/bundle/release/app-release.aab`
- `app/build/outputs/apk/release/app-release.apk`

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

The workflow decodes the keystore into the runner temp dir, sets `KEYSTORE_FILE` / `KEYSTORE_PASSWORD` / `KEY_ALIAS` / `KEY_PASSWORD`, runs `:app:bundleRelease` and `:app:assembleRelease`, verifies the APK with `apksigner`, and uploads artifacts. If `KEYSTORE_BASE64` is unset, it runs the throwaway smoke instead of failing, so the AAB path stays proven.

## Throwaway smoke (no Play key)

```bash
./scripts/check-release-scaffold.sh
./scripts/smoke-signed-aab.sh
```

This generates a 1-day PKCS12 in `$TMPDIR`, builds a signed AAB, and verifies the jar signature. **Do not upload that AAB to Play** — Play app signing is bound to the first upload key.

## Play Console upload (manual)

1. Use the **production** upload keystore, not the smoke keystore.
2. In Play Console: **Release → Production / Testing → Create release → Upload AAB**.
3. Enable **Play App Signing**. Keep the upload key; Google holds the app-signing key.
4. Complete Data Safety, IME, and privacy-policy fields using [PLAY_STORE.md](PLAY_STORE.md) and [PRIVACY.md](PRIVACY.md).
5. Publish the privacy policy at a **public HTTPS URL** (GitHub Pages or a site you control). Play will not accept a repo-only markdown file.

This repository does not call `fastlane supply`, Play Developer API, or `r0adkll/upload-google-play`.

## SDK target

`targetSdk` / `compileSdk` are **36**. R8/minify stays **off** for release so Vault/IME/Room keep current behavior. Enabling shrinking is a separate regression pass.
