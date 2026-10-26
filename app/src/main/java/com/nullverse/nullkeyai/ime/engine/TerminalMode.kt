package com.nullverse.nullkeyai.ime.engine

import android.text.InputType

/**
 * Terminal mode: in SSH/terminal apps and raw input fields the keyboard shows
 * the terminal row and stops acting like a prose keyboard (no suggestions,
 * no learning, no swipe and so no auto-space), and Ctrl+A goes through.
 * Detection only reads the [android.view.inputmethod.EditorInfo] the system
 * hands every IME; nothing is queried or sent anywhere.
 */
object TerminalMode {
    val TERMINAL_PACKAGES: Set<String> = setOf(
        "com.termux",
        "com.sonelli.juicessh",
        "org.connectbot",
    )

    fun isTerminalPackage(packageName: String?): Boolean = packageName in TERMINAL_PACKAGES

    /** Raw key views (TYPE_NULL) and visible-password fields that ask for no text processing. */
    fun isRawInputType(inputType: Int): Boolean {
        val cls = inputType and InputType.TYPE_MASK_CLASS
        if (cls == InputType.TYPE_NULL) return true
        return cls == InputType.TYPE_CLASS_TEXT &&
            (inputType and InputType.TYPE_MASK_VARIATION) == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
    }

    fun detect(packageName: String?, inputType: Int, enabled: Boolean): Boolean =
        enabled && (isTerminalPackage(packageName) || isRawInputType(inputType))
}
