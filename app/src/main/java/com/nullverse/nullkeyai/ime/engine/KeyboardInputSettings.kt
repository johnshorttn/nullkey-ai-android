package com.nullverse.nullkeyai.ime.engine

import androidx.annotation.StringRes
import com.nullverse.nullkeyai.R
import kotlin.math.roundToInt

enum class KeyboardThemeId {
    DARK_VAULT,
    LIGHT,
    SYSTEM;

    @get:StringRes
    val labelRes: Int
        get() = when (this) {
            DARK_VAULT -> R.string.keyboard_theme_dark_vault
            LIGHT -> R.string.keyboard_theme_light
            SYSTEM -> R.string.keyboard_theme_system
        }

    fun resolve(nightMode: Boolean): KeyboardThemeId = when (this) {
        SYSTEM -> if (nightMode) DARK_VAULT else LIGHT
        else -> this
    }

    companion object {
        fun parse(value: String?): KeyboardThemeId =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: DARK_VAULT
    }
}

/**
 * Clamps and SeekBar mapping for keyboard height and long-press timing.
 * Persistence lives in [KeyboardEnginePreferences].
 */
object KeyboardInputSettings {
    const val HEIGHT_SCALE_MIN = 0.80f
    const val HEIGHT_SCALE_MAX = 1.40f
    const val HEIGHT_SCALE_DEFAULT = 1.0f
    const val HEIGHT_PERCENT_MIN = 80
    const val HEIGHT_PERCENT_STEP = 5
    const val HEIGHT_PROGRESS_MAX = 12

    const val LONG_PRESS_MS_MIN = 200
    const val LONG_PRESS_MS_MAX = 800
    const val LONG_PRESS_MS_STEP = 50
    const val LONG_PRESS_MS_DEFAULT = 400
    const val LONG_PRESS_PROGRESS_MAX = 12

    fun clampHeightScale(scale: Float): Float = progressToHeightScale(heightScaleToProgress(scale))

    fun heightScaleToProgress(scale: Float): Int {
        val percent = (scale * 100f).roundToInt().coerceIn(HEIGHT_PERCENT_MIN, HEIGHT_PERCENT_MAX)
        val snapped = HEIGHT_PERCENT_MIN +
            (((percent - HEIGHT_PERCENT_MIN + HEIGHT_PERCENT_STEP / 2) / HEIGHT_PERCENT_STEP) *
                HEIGHT_PERCENT_STEP)
        return ((snapped - HEIGHT_PERCENT_MIN) / HEIGHT_PERCENT_STEP)
            .coerceIn(0, HEIGHT_PROGRESS_MAX)
    }

    fun progressToHeightScale(progress: Int): Float {
        val p = progress.coerceIn(0, HEIGHT_PROGRESS_MAX)
        return (HEIGHT_PERCENT_MIN + p * HEIGHT_PERCENT_STEP) / 100f
    }

    fun heightPercent(scale: Float): Int = (clampHeightScale(scale) * 100f).roundToInt()

    fun clampLongPressMs(ms: Int): Int {
        val snapped = ((ms + LONG_PRESS_MS_STEP / 2) / LONG_PRESS_MS_STEP) * LONG_PRESS_MS_STEP
        return snapped.coerceIn(LONG_PRESS_MS_MIN, LONG_PRESS_MS_MAX)
    }

    fun longPressMsToProgress(ms: Int): Int =
        ((clampLongPressMs(ms) - LONG_PRESS_MS_MIN) / LONG_PRESS_MS_STEP)
            .coerceIn(0, LONG_PRESS_PROGRESS_MAX)

    fun progressToLongPressMs(progress: Int): Int {
        val p = progress.coerceIn(0, LONG_PRESS_PROGRESS_MAX)
        return LONG_PRESS_MS_MIN + p * LONG_PRESS_MS_STEP
    }

    private const val HEIGHT_PERCENT_MAX = 140
}
