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

See [docs/PRIVACY.md](docs/PRIVACY.md).

## Build

```bash
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
```

Signed Play bundle (requires a local, git-ignored `keystore.properties` — copy [keystore.properties.example](keystore.properties.example)):

```bash
./gradlew :app:checkReleaseScaffold
./gradlew :app:bundleRelease
```

Throwaway signed-AAB smoke (do **not** upload to Play):

```bash
./scripts/smoke-signed-aab.sh
```

Full AAB / CI / secret setup: [docs/RELEASE_AAB.md](docs/RELEASE_AAB.md).  
Play Console listing and Data Safety notes: [docs/PLAY_STORE.md](docs/PLAY_STORE.md).  
Architecture target: [docs/NULLKEY_AI_2_SPEC.md](docs/NULLKEY_AI_2_SPEC.md).  
Current rewrite notes: [docs/RELEASE_NOTES.md](docs/RELEASE_NOTES.md).

## CI

Pull requests run GitHub Actions **Android CI**: debug assemble, JVM unit tests, emulator instrumented tests (API 34), plus a throwaway-signed AAB smoke. The **Release (signed)** workflow builds a production AAB only when repository secrets are configured; it never reads keystores from git.
