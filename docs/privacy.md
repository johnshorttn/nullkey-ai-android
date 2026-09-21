---
title: Privacy Policy
---

# NullKey AI Privacy Policy

**Product:** NullKey AI for Android (`com.nullverse.nullkeyai`)  
**Public URL:** https://johnshorttn.github.io/nullkey-ai-android/privacy.html  
**Last updated:** 21 September 2026  
**Applies to:** the app built from the `rewrite/v2` line of this repository (version 1.2, versionCode 12).

This policy describes the shipping keyboard and Vault. On-device OCR, spelling or grammar assistance, hosted sync, plugins, and any feature that would add the `INTERNET` permission are **not** in this build and are not covered as if they were.

## Who we are

NullKey AI is an Android input method (keyboard) and a clipboard vault that stays on the device. There is no required account.

## Data we do not collect

This app does **not** include the `INTERNET` permission. It does not include advertising, analytics, crash-reporting, or social SDKs. NullKey does **not**:

- send keystrokes, clipboard contents, or Vault items to a NullKey server or any other server
- create a user account or profile
- show ads
- sell or share personal information with third parties

There is no production sync host in this build. Sync code in the repository is an offline foundation and does not transmit Vault data.

Google Play may process install and purchase metadata on its own terms when you download the app from Play. That processing is outside this app.

## Data stored only on your device

Depending on how you use NullKey, the app may store **on this device**:

- **Vault clips** (text and, when you save them, images or files), tags, notes, and pin, protect, or trash state
- **Keyboard suggestions** (word frequencies in private app storage)
- **Settings** (for example swipe-typing and swipe-action preferences)
- **Android Keystore keys** used to encrypt protected Vault fields
- **Optional clipboard-monitor** notification state, if you start the monitor

Uninstalling NullKey removes app-private storage. Files you explicitly export remain wherever you saved them.

## Keyboard (input method)

NullKey can read text in the focused field only as needed to compose, suggest, and delete, using the Android Input Method Framework. Suggestions are generated on-device. This build does not transmit what you type.

Enabling a third-party keyboard is a sensitive system setting. Android shows its own warnings. You can switch keyboards in system settings at any time.

## Clipboard and Vault

Clipboard access follows Android rules. Capture sources include:

- NullKey as the current keyboard
- the NullKey app in the foreground
- an optional foreground-service monitor **you start**
- explicit **Capture clip**

Android may refuse clipboard reads when another app is in the foreground or when the keyboard is hidden. NullKey does not bypass those controls.

**Clipboard Capture Lab** runs only after consent and only when developer options are on. It places a generated test string on the clipboard, checks whether Android delivers and allows a read, then restores the previous clipboard when it can. It does not display, log, or persist your existing clipboard contents. The current clip may be held briefly in memory so it can be restored.

Deleted clips can remain in Trash until you restore or permanently delete them.

## Protected clips, export, and backup

Protected Vault payloads are encrypted with AES-GCM using a key in the Android Keystore on this device. That key is not exported. Device-bound protected items are not restored from a plain JSON export.

**JSON export** is user-initiated. Unprotected clip text is written to a file you choose. Protected payloads stay ciphertext. Export is not Android Auto Backup, and NullKey does not upload the file.

**Secure Backup / Restore** writes or reads an encrypted file you choose. You create the password. NullKey does not upload that file.

Android Auto Backup is **off** (`android:allowBackup="false"`). Backup rules also exclude the database, shared preferences, and vault files from device transfer. Treat any file you export yourself as your copy, not a NullKey cloud.

## Permissions

- **Input method** — so NullKey can be selected as a keyboard
- **Notifications** — clipboard monitor status, if you start the monitor
- **Foreground service (special use)** — optional clipboard capture while Android permits it

No camera, microphone, contacts, location, or network permission is declared.

## Not in this version

This policy does not describe on-device OCR, spelling or grammar assistance, plugin loading, or a hosted sync service, because those features are not in this build. If a later version adds them without network access, this policy will be updated to match. If a later version adds network access, this policy and the Play Data Safety form will be updated **before** that build is listed. Adding `INTERNET` would be a product change, not a silent default.

## Children

NullKey is not directed at children. Clipboard contents can be sensitive. We recommend adult users.

## Changes

Material changes to how the app handles data will be reflected in this page before the corresponding build is listed on Play. The source text in the repository is [docs/privacy.md](https://github.com/johnshorttn/nullkey-ai-android/blob/rewrite/v2/docs/privacy.md).

## Contact

- Support, bug reports, and feature requests: [NullKey AI support](support.html)
- Security vulnerabilities: [private GitHub advisory](https://github.com/johnshorttn/nullkey-ai-android/security/advisories/new) (do not file these as public issues)
- Play listing contact email, once the app is listed, is set in Play Console

Do not include clipboard contents, passwords, typed text, or Vault data in a public report.
