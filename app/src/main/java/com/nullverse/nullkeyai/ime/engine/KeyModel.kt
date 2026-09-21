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
}

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
