# Play Store listing notes (NullKey AI)

Draft copy and Console checklist for issue #17. **Not an upload.** Values match the shipping `rewrite/v2` tree, not the full 2.0 spec. OCR, cloud AI, plugins, and hosted sync are **not** in this listing yet.

Package: `com.nullverse.nullkeyai`  
Version in Gradle: **1.2** / versionCode **12**  
Default language: English (US)

## Store listing

**App name (30):** NullKey AI

**Short description (80):** Offline clipboard keyboard with a private on-device vault.

**Full description (draft):**

NullKey AI is a privacy-first Android keyboard with a local clipboard vault.

Type on a QWERTY keyboard with symbols, long-press characters, optional swipe typing, and on-device word suggestions. Save clips to Null Vault — search, pin, protect, tag, and restore from Trash. Protected items use the Android Keystore on this device. Encrypted backups are files you export; NullKey does not upload them.

Clipboard capture uses Android-supported paths only: the keyboard, the NullKey app, an optional monitor you start, and explicit capture. A consent-gated Clipboard Capture Lab uses generated test strings, not your real clipboard.

NullKey AI does not include an account, ads, analytics, or INTERNET permission. Suggestions and Vault data stay on the device. Optional sync in this release is a local foundation only.

The voice of your vault. The memory of your mind.

**Category:** Productivity (alternate: Tools)  
**Tags:** keyboard, clipboard, privacy, offline, vault  
**Contact email:** set in Play Console (developer account).  
**Privacy policy URL:** required for IME apps. Host [PRIVACY.md](PRIVACY.md) on HTTPS before review.

## Graphics checklist

Play needs these assets (not generated in this repo):

- High-res icon 512×512 PNG (32-bit)
- Feature graphic 1024×500
- Phone screenshots (at least 2): setup / enable keyboard, IME composing, Vault list, protected clip, encrypted backup
- Optional 7-inch / 10-inch tablet screenshots
- Promo video optional

Use current dark Vault UI. Do not screenshot other people’s clipboard contents.

## Data safety (Play form)

Google’s “collected” means data sent off the device. This app has **no INTERNET permission** and no third-party SDKs that transmit user content.

| Question | Answer |
| --- | --- |
| Does the app collect or share user data? | **No** (nothing leaves the device via this app) |
| Data shared with third parties | No |
| Data collected | No |
| Encryption in transit | Not applicable (no network) |
| Users can request deletion | Not applicable (no account / no server). Uninstall removes local Vault. Encrypted backup files the user exported are under their control. |
| Independent security review | No |

On-device (not reported as “collected” on Data Safety, but disclose in the privacy policy):

- Vault clips, tags, notes, and optional image/file attachments
- Keyboard suggestion frequencies in private SharedPreferences
- Device-local Keystore keys for protected clips
- Optional clipboard monitor notification state

If `INTERNET` is added later, this form must be redone before release.

## App content / policy

| Topic | Notes |
| --- | --- |
| Ads | No |
| In-app purchases | No |
| Target audience | 18+ recommended (clipboard can contain sensitive text). Content rating questionnaire: IME + user-generated clipboard. |
| News | No |
| COVID | No |
| Data-safety declared | See table above |
| **Input method** | Yes. Play requires a privacy policy and must not send keystrokes off-device without prominent disclosure. This build does not. |
| Foreground service | **Special use** — clipboard capture while permitted. Console declaration: “Clipboard capture for the NullKey clipboard keyboard when the user starts the monitor.” Matches `AndroidManifest` `PROPERTY_SPECIAL_USE_FGS_SUBTYPE`. |
| Photos / videos | User may store images in Vault via capture/share if the platform provides them. All local. No broad photo-grid permission in the manifest today. |
| Notifications | `POST_NOTIFICATIONS` for the optional monitor. |
| Accessibility | IME; TalkBack per-key virtual views and spoken labels are in this tree. |

## Technical Console fields

| Field | Current tree | Play (as of 2026-08-31) |
| --- | --- | --- |
| `minSdk` | 24 | OK |
| `targetSdk` / `compileSdk` | **36** | Meets the 2026-08-31 phone/tablet floor. |
| AAB | `./gradlew :app:bundleRelease` | Required (not APK) |
| Play App Signing | Upload key from `docs/RELEASE_AAB.md` | Required |
| R8 | Off | Allowed; keep off until a minify regression pass |
| Native 16 KB | No `.so` files | N/A |

## Permissions to declare in Console

From `app/src/main/AndroidManifest.xml`:

- `FOREGROUND_SERVICE`
- `FOREGROUND_SERVICE_SPECIAL_USE`
- `POST_NOTIFICATIONS`
- IME `BIND_INPUT_METHOD` (system; granted when the user enables the keyboard)

No `INTERNET`, `CAMERA`, `RECORD_AUDIO`, `READ_CONTACTS`, or location.

## Release hygiene

1. `./scripts/check-release-scaffold.sh`
2. `./scripts/smoke-signed-aab.sh` (throwaway; not for Console)
3. Production `bundleRelease` with the upload keystore
4. Confirm Data Safety still matches the merged manifest (no new network libraries)
5. Do not upload CI smoke artifacts

## Intentionally not claimed in this listing

On-device OCR, spelling/grammar cloud or ML Kit, plugin `.jar` loading, WebView settings, hosted sync, and Play upload automation. See issue #17 remaining scope and [RELEASE_NOTES.md](RELEASE_NOTES.md).
