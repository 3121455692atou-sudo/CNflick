package com.example.flickime.engine

object ZhuyinConverter {
    private val initialMap = mapOf(
        "ㄅ" to "b", "ㄆ" to "p", "ㄇ" to "m", "ㄈ" to "f",
        "ㄉ" to "d", "ㄊ" to "t", "ㄋ" to "n", "ㄌ" to "l",
        "ㄍ" to "g", "ㄎ" to "k", "ㄏ" to "h",
        "ㄐ" to "j", "ㄑ" to "q", "ㄒ" to "x",
        "ㄓ" to "zh", "ㄔ" to "ch", "ㄕ" to "sh", "ㄖ" to "r",
        "ㄗ" to "z", "ㄘ" to "c", "ㄙ" to "s"
    )

    private val finalMap = mapOf(
        "" to "",
        "ㄚ" to "a", "ㄛ" to "o", "ㄜ" to "e", "ㄝ" to "e",
        "ㄞ" to "ai", "ㄟ" to "ei", "ㄠ" to "ao", "ㄡ" to "ou",
        "ㄢ" to "an", "ㄣ" to "en", "ㄤ" to "ang", "ㄥ" to "eng", "ㄦ" to "er",
        "ㄧ" to "i", "ㄧㄚ" to "ia", "ㄧㄝ" to "ie", "ㄧㄠ" to "iao", "ㄧㄡ" to "iu",
        "ㄧㄢ" to "ian", "ㄧㄣ" to "in", "ㄧㄤ" to "iang", "ㄧㄥ" to "ing",
        "ㄨ" to "u", "ㄨㄚ" to "ua", "ㄨㄛ" to "uo", "ㄨㄞ" to "uai", "ㄨㄟ" to "ui",
        "ㄨㄢ" to "uan", "ㄨㄣ" to "un", "ㄨㄤ" to "uang", "ㄨㄥ" to "ong",
        "ㄩ" to "v", "ㄩㄝ" to "ve", "ㄩㄢ" to "van", "ㄩㄣ" to "vn", "ㄩㄥ" to "iong"
    )

    fun normalize(input: String): String {
        return input.filter { isZhuyin(it) || it == 'ˊ' || it == 'ˇ' || it == 'ˋ' || it == '˙' }
    }

    fun toPinyin(zhuyinSyllable: String): String {
        val raw = normalize(zhuyinSyllable)
            .replace("ˊ", "")
            .replace("ˇ", "")
            .replace("ˋ", "")
            .replace("˙", "")
        if (raw.isBlank()) return ""

        val initial = initialMap.entries.firstOrNull { raw.startsWith(it.key) }
        val initialZhuyin = initial?.key.orEmpty()
        val initialPinyin = initial?.value.orEmpty()
        val finalZhuyin = raw.removePrefix(initialZhuyin)
        val finalPinyin = finalMap[finalZhuyin] ?: return raw

        if (initialPinyin.isBlank()) {
            return applyZeroInitialRule(finalPinyin)
        }

        return when (initialPinyin) {
            "j", "q", "x" -> when (finalPinyin) {
                "v" -> "${initialPinyin}u"
                "ve" -> "${initialPinyin}ue"
                "van" -> "${initialPinyin}uan"
                "vn" -> "${initialPinyin}un"
                else -> initialPinyin + finalPinyin
            }
            else -> initialPinyin + finalPinyin
        }
    }

    private fun applyZeroInitialRule(final: String): String {
        return when (final) {
            "i" -> "yi"
            "ia" -> "ya"
            "ie" -> "ye"
            "iao" -> "yao"
            "iu" -> "you"
            "ian" -> "yan"
            "in" -> "yin"
            "iang" -> "yang"
            "ing" -> "ying"

            "u" -> "wu"
            "ua" -> "wa"
            "uo" -> "wo"
            "uai" -> "wai"
            "ui" -> "wei"
            "uan" -> "wan"
            "un" -> "wen"
            "uang" -> "wang"
            "ong" -> "weng"

            "v" -> "yu"
            "ve" -> "yue"
            "van" -> "yuan"
            "vn" -> "yun"
            "iong" -> "yong"
            else -> final
        }
    }

    private fun isZhuyin(c: Char): Boolean = c in '\u3105'..'\u312F'
}
