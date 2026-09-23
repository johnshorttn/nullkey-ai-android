package com.nullverse.nullkeyai

import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nullverse.nullkeyai.ime.engine.NullKeyKeyboardView
import com.nullverse.nullkeyai.ime.engine.PlacedKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * On-device coverage for the IME engine surface: swipe typing emits a gesture
 * path from real MotionEvents without enabling the system IME.
 */
@RunWith(AndroidJUnit4::class)
class KeyboardEngineInstrumentedTest {

    private fun layoutView(width: Int = 1080): NullKeyKeyboardView {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val view = NullKeyKeyboardView(context)
        view.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        )
        view.layout(0, 0, view.measuredWidth, view.measuredHeight)
        return view
    }

    private fun swipe(view: View, start: PlacedKey, end: PlacedKey) {
        val downTime = SystemClock.uptimeMillis()
        view.dispatchTouchEvent(
            MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, start.slot.centerX, start.slot.centerY, 0)
        )
        view.dispatchTouchEvent(
            MotionEvent.obtain(downTime, downTime + 16, MotionEvent.ACTION_MOVE, end.slot.centerX, end.slot.centerY, 0)
        )
        view.dispatchTouchEvent(
            MotionEvent.obtain(downTime, downTime + 32, MotionEvent.ACTION_UP, end.slot.centerX, end.slot.centerY, 0)
        )
    }

    @Test
    fun swipeAcrossLetterKeysEmitsGesturePath() {
        val view = layoutView()
        val paths = mutableListOf<String>()
        val codes = mutableListOf<Int>()
        view.listener = object : NullKeyKeyboardView.Listener {
            override fun onKey(code: Int) {
                codes += code
            }
            override fun onGestureWord(path: String) {
                paths += path
            }
        }
        swipe(view, view.keyWithLabel("h"), view.keyWithLabel("e"))
        val path = paths.single()
        assertTrue(path.startsWith("h"))
        assertTrue(path.endsWith("e"))
        assertTrue(path.all { it.isLetter() })
        assertTrue(codes.isEmpty())
    }

    @Test
    fun flickFromHToOSamplesInteriorKeys() {
        val view = layoutView()
        val paths = mutableListOf<String>()
        view.listener = object : NullKeyKeyboardView.Listener {
            override fun onGestureWord(path: String) {
                paths += path
            }
        }
        swipe(view, view.keyWithLabel("h"), view.keyWithLabel("o"))
        val path = paths.single()
        assertEquals("hjio", path)
    }

    @Test
    fun slowDragThroughHelloEmitsALetterTrail() {
        val view = layoutView()
        val paths = mutableListOf<String>()
        view.listener = object : NullKeyKeyboardView.Listener {
            override fun onGestureWord(path: String) {
                paths += path
            }
        }
        val downTime = SystemClock.uptimeMillis()
        val points = listOf("h", "e", "l", "o").map { view.keyWithLabel(it) }
        view.dispatchTouchEvent(
            MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, points[0].slot.centerX, points[0].slot.centerY, 0)
        )
        points.drop(1).forEachIndexed { index, key ->
            view.dispatchTouchEvent(
                MotionEvent.obtain(
                    downTime,
                    downTime + 20L * (index + 1),
                    MotionEvent.ACTION_MOVE,
                    key.slot.centerX,
                    key.slot.centerY,
                    0,
                )
            )
        }
        val end = points.last()
        view.dispatchTouchEvent(
            MotionEvent.obtain(downTime, downTime + 120, MotionEvent.ACTION_UP, end.slot.centerX, end.slot.centerY, 0)
        )
        val path = paths.single()
        assertTrue(path.startsWith("h"))
        assertTrue(path.endsWith("o"))
        assertTrue(path.all { it.isLetter() })
        assertTrue(path.length >= 4)
    }
}
