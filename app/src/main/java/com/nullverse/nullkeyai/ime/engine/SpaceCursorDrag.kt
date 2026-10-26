package com.nullverse.nullkeyai.ime.engine

import kotlin.math.abs
import kotlin.math.floor

/**
 * Horizontal drag on the space bar. One step is about one key height, which
 * is roughly one character of travel. A shorter move stays a tap.
 *
 * Steps are measured from the finger-down point, so reversing direction walks
 * the cursor back. The long-press timer is not required; the finger just has
 * to stay down and cross a step.
 */
class SpaceCursorDrag(
    val stepPx: Float,
) {
    enum class Axis { UNDECIDED, HORIZONTAL, VERTICAL }

    var engaged: Boolean = false
        private set

    private var originX = 0f
    private var originY = 0f
    private var appliedSteps = 0

    fun begin(x: Float, y: Float) {
        originX = x
        originY = y
        engaged = false
        appliedSteps = 0
    }

    fun axis(x: Float, y: Float): Axis {
        if (engaged) return Axis.HORIZONTAL
        if (stepPx <= 0f) return Axis.UNDECIDED
        val dx = abs(x - originX)
        val dy = abs(y - originY)
        val slop = stepPx * SLOP_FRACTION
        if (dx < slop && dy < slop) return Axis.UNDECIDED
        return if (dx >= dy) Axis.HORIZONTAL else Axis.VERTICAL
    }

    /**
     * @return newly crossed character steps since the last move. Negative is left.
     */
    fun move(x: Float, y: Float): Int {
        if (stepPx <= 0f) return 0
        val steps = signedSteps(x - originX)
        if (!engaged) {
            if (axis(x, y) != Axis.HORIZONTAL || abs(steps) < 1) return 0
            engaged = true
        }
        val delta = steps - appliedSteps
        appliedSteps = steps
        return delta
    }

    private fun signedSteps(dx: Float): Int {
        val magnitude = floor(abs(dx) / stepPx + 1e-3).toInt()
        return if (dx < 0f) -magnitude else magnitude
    }

    companion object {
        const val SLOP_FRACTION = 0.35f

        fun stepPxFor(key: PlacedKey): Float = key.slot.height.coerceAtLeast(1f)
    }
}
