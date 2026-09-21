package com.nullverse.nullkeyai.writing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WritingAssistantTest {
    private val dictionary = SpellingDictionary.fromFrequencyOrder(
        sequenceOf(
            "the", "their", "there", "they", "receive", "apple", "banana",
            "university", "hour", "honest", "don't", "i'm", "you're", "it's",
            "welcome", "is", "are", "was", "were", "an", "a", "hello", "spelling",
            "cat",
        )
    )
    private val assistant = WritingAssistant(dictionary)

    @Test
    fun spellingSuggestsANearDictionaryWord() {
        assertEquals(listOf("receive"), assistant.suggestionsFor("recieve"))
        assertEquals(listOf("the"), assistant.suggestionsFor("teh"))
        assertTrue(assistant.suggestionsFor("the").isEmpty())
    }

    @Test
    fun distanceTwoStillFindsAWordWhenNothingIsOneEditAway() {
        assertEquals(listOf("hello"), assistant.suggestionsFor("hloo"))
    }

    @Test
    fun preservesCapitalization() {
        assertEquals(listOf("Receive"), assistant.suggestionsFor("Recieve"))
        assertEquals(listOf("RECEIVE"), assistant.suggestionsFor("RECIEVE"))
    }

    @Test
    fun contractionsAndArticlesAndHomophones() {
        assertEquals("don't", assistant.review("i dont know").single { it.code == WritingIssue.CONTRACTION }.suggestions.single())
        assertEquals("I'm", assistant.suggestionsFor("im").single())
        assertEquals("I", assistant.review("i think").first().suggestions.single())
        val article = assistant.review("a apple").single()
        assertEquals(WritingIssue.ARTICLE, article.code)
        assertEquals("an", article.suggestions.single())
        assertEquals("a", assistant.review("an banana").single().suggestions.single())
        assertEquals("an", assistant.review("a hour").single().suggestions.single())
        assertTrue(assistant.review("a university").isEmpty())
        assertEquals("you're", assistant.review("your welcome").single().suggestions.single())
        assertEquals("it's", assistant.review("its the cat").single().suggestions.single())
        assertEquals("there", assistant.review("their is").single().suggestions.single())
    }

    @Test
    fun repeatedWordCanBeDeleted() {
        val text = "the the cat"
        val issue = assistant.review(text).single()
        assertEquals(WritingIssue.REPEATED_WORD, issue.code)
        assertEquals("the cat", WritingReplacement.apply(text, issue, ""))
    }

    @Test
    fun skipsShortAcronymsAndUnknownNamesWithoutANearWord() {
        assertTrue(assistant.review("OCR").isEmpty())
        assertTrue(assistant.suggestionsFor("Zxqvpl").isEmpty())
        assertTrue(assistant.review("").isEmpty())
    }

    @Test
    fun fixesListOneRowPerSuggestion() {
        val text = "teh"
        val fixes = WritingFixes.from(text, assistant.review(text))
        assertEquals(listOf("teh → the"), fixes.map { it.label })
        assertEquals("the", WritingReplacement.apply(text, fixes.single().issue, fixes.single().suggestion))
    }

    @Test
    fun suggestionPlanPrefersPrefixCompletions() {
        assertEquals(listOf("the"), SuggestionPlan.forTypedWord("th", listOf("the"), listOf("teh"), 3))
        assertEquals(listOf("the"), SuggestionPlan.forTypedWord("teh", emptyList(), listOf("the"), 3))
        assertTrue(SuggestionPlan.forTypedWord("", listOf("the"), listOf("the"), 3).isEmpty())
    }
}
