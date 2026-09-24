package com.nullverse.nullkeyai.ime.engine

/**
 * Shift / caps-lock / layer state for the custom keyboard engine.
 *
 * Shift tap: OFF → one-shot ON; ON → OFF; LOCKED → OFF.
 * A second shift tap within [KeyboardEngineDefaults.SHIFT_DOUBLE_TAP_MS]
 * engages caps lock. Long-press also locks caps.
 * Committing a letter consumes one-shot shift.
 */
class ModifierController(
    private val doubleTapMs: Long = KeyboardEngineDefaults.SHIFT_DOUBLE_TAP_MS,
) {
    var shift: ShiftState = ShiftState.OFF
        private set

    var layer: KeyboardLayer = KeyboardLayer.LETTERS
        private set

    private var lastShiftTapMs: Long = -1L

    val isShifted: Boolean get() = shift != ShiftState.OFF
    val isCapsLock: Boolean get() = shift == ShiftState.LOCKED

    fun onShiftTap(nowMs: Long) {
        when (shift) {
            ShiftState.LOCKED -> {
                shift = ShiftState.OFF
                lastShiftTapMs = -1L
            }
            ShiftState.ON, ShiftState.OFF -> {
                if (lastShiftTapMs >= 0L && nowMs - lastShiftTapMs <= doubleTapMs) {
                    shift = ShiftState.LOCKED
                    lastShiftTapMs = -1L
                } else {
                    shift = if (shift == ShiftState.OFF) ShiftState.ON else ShiftState.OFF
                    lastShiftTapMs = nowMs
                }
            }
        }
    }

    fun onShiftLongPress() {
        shift = ShiftState.LOCKED
        lastShiftTapMs = -1L
    }

    fun onLetterCommitted() {
        if (shift == ShiftState.ON) {
            shift = ShiftState.OFF
            lastShiftTapMs = -1L
        }
    }

    fun toggleLayer() {
        layer = if (layer == KeyboardLayer.LETTERS) {
            KeyboardLayer.SYMBOLS
        } else {
            KeyboardLayer.LETTERS
        }
    }

    fun applyToCode(code: Int): Int {
        if (code <= 0 || !isShifted) return code
        val ch = code.toChar()
        if (!ch.isLetter()) return code
        return ch.uppercaseChar().code
    }

    fun labelFor(spec: KeySpec): String {
        if (spec.code == KeyCodes.SHIFT) {
            return if (isCapsLock) "⇪" else "⇧"
        }
        if (spec.code == KeyCodes.MODE_CHANGE) {
            return if (layer == KeyboardLayer.SYMBOLS) "ABC" else "?123"
        }
        val shiftLetters = isShifted && layer == KeyboardLayer.LETTERS
        return spec.displayLabel(shiftLetters)
    }

    fun reset() {
        shift = ShiftState.OFF
        layer = KeyboardLayer.LETTERS
        lastShiftTapMs = -1L
    }
}
