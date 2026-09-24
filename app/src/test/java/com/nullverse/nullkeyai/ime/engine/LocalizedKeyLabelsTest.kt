package com.nullverse.nullkeyai.ime.engine

import androidx.test.core.app.ApplicationProvider
import com.nullverse.nullkeyai.R
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LocalizedKeyLabelsTest {

    @Test
    fun functionKeysUseStringResources() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val space = KeySpec(code = KeyCodes.SPACE, label = "space")
        val enter = KeySpec(code = KeyCodes.DONE, label = "enter")
        val symbols = KeySpec(code = KeyCodes.MODE_CHANGE, label = "?123", isModifier = true)
        val letters = KeySpec(code = KeyCodes.MODE_CHANGE, label = "ABC", isModifier = true)
        assertEquals(context.getString(R.string.key_space), LocalizedKeyLabels.resolve(context, space, "space"))
        assertEquals(context.getString(R.string.key_enter), LocalizedKeyLabels.resolve(context, enter, "enter"))
        assertEquals(context.getString(R.string.key_symbols), LocalizedKeyLabels.resolve(context, symbols, "?123"))
        assertEquals(context.getString(R.string.key_letters), LocalizedKeyLabels.resolve(context, letters, "ABC"))
        assertEquals("q", LocalizedKeyLabels.resolve(context, KeySpec(code = 'q'.code, label = "q"), "q"))
    }
}
