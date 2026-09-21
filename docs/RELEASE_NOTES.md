# Release notes — rewrite/v2 (in progress)

These notes describe the current integration branch, not a Play production rollout. Issue #17 is **not** 100%.

## 1.2 (versionCode 12) — development

Shipped on `rewrite/v2` (and open focused PRs, unmerged):

- Custom keyboard engine with legacy `KeyboardView` fallback
- Vault: search, files filter, pin/protect/tag/trash, encrypted backup/restore
- Optional clipboard monitor and consent-gated Capture Lab
- On-device word suggestions and swipe-typing toggle
- Sync **foundation** (in-memory transport only; no production hosts)

Play/AAB path (this documentation slice):

- Placeholder signing config, git-ignored secrets, `bundleRelease`
- Throwaway signed-AAB smoke script and CI job
- Listing / Data Safety / privacy drafts

## Not in 1.2 listing

- On-device OCR / image text in Vault
- Privacy-first spelling/grammar assistance
- Hosted sync, plugins, WebView settings packs
- `targetSdk` 36 (required for new Play phone uploads after 31 August 2026)

## Open integration PRs (do not merge from this work)

Vault swipe cancel UX, swipe-typing polish, theme/settings, TalkBack/incognito, UI hardening/localization, plus earlier keyboard-engine / sync-engine / accented long-press tracks.
