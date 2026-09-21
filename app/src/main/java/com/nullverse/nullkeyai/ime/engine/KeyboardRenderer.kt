package com.nullverse.nullkeyai.ime.engine

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import androidx.core.content.ContextCompat
import com.nullverse.nullkeyai.R

data class KeyboardTheme(
    val backgroundColor: Int,
    val keyColor: Int,
    val keyPressedColor: Int,
    val modifierColor: Int,
    val modifierActiveColor: Int,
    val labelColor: Int,
    val hintColor: Int,
    val pressedStrokeColor: Int,
    val gestureTrailColor: Int,
    val cornerRadiusPx: Float,
    val labelTextSizePx: Float,
    val hintTextSizePx: Float,
    val strokeWidthPx: Float,
) {
    companion object {
        fun from(context: Context, orientation: LayoutOrientation): KeyboardTheme {
            val density = context.resources.displayMetrics.density
            val scaled = context.resources.displayMetrics.scaledDensity
            val labelSp = if (orientation == LayoutOrientation.LANDSCAPE) 14f else 16f
            return KeyboardTheme(
                backgroundColor = ContextCompat.getColor(context, R.color.kb_background),
                keyColor = ContextCompat.getColor(context, R.color.kb_key),
                keyPressedColor = ContextCompat.getColor(context, R.color.kb_key_pressed),
                modifierColor = ContextCompat.getColor(context, R.color.kb_key_modifier),
                modifierActiveColor = ContextCompat.getColor(context, R.color.kb_key_modifier_active),
                labelColor = ContextCompat.getColor(context, R.color.kb_label),
                hintColor = ContextCompat.getColor(context, R.color.kb_label_hint),
                pressedStrokeColor = ContextCompat.getColor(context, R.color.kb_key_stroke_pressed),
                gestureTrailColor = ContextCompat.getColor(context, R.color.kb_key_stroke_pressed),
                cornerRadiusPx = 8f * density,
                labelTextSizePx = labelSp * scaled,
                hintTextSizePx = 10f * scaled,
                strokeWidthPx = 1.5f * density,
            )
        }
    }
}

/**
 * Draws placed keys onto a [Canvas]. Kept separate from touch handling so the
 * renderer can evolve (themes, animations) without changing hit-testing.
 */
class KeyboardRenderer {
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }
    private val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT
    }
    private val rect = RectF()

    fun draw(
        canvas: Canvas,
        theme: KeyboardTheme,
        controller: KeyboardController,
        context: Context? = null,
    ) {
        canvas.drawColor(theme.backgroundColor)
        strokePaint.strokeWidth = theme.strokeWidthPx
        labelPaint.textSize = theme.labelTextSizePx
        hintPaint.textSize = theme.hintTextSizePx
        hintPaint.color = theme.hintColor
        val geometry = controller.geometry
        for (key in geometry.placedKeys) {
            val pressed = controller.isPressed(key)
            val modifierActive = controller.isModifierActive(key)
            fillPaint.color = when {
                pressed -> theme.keyPressedColor
                modifierActive -> theme.modifierActiveColor
                key.spec.isModifier -> theme.modifierColor
                else -> theme.keyColor
            }
            rect.set(key.visual.left, key.visual.top, key.visual.right, key.visual.bottom)
            canvas.drawRoundRect(rect, theme.cornerRadiusPx, theme.cornerRadiusPx, fillPaint)
            if (pressed || modifierActive) {
                strokePaint.color = theme.pressedStrokeColor
                canvas.drawRoundRect(rect, theme.cornerRadiusPx, theme.cornerRadiusPx, strokePaint)
            }
            val engineLabel = controller.labelFor(key)
            val label = if (context != null) {
                LocalizedKeyLabels.resolve(context, key.spec, engineLabel)
            } else {
                engineLabel
            }
            labelPaint.color = theme.labelColor
            val textY = key.visual.centerY - (labelPaint.descent() + labelPaint.ascent()) / 2f
            canvas.drawText(label, key.visual.centerX, textY, labelPaint)
            val popup = key.spec.popupCharacters
            if (popup.isNotEmpty()) {
                canvas.drawText(
                    popup.first().toString(),
                    key.visual.right - theme.hintTextSizePx * 0.7f,
                    key.visual.top + theme.hintTextSizePx,
                    hintPaint,
                )
            }
        }
    }
}
