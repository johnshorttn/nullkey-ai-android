# Release notes — rewrite/v2 (in progress)

These notes describe the current integration branch, not a Play production rollout. Issue #17 is **not** 100%.

## 1.2 (versionCode 12) — development

Shipped on `rewrite/v2` (and open focused PRs, unmerged):

- Custom keyboard engine with legacy `KeyboardView` fallback
- Vault: search, files filter, pin/protect/tag/trash, encrypted backup/restore
- Optional clipboard monitor and consent-gated Capture Lab
- On-device word suggestions and swipe-typing toggle
- On-device Latin OCR for Vault images (bundled ML Kit model; no `INTERNET` or `ACCESS_NETWORK_STATE`) and offline English spelling (bundled dictionary and edit distance, not a full grammar checker)
- Sync **foundation** (in-memory transport only; no production hosts)

Play/AAB path:

- Placeholder signing config, git-ignored secrets, `bundleRelease`
- Throwaway signed-AAB smoke script and CI job
- Listing / Data Safety notes, and a public privacy policy at https://johnshorttn.github.io/nullkey-ai-android/privacy.html
- Release R8 minify + resource shrinking, with keep rules for IME, Room, and persisted enum names. Debug stays unminified. Opt out with `-Pnullkey.releaseMinify=false`.

## Not in 1.2 listing

- OCR beyond bundled Latin script, and grammar beyond a small offline English rule set (see [PRIVACY.md](PRIVACY.md))
- Hosted sync, plugins, WebView settings packs
- On-device confirmation of the minified release IME (CI checks the mapping and dex, not a release install)
- Promoting `rewrite/v2` to `main` (issue #16)

## Open integration PRs (do not merge from this work)

Vault swipe cancel UX, swipe-typing polish, theme/settings, TalkBack/incognito, UI hardening/localization, plus earlier keyboard-engine / sync-engine / accented long-press tracks.
