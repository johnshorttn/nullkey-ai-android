package com.nullverse.nullkeyai.writing

/**
 * In-memory English word list ordered from more common to less common.
 * Lookups and edit-distance suggestions stay on device; the list is bundled
 * in the APK and never fetched over the network.
 */
class SpellingDictionary private constructor(
    private val rank: Map<String, Int>,
) {
    fun contains(word: String): Boolean = rank.containsKey(normalize(word))

    fun rankOf(word: String): Int? = rank[normalize(word)]

    /**
     * Lowercase corrections for an unknown token. Distance-1 edits win.
     * Distance-2 is used only when nothing is one edit away, and only for
     * mid-length tokens, so a long paste cannot scan the whole list.
     */
    fun corrections(word: String, limit: Int = 3): List<String> {
        val token = normalize(word)
        if (limit <= 0 || token.length < 2 || token.any { it !in 'a'..'z' && it != '\'' }) {
            return emptyList()
        }
        if (rank.containsKey(token)) return emptyList()
        val distanceOne = knownEdits(edits(token))
        val pool = if (distanceOne.isNotEmpty()) {
            distanceOne
        } else if (token.length in 4..12) {
            val found = ArrayList<String>(24)
            val seen = HashSet<String>()
            for (once in edits(token)) {
                for (twice in edits(once)) {
                    if (twice != token && rank.containsKey(twice) && seen.add(twice)) {
                        found.add(twice)
                        if (found.size >= 40) break
                    }
                }
                if (found.size >= 40) break
            }
            found
        } else {
            emptyList()
        }
        return pool
            .sortedWith(compareBy<String> { rank[it] ?: Int.MAX_VALUE }.thenBy { it.length }.thenBy { it })
            .take(limit)
    }

    private fun knownEdits(candidates: Set<String>): List<String> =
        candidates.filter { rank.containsKey(it) }

    companion object {
        fun fromFrequencyOrder(words: Sequence<String>): SpellingDictionary {
            val rank = HashMap<String, Int>(4096)
            var index = 0
            for (raw in words) {
                val word = normalize(raw)
                if (word.isEmpty() || word in rank) continue
                if (word.any { it !in 'a'..'z' && it != '\'' }) continue
                rank[word] = index
                index++
            }
            return SpellingDictionary(rank)
        }

        fun normalize(word: String): String = word.trim().lowercase()

        private val alphabet: CharArray = (('a'..'z').toList() + '\'').toCharArray()

        internal fun edits(word: String): Set<String> {
            val results = HashSet<String>(word.length * 56)
            for (i in word.indices) {
                val left = word.substring(0, i)
                val right = word.substring(i + 1)
                results.add(left + right)
                if (i + 1 < word.length) {
                    results.add(left + word[i + 1] + word[i] + word.substring(i + 2))
                }
                for (c in alphabet) {
                    results.add(left + c + right)
                    results.add(left + c + word.substring(i))
                }
            }
            for (c in alphabet) results.add(word + c)
            results.remove(word)
            return results
        }
    }
}
