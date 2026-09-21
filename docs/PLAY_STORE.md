# Play Store listing notes (NullKey AI)

Draft copy and Console checklist for issue #17. **Not an upload.** Values match the shipping `rewrite/v2` tree, not the full 2.0 spec. Cloud AI, plugins, and hosted sync are **not** in this listing yet. On-device Latin OCR and offline English spelling are in the build; refresh the store description before any upload.

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
**Privacy policy URL:** https://johnshorttn.github.io/nullkey-ai-android/privacy.html

That HTTPS page is already live. Paste it into Play Console. Prefer it over the draft page [PRIVACY.md](PRIVACY.md) (`/PRIVACY` on the same site). Source: [privacy.md](privacy.md).

## Graphics checklist

Play needs these assets (not generated in this repo):

- High-res icon 512×512 PNG (32-bit)
- Feature graphic 1024×500
- Phone screenshots (at least 2): setup / enable keyboard, IME composing, Vault list, protected clip, encrypted backup
- Optional 7-inch / 10-inch tablet screenshots
- Promo video optional

Use current dark Vault UI. Do not screenshot other people’s clipboard contents.

## Data safety (Play form)

Google’s “collected” means data sent off the device. This app has **no `INTERNET` or `ACCESS_NETWORK_STATE` permission**. ML Kit Latin OCR is bundled in the APK; its Clearcut uploader is not packaged, and both network permissions are stripped from the merged manifest, so it cannot transmit images, keystrokes, or OCR text. Spelling is a bundled dictionary and edit distance, not a full grammar checker. There is no network AI, camera permission, or cloud sync.

| Question | Answer |
| --- | --- |
| Does the app collect or share user data? | **No** (nothing leaves the device via this app) |
| Data shared with third parties | No |
| Data collected | No |
| Encryption in transit | Not applicable (no network) |
| Users can request deletion | Not applicable (no account / no server). Uninstall removes local Vault. Encrypted backup files the user exported are under their control. |
| Independent security review | No |

On-device (not reported as “collected” on Data Safety, but disclose in the privacy policy):

- Vault clips, tags, notes, optional image/file attachments, and text extracted from images you scan
- Keyboard suggestion frequencies in private SharedPreferences, plus a bundled English spelling list (not user content)
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
| R8 | **On** for release (minify + resource shrink). Debug stays off. Opt out: `-Pnullkey.releaseMinify=false`. | Upload `mapping.txt` with the AAB. See [RELEASE_AAB.md](RELEASE_AAB.md). |
| Native 16 KB | Bundled OCR ships `libmlkit_google_ocr_pipeline.so` (ELF LOAD align 16 KB / `0x4000`) | Confirm Play’s 16 KB check on the AAB before upload. |

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
3. Production `bundleRelease` with the upload keystore (R8 on) and upload `mapping.txt` alongside the AAB
4. Confirm Data Safety still matches the merged manifest (no new network libraries)
5. Do not upload CI smoke artifacts

## Intentionally not claimed in this listing

Cloud OCR, unbundled ML Kit model download, full grammar parsing, plugin `.jar` loading, WebView settings, hosted sync, and Play upload automation. Latin on-device OCR and the bundled English word list are in this tree. See issue #17 remaining scope and [RELEASE_NOTES.md](RELEASE_NOTES.md).


## Permanent staged release pipeline

NullKey uses this release progression:

**Development → Internal Testing → Beta → Production**

- Development happens on `rewrite/v2` and must pass Android CI.
- Internal Testing receives the first production-signed candidate.
- Beta is a permanent opt-in channel. New feature releases should spend time in Beta before Production.
- Production is never automatic. It requires John's explicit owner approval.
- Prefer promoting the **same tested AAB** from Beta to Production instead of rebuilding it.
- Critical stable hotfixes may use an expedited Internal → Production path after green CI and explicit owner approval.
- `.github/workflows/play-staged-release.yml` builds the signed immutable candidate and enforces an explicit production approval phrase. Until Play Developer API credentials are intentionally configured, upload/promotion remains a controlled Play Console action.
- Public privacy policy: https://johnshorttn.github.io/nullkey-ai-android/privacy.html
- Support: https://johnshorttn.github.io/nullkey-ai-android/support.html
- Beta information: https://johnshorttn.github.io/nullkey-ai-android/beta.html
