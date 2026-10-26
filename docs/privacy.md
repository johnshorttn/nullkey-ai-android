# NullKey AI Privacy Policy

**Play listing URL:** https://johnshorttn.github.io/nullkey-ai-android/privacy.html  
**Last updated: September 21, 2026**

This page is the canonical public policy. The longer draft at [PRIVACY.md](PRIVACY.md) is not the Play URL.

NullKey AI is an Android keyboard and clipboard/vault application designed around local, privacy-conscious operation.

## Keyboard and typed text

NullKey AI operates as an Android input method. Text you type is processed on the device as needed to provide keyboard functionality. NullKey AI does not sell typed text, use it for advertising, or upload keystrokes to a cloud or network service in this build.

Features that can operate locally are designed to do so. This build has no network AI. Typed text is not transmitted to an external AI, cloud, analytics, or other service.

## Clipboard and Vault

Clipboard items and Vault content are stored on the user's device as part of NullKey AI's core functionality. This build does not upload clipboard contents or Vault items to a cloud service, and it does not include cloud sync. Sensitive Vault or clipboard content is not intentionally included in diagnostics or public support reports.

Deleted clips may be retained in NullKey AI's Trash for up to 30 days so they can be restored before permanent deletion.

## OCR and language assistance

In this build, image-text recognition (OCR) and English spelling help run on the device and offline. Neither feature uploads what it reads or suggests.

**Image text (OCR).** You can extract text from an image you choose with the system file picker, or from an image already stored in the Vault. Recognition uses a Latin-script model bundled in the app. It does not download a model, and it does not use a camera permission. Latin script is a limit of this model: writing systems outside Latin script are not recognized. If recognition is unavailable on a device, NullKey says so and keeps the image on the device. There is no network or cloud fallback. Recognized text may be saved on an unprotected Vault item so you can search or paste it. Protected clips are not given a stored plaintext copy of that text. OCR text is not uploaded.

**English spelling.** Spelling suggestions compare a typed word with an English dictionary bundled in the app (about 50,000 words, roughly 456 KB uncompressed) using edit distance. That check runs offline and is not fetched over the network. It is not a full grammar checker: it does not parse sentences, judge style, or check languages other than English. A few narrow English patterns (such as a/an, a repeated word, a few contractions, and a handful of homophones) may also be flagged on the device; those checks are not a grammar engine. Password fields are not spell-checked.

## Backups and exports

NullKey AI may provide user-initiated export/import functionality. Users control the destination of manually exported data. Sensitive application data is excluded from ordinary Android cloud backup where configured by the application.

## Diagnostics and support

Diagnostic information may include items such as the NullKey version/build, Android version, device model, release channel, and technical error information. Diagnostics are designed not to include clipboard contents, typed text, passwords, Vault contents, text recognized from images, or other sensitive user content unless the user deliberately chooses to provide such information.

## Network access

This build does **not** include the `INTERNET` permission and does **not** include the `ACCESS_NETWORK_STATE` permission. It does not upload keystrokes, clipboard contents, Vault items, or OCR text. Keyboard, clipboard, Vault, image-text, and spelling features run on the device and offline. This build does not include network AI, a camera permission, or cloud sync. If a later version adds network access, this policy and the Play Data Safety form will be updated before that build is listed.

## Data sales and advertising

NullKey AI does not sell personal data. Core privacy and security protections are not conditioned on purchasing a paid upgrade.

## Security

Please do not disclose security vulnerabilities in a public issue. See the project's [security reporting instructions](https://github.com/johnshorttn/nullkey-ai-android/blob/rewrite/v2/SECURITY.md).

## Changes

This policy may be updated as NullKey AI develops. Material changes affecting how user data is handled should be reflected here before the corresponding feature is released.

## Contact and support

For support, bug reports, and feature requests, see the [NullKey AI support page](support.md).
