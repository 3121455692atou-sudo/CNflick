package com.example.flickime.engine

import com.example.flickime.model.KeyZone
import java.util.Locale

/**
 * Classifies standard pinyin fragments independently of their physical key.
 *
 * A custom layout can move an initial onto a key that was originally configured
 * as a final key (and vice versa). The token itself therefore takes precedence
 * over the key's fallback zone.
 */
internal object PinyinTokenClassifier {
    val initialsLongestFirst: List<String> = listOf(
        "zh", "ch", "sh",
        "b", "p", "m", "f",
        "d", "t", "n", "l",
        "g", "k", "h",
        "j", "q", "x",
        "r", "z", "c", "s",
        "y", "w"
    )
    private val finals: Set<String> = setOf(
        "a", "ai", "an", "ang", "ao",
        "o", "ong", "ou",
        "e", "ei", "en", "eng", "er",
        "i", "ia", "ian", "iang", "iao", "ie", "in", "ing", "iong", "iu",
        "u", "ua", "uai", "uan", "uang", "ue", "ui", "un", "uo",
        "v", "ve", "van", "vn"
    )

    fun resolveZone(text: String, fallback: KeyZone): KeyZone {
        return when {
            isInitial(text) -> KeyZone.Shengmu
            isFinal(text) -> KeyZone.Yunmu
            else -> fallback
        }
    }

    fun isInitial(text: String): Boolean {
        return normalize(text) in initialsLongestFirst
    }

    fun isFinal(text: String): Boolean {
        return normalize(text) in finals
    }

    private fun normalize(text: String): String {
        return text.lowercase(Locale.ROOT)
            .replace("ü", "v")
            .replace("u:", "v")
    }
}
