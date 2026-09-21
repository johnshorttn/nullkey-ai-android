package com.nullverse.nullkeyai.ui

import android.view.View
import androidx.core.graphics.Insets
import androidx.core.view.WindowInsetsCompat
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
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
    fun padAppliesListenerWithoutCrashing() {
        val view = View(ApplicationProvider.getApplicationContext())
        SystemBarInsets.pad(view)
        assertEquals(0, view.paddingTop)
    }
}
