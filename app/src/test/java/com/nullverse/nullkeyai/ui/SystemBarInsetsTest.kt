package com.nullverse.nullkeyai.ui

import android.app.Activity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SystemBarInsetsTest {

    @Test
    fun barsIncludeSystemBarsAndCutout() {
        val insets = WindowInsetsCompat.Builder()
            .setInsets(WindowInsetsCompat.Type.systemBars(), Insets.of(4, 48, 6, 24))
            .setInsets(WindowInsetsCompat.Type.displayCutout(), Insets.of(0, 12, 0, 0))
            .build()
        val bars = SystemBarInsets.bars(insets)
        assertEquals(4, bars.left)
        assertEquals(48, bars.top)
        assertEquals(6, bars.right)
        assertEquals(24, bars.bottom)
    }

    @Test
    fun activityContentPaddingUsesTheImeWhenTheKeyboardCoversTheNavBar() {
        val insets = WindowInsetsCompat.Builder()
            .setInsets(WindowInsetsCompat.Type.systemBars(), Insets.of(3, 40, 5, 48))
            .setInsets(WindowInsetsCompat.Type.ime(), Insets.of(0, 0, 0, 420))
            .build()
        val padding = SystemBarInsets.activityContentPadding(insets)
        assertEquals(3, padding.left)
        assertEquals(40, padding.top)
        assertEquals(5, padding.right)
        assertEquals(420, padding.bottom)
    }

    @Test
    fun activityContentPaddingKeepsTheNavBarWhenTheImeIsHidden() {
        val insets = WindowInsetsCompat.Builder()
            .setInsets(WindowInsetsCompat.Type.systemBars(), Insets.of(1, 20, 2, 48))
            .setInsets(WindowInsetsCompat.Type.ime(), Insets.NONE)
            .build()
        val padding = SystemBarInsets.activityContentPadding(insets)
        assertEquals(48, padding.bottom)
    }

    @Test
    fun imeRootPaddingIgnoresTheKeyboardInset() {
        val view = View(ApplicationProvider.getApplicationContext())
        SystemBarInsets.pad(view)
        ViewCompat.dispatchApplyWindowInsets(view, keyboardInsets(imeBottom = 400, navBottom = 24))
        assertEquals(24, view.paddingBottom)
        assertEquals(12, view.paddingTop)
    }

    @Test
    fun activityContentPaddingReservesRoomForTheKeyboard() {
        val view = View(ApplicationProvider.getApplicationContext())
        SystemBarInsets.applyActivityContentInsets(view)
        ViewCompat.dispatchApplyWindowInsets(view, keyboardInsets(imeBottom = 360, navBottom = 48))
        assertEquals(360, view.paddingBottom)
        assertEquals(12, view.paddingTop)
    }

    @Test
    fun focusedSearchFieldScrollsAboveTheIme() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val scroll = ScrollView(context)
        val column = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        val spacer = View(context)
        val field = EditText(context)
        column.addView(spacer, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1200))
        column.addView(field, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 80))
        scroll.addView(
            column,
            ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT),
        )
        activity.setContentView(scroll)
        scroll.measure(
            View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(500, View.MeasureSpec.EXACTLY),
        )
        scroll.layout(0, 0, 400, 500)
        assertTrue(field.requestFocus())
        scroll.scrollTo(0, 0)
        assertEquals(0, scroll.scrollY)
        assertTrue(SystemBarInsets.bringFocusedDescendantAboveIme(scroll))
        assertTrue(scroll.scrollY > 0)
        val fieldBottom = field.top + field.height
        assertTrue(fieldBottom <= scroll.scrollY + scroll.height)
    }

    private fun keyboardInsets(imeBottom: Int, navBottom: Int): WindowInsetsCompat {
        return WindowInsetsCompat.Builder()
            .setInsets(WindowInsetsCompat.Type.systemBars(), Insets.of(0, 12, 0, navBottom))
            .setInsets(WindowInsetsCompat.Type.ime(), Insets.of(0, 0, 0, imeBottom))
            .build()
    }
}
