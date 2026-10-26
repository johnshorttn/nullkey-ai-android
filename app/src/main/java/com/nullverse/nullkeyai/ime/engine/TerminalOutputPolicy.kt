package com.nullverse.nullkeyai.ime.engine

import android.text.InputType

/** How terminal keys and Ctrl/Alt/Meta chords leave the keyboard. */
enum class TerminalKeyOutput {
    /** Real Android key events with meta state (default; what Termux expects). */
    KEY_EVENTS,

    /** Escape sequences as text, only while terminal mode is on. */
    ESCAPES_IN_TERMINAL_MODE,

    /** Escape sequences as text everywhere, for web terminals in a browser. */
    ESCAPES_EVERYWHERE;

    companion object {
        fun parse(raw: String?): TerminalKeyOutput =
            entries.firstOrNull { it.name == raw } ?: KEY_EVENTS
    }
}

/**
 * One place that decides, for the field being typed in, whether terminal mode
 * is on, whether Ctrl+A goes through and whether chords are sent as escape
 * sequences. Pure so the terminal test matrix can be unit tested.
 */
object TerminalOutputPolicy {

    data class Decision(
        val terminalMode: Boolean,
        val allowCtrlA: Boolean,
        val useEscapes: Boolean,
    )

    fun decide(
        packageName: String?,
        inputType: Int,
        terminalModeAuto: Boolean,
        ctrlAInTextFields: Boolean,
        output: TerminalKeyOutput,
        vaultSearch: Boolean = false,
    ): Decision {
        val terminal = !vaultSearch && TerminalMode.detect(packageName, inputType, terminalModeAuto)
        val rawField = (inputType and InputType.TYPE_MASK_CLASS) == InputType.TYPE_NULL
        val allowCtrlA = terminal || ctrlAInTextFields || rawField
        val useEscapes = !vaultSearch && when (output) {
            TerminalKeyOutput.KEY_EVENTS -> false
            TerminalKeyOutput.ESCAPES_IN_TERMINAL_MODE -> terminal
            TerminalKeyOutput.ESCAPES_EVERYWHERE -> true
        }
        return Decision(terminal, allowCtrlA, useEscapes)
    }

    /**
     * What to do with one stroke: the normal plan, or the escape text when
     * escapes are on and the stroke has a sequence. The Ctrl-A guard runs
     * first, so a blocked Ctrl+A is never turned into 0x01.
     */
    fun plan(stroke: KeyStroke, decision: Decision): KeyStrokePlan {
        val plan = KeyStrokePlanner.plan(stroke, decision.allowCtrlA)
        if (!decision.useEscapes || plan !is KeyStrokePlan.Events) return plan
        val text = TerminalEscapes.encode(stroke) ?: return plan
        return KeyStrokePlan.Escape(text)
    }
}
