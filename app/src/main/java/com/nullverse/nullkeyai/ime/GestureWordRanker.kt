package com.nullverse.nullkeyai.ime

/**
 * On-device swipe-word ranking. Matches a visited-key path to dictionary words
 * without network or ML: first/last letters must match, extras on the path are
 * cheap, and a small number of missed interior letters is allowed so a swipe
 * that skips a key can still resolve.
 */
object GestureWordRanker {
    const val SKIP_PATH_COST = 1
    const val MISS_WORD_COST = 12

    fun rank(path: String, counts: Map<String, Int>, max: Int = 3): List<String> {
        val gesture = lettersOf(path)
        if (gesture.length < 2) return emptyList()
        return counts.asSequence()
            .mapNotNull { (word, frequency) ->
                val score = score(gesture, word) ?: return@mapNotNull null
                Triple(word, score, frequency)
            }
            .sortedWith(
                compareBy<Triple<String, Int, Int>> { it.second }
                    .thenByDescending { it.third }
                    .thenBy { it.first.length }
                    .thenBy { it.first }
            )
            .map { it.first }
            .take(max)
            .toList()
    }

    fun applyCasing(word: String, path: String): String {
        val first = path.trim().firstOrNull { it.isLetter() } ?: return word
        return if (first.isUpperCase()) {
            word.replaceFirstChar { it.uppercaseChar() }
        } else {
            word
        }
    }

    internal fun score(path: String, word: String): Int? {
        val gesture = collapseRepeats(lettersOf(path))
        val target = collapseRepeats(lettersOf(word))
        if (gesture.length < 2 || target.length < 2) return null
        if (gesture.first() != target.first() || gesture.last() != target.last()) return null
        return alignmentCost(gesture, target)
    }

    internal fun collapseRepeats(value: String): String = buildString(value.length) {
        value.forEach { character ->
            if (isEmpty() || last() != character) append(character)
        }
    }

    private fun lettersOf(value: String): String =
        value.trim().lowercase().filter { it.isLetter() }

    private fun maxMisses(wordLength: Int): Int = when {
        wordLength <= 3 -> 0
        wordLength <= 5 -> 1
        else -> 2
    }

    /**
     * Minimum edit cost to consume [gesture] against [word]. Both strings are
     * already collapsed and share first/last letters.
     */
    private fun alignmentCost(gesture: String, word: String): Int? {
        val n = gesture.length
        val m = word.length
        val maxMisses = maxMisses(m)
        data class State(val i: Int, val j: Int, val misses: Int)
        val best = HashMap<State, Int>()
        val queue = ArrayDeque<State>()

        fun offer(state: State, cost: Int) {
            if (state.misses > maxMisses) return
            if (state.i > n || state.j > m) return
            val previous = best[state]
            if (previous == null || cost < previous) {
                best[state] = cost
                queue.add(state)
            }
        }

        offer(State(1, 1, 0), 0)
        var result: Int? = null
        while (queue.isNotEmpty()) {
            val state = queue.removeFirst()
            val cost = best[state] ?: continue
            val i = state.i
            val j = state.j
            val misses = state.misses
            if (j == m) {
                if (i == n) result = minOf(result ?: cost, cost)
                continue
            }
            if (i == n) continue

            val lastWord = j == m - 1
            val lastPath = i == n - 1
            when {
                lastWord && lastPath -> {
                    if (gesture[i] == word[j]) offer(State(n, m, misses), cost)
                }
                lastWord -> offer(State(i + 1, j, misses), cost + SKIP_PATH_COST)
                lastPath -> offer(State(i, j + 1, misses + 1), cost + MISS_WORD_COST)
                else -> {
                    if (gesture[i] == word[j]) {
                        offer(State(i + 1, j + 1, misses), cost)
                    }
                    offer(State(i + 1, j, misses), cost + SKIP_PATH_COST)
                    if (j > 0) {
                        offer(State(i, j + 1, misses + 1), cost + MISS_WORD_COST)
                    }
                }
            }
        }
        return result
    }
}

data class SwipeCommitResult(
    val committed: String,
    val suggestions: List<String>,
)

object SwipeCommit {
    fun resolve(path: String, ranked: List<String>): SwipeCommitResult? {
        if (ranked.isEmpty()) return null
        val cased = ranked.map { GestureWordRanker.applyCasing(it, path) }
        return SwipeCommitResult(committed = cased.first(), suggestions = cased)
    }
}
