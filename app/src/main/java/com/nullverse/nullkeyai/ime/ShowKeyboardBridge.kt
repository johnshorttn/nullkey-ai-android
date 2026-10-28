package com.nullverse.nullkeyai.ime

import android.os.Build
import android.text.InputType
import android.view.inputmethod.EditorInfo
import java.lang.ref.WeakReference

/**
 * In-process link from the notification's "Show keyboard" action to the
 * running [NullKeyImeService]. Both live in the app process, so no exported
 * receiver, broadcast or permission is involved.
 */
object ShowKeyboardBridge {

    /** Implemented by the running IME. */
    fun interface Target {
        /** Show the keyboard as soon as a text field is bound, or explain why not. */
        fun showWhenEditorReady()
    }

    @Volatile private var target: WeakReference<Target>? = null

    fun attach(t: Target) { target = WeakReference(t) }

    fun detach(t: Target) {
        if (target?.get() === t) target = null
    }

    fun current(): Target? = target?.get()

    /** What the helper button should do for the current setup. */
    enum class Outcome {
        /** NullKey is the current keyboard and running: ask it to show itself. */
        SHOW_SELF,
        /** NullKey is enabled but another keyboard is current: open the system picker. */
        PICKER,
        /** NullKey is not enabled yet: open Android's keyboard settings. */
        ENABLE_IN_SETTINGS,
    }

    enum class Mode { SHOW, SWITCH }

    fun decide(mode: Mode, enabled: Boolean, selected: Boolean, imeRunning: Boolean): Outcome = when {
        !enabled -> Outcome.ENABLE_IN_SETTINGS
        mode == Mode.SWITCH -> Outcome.PICKER
        !selected -> Outcome.PICKER
        // Selected but not bound yet (just switched, or the system restarted it):
        // the picker is the only legitimate way to make Android bind it again.
        !imeRunning -> Outcome.PICKER
        else -> Outcome.SHOW_SELF
    }

    /**
     * True when [info] is a real text field the keyboard can type into. The
     * helper's own trampoline window has no editor (TYPE_NULL), so it never
     * counts.
     */
    fun isTextEditor(info: EditorInfo?): Boolean =
        info != null && info.inputType != InputType.TYPE_NULL

    /** requestShowSelf exists from API 28; older releases use the IMM token call. */
    fun usesRequestShowSelf(sdkInt: Int = Build.VERSION.SDK_INT): Boolean = sdkInt >= Build.VERSION_CODES.P
}
