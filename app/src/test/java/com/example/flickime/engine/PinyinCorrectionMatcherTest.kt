package com.example.flickime.engine

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PinyinCorrectionMatcherTest {
    private val frequencies = mapOf(
        "ni" to 100,
        "hao" to 90,
        "kao" to 80,
        "kan" to 70,
        "ke" to 60,
        "gao" to 50,
        "yi" to 40
    )

    @Test
    fun correctsOneMistypedLetterInsideASentence() {
        val queries = PinyinCorrectionMatcher.buildQueries(
            inputSyllables = listOf("ni", "hqo"),
            syllableFrequencies = frequencies
        )

        assertTrue(queries.any { it.query == "nihao" && it.syllables == listOf("ni", "hao") })
    }

    @Test
    fun expandsAnUnfinishedInitialWhenOneFlickWasMissed() {
        val queries = PinyinCorrectionMatcher.buildQueries(
            inputSyllables = listOf("k", "yi"),
            syllableFrequencies = frequencies
        )

        assertTrue(queries.any { it.query == "keyi" && it.syllables == listOf("ke", "yi") })
    }

    @Test
    fun correctsOneWrongFinalFlickEvenWhenItChangesMultipleLetters() {
        val queries = PinyinCorrectionMatcher.buildQueries(
            inputSyllables = listOf("kang", "yi"),
            syllableFrequencies = frequencies
        )

        assertTrue(queries.any { it.query == "kaoyi" && it.syllables == listOf("kao", "yi") })
    }

    @Test
    fun correctsAValidButWrongInitialUsingSentenceContext() {
        val queries = PinyinCorrectionMatcher.buildQueries(
            inputSyllables = listOf("ni", "gao"),
            syllableFrequencies = frequencies
        )

        assertTrue(queries.any { it.query == "nihao" && it.syllables == listOf("ni", "hao") })
    }

    @Test
    fun changesOnlyOneSyllablePerCorrectionQuery() {
        val input = listOf("ni", "hqo")
        val queries = PinyinCorrectionMatcher.buildQueries(input, frequencies)

        assertTrue(queries.isNotEmpty())
        assertTrue(queries.all { query ->
            query.syllables.indices.count { query.syllables[it] != input[it] } == 1
        })
    }

    @Test
    fun spreadsTheCorrectionBudgetAcrossALongSentence() {
        val input = listOf("hqo", "hqo", "hqo", "hqo")
        val queries = PinyinCorrectionMatcher.buildQueries(
            inputSyllables = input,
            syllableFrequencies = mapOf(
                "hao" to 100,
                "heo" to 90,
                "hio" to 80,
                "huo" to 70
            ),
            maxQueries = 4
        )

        input.indices.forEach { changedIndex ->
            assertTrue(queries.any { query ->
                query.syllables.indices.filter { query.syllables[it] != input[it] } == listOf(changedIndex)
            })
        }
    }

    @Test
    fun detectsInsertionDeletionAndSubstitutionButNotTwoEdits() {
        assertTrue(PinyinCorrectionMatcher.isOneEditAway("ka", "kao"))
        assertTrue(PinyinCorrectionMatcher.isOneEditAway("kao", "gao"))
        assertTrue(PinyinCorrectionMatcher.isOneEditAway("kao", "ko"))
        assertFalse(PinyinCorrectionMatcher.isOneEditAway("kao", "gui"))
        assertFalse(PinyinCorrectionMatcher.isOneEditAway("kao", "kao"))
    }

    @Test
    fun ranksSingleCharacterTyposAheadOfBroaderFlickReplacements() {
        val queries = PinyinCorrectionMatcher.buildQueries(
            inputSyllables = listOf("ni", "hqo"),
            syllableFrequencies = frequencies + mapOf("hui" to 1_000)
        )

        assertTrue(queries.first { it.query == "nihao" }.priority < queries.first { it.query == "nihui" }.priority)
    }
}
