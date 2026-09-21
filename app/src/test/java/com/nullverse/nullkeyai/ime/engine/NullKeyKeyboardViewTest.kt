package com.nullverse.nullkeyai.ime.engine

import android.content.Context
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class NullKeyKeyboardViewTest {

    private fun layoutView(width: Int = 1080): NullKeyKeyboardView {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val view = NullKeyKeyboardView(context)
        view.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        )
        view.layout(0, 0, view.measuredWidth, view.measuredHeight)
        return view
    }

    private fun tap(view: View, x: Float, y: Float) {
        val downTime = SystemClock.uptimeMillis()
        view.dispatchTouchEvent(
            MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, x, y, 0)
        )
        view.dispatchTouchEvent(
            MotionEvent.obtain(downTime, downTime + 16, MotionEvent.ACTION_UP, x, y, 0)
        )
    }

    @Test
    fun measuresAPortraitKeyboardAndPlacesQwerty() {
        val view = layoutView()
        assertTrue(view.measuredHeight > 100)
        assertTrue(view.controller.geometry.placedKeys.any { it.spec.label == "q" })
        assertEquals(LayoutOrientation.PORTRAIT, view.controller.orientation)
        assertEquals(4, view.controller.geometry.spec.rows.size)
    }

    @Test
    fun tapQwertyKeyEmitsCharacterCode() {
        val view = layoutView()
        val codes = mutableListOf<Int>()
        view.listener = object : NullKeyKeyboardView.Listener {
            override fun onKey(code: Int) {
                codes += code
            }
        }
        val q = view.keyWithLabel("q")
        tap(view, q.slot.centerX, q.slot.centerY)
        assertEquals(listOf('q'.code), codes)
    }

    @Test
    fun shiftThenLetterEmitsUppercaseThroughTheView() {
        val view = layoutView()
        val codes = mutableListOf<Int>()
        view.listener = object : NullKeyKeyboardView.Listener {
            override fun onKey(code: Int) {
                codes += code
            }
        }
        val shift = view.keyWithLabel("⇧")
        val a = view.keyWithLabel("a")
        tap(view, shift.slot.centerX, shift.slot.centerY)
        tap(view, a.slot.centerX, a.slot.centerY)
        assertEquals(listOf('A'.code), codes)
    }

    @Test
    fun talkBackVirtualViewsExposeSpokenKeyLabels() {
        val view = layoutView()
        val provider = view.accessibilityNodeProvider
        assertTrue(provider != null)
        val q = view.keyWithLabel("q")
        val qNode = requireNotNull(provider!!.createAccessibilityNodeInfo(q.id))
        assertEquals("q", qNode.contentDescription.toString())
        val space = view.keyWithLabel("space")
        val spaceNode = requireNotNull(provider.createAccessibilityNodeInfo(space.id))
        assertEquals("Space", spaceNode.contentDescription.toString())
        val delete = view.keyWithLabel("⌫")
        val deleteNode = requireNotNull(provider.createAccessibilityNodeInfo(delete.id))
        assertEquals("Delete", deleteNode.contentDescription.toString())
        val shift = view.keyWithLabel("⇧")
        val shiftNode = requireNotNull(provider.createAccessibilityNodeInfo(shift.id))
        assertEquals("Shift", shiftNode.contentDescription.toString())
    }

    @Test
    fun talkBackVirtualClickCommitsTheKey() {
        val view = layoutView()
        val codes = mutableListOf<Int>()
        view.listener = object : NullKeyKeyboardView.Listener {
            override fun onKey(code: Int) {
                codes += code
            }
        }
        val q = view.keyWithLabel("q")
        val provider = view.accessibilityNodeProvider
        assertTrue(provider != null)
        assertTrue(provider!!.performAction(q.id, android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK, null))
        assertEquals(listOf('q'.code), codes)
    }
}

@RunWith(RobolectricTestRunner::class)
class KeyboardEnginePreferencesTest {

    @Test
    fun customEngineDefaultsToEnabledAndCanBeToggled() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertTrue(KeyboardEnginePreferences.useCustomEngine(context))
        KeyboardEnginePreferences.setUseCustomEngine(context, false)
        assertEquals(false, KeyboardEnginePreferences.useCustomEngine(context))
        KeyboardEnginePreferences.setUseCustomEngine(context, true)
        assertTrue(KeyboardEnginePreferences.useCustomEngine(context))
    }

    @Test
    fun incognitoDefaultsOffAndBlocksLearning() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences(KeyboardEnginePreferences.PREFS, Context.MODE_PRIVATE).edit().clear().commit()
        assertEquals(false, KeyboardEnginePreferences.incognitoEnabled(context))
        assertTrue(KeyboardEnginePreferences.shouldLearn(context))
        KeyboardEnginePreferences.setIncognitoEnabled(context, true)
        assertTrue(KeyboardEnginePreferences.incognitoEnabled(context))
        assertEquals(false, KeyboardEnginePreferences.shouldLearn(context))
        KeyboardEnginePreferences.setIncognitoEnabled(context, false)
        assertTrue(KeyboardEnginePreferences.shouldLearn(context))
    }

    @Test
    fun developerOptionsDefaultOffAndPersist() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences(KeyboardEnginePreferences.PREFS, Context.MODE_PRIVATE).edit().clear().commit()
        assertEquals(false, KeyboardEnginePreferences.developerOptionsEnabled(context))
        KeyboardEnginePreferences.setDeveloperOptionsEnabled(context, true)
        assertTrue(KeyboardEnginePreferences.developerOptionsEnabled(context))
        KeyboardEnginePreferences.setDeveloperOptionsEnabled(context, false)
        assertEquals(false, KeyboardEnginePreferences.developerOptionsEnabled(context))
    }
}
