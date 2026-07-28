package com.example.flickime.engine

import com.example.flickime.model.KeyZone
import org.junit.Assert.assertEquals
import org.junit.Test

class PinyinTokenClassifierTest {
    @Test
    fun initialsRemainInitialsWhenMovedOntoFinalKeys() {
        PinyinTokenClassifier.initialsLongestFirst.forEach { initial ->
            assertEquals(
                "$initial should not inherit the physical key's final zone",
                KeyZone.Shengmu,
                PinyinTokenClassifier.resolveZone(initial, KeyZone.Yunmu)
            )
        }
    }

    @Test
    fun finalsRemainFinalsWhenMovedOntoInitialKeys() {
        val finals = listOf(
            "a", "ai", "an", "ang", "ao",
            "e", "ei", "en", "eng", "er",
            "i", "ia", "ian", "iang", "iao", "ie", "in", "ing", "iong", "iu",
            "o", "ong", "ou",
            "u", "ua", "uai", "uan", "uang", "ue", "ui", "un", "uo",
            "ü", "v", "ve", "van", "vn", "u:", "u:e"
        )

        finals.forEach { final ->
            assertEquals(
                "$final should not inherit the physical key's initial zone",
                KeyZone.Yunmu,
                PinyinTokenClassifier.resolveZone(final, KeyZone.Shengmu)
            )
        }
    }

    @Test
    fun movedKAndAoResolveToOneComposableInitialFinalPair() {
        assertEquals(
            KeyZone.Shengmu,
            PinyinTokenClassifier.resolveZone("k", KeyZone.Yunmu)
        )
        assertEquals(
            KeyZone.Yunmu,
            PinyinTokenClassifier.resolveZone("ao", KeyZone.Yunmu)
        )
    }

    @Test
    fun movedLAndAiResolveToOneComposableInitialFinalPair() {
        assertEquals(
            KeyZone.Shengmu,
            PinyinTokenClassifier.resolveZone("l", KeyZone.Yunmu)
        )
        assertEquals(
            KeyZone.Yunmu,
            PinyinTokenClassifier.resolveZone("ai", KeyZone.Shengmu)
        )
    }

    @Test
    fun unknownCustomTokensKeepTheConfiguredFallbackZone() {
        assertEquals(
            KeyZone.Shengmu,
            PinyinTokenClassifier.resolveZone("custom", KeyZone.Shengmu)
        )
        assertEquals(
            KeyZone.Yunmu,
            PinyinTokenClassifier.resolveZone("custom", KeyZone.Yunmu)
        )
        assertEquals(
            KeyZone.Shengmu,
            PinyinTokenClassifier.resolveZone("apple", KeyZone.Shengmu)
        )
    }
}
