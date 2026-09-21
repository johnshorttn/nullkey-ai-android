# NullKey AI 2.0 — Product & Architecture Specification

**Status:** Baseline / implementation target  
**Branch:** `rewrite/v2`

## 1. Product Goal

NullKey AI 2.0 is a full-featured Android keyboard first, with an integrated clipboard vault, secure capture pipeline, remote-control input surface, OCR, AI tools, customization, and extensibility.

The keyboard should provide the mainstream capabilities users expect from a polished keyboard while using original NullKey branding, UI, code, and assets.

Core principles:

- Keyboard-first: the IME is a client of NullKey Core, not the entire application.
- Offline-first: core typing, Vault, search, organization, and basic suggestions work without a server/account.
- Privacy-aware: capabilities use Android-supported access paths and clearly expose their state.
- Modular: keyboard, Vault, capture, remote input, AI/OCR, settings, and plugins communicate through defined interfaces.
- Data preservation: explicit Room migrations; never depend on destructive migration for Vault data.
- Configurable: advanced behavior should be optional rather than forced.

## 2. Keyboard Foundation

Target capabilities:

- QWERTY and alternate language/layout support
- numbers and symbols
- long-press characters
- shift and caps lock
- smart punctuation
- configurable number row
- cursor movement using the spacebar
- swipe-delete by word
- keyboard height and resizing
- one-handed mode
- floating mode where practical
- haptics and sound
- configurable long-press timing
- custom rows/keys
- per-app input profiles
- incognito/private input behavior
- multilingual dictionaries and personal dictionary
- autocorrect with undo
- next-word/predictive suggestions
- glide/swipe typing

The long-term keyboard renderer should not depend on the legacy Android `Keyboard/KeyboardView` API. A dedicated renderer/input engine should own touch geometry, gestures, layouts, animations, themes, and key state.

## 3. Toolbar

The keyboard toolbar provides expandable access to:

- Clipboard / Null Vault
- Voice
- AI
- OCR / Scan Text
- Translate
- Remote Input
- snippets/templates
- Settings
- configurable shortcuts

Opening the Vault from the keyboard should provide a purpose-built Vault surface rather than simply placing a scrolling list above the keyboard.

## 4. Clipboard Capture Engine

Goal: capture clipboard contents whenever Android legitimately makes them available, including while the keyboard UI is hidden when the platform permits it.

All capture methods feed one Capture Engine.

Candidate capture sources:

- active/default IME
- foreground NullKey application
- foreground service where Android permits clipboard reads
- explicit Save to NullKey
- Android Share to NullKey
- manual capture
- supported platform integrations

Optional advanced integrations may be implemented separately where appropriate, but normal operation must not depend on bypassing Android security/privacy controls.

The architecture must not assume that an overlay grants clipboard access.

### Clipboard Capture Lab

Before relying on a capture technique, NullKey will include an opt-in diagnostic test. It asks the user for permission before running and uses generated test strings rather than recording the user's existing clipboard.

Test conditions include:

- NullKey default IME, keyboard visible
- NullKey default IME, keyboard hidden
- another app foreground
- NullKey app foreground
- foreground service
- overlay active
- relevant focus transitions where Android permits them
- locked/unlocked state where safe to test

For each condition, record:

- clipboard-change event received
- clipboard contents readable
- latency
- Android/version/device capability information
- visible interruption, if any

The diagnostics screen remains available after development so Android updates can be retested.

## 5. Null Vault

The keyboard, notification, and management application use the same Vault/repository layer.

Capture types should support text and, where Android access/persistence rules permit, images/files/URIs.

A Vault item can include:

- ID
- content type
- payload or managed attachment reference
- MIME type
- creation/update timestamps
- last-used timestamp
- source metadata when Android exposes it
- pinned state
- protected state
- trash state
- content hash/deduplication metadata
- notes
- multiple tags
- OCR/searchable text
- sync/backup metadata as needed

Use proper many-to-many tags rather than a single tag column.

### Retention

- configurable retention periods
- pinned items are exempt from automatic retention deletion
- trash has configurable expiry
- manual permanent deletion
- retention rules can eventually be assigned by tag/type/rule

### Search and organization

Search should cover:

- clip text
- tags
- notes
- OCR text
- aliases/names

Filters include recent, pinned, protected, text, images, files, tags, and saved/smart collections.

Room FTS should be evaluated for scalable full-text search.

### Sensitive content

NullKey may capture ordinary clipboard text that the user explicitly copied, including copied credential text when Android exposes it.

Do not scrape passwords from protected/masked fields or bypass secure-field protections.

Pin and Protect are separate concepts:

- **Pin:** exempt from automatic retention deletion.
- **Protect:** encrypt/restrict access and optionally require device authentication/biometrics.

Security design should use Android Keystore-backed keys and support encrypted backup/export.

## 6. Notification & Quick Access

A persistent/ongoing NullKey notification may expose:

- Open Vault
- Search Vault
- Pause/Resume capture
- Capture Now where supported
- current capture state

Clipboard contents do not need to be displayed in the notification.

A Quick Settings tile should be considered for fast Vault access and/or capture toggle.

## 7. Tags, Rules, and Smart Actions

Support multiple user-defined tags per item. Initial suggested tags may include Personal, Work, and Coding but are not hard-coded limitations.

A future Rules Engine can perform actions such as:

- apply tags based on source/content/type
- pin automatically
- protect automatically
- assign retention
- trigger allowed actions

Rules must remain understandable and user-editable.

## 8. Snippets & Templates

Support reusable snippets and templates available from the keyboard/Vault.

Potential variables:

- date
- time
- user-defined values

Custom shortcuts may expand templates directly into the active input connection.

Sensitive credentials should be referenced through protected Vault mechanisms rather than stored casually in ordinary macros.

## 9. OCR

OCR workflow:

image/camera/share -> recognize -> review/edit -> paste or save to Vault.

OCR text can optionally become searchable Vault metadata.

## 10. AI Layer

AI is optional enhancement, not a dependency for core keyboard functionality.

Provider abstraction should allow:

- local/offline providers
- OpenAI/remote providers
- additional future providers

Potential actions:

- spelling
- grammar
- rewrite
- shorten/expand
- tone changes
- summarize
- explain
- translate
- custom prompts

Selected text should be operable from the keyboard when Android's input APIs provide appropriate access.

## 11. Remote Input Mode

Remote Input is a first-class keyboard mode intended for supported terminal, remote-desktop, browser-remote, and other compatible clients.

Special keys can include:

- Esc
- Tab
- Ctrl
- Alt
- Shift
- Win/Super
- arrows
- Home/End
- Insert/Delete
- Page Up/Page Down
- F1-F12
- terminal symbols

Modifier keys support tap/latch/lock behavior as appropriate.

Per-app profiles may automatically choose Normal, Terminal, Remote Desktop, Coding, or custom layouts.

## 12. Trackpad / Virtual Mouse

Remote Input includes a dedicated Trackpad surface through an adapter-based remote input engine.

Pointer events include:

- move
- left down/up/click
- middle down/up/click
- right down/up/click
- scroll
- drag

Actual pointer injection depends on capabilities exposed by the remote client/integration; the IME must not assume universal system-pointer control.

### Portrait layout

When Trackpad Mode is active:

- keyboard key area becomes the pointing surface
- vertical sensitivity slider appears along the left edge of the pad by default
- mouse-button row appears below the pad
- mode strip appears below the mouse buttons

Conceptual layout:

```text
┌──────┬─────────────────────────────────────────┐
│ FAST │                                         │
│  ▲   │                                         │
│  │   │                                         │
│  ●   │                TRACKPAD                 │
│  │   │                                         │
│  ▼   │                                         │
│ SLOW │                                         │
├──────┴─────────────────────────────────────────┤
│   LEFT CLICK   │ MIDDLE CLICK │  RIGHT CLICK  │
├────────────────────────────────────────────────┤
│    TP ●    │    S ○    │    TB ○    │   ⚙↩   │
└────────────────────────────────────────────────┘
```

### Mouse buttons

Left, Middle, and Right are dedicated buttons.

Default interaction:

- tap Left -> left click
- tap Middle -> middle click
- tap Right -> right click
- press-and-hold a mouse button -> arm/latch touch-click for that button
- while armed, a pad tap performs the selected button click
- visual state and haptic feedback indicate armed state
- one armed button at a time by default
- advanced settings may permit additional behavior

Drag behavior should support proper button-down, movement, and button-up semantics.

### TP control

`TP` is the master Trackpad toggle.

Turning Trackpad off restores the appropriate remote keyboard surface.

### Scroll mode

`S` toggles Scroll mode.

When active:

- vertical movement -> vertical scroll
- horizontal movement -> horizontal scroll

Optional two-finger scrolling can remain available while normal Trackpad mode is active.

Press-and-hold Scroll may provide temporary scroll mode, returning to pointer movement on release.

### Trackball mode

`TB` toggles Virtual Trackball mode.

Behavior:

- flick -> pointer receives momentum
- touch pad -> catch/stop momentum
- ordinary controlled movement remains available
- adjustable speed/sensitivity
- adjustable momentum
- adjustable friction

Scroll and Trackball movement modes should not conflict; active-state rules must be explicit.

### Contextual sensitivity slider

A vertical sensitivity slider is located on the left side of the portrait pad.

Its value is contextual:

- Trackpad mode -> pointer sensitivity
- Scroll mode -> scroll sensitivity
- Trackball mode -> flick/trackball sensitivity

Each mode remembers its own value.

Possible interactions:

- live percentage while adjusting
- center/default haptic detent
- double tap -> reset current mode sensitivity

The slider can be disabled in Settings. When disabled, its area is reclaimed by the trackpad.

Portrait and landscape slider visibility may be configured independently.

### Settings / Keyboard return control

The final mode-strip control uses a combined gear/return visual, conceptually `⚙↩`.

Behavior:

- tap -> Trackpad/Remote settings
- press and hold -> return to keyboard

The first time the long-press keyboard-return action is used, show confirmation:

**Switch back to keyboard?**

Actions: Cancel / Switch.

Include **Don't ask again**. The same preference is available under Input settings.

Provide a distinct haptic confirmation when the long-press action activates.

### Landscape mode

Trackpad input is never full-width in landscape.

When first entering landscape Trackpad Mode, NullKey asks whether the compact Trackpad should dock:

- Left
- Right

The choice can be remembered.

Settings:

`Settings -> Input -> Landscape Trackpad Side`

Options:

- Ask
- Left
- Right

Landscape Trackpad width is configurable within sensible bounds to minimize obstruction of the remote application.

When docked, the trackpad, buttons, and mode controls occupy only the selected side.

The sensitivity slider follows the appropriate outer/reachable edge for the docked orientation and may be independently hidden in landscape.

## 13. Input Settings

Input settings should eventually include:

- keyboard height
- long-press timing
- haptic behavior
- per-app profiles
- gesture settings
- Trackpad sensitivity
- Scroll sensitivity
- Trackball sensitivity
- Trackball momentum
- Trackball friction
- show portrait sensitivity slider
- show landscape sensitivity slider
- landscape Trackpad side: Ask/Left/Right
- landscape Trackpad width
- confirm Trackpad-to-keyboard switch
- tap-to-click (optional)
- two-finger scrolling
- reverse/natural scrolling
- mouse-button latch behavior

## 14. Customization & Plugins

Support original NullKey themes and configurable keyboard layouts.

Future plugin architecture should favor constrained, permission-aware extension points rather than arbitrary untrusted code loaded into the primary IME process.

Potential extension points:

- toolbar actions
- Vault actions
- transformations
- AI providers
- remote adapters
- themes
- rules/actions

## 15. Cross-Device Sync & Web Vault

NullKey remains offline-first: the local Vault works without an account or network connection. Sync is an optional layer over the local repository.

Every synchronizable record uses a stable UUID that is independent of the local Room primary key. Synchronization metadata includes revision, originating device, last modifying device, sync state, and deletion tombstones. Attachments such as images/files are synchronized as managed encrypted assets rather than database BLOBs.

The sync engine is provider-independent. Potential transports include a NullKey-hosted API, user-controlled server/WebDAV-compatible storage, and future direct device integrations.

Security principles:

- encrypt Vault payloads/attachments before remote storage where practical
- never rely on transport encryption alone for protected Vault content
- allow sensitive/protected items to be excluded from synchronization
- support device enrollment and revocation
- retain tombstones long enough to prevent deleted records being resurrected by an offline device
- expose conflicts rather than silently destroying competing edits

The web portal is a first-class NullKey client. Subject to authentication, enrollment, and Vault protection rules it can:

- search/browse the Vault
- copy text
- preview images and download files
- edit notes
- manage tags, pin/protect state, trash and restore
- create browser-originated clips that synchronize to enrolled devices
- display capture provenance/device information
- show sync/device status
- revoke devices and sessions

The portal/server must not require the Android keyboard to be online for local keyboard or Vault operation.

## 16. Suggested Architecture

Initial logical boundaries:

```text
NullKey Core
├── model
├── database
├── settings
├── security
├── capture
└── common

Keyboard
├── IME shell
├── renderer
├── touch engine
├── layouts
├── suggestions
├── autocorrect
├── gestures/glide
├── toolbar
└── profiles

Vault
├── repository
├── search
├── tags
├── retention
├── protection
└── backup

Remote
├── RemoteInputEngine
├── TrackpadEngine
├── ModifierManager
├── profiles
└── adapters

AI
├── provider API
├── local providers
└── remote providers

Features
├── OCR
├── notifications
├── snippets
├── themes
└── diagnostics
```

These may begin as package boundaries and become separate Gradle modules when justified.

## 17. Migration from NullKey 1.x

The existing implementation remains available on `main` while 2.0 is developed on `rewrite/v2`.

Reusable concepts include:

- Kotlin/coroutines
- Room
- repository pattern
- JSON backup concepts
- local suggestion learning
- package/application identity
- behavioral tests that still represent desired functionality

Rewrite/refactor targets include:

- legacy Keyboard/KeyboardView renderer
- monolithic IME responsibilities
- monolithic MainActivity responsibilities
- clipboard capture architecture
- single-tag data model
- settings architecture
- database migration policy

A migration from the existing `nullkey.db` should preserve existing clips wherever practical.

## 18. Initial Implementation Order

1. Preserve baseline and establish 2.0 architecture/package boundaries.
2. Inspect and preserve useful existing tests.
3. Build Clipboard Capture Lab and collect real-device capability results.
4. Define/migrate Vault schema with explicit Room migrations.
5. Establish Settings/DataStore and Security foundations.
6. Implement new keyboard renderer/touch engine and core typing.
7. Add suggestion/autocorrect abstraction and migrate local learning.
8. Implement toolbar and integrated Vault surface.
9. Implement Remote Input and Trackpad prototype according to this spec.
10. Add glide typing, OCR, AI providers, rules, advanced customization, and plugins incrementally.

## 19. Acceptance Principle

A feature is not considered complete merely because its UI exists. Each subsystem must expose real capability/state, degrade cleanly when Android or a remote client does not support an operation, preserve user data, and avoid claiming access that the platform has not actually granted.

## 20. Play Store / signed AAB

Release signing, AAB smoke, listing notes, and the privacy-policy draft live next to this spec:

- [RELEASE_AAB.md](RELEASE_AAB.md) — keystore placeholders, env vars, CI, no secrets in git
- [PLAY_STORE.md](PLAY_STORE.md) — listing copy, Data Safety, IME / FGS declarations
- [PRIVACY.md](PRIVACY.md) — policy draft matching the shipping offline defaults
- [RELEASE_NOTES.md](RELEASE_NOTES.md) — what 1.2 actually contains

Do not treat a signed AAB as Play-submittable until `targetSdk` meets the current Console floor (API 36 for phone listings after 31 August 2026) and issue #17 remaining product scope is either shipped or explicitly deferred.
