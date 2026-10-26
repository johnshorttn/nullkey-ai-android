package com.nullverse.nullkeyai.writing

/**
 * Offline spelling and a small set of high-precision English grammar checks.
 * This is not a full parser: unknown names are left alone unless they are
 * one or two edits from a dictionary word, and only a few usage patterns
 * (articles, repeated words, contractions, and a handful of homophones)
 * are flagged.
 */
class WritingAssistant(private val dictionary: SpellingDictionary) {

    fun suggestionsFor(token: String, limit: Int = 3): List<String> {
        if (limit <= 0 || token.isBlank()) return emptyList()
        contractionSuggestion(token)?.let { return listOf(it).take(limit) }
        if (isKnown(token) || isShortAcronym(token)) return emptyList()
        return dictionary.corrections(token, limit).map { matchCase(token, it) }
    }

    fun review(text: String, maxIssues: Int = 24): List<WritingIssue> {
        if (text.isBlank() || maxIssues <= 0) return emptyList()
        val tokens = TOKEN.findAll(text).toList()
        val issues = ArrayList<WritingIssue>(minOf(8, maxIssues))
        for (index in tokens.indices) {
            if (issues.size >= maxIssues) break
            val token = tokens[index]
            val word = token.value
            val previous = tokens.getOrNull(index - 1)
            val next = tokens.getOrNull(index + 1)
            val issue = repeatedWord(text, previous, token)
                ?: article(word, next?.value, token.range)
                ?: homophone(word, next?.value, token.range)
                ?: contraction(word, token.range)
                ?: capitalizeI(word, token.range)
                ?: spelling(word, token.range)
            if (issue != null) issues += issue
        }
        return issues
    }

    /** Letter weights from the spelling list. Swipe uses these only after the seed misses. */
    fun gestureLexicon(): Map<String, Int> = dictionary.gestureWeights()

    fun isKnown(word: String): Boolean {
        val token = word.lowercase()
        if (dictionary.contains(token)) return true
        if (token.endsWith("'s") && dictionary.contains(token.removeSuffix("'s"))) return true
        return false
    }

    private fun repeatedWord(text: String, previous: MatchResult?, token: MatchResult): WritingIssue? {
        if (previous == null) return null
        if (!previous.value.equals(token.value, ignoreCase = true)) return null
        val gap = text.substring(previous.range.last + 1, token.range.first)
        if (gap.isEmpty() || gap.any { !it.isWhitespace() }) return null
        return WritingIssue(
            start = previous.range.last + 1,
            end = token.range.last + 1,
            code = WritingIssue.REPEATED_WORD,
            suggestions = listOf(""),
        )
    }

    private fun article(word: String, next: String?, range: IntRange): WritingIssue? {
        if (next.isNullOrBlank()) return null
        val wantsAn = wantsAn(next)
        val replacement = when (word.lowercase()) {
            "a" -> if (wantsAn) "an" else null
            "an" -> if (!wantsAn) "a" else null
            else -> null
        } ?: return null
        return WritingIssue(
            start = range.first,
            end = range.last + 1,
            code = WritingIssue.ARTICLE,
            suggestions = listOf(matchCase(word, replacement)),
        )
    }

    private fun homophone(word: String, next: String?, range: IntRange): WritingIssue? {
        if (next.isNullOrBlank()) return null
        val replacement = when (word.lowercase()) {
            "your" -> if (next.lowercase() in YOUR_CONTEXT) "you're" else null
            "its" -> if (next.lowercase() in ITS_CONTEXT) "it's" else null
            "their" -> if (next.lowercase() in THEIR_CONTEXT) "there" else null
            else -> null
        } ?: return null
        return WritingIssue(
            start = range.first,
            end = range.last + 1,
            code = WritingIssue.HOMOPHONE,
            suggestions = listOf(matchCase(word, replacement)),
        )
    }

    private fun capitalizeI(word: String, range: IntRange): WritingIssue? {
        if (word != "i") return null
        return WritingIssue(
            start = range.first,
            end = range.last + 1,
            code = WritingIssue.CAPITALIZE,
            suggestions = listOf("I"),
        )
    }

    private fun contraction(word: String, range: IntRange): WritingIssue? {
        val suggestion = contractionSuggestion(word) ?: return null
        return WritingIssue(
            start = range.first,
            end = range.last + 1,
            code = WritingIssue.CONTRACTION,
            suggestions = listOf(suggestion),
        )
    }

    private fun spelling(word: String, range: IntRange): WritingIssue? {
        if (isKnown(word) || isShortAcronym(word)) return null
        val suggestions = dictionary.corrections(word, 3).map { matchCase(word, it) }
        if (suggestions.isEmpty()) return null
        return WritingIssue(
            start = range.first,
            end = range.last + 1,
            code = WritingIssue.SPELLING,
            suggestions = suggestions,
        )
    }

    private fun contractionSuggestion(word: String): String? {
        if (isKnown(word)) return null
        val mapped = CONTRACTIONS[word.lowercase()] ?: return null
        return when {
            word == "IM" -> "I'M"
            word.equals("im", ignoreCase = true) -> "I'm"
            word.length > 1 && word.all { !it.isLetter() || it.isUpperCase() } -> mapped.uppercase()
            word.firstOrNull()?.isUpperCase() == true -> mapped.replaceFirstChar { it.titlecase() }
            else -> mapped
        }
    }

    private fun wantsAn(next: String): Boolean {
        val word = next.lowercase()
        if (word in AN_BEFORE_CONSONANT_LETTER) return true
        if (word in A_BEFORE_VOWEL_LETTER) return false
        return word.isNotEmpty() && word[0] in "aeiou"
    }

    private fun isShortAcronym(word: String): Boolean =
        word.length in 2..5 && word.all { it.isLetter() && it.isUpperCase() }

    companion object {
        private val TOKEN = Regex("[A-Za-z]+(?:'[A-Za-z]+)?")

        private val AN_BEFORE_CONSONANT_LETTER = setOf(
            "honest", "hour", "honor", "honour", "heir",
        )
        private val A_BEFORE_VOWEL_LETTER = setOf(
            "one", "once", "university", "user", "european", "uniform",
            "unique", "union", "unit", "useful", "used", "usual", "universe",
        )
        private val YOUR_CONTEXT = setOf("a", "an", "the", "welcome")
        private val ITS_CONTEXT = setOf("a", "an", "the")
        private val THEIR_CONTEXT = setOf("is", "are", "was", "were")

        /** Only forms that are not themselves dictionary words. */
        private val CONTRACTIONS = mapOf(
            "dont" to "don't",
            "doesnt" to "doesn't",
            "didnt" to "didn't",
            "isnt" to "isn't",
            "arent" to "aren't",
            "wasnt" to "wasn't",
            "werent" to "weren't",
            "havent" to "haven't",
            "hasnt" to "hasn't",
            "hadnt" to "hadn't",
            "wouldnt" to "wouldn't",
            "couldnt" to "couldn't",
            "shouldnt" to "shouldn't",
            "im" to "I'm",
            "ive" to "I've",
            "youre" to "you're",
            "theyre" to "they're",
            "theres" to "there's",
            "whats" to "what's",
        )

        fun matchCase(original: String, suggestion: String): String {
            val letters = original.filter { it.isLetter() }
            if (letters.length > 1 && letters.all { it.isUpperCase() }) {
                return suggestion.uppercase()
            }
            if (original.firstOrNull()?.isUpperCase() == true) {
                return suggestion.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
            }
            return suggestion
        }
    }
}

data class WritingIssue(
    val start: Int,
    val end: Int,
    val code: String,
    val suggestions: List<String>,
) {
    companion object {
        const val SPELLING = "spelling"
        const val REPEATED_WORD = "repeated_word"
        const val ARTICLE = "article"
        const val CONTRACTION = "contraction"
        const val HOMOPHONE = "homophone"
        const val CAPITALIZE = "capitalize"
    }
}

object WritingReplacement {
    fun apply(text: String, issue: WritingIssue, suggestion: String): String {
        if (issue.start < 0 || issue.end > text.length || issue.start > issue.end) return text
        if (suggestion.isEmpty() && issue.code != WritingIssue.REPEATED_WORD) return text
        return text.replaceRange(issue.start, issue.end, suggestion)
    }
}

data class WritingFix(
    val label: String,
    val issue: WritingIssue,
    val suggestion: String,
)

object WritingFixes {
    fun from(text: String, issues: List<WritingIssue>, limit: Int = 8): List<WritingFix> {
        val fixes = ArrayList<WritingFix>(limit)
        for (issue in issues) {
            for (suggestion in issue.suggestions) {
                if (fixes.size >= limit) return fixes
                val original = text.substring(issue.start, issue.end).trim()
                val label = if (suggestion.isEmpty()) {
                    "Delete repeated $original"
                } else {
                    "$original → $suggestion"
                }
                fixes += WritingFix(label, issue, suggestion)
            }
        }
        return fixes
    }
}

/**
 * Prefix completions from the learned keyboard model win. Spelling fills the
 * strip only when the typed token is not a prefix of a known word.
 */
object SuggestionPlan {
    fun forTypedWord(
        typed: String,
        completions: List<String>,
        spelling: List<String>,
        max: Int,
    ): List<String> {
        if (typed.isBlank() || max <= 0) return emptyList()
        if (completions.isNotEmpty()) return completions.take(max)
        return spelling.take(max)
    }
}
