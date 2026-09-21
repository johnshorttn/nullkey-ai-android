# NullKey AI (Android)

Offline-first clipboard keyboard with a private on-device vault.

**Branch:** `rewrite/v2` is the integration target.  
**Package:** `com.nullverse.nullkeyai`  
**Version:** 1.2 (versionCode 12)

> Issue #17 release-ready work is **not** complete. Open product slices and Play target-API work remain. Do not treat this tree as 100%.

## Privacy defaults

- No `INTERNET` permission. Typing, suggestions, and Vault stay on-device.
- Optional clipboard monitor uses a special-use foreground service when the user starts it.
- Protected clips use Android Keystore-backed AES-GCM. Encrypted backups are user-exported files.
- Sync in this tree is a local/in-memory foundation. No production hosts or API keys.

Public privacy policy (Play IME listing): **https://johnshorttn.github.io/nullkey-ai-android/privacy.html**

Source: [docs/privacy.md](docs/privacy.md). GitHub Pages already publishes the `docs/` folder from `rewrite/v2`. The URL is HTTPS today; the page text updates when `docs/privacy.md` merges to that branch. If Pages is turned off later: repository **Settings → Pages → Deploy from a branch → `rewrite/v2` → `/docs`**.

## Build

```bash
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
```

Signed Play bundle (requires a local, git-ignored `keystore.properties` — copy [keystore.properties.example](keystore.properties.example)). Release runs R8 minify and resource shrinking; debug does not:

```bash
./gradlew :app:checkReleaseScaffold
./gradlew :app:bundleRelease
```

Unminified diagnostic AAB: `./gradlew :app:bundleRelease -Pnullkey.releaseMinify=false`.

Throwaway signed-AAB smoke (do **not** upload to Play):

```bash
./scripts/smoke-signed-aab.sh
```

Full AAB / CI / secret setup: [docs/RELEASE_AAB.md](docs/RELEASE_AAB.md).  
Play Console listing and Data Safety notes: [docs/PLAY_STORE.md](docs/PLAY_STORE.md).  
Architecture target: [docs/NULLKEY_AI_2_SPEC.md](docs/NULLKEY_AI_2_SPEC.md).  
Current rewrite notes: [docs/RELEASE_NOTES.md](docs/RELEASE_NOTES.md).

## CI

Pull requests run GitHub Actions **Android CI**: debug assemble, JVM unit tests, emulator instrumented tests on **API 34** and **API 36** (`google_apis` x86_64, KVM), plus a throwaway-signed AAB smoke. The **Release (signed)** workflow builds a production AAB only when repository secrets are configured; it never reads keystores from git.

Instrumented tests locally (boot a Google APIs emulator first; API 36 matches `compileSdk` / `targetSdk`):

```bash
./gradlew :app:connectedDebugAndroidTest
```
