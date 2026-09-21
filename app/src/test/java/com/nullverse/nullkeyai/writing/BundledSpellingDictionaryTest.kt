package com.nullverse.nullkeyai.writing

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BundledSpellingDictionaryTest {
    @Test
    fun assetDictionaryCorrectsACommonMisspellingOffline() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val dictionary = BundledSpellingDictionary.load(context)
        val assistant = WritingAssistant(dictionary)
        assertTrue(dictionary.contains("receive"))
        assertTrue(dictionary.contains("don't"))
        assertTrue(dictionary.contains("nullkey"))
        assertEquals("receive", assistant.suggestionsFor("recieve").first())
        assertTrue(assistant.suggestionsFor("the").isEmpty())
    }
}
