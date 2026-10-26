package com.nullverse.nullkeyai.writing

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BundledGestureFrequencyTest {
    @Before
    fun clearCache() {
        BundledGestureFrequency.clearCacheForTests()
    }

    @Test
    fun bundledTopNIsAboutTenThousand() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val weights = BundledGestureFrequency.load(context)
        assertEquals(10_000, weights.size)
    }

    @Test
    fun commonWordsHaveHighWeights() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val weights = BundledGestureFrequency.get(context)
        assertTrue(weights.containsKey("the"))
        assertTrue(weights.containsKey("and"))
        assertTrue(weights.containsKey("you"))
        assertTrue(weights.containsKey("that"))
        assertTrue(weights.containsKey("have"))
        assertTrue(weights.getValue("the") > weights.getValue("have"))
        assertTrue(weights.getValue("and") > weights.getValue("have"))
        assertTrue(weights.getValue("the") > 1_000)
    }

    @Test
    fun getReturnsCachedMap() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val first = BundledGestureFrequency.get(context)
        val second = BundledGestureFrequency.get(context)
        assertTrue(first === second)
    }

    @Test
    fun invalidLinesAreSkipped() {
        val weights = BundledGestureFrequency.parseLines(
            sequenceOf(
                "# comment",
                "",
                "the\t100",
                "no-tab-here",
                "a\t5",
                "don't\t9",
                "Hello\t50",
                "hello\t75",
                "bad\tnotanumber",
                "zero\t0",
                "neg\t-3",
                "ok\t",
                "\t12",
                "world\t10",
            ),
        )
        assertEquals(mapOf("the" to 100, "hello" to 75, "world" to 10), weights)
    }
}
