package com.nullverse.nullkeyai

import androidx.test.core.app.ApplicationProvider
import com.nullverse.nullkeyai.ime.WordSuggester
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class WordSuggesterTest {

    private fun newSuggester() =
        WordSuggester.get(ApplicationProvider.getApplicationContext())

    @Test
    fun suggests_seededWordsByPrefix() {
        val s = newSuggester()
        val results = s.suggest("th", 5)
        assertTrue(results.contains("the"))
        assertTrue(results.all { it.startsWith("th") })
    }

    @Test
    fun blankPrefix_returnsNothing() {
        assertTrue(newSuggester().suggest("", 3).isEmpty())
    }

    @Test
    fun exactPrefixWord_isNotSuggestedBackToItself() {
        val results = newSuggester().suggest("the", 5)
        assertFalse(results.contains("the"))
    }

    @Test
    fun learnedWord_isSuggestedAndRanksByFrequency() {
        val s = newSuggester()
        repeat(5) { s.learn("nullverse") }
        s.learn("nullkey")
        val results = s.suggest("null", 3)
        assertTrue(results.contains("nullverse"))
        // More frequent word ranks ahead of the less frequent one.
        assertTrue(results.indexOf("nullverse") < results.indexOf("nullkey"))
    }

    @Test
    fun learn_ignoresTooShortOrNonAlpha() {
        val s = newSuggester()
        s.learn("a")
        s.learn("h3llo")
        assertEquals(emptyList<String>(), s.suggest("h3", 3))
    }
}
