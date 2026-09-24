package com.nullverse.nullkeyai.ime.engine

/**
 * Turns a finger drag into whole cursor steps. Movement smaller than a step
 * stays in the remainder, so a slow drag still moves one character at a time
 * and a small jitter does not.
 *
 * Positive x is right. Positive y is down the screen, which is the next line.
 */
class TrackpadPointer(
    val stepX: Float,
    val stepY: Float,
) {
    private var lastX = 0f
    private var lastY = 0f
    private var pendingX = 0f
    private var pendingY = 0f
    private var tracking = false

    fun down(x: Float, y: Float) {
        lastX = x
        lastY = y
        pendingX = 0f
        pendingY = 0f
        tracking = true
    }

    fun move(x: Float, y: Float): Pair<Int, Int> {
        if (!tracking || stepX <= 0f || stepY <= 0f) return 0 to 0
        pendingX += x - lastX
        pendingY += y - lastY
        lastX = x
        lastY = y
        val stepsX = wholeSteps(pendingX, stepX)
        val stepsY = wholeSteps(pendingY, stepY)
        pendingX -= stepsX * stepX
        pendingY -= stepsY * stepY
        return stepsX to stepsY
    }

    fun reset() {
        tracking = false
        pendingX = 0f
        pendingY = 0f
    }

    private fun wholeSteps(pending: Float, step: Float): Int = (pending / step).toInt()

    companion object {
        const val STEP_X_DP = 24f
        const val STEP_Y_DP = 32f

        fun forDensity(density: Float): TrackpadPointer {
            val scale = if (density > 0f) density else 1f
            return TrackpadPointer(STEP_X_DP * scale, STEP_Y_DP * scale)
        }
    }
}
