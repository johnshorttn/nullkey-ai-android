# Privacy policy (draft) — NullKey AI

**Product:** NullKey AI for Android (`com.nullverse.nullkeyai`)  
**Status:** Longer draft. Do not paste this page into Play Console.  
**Canonical Play URL:** https://johnshorttn.github.io/nullkey-ai-android/privacy.html  
**Last updated:** 21 September 2026  
**Applies to:** the `rewrite/v2` app as built from this repository.

This policy describes the shipping keyboard and Vault. Hosted sync, network AI, and plugins are **not** in this build. On-device image text and offline English spelling are.

## Who we are

NullKey AI is an Android input method and clipboard vault. There is no required account.

## Data we do not collect

The app does **not** include the `INTERNET` permission or the `ACCESS_NETWORK_STATE` permission, and it does not include advertising, analytics, crash-reporting, or social SDKs. We do **not**:

- send keystrokes, clipboard contents, Vault items, or OCR text to a NullKey server or any other cloud service
- upload images chosen for text recognition
- create a user account or profile
- show ads
- sell or share personal information with third parties
- provide network AI, a camera permission, or cloud sync in this build

Google Play may process install and purchase metadata on its own terms when you download the app from Play. That is outside this app process.

## Data stored only on your device

Depending on how you use NullKey, the app may store **on this device**:

- **Vault clips** (text and, when you save them, images/files), tags, notes, pin/protect/trash state
- **Keyboard suggestions** (word frequencies in private app storage) and a bundled English word list used for spelling
- **Text recognized from images you choose to scan**, stored on the Vault row when that clip is not protected
- **Settings** (for example swipe-typing and swipe-action preferences)
- **Android Keystore keys** used to encrypt protected Vault fields
- **Optional clipboard monitor** notification state if you start it

Uninstalling NullKey removes app-private storage. Files you explicitly exported (JSON or encrypted backup) remain wherever you saved them.

## Keyboard (input method)

NullKey can read text in the focused field only as needed to compose, suggest, and delete, using the Android Input Method Framework. Word completions and spelling suggestions are generated on-device from a bundled English dictionary (about 50,000 words, roughly 456 KB uncompressed in the app) using edit distance. That help is offline. It is not a full grammar checker: it does not parse sentences or cover languages other than English. A few narrow English patterns may be flagged locally; those are not a grammar engine. This build does not transmit what you type. Password fields are not spell-checked.

Enabling a third-party keyboard is a sensitive system setting. Android will show its own warnings. You can switch keyboards in system settings at any time.

## Keyboard after a restart (before first unlock)

After the phone restarts, Android keeps most app data encrypted until you unlock the device once. NullKey's keyboard service is marked *direct-boot aware*, so it can still type on the lock screen (for example your PIN or password) instead of Android warning that the app "can't start until you unlock your device."

Until that first unlock the keyboard runs in **basic mode**:

- Layout, theme, height, vibration/sound, and similar look-and-feel settings come from a small copy kept in Android's *device-protected* storage. That copy contains **only** those non-sensitive settings. It never contains Vault items, clipboard data, learned words, encryption keys, passwords, incognito or developer settings.
- The **Vault is not opened** and no clipboard history is shown or captured.
- **No learning:** typed words are not added to suggestions, and your learned-word list is not read. Only the bundled English word list (part of the app) is used.
- **No OCR**, and **nothing is written** to the encrypted (credential-protected) storage.
- Settings cannot be changed in basic mode.

When you unlock, the keyboard switches to full mode in place (no restart) and the Vault, learned words, and your other settings become available as usual. Vault encryption keys stay in Android Keystore and the encrypted storage; none are moved to device-protected storage. No permission is added for this, and NullKey still has no `INTERNET` permission and no accessibility service.

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

No camera, microphone, contacts, location, `INTERNET`, or `ACCESS_NETWORK_STATE` permission is declared. Image text uses a file you already have. It does not open the camera.

## Image text (on-device OCR)

You can scan an image with the system file picker, or open an image already in the Vault, and extract text on this device. This runs offline. NullKey uses a Latin-script recognition model that ships inside the app (native library `libmlkit_google_ocr_pipeline.so`, about 6.5–11 MB depending on CPU type; Play delivers one type). Latin script is a limit: writing systems outside Latin script are not recognized. The model is not downloaded, and the image and recognized text are not uploaded. The app does not request `INTERNET` or `ACCESS_NETWORK_STATE`, and there is no cloud or network-AI fallback.

Recognized text is saved on the Vault item so search and the keyboard can paste it. Protected clips are not given a plaintext copy of that text. If recognition is unavailable on a device, NullKey says so and keeps the image local.

The Play listing page is [privacy.md](privacy.md), published as [https://johnshorttn.github.io/nullkey-ai-android/privacy.html](https://johnshorttn.github.io/nullkey-ai-android/privacy.html) when GitHub Pages rebuilds `rewrite/v2` `/docs`. Keep that URL. This file is the longer draft. Do not paste it into Play Console.

## Children

NullKey is not directed at children. Clipboard contents can be sensitive; we recommend adult users.

## Changes

If a future version adds network features, this policy and Play Data Safety will be updated **before** that build is listed. Adding `INTERNET` is a product change, not a silent default.

## Contact

Use the developer email on the Play Store listing, or open an issue on the public GitHub repository for this app.
