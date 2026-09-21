package com.nullverse.nullkeyai.ime.engine

import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.os.SystemClock
import android.util.AttributeSet
import android.view.MotionEvent
import android.widget.PopupMenu
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
                        controller.move(pointerId, event.getX(index), event.getY(index))
                    }
                }
            }
            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_POINTER_UP -> {
                val index = event.actionIndex
                controller.up(event.getPointerId(index), event.getX(index), event.getY(index))
            }
            MotionEvent.ACTION_CANCEL -> controller.cancel()
            else -> return super.onTouchEvent(event)
        }
        return true
    }

    fun resetEngine() {
        controller.reset()
    }

    @VisibleForTesting
    internal fun keyWithLabel(label: String): PlacedKey =
        controller.geometry.placedKeys.first { it.spec.label.equals(label, ignoreCase = true) }

    private fun showCharacterPopup(characters: String) {
        val popup = PopupMenu(context, this)
        characters.forEachIndexed { index, character ->
            popup.menu.add(0, character.code, index, character.toString())
        }
        popup.setOnMenuItemClickListener { item ->
            controller.commitPopupCharacter(item.itemId.toChar())
            true
        }
        popup.show()
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
