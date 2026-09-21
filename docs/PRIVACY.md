# Privacy policy (draft) — NullKey AI

**Product:** NullKey AI for Android (`com.nullverse.nullkeyai`)  
**Status:** Longer draft. Do not paste this page into Play Console.  
**Canonical Play URL:** https://johnshorttn.github.io/nullkey-ai-android/privacy.html  
**Last updated:** 21 September 2026  
**Applies to:** the `rewrite/v2` app as built from this repository.

This policy describes the shipping keyboard and Vault. Features listed as deferred in issue #17 (on-device OCR, network AI, hosted sync, plugins) are **not** covered because they are not in this build.

## Who we are

NullKey AI is an Android input method and clipboard vault. There is no required account.

## Data we do not collect

The app does **not** include the `INTERNET` permission and does not include advertising, analytics, crash-reporting, or social SDKs. We do **not**:

- send keystrokes, clipboard contents, or Vault items to a NullKey server
- create a user account or profile
- show ads
- sell or share personal information with third parties

Google Play may process install and purchase metadata on its own terms when you download the app from Play. That is outside this app process.

## Data stored only on your device

Depending on how you use NullKey, the app may store **on this device**:

- **Vault clips** (text and, when you save them, images/files), tags, notes, pin/protect/trash state
- **Keyboard suggestions** (word frequencies in private app storage)
- **Settings** (for example swipe-typing and swipe-action preferences)
- **Android Keystore keys** used to encrypt protected Vault fields
- **Optional clipboard monitor** notification state if you start it

Uninstalling NullKey removes app-private storage. Files you explicitly exported (JSON or encrypted backup) remain wherever you saved them.

## Keyboard (input method)

NullKey can read text in the focused field only as needed to compose, suggest, and delete, using the Android Input Method Framework. Suggestions are generated on-device. This build does not transmit what you type.

Enabling a third-party keyboard is a sensitive system setting. Android will show its own warnings. You can switch keyboards in system settings at any time.

## Clipboard capture

Clipboard access follows Android rules. Capture sources include:

- NullKey as the current IME
- the NullKey app in the foreground
- an optional foreground-service monitor **you start**
- explicit **Capture clip**

Android may refuse clipboard reads when another app is in the foreground or when the keyboard is hidden. NullKey does not bypass those controls.

**Clipboard Capture Lab** runs only after consent. It places a generated test string on the clipboard, checks whether Android delivers and allows a read, then restores the previous clipboard when it can. It does not display, log, or persist your existing clipboard contents (the current clip may be held briefly in memory for restore).

## Protected clips and backups

Protected Vault payloads are encrypted with AES-GCM using a key in Android Keystore on this device. Device-bound protected items may not restore on another phone from a plain JSON export.

**Secure Backup / Restore** writes or reads an encrypted file you choose. You create the password. NullKey does not upload that file.

Android Auto Backup is off (`android:allowBackup="false"`). Backup rules also exclude the database, preferences, and vault files. Treat any file you export yourself as your copy, not a NullKey cloud.

## Permissions

- **Input method** — so NullKey can be selected as a keyboard
- **Notifications** — clipboard monitor status, if you start the monitor
- **Foreground service (special use)** — optional clipboard capture while Android permits it

No camera, microphone, contacts, location, or network permission is declared.

## Children

NullKey is not directed at children. Clipboard contents can be sensitive; we recommend adult users.

## Changes

If a future version adds network features, this policy and Play Data Safety will be updated **before** that build is listed. Adding `INTERNET` is a product change, not a silent default.

## Contact

Use the developer email on the Play Store listing, or open an issue on the public GitHub repository for this app.
