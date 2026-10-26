package com.nullverse.nullkeyai.writing

import android.content.Context

/**
 * Loads the top-N gesture frequency table shipped in assets. The parsed map is
 * cached for the process so the keyboard does not re-read it on every swipe.
 *
 * Not wired into [com.nullverse.nullkeyai.ime.WordSuggester] yet (PR B).
 */
object BundledGestureFrequency {
    private const val ASSET = "gesture/en_top_n.txt"

    @Volatile
    private var cached: Map<String, Int>? = null

    fun get(context: Context): Map<String, Int> {
        cached?.let { return it }
        return synchronized(this) {
            cached ?: load(context.applicationContext).also { cached = it }
        }
    }

    fun load(context: Context): Map<String, Int> {
        return context.assets.open(ASSET).bufferedReader().useLines { lines ->
            parseLines(lines)
        }
    }

    /**
     * Parses `word<TAB>weight` lines. Blank lines, `#` comments, malformed rows,
     * non-positive weights, and non letter-keys (len&lt;2 or non a–z) are skipped.
     * When the same word appears twice, the higher weight wins.
     */
    internal fun parseLines(lines: Sequence<String>): Map<String, Int> {
        val weights = HashMap<String, Int>(12_288)
        for (raw in lines) {
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith("#")) continue
            val tab = line.indexOf('\t')
            if (tab <= 0 || tab == line.lastIndex) continue
            val word = line.substring(0, tab).trim().lowercase()
            if (word.length < 2 || word.any { it !in 'a'..'z' }) continue
            val weight = line.substring(tab + 1).trim().toIntOrNull() ?: continue
            if (weight <= 0) continue
            val existing = weights[word]
            if (existing == null || weight > existing) {
                weights[word] = weight
            }
        }
        return weights
    }

    /** Test helper: drop the process cache between Robolectric cases. */
    internal fun clearCacheForTests() {
        synchronized(this) { cached = null }
    }
}
