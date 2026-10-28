package com.nullverse.nullkeyai.boot

import android.content.Context
import androidx.core.os.UserManagerCompat

/**
 * NullKey's keyboard service is `directBootAware` so it can type on the lock
 * screen after a reboot (PIN/password fields) instead of Android warning that
 * the app "can't start until you unlock your device".
 *
 * Before the first unlock, credential-encrypted (CE) storage, where the Vault,
 * its Keystore-wrapped fields, learned words, and every other private file
 * live, is unavailable. NullKey then runs in **basic mode**: layouts and theme
 * come from [BootSettingsMirror] in device-protected storage, and nothing
 * touches the Vault, learned words, clipboard history, or OCR. This is the one
 * place that question is answered.
 */
object DirectBootGate {
    fun isUserUnlocked(context: Context): Boolean = UserManagerCompat.isUserUnlocked(context)
}
