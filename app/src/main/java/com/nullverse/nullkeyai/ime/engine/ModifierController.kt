package com.nullverse.nullkeyai.ime.engine

/**
 * Shift / caps-lock / layer state for the custom keyboard engine.
 *
 * Shift tap: OFF → one-shot ON; ON → OFF; LOCKED → OFF.
 * A second shift tap within [KeyboardEngineDefaults.SHIFT_DOUBLE_TAP_MS]
 * engages caps lock. Long-press also locks caps.
 * Committing a letter consumes one-shot shift.
 *
 * Ctrl, Alt and Meta ([StickyModifier]) follow the same tap / double-tap /
 * long-press rules, but are consumed by any non-modifier key, not just
 * letters. Locked modifiers stay on until tapped again.
 *
 * Chording: while a modifier key is physically held by one finger
 * ([holdShift] / [holdSticky]) every key typed by another finger gets that
 * modifier. A held modifier is separate from the tap state above: typing a key
 * never clears it, and [releaseShift] / [releaseSticky] drop it without
 * touching one-shot or lock. One-shot modifiers that were already on are still
 * consumed by the first key, as usual.
 */
class ModifierController(
    private val doubleTapMs: Long = KeyboardEngineDefaults.SHIFT_DOUBLE_TAP_MS,
) {
    var shift: ShiftState = ShiftState.OFF
        private set

    var layer: KeyboardLayer = KeyboardLayer.LETTERS
        private set

    private var lastShiftTapMs: Long = -1L

    private val sticky = mutableMapOf<StickyModifier, ShiftState>()
    private val lastStickyTapMs = mutableMapOf<StickyModifier, Long>()

    private var shiftHeld: Boolean = false
    private val heldSticky = mutableSetOf<StickyModifier>()

    /** Shift is on: tapped (one-shot or caps lock) or held for a chord. */
    val isShifted: Boolean get() = shift != ShiftState.OFF || shiftHeld
    val isShiftHeld: Boolean get() = shiftHeld
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

    fun stickyState(modifier: StickyModifier): ShiftState = sticky[modifier] ?: ShiftState.OFF

    fun isStickyActive(modifier: StickyModifier): Boolean =
        stickyState(modifier) != ShiftState.OFF || modifier in heldSticky

    fun isStickyHeld(modifier: StickyModifier): Boolean = modifier in heldSticky

    val isAnyHeld: Boolean get() = shiftHeld || heldSticky.isNotEmpty()

    /** A finger is holding Shift down while another finger types. */
    fun holdShift() {
        shiftHeld = true
    }

    /** The holding finger lifted. One-shot and caps lock are left as they were. */
    fun releaseShift() {
        shiftHeld = false
    }

    fun holdSticky(modifier: StickyModifier) {
        heldSticky += modifier
    }

    fun releaseSticky(modifier: StickyModifier) {
        heldSticky -= modifier
    }

    /**
     * Put a modifier back to [state]. Used when a long-press lock fired on a
     * key that then turned out to be held for a chord.
     */
    fun restoreShift(state: ShiftState) {
        shift = state
        lastShiftTapMs = -1L
    }

    fun restoreSticky(modifier: StickyModifier, state: ShiftState) {
        sticky[modifier] = state
        lastStickyTapMs[modifier] = -1L
    }

    /** Ctrl/Alt/Meta that apply to the next key, in a stable order. */
    fun activeStickyModifiers(): Set<StickyModifier> =
        StickyModifier.entries.filterTo(linkedSetOf()) { isStickyActive(it) }

    fun onStickyTap(modifier: StickyModifier, nowMs: Long) {
        val last = lastStickyTapMs[modifier] ?: -1L
        when (stickyState(modifier)) {
            ShiftState.LOCKED -> {
                sticky[modifier] = ShiftState.OFF
                lastStickyTapMs[modifier] = -1L
            }
            ShiftState.ON, ShiftState.OFF -> {
                if (last >= 0L && nowMs - last <= doubleTapMs) {
                    sticky[modifier] = ShiftState.LOCKED
                    lastStickyTapMs[modifier] = -1L
                } else {
                    sticky[modifier] =
                        if (stickyState(modifier) == ShiftState.OFF) ShiftState.ON else ShiftState.OFF
                    lastStickyTapMs[modifier] = nowMs
                }
            }
        }
    }

    fun onStickyLongPress(modifier: StickyModifier) {
        sticky[modifier] = ShiftState.LOCKED
        lastStickyTapMs[modifier] = -1L
    }

    /**
     * A key was sent with the current modifiers. Clears one-shot Ctrl/Alt/Meta
     * and one-shot Shift; locked modifiers stay.
     */
    fun consumeOneShot() {
        for (modifier in StickyModifier.entries) {
            if (stickyState(modifier) == ShiftState.ON) {
                sticky[modifier] = ShiftState.OFF
                lastStickyTapMs[modifier] = -1L
            }
        }
        onLetterCommitted()
    }

    fun toggleLayer() {
        layer = if (layer == KeyboardLayer.LETTERS) {
            KeyboardLayer.SYMBOLS
        } else {
            KeyboardLayer.LETTERS
        }
    }

    /** Like [applyToCode], but Shift also picks a PC key's [KeySpec.shiftedCode]. */
    fun applyToKey(spec: KeySpec): Int {
        val shiftedCode = spec.shiftedCode
        if (isShifted && shiftedCode != null) return shiftedCode
        return applyToCode(spec.code)
    }

    /** Whether typing [spec] should clear a one-shot Shift. */
    fun consumesShift(spec: KeySpec): Boolean =
        spec.code.toChar().isLetter() || (isShifted && spec.shiftedCode != null)

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
        KeyCodes.stickyModifierFor(spec.code)?.let { modifier ->
            // Locked reads in caps so the state is visible without colour.
            return if (stickyState(modifier) == ShiftState.LOCKED) spec.label.uppercase() else spec.label
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
        sticky.clear()
        lastStickyTapMs.clear()
        shiftHeld = false
        heldSticky.clear()
    }
}
