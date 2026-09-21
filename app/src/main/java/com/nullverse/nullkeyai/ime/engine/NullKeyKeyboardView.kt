package com.nullverse.nullkeyai.ime.engine

import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.os.SystemClock
import android.util.AttributeSet
import android.view.MotionEvent
import android.widget.PopupWindow
import android.widget.LinearLayout
import android.widget.TextView
import android.view.Gravity
import android.view.View
import androidx.annotation.VisibleForTesting

/**
 * Custom keyboard surface: renders [KeyboardGeometry] and feeds pointer events
 * into [KeyboardController]. No dependency on the legacy Keyboard/KeyboardView
 * APIs.
 */
class NullKeyKeyboardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    interface Listener {
        fun onKey(code: Int)
        fun onLongPress(code: Int, popupCharacters: String) {}
        fun onGestureWord(path: String) {}
    }

    var listener: Listener? = null

    private val viewScheduler = object : TaskScheduler {
        override fun nowMs(): Long = SystemClock.uptimeMillis()
        override fun schedule(delayMs: Long, action: () -> Unit): Cancellable {
            val runnable = Runnable { action() }
            postDelayed(runnable, delayMs)
            return Cancellable { removeCallbacks(runnable) }
        }
    }

    private val renderer = KeyboardRenderer()
    private val gestureTrail = mutableListOf<PlacedKey>()
    private var gesturePointerX = 0f
    private var gesturePointerY = 0f
    private var gesturePointerActive = false
    private var accentPopup: PopupWindow? = null
    private var popupSourceKeyId: String? = null
    private val gesturePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private var theme: KeyboardTheme = KeyboardTheme.from(context, currentOrientation())

    @VisibleForTesting
    internal val controller: KeyboardController = KeyboardController(
        scheduler = viewScheduler,
        host = object : KeyboardController.Host {
            override fun requestRedraw() {
                postInvalidateOnAnimation()
            }

            override fun onKey(code: Int) {
                listener?.onKey(code)
            }

            override fun onGestureWord(path: String) {
                listener?.onGestureWord(path)
            }

            override fun onGestureProgress(keys: List<PlacedKey>) {
                gestureTrail.clear()
                gestureTrail.addAll(keys)
            }

            override fun onLongPress(code: Int, popupCharacters: String) {
                if (popupCharacters.isBlank()) {
                    listener?.onLongPress(code, popupCharacters)
                } else {
                    showCharacterPopup(popupCharacters)
                }
            }
        },
    )

    init {
        controller.swipeTypingEnabled = KeyboardEnginePreferences.swipeTypingEnabled(context)
        isClickable = true
        isFocusable = false
        contentDescription = context.getString(com.nullverse.nullkeyai.R.string.ime_label)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = resolveSize(suggestedMinimumWidth, widthMeasureSpec)
        val orientation = currentOrientation()
        val spec = DefaultKeyboardLayoutProvider().spec(controller.modifiers.layer, orientation)
        val preferred = preferredKeyboardHeightPx(resources.displayMetrics.density, orientation, spec.rows.size)
        val heightMode = MeasureSpec.getMode(heightMeasureSpec)
        val heightSize = MeasureSpec.getSize(heightMeasureSpec)
        val height = when (heightMode) {
            MeasureSpec.EXACTLY -> heightSize
            MeasureSpec.AT_MOST -> preferred.coerceAtMost(heightSize)
            else -> preferred
        }
        setMeasuredDimension(width.coerceAtLeast(1), height.coerceAtLeast(1))
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        applySize(w, h)
    }

    override fun onConfigurationChanged(newConfig: Configuration?) {
        super.onConfigurationChanged(newConfig)
        requestLayout()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        renderer.draw(canvas, theme, controller)
        drawGestureTrail(canvas)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN,
            MotionEvent.ACTION_POINTER_DOWN -> {
                val index = event.actionIndex
                controller.down(event.getPointerId(index), event.getX(index), event.getY(index))
            }
            MotionEvent.ACTION_MOVE -> {
                val pointerId = controller.touch.activePointerId
                if (pointerId != null) {
                    val index = event.findPointerIndex(pointerId)
                    if (index >= 0) {
                        gesturePointerX = event.getX(index)
                        gesturePointerY = event.getY(index)
                        gesturePointerActive = true
                        controller.move(pointerId, gesturePointerX, gesturePointerY)
                    }
                }
            }
            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_POINTER_UP -> {
                val index = event.actionIndex
                gesturePointerActive = false
                controller.up(event.getPointerId(index), event.getX(index), event.getY(index))
            }
            MotionEvent.ACTION_CANCEL -> {
                gesturePointerActive = false
                controller.cancel()
            }
            else -> return super.onTouchEvent(event)
        }
        return true
    }

    override fun onDetachedFromWindow() {
        accentPopup?.dismiss()
        accentPopup = null
        popupSourceKeyId = null
        controller.cancel()
        super.onDetachedFromWindow()
    }

    fun resetEngine() {
        accentPopup?.dismiss()
        accentPopup = null
        popupSourceKeyId = null
        controller.swipeTypingEnabled = KeyboardEnginePreferences.swipeTypingEnabled(context)
        controller.reset()
    }

    fun setSwipeTypingEnabled(enabled: Boolean) {
        controller.swipeTypingEnabled = enabled
        KeyboardEnginePreferences.setSwipeTypingEnabled(context, enabled)
    }

    @VisibleForTesting
    internal fun keyWithLabel(label: String): PlacedKey =
        controller.geometry.placedKeys.first { it.spec.label.equals(label, ignoreCase = true) }

    private fun showCharacterPopup(characters: String) {
        accentPopup?.dismiss()
        accentPopup = null
        popupSourceKeyId = controller.touch.pressed?.id
        val density = resources.displayMetrics.density
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding((6 * density).toInt(), (4 * density).toInt(), (6 * density).toInt(), (4 * density).toInt())
            setBackgroundColor(theme.keyColor)
        }
        val popup = PopupWindow(row, LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT, true).apply {
            isOutsideTouchable = true
            elevation = 8f * density
            setOnDismissListener {
                if (accentPopup === this) {
                    accentPopup = null
                    popupSourceKeyId = null
                    popupSourceKeyId = null
                }
            }
        }
        accentPopup = popup
        characters.forEach { character ->
            row.addView(TextView(context).apply {
                text = character.toString()
                textSize = 22f
                gravity = Gravity.CENTER
                setTextColor(theme.labelColor)
                minWidth = (42 * density).toInt()
                minHeight = (44 * density).toInt()
                setOnClickListener {
                    controller.commitPopupCharacter(character)
                    popup.dismiss()
                    accentPopup = null
                }
            })
        }
        val key = controller.touch.pressed
        if (key == null) {
            popup.showAtLocation(this, Gravity.CENTER, 0, 0)
            return
        }
        row.measure(MeasureSpec.UNSPECIFIED, MeasureSpec.UNSPECIFIED)
        val maxX = (width - row.measuredWidth).coerceAtLeast(0)
        val x = (key.slot.centerX - row.measuredWidth / 2f).toInt().coerceIn(0, maxX)
        val aboveY = (key.slot.top - row.measuredHeight - 8f * density).toInt()
        val belowY = (key.slot.bottom + 8f * density).toInt()
        val maxY = (height - row.measuredHeight).coerceAtLeast(0)
        val y = (if (aboveY >= 0) aboveY else belowY).coerceIn(0, maxY)
        popup.showAtLocation(this, Gravity.TOP or Gravity.START, x, y)
    }

    private fun drawGestureTrail(canvas: Canvas) {
        if (!controller.swipeTypingEnabled || gestureTrail.size < 2) return
        val path = Path()
        gestureTrail.forEachIndexed { index, key ->
            val x = key.slot.centerX
            val y = key.slot.centerY
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        if (gesturePointerActive) path.lineTo(gesturePointerX, gesturePointerY)
        gesturePaint.strokeWidth = 6f * resources.displayMetrics.density
        gesturePaint.color = theme.gestureTrailColor
        gesturePaint.alpha = 150
        canvas.drawPath(path, gesturePaint)
    }

    private fun applySize(width: Int, height: Int) {
        val density = resources.displayMetrics.density
        val orientation = currentOrientation()
        theme = KeyboardTheme.from(context, orientation)
        controller.resize(
            widthPx = width.toFloat(),
            heightPx = height.toFloat(),
            orientation = orientation,
            paddingHorizontalPx = keyboardPaddingHorizontalPx(density),
            paddingVerticalPx = keyboardPaddingVerticalPx(density),
            gapPx = keyboardGapPx(density, orientation),
        )
    }

    private fun currentOrientation(): LayoutOrientation {
        return if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            LayoutOrientation.LANDSCAPE
        } else {
            LayoutOrientation.PORTRAIT
        }
    }
}
