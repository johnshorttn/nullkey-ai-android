package com.nullverse.nullkeyai.ime

import android.content.Context
import android.content.SharedPreferences

/**
 * A lightweight, on-device word-completion engine. It keeps a frequency map of
 * words the user types (seeded with common English words) and suggests
 * completions for the current prefix, most-frequent first. No network or ML
 * model is involved, so it stays private and dependency-free.
 */
class WordSuggester private constructor(
    private val prefs: SharedPreferences,
    private val counts: MutableMap<String, Int>
) {

    /** Return up to [max] suggested words that start with [prefix]. */
    fun suggest(prefix: String, max: Int = 3): List<String> {
        val p = prefix.trim().lowercase()
        if (p.isEmpty()) return emptyList()
        return counts.asSequence()
            .filter { it.key.startsWith(p) && it.key != p }
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }
                .thenBy { it.key.length })
            .map { it.key }
            .take(max)
            .toList()
    }

    /** Resolve a swipe-key path to the closest known words using ordered key matches. */
    fun suggestGesture(path: String, max: Int = 3): List<String> =
        GestureWordRanker.rank(path, counts, max)

    /**
     * Record that [word] was used, increasing its future suggestion priority.
     * Pass [enabled] = false for incognito / no-learn sessions so nothing is
     * persisted. Seeded dictionary suggestions still work.
     */
    fun learn(word: String, enabled: Boolean = true) {
        if (!enabled) return
        val w = word.trim().lowercase()
        if (w.length < MIN_LEARN_LENGTH || !w.all { it.isLetter() }) return
        counts[w] = (counts[w] ?: 0) + 1
        persist()
    }

    private fun persist() {
        // Keep only the most frequent words to bound storage.
        val top = counts.entries
            .sortedByDescending { it.value }
            .take(MAX_WORDS)
        val encoded = top.joinToString(SEP) { "${it.key}:${it.value}" }
        prefs.edit().putString(KEY_WORDS, encoded).apply()
    }

    companion object {
        private const val PREFS = "nullkey_suggester"
        private const val KEY_WORDS = "words"
        private const val SEP = ","
        private const val MAX_WORDS = 2000
        private const val MIN_LEARN_LENGTH = 2

        private val SEED = listOf(
            "the", "and", "you", "that", "have", "for", "not", "with", "this",
            "but", "his", "from", "they", "say", "her", "she", "will", "one",
            "all", "would", "there", "their", "what", "out", "about", "who",
            "get", "which", "when", "make", "can", "like", "time", "just",
            "him", "know", "take", "people", "into", "year", "your", "good",
            "some", "could", "them", "see", "other", "than", "then", "now",
            "look", "only", "come", "its", "over", "think", "also", "back",
            "after", "use", "two", "how", "our", "work", "first", "well",
            "way", "even", "new", "want", "because", "any", "these", "give",
            "day", "most", "clipboard", "keyboard", "nullkey", "hello", "thanks",
            "to", "of", "in", "on", "is", "it", "be", "as", "at", "or", "an",
            "we", "do", "if", "so", "up", "no", "yes", "go", "me", "my",
            "please", "help", "here", "need", "great"
        )

        fun get(context: Context): WordSuggester {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val counts = decode(prefs.getString(KEY_WORDS, null))
            if (counts.isEmpty()) SEED.forEach { counts[it] = 1 }
            return WordSuggester(prefs, counts)
        }

        private fun decode(encoded: String?): MutableMap<String, Int> {
            val map = HashMap<String, Int>()
            if (encoded.isNullOrBlank()) return map
            for (pair in encoded.split(SEP)) {
                val idx = pair.lastIndexOf(':')
                if (idx <= 0) continue
                val word = pair.substring(0, idx)
                val count = pair.substring(idx + 1).toIntOrNull() ?: continue
                map[word] = count
            }
            return map
        }
    }
}
