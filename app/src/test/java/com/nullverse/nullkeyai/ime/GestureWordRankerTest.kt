package com.nullverse.nullkeyai.ime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GestureWordRankerTest {

    private val lexicon = mapOf(
        "hello" to 5,
        "help" to 2,
        "hero" to 1,
        "world" to 3,
        "the" to 8,
        "there" to 4,
        "to" to 7,
        "two" to 3,
        "thanks" to 2,
        "that" to 6,
    )

    @Test
    fun exactCollapsedPathRanksFirst() {
        assertEquals("hello", GestureWordRanker.rank("heelllo", lexicon, 3).first())
        assertTrue("world" !in GestureWordRanker.rank("heelllo", lexicon, 3))
    }

    @Test
    fun extraPathKeysStillPreferTheTighterWord() {
        val results = GestureWordRanker.rank("thge", lexicon, 3)
        assertEquals("the", results.first())
        assertTrue("there" !in results)
    }

    @Test
    fun missedInteriorLetterStillMatchesLongerWord() {
        val results = GestureWordRanker.rank("hlo", lexicon, 3)
        assertEquals("hello", results.first())
    }

    @Test
    fun shortWordsDoNotAllowMissedLetters() {
        assertTrue(GestureWordRanker.rank("to", lexicon, 3).contains("to"))
        assertTrue("two" !in GestureWordRanker.rank("to", lexicon, 3))
    }

    @Test
    fun endpointsMustMatch() {
        assertTrue(GestureWordRanker.rank("help", lexicon, 3).none { it == "hello" })
        assertNull(GestureWordRanker.score("help", "hello"))
    }

    @Test
    fun frequencyBreaksEqualAlignment() {
        val results = GestureWordRanker.rank("hello", lexicon, 3)
        assertEquals("hello", results.first())
    }

    @Test
    fun shortPathIsIgnored() {
        assertTrue(GestureWordRanker.rank("h", lexicon, 3).isEmpty())
        assertTrue(GestureWordRanker.rank("", lexicon, 3).isEmpty())
    }

    @Test
    fun applyCasingFollowsShiftedPath() {
        assertEquals("Hello", GestureWordRanker.applyCasing("hello", "He"))
        assertEquals("hello", GestureWordRanker.applyCasing("hello", "he"))
        assertEquals("Thanks", GestureWordRanker.applyCasing("thanks", "Tgvanks"))
    }

    @Test
    fun swipeCommitUsesRankedCasingAndDropsUnknownPaths() {
        val resolved = SwipeCommit.resolve("Heelllo", listOf("hello", "help"))
        assertEquals("Hello", resolved?.committed)
        assertEquals(listOf("Hello", "Help"), resolved?.suggestions)
        assertNull(SwipeCommit.resolve("qw", emptyList()))
    }

    @Test
    fun unmatchedPathDoesNotGuessAWord() {
        assertTrue(GestureWordRanker.rank("qzx", lexicon, 3).isEmpty())
    }

    @Test
    fun repeatedHelloRankingStaysCheapEnoughToPreviewDuringADrag() {
        val path = "hgftrertyhjklo"
        val start = System.nanoTime()
        repeat(40) {
            assertEquals("hello", GestureWordRanker.rank(path, lexicon, 3).first())
        }
        val elapsedMs = (System.nanoTime() - start) / 1_000_000.0
        // Dev-machine measurement after buffer reuse: 14.4ms for these 40 ranks.
        assertTrue("40 seed ranks of a hello trail took ${elapsedMs}ms", elapsedMs < 80.0)
    }

    @Test
    fun straightFlickStillResolvesHelloWhenInteriorKeysAreSkipped() {
        assertEquals("hello", GestureWordRanker.rank("hjio", lexicon, 3).first())
        assertEquals("hello", GestureWordRanker.rank("hjkio", lexicon, 3).first())
    }

    @Test
    fun straightFlickPrefersTheOverLongerEndpointMatches() {
        val results = GestureWordRanker.rank("tre", lexicon, 3)
        assertEquals("the", results.first())
        assertTrue(results.indexOf("the") < results.indexOf("there"))
    }

    @Test
    fun chordFromTToSPrefersThisOverThanks() {
        val withThis = lexicon + ("this" to 9)
        assertEquals("this", GestureWordRanker.rank("trds", withThis, 3).first())
    }

    @Test
    fun topRowFlickResolvesQuipFromTheFallbackLexicon() {
        val withQuip = lexicon + ("quip" to 1)
        assertEquals("quip", GestureWordRanker.rank("qwertyuiop", withQuip, 3).first())
        assertEquals("quip", GestureWordRanker.rank("qp", withQuip, 3).first())
    }

    @Test
    fun zeroMissShortWordBeatsAFlickThatOnlyMissesIntoHello() {
        val withHo = lexicon + ("ho" to 1)
        assertEquals("ho", GestureWordRanker.rank("hjio", withHo, 3).first())
    }
}
