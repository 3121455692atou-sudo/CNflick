package com.example.flickime.engine

import kotlin.math.abs

internal data class PinyinCorrectionQuery(
    val query: String,
    val syllables: List<String>,
    val priority: Int
)

private data class PinyinCorrectionOption(
    val syllable: String,
    val priority: Int
)

/**
 * Generates conservative pinyin corrections: one syllable may contain a
 * one-character typo, replace one initial/final flick, or expand an unfinished
 * initial to a complete syllable.
 */
internal object PinyinCorrectionMatcher {
    fun buildQueries(
        inputSyllables: List<String>,
        syllableFrequencies: Map<String, Int>,
        maxQueries: Int = 240
    ): List<PinyinCorrectionQuery> {
        if (inputSyllables.isEmpty() || syllableFrequencies.isEmpty() || maxQueries <= 0) {
            return emptyList()
        }

        val optionsByIndex = inputSyllables.map { correctionOptions(it, syllableFrequencies) }
        val out = LinkedHashMap<String, PinyinCorrectionQuery>()
        var optionIndex = 0
        while (out.size < maxQueries && optionsByIndex.any { optionIndex < it.size }) {
            inputSyllables.indices.forEach { index ->
                if (out.size >= maxQueries) return@forEach
                val corrected = optionsByIndex[index].getOrNull(optionIndex) ?: return@forEach
                val parts = inputSyllables.toMutableList()
                parts[index] = corrected.syllable
                val query = parts.joinToString("")
                out.putIfAbsent(query, PinyinCorrectionQuery(query, parts, corrected.priority))
            }
            optionIndex++
        }
        return out.values.toList()
    }

    private fun correctionOptions(
        input: String,
        syllableFrequencies: Map<String, Int>
    ): List<PinyinCorrectionOption> {
        if (input.isBlank()) return emptyList()
        val unfinishedInitial = PinyinTokenClassifier.isInitial(input)
        val inputInitial = initialOf(input)
        val inputFinal = input.removePrefix(inputInitial)

        return syllableFrequencies.keys.asSequence()
            .filter { it != input }
            .filter { candidate ->
                if (unfinishedInitial) {
                    candidate.startsWith(input)
                } else {
                    val candidateInitial = initialOf(candidate)
                    val candidateFinal = candidate.removePrefix(candidateInitial)
                    isOneEditAway(input, candidate) ||
                        (inputInitial.isNotEmpty() && candidateInitial == inputInitial) ||
                        (inputFinal.isNotEmpty() && candidateFinal == inputFinal)
                }
            }
            .sortedWith(
                compareBy<String> { if (isOneEditAway(input, it)) 0 else 1 }
                    .thenByDescending { initialOf(it) == inputInitial }
                    .thenBy { abs(it.length - input.length) }
                    .thenByDescending { syllableFrequencies[it] ?: 0 }
                    .thenBy { it }
            )
            .take(24)
            .map { candidate ->
                PinyinCorrectionOption(
                    syllable = candidate,
                    priority = when {
                        isOneEditAway(input, candidate) -> 0
                        unfinishedInitial -> 1
                        else -> 2
                    }
                )
            }
            .toList()
    }

    private fun initialOf(syllable: String): String {
        return PinyinTokenClassifier.initialsLongestFirst
            .firstOrNull { syllable.startsWith(it) }
            .orEmpty()
    }

    internal fun isOneEditAway(left: String, right: String): Boolean {
        if (left == right || abs(left.length - right.length) > 1) return false

        if (left.length == right.length) {
            return left.indices.count { left[it] != right[it] } == 1
        }

        val shorter = if (left.length < right.length) left else right
        val longer = if (left.length < right.length) right else left
        var shortIndex = 0
        var longIndex = 0
        var skipped = false

        while (shortIndex < shorter.length && longIndex < longer.length) {
            if (shorter[shortIndex] == longer[longIndex]) {
                shortIndex++
                longIndex++
            } else {
                if (skipped) return false
                skipped = true
                longIndex++
            }
        }
        return true
    }
}
