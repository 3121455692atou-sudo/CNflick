package com.example.flickime.model

enum class InputLanguage(val id: String) {
    PINYIN("pinyin"),
    ZHUYIN("zhuyin"),
    JAPANESE("japanese"),
    SHAPE("shape");

    companion object {
        fun fromId(raw: String): InputLanguage {
            return values().firstOrNull { it.id == raw } ?: PINYIN
        }
    }
}
