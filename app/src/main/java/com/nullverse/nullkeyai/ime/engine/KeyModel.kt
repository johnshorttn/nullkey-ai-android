package com.nullverse.nullkeyai.ime.engine

import kotlin.math.hypot

/**
 * Key codes aligned with the legacy `android.inputmethodservice.Keyboard`
 * constants so the IME can share one commit path between the custom engine
 * and the fallback KeyboardView.
 */
object KeyCodes {
    const val SHIFT = -1
    const val MODE_CHANGE = -2
    const val CANCEL = -3
    const val DONE = -4
    const val DELETE = -5
    const val ALT = -6
    const val SPACE = 32

    // Terminal / PC keys. These sit in -9001..-9099 on purpose: the engine
    // tests `code.toChar().isLetter()` in several places, and these values map
    // to UTF-16 surrogates, which are never letters. A range like -100 would
    // land on halfwidth katakana and be mistaken for a letter in swipe paths.
    const val ESCAPE = -9001
    const val TAB = -9002
    const val CTRL = -9003
    const val META = -9004
    const val ARROW_LEFT = -9010
    const val ARROW_RIGHT = -9011
    const val ARROW_UP = -9012
    const val ARROW_DOWN = -9013
    const val HOME = -9014
    const val END = -9015
    const val PAGE_UP = -9016
    const val PAGE_DOWN = -9017
    const val INSERT = -9018
    const val FORWARD_DELETE = -9019
    const val F1 = -9021
    const val F12 = -9032

    /** F1..F12 codes, F1 first. */
    val FUNCTION_KEYS: List<Int> = (0 until 12).map { F1 - it }

    fun functionKey(n: Int): Int {
        require(n in 1..12) { "F$n" }
        return F1 - (n - 1)
    }

    /** 1-based F-key number, or null when [code] is not F1..F12. */
    fun functionKeyNumber(code: Int): Int? =
        if (code in F12..F1) F1 - code + 1 else null

    /** Sticky modifiers (Ctrl, Alt, Meta). Shift keeps its own state machine. */
    fun stickyModifierFor(code: Int): StickyModifier? = when (code) {
        CTRL -> StickyModifier.CTRL
        ALT -> StickyModifier.ALT
        META -> StickyModifier.META
        else -> null
    }

    /** Keys that only make sense as real KeyEvents (never commitText). */
    fun isKeyEventKey(code: Int): Boolean = when (code) {
        ESCAPE, TAB, ARROW_LEFT, ARROW_RIGHT, ARROW_UP, ARROW_DOWN,
        HOME, END, PAGE_UP, PAGE_DOWN, INSERT, FORWARD_DELETE -> true
        else -> functionKeyNumber(code) != null
    }
}

/** Ctrl / Alt / Meta. Each is OFF, one-shot (ON), or LOCKED like Shift. */
enum class StickyModifier { CTRL, ALT, META }

enum class KeyboardLayer { LETTERS, SYMBOLS }

enum class LayoutOrientation { PORTRAIT, LANDSCAPE }

enum class ShiftState { OFF, ON, LOCKED }

data class KeySpec(
    val code: Int,
    val label: String,
    val shiftedLabel: String? = null,
    val widthWeight: Float = 1f,
    val isModifier: Boolean = false,
    val isRepeatable: Boolean = false,
    val popupCharacters: String = "",
) {
    fun displayLabel(shifted: Boolean): String {
        if (shifted) {
            shiftedLabel?.let { return it }
            if (label.length == 1 && label[0].isLetter()) {
                return label.uppercase()
            }
        }
        return label
    }
}

data class KeyRow(
    val keys: List<KeySpec>,
    val leadingGapWeight: Float = 0f,
    val trailingGapWeight: Float = 0f,
)

data class KeyboardLayoutSpec(
    val id: String,
    val layer: KeyboardLayer,
    val rows: List<KeyRow>,
)

data class KeyBounds(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
    val centerX: Float get() = (left + right) / 2f
    val centerY: Float get() = (top + bottom) / 2f

    fun contains(x: Float, y: Float): Boolean =
        x >= left && x < right && y >= top && y < bottom

    fun inset(dx: Float, dy: Float): KeyBounds = KeyBounds(
        left = left + dx,
        top = top + dy,
        right = right - dx,
        bottom = bottom - dy,
    )

    fun distanceTo(x: Float, y: Float): Float {
        val dx = when {
            x < left -> left - x
            x > right -> x - right
            else -> 0f
        }
        val dy = when {
            y < top -> top - y
            y > bottom -> y - bottom
            else -> 0f
        }
        return hypot(dx, dy)
    }
}

data class PlacedKey(
    val id: Int,
    val spec: KeySpec,
    val slot: KeyBounds,
    val visual: KeyBounds,
)

object KeyboardEngineDefaults {
    const val LONG_PRESS_MS = 400L
    const val REPEAT_START_MS = 400L
    const val REPEAT_INTERVAL_MS = 50L
    const val SHIFT_DOUBLE_TAP_MS = 300L
    const val PORTRAIT_KEY_HEIGHT_DP = 48f
    const val LANDSCAPE_KEY_HEIGHT_DP = 32f
    const val VERTICAL_PADDING_DP = 8f
    const val HORIZONTAL_PADDING_DP = 3f
    const val PORTRAIT_GAP_DP = 3f
    const val LANDSCAPE_GAP_DP = 2f
}
