package com.example.flickime.engine

class SyllableComposer {
    private var shengmu: String? = null

    fun currentComposing(): String = shengmu.orEmpty()

    fun onShengmu(part: String): String {
        shengmu = part
        return part
    }

    fun onYunmu(part: String): String {
        val full = shengmu.orEmpty() + part
        shengmu = null
        return full
    }

    fun reset() {
        shengmu = null
    }
}
