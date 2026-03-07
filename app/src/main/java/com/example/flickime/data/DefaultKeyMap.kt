package com.example.flickime.data

import com.example.flickime.model.FlickKeySpec
import com.example.flickime.model.KeyZone

object DefaultKeyMap {
    // 布局顺序：1-4 第一行，5-8 第二行，9-12 第三行。
    val keys: List<FlickKeySpec> = listOf(
        // [1]~[5] 声母 + 扩展音节
        FlickKeySpec(center = "b", left = "p", up = "m", right = "f", down = "w", zone = KeyZone.Shengmu),
        FlickKeySpec(center = "d", left = "t", up = "n", right = "l", down = "y", zone = KeyZone.Shengmu),
        FlickKeySpec(center = "g", left = "k", up = "h", right = "j", down = "q", zone = KeyZone.Shengmu),
        FlickKeySpec(center = "x", left = "zh", up = "ch", right = "sh", down = "r", zone = KeyZone.Shengmu),
        FlickKeySpec(center = "z", left = "c", up = "s", right = "ü", down = "v", zone = KeyZone.Shengmu),

        // [6]~[12] 韵母区
        FlickKeySpec(center = "a", left = "ai", up = "an", right = "ang", down = "ao", zone = KeyZone.Yunmu),
        FlickKeySpec(center = "e", left = "ei", up = "en", right = "eng", down = "ou", zone = KeyZone.Yunmu),
        FlickKeySpec(center = "i", left = "ie", up = "in", right = "ing", down = "iu", zone = KeyZone.Yunmu),
        FlickKeySpec(center = "ia", left = "iong", up = "ian", right = "iang", down = "iao", zone = KeyZone.Yunmu),
        FlickKeySpec(center = "u", left = "ui", up = "un", right = "ong", down = "uo", zone = KeyZone.Yunmu),
        FlickKeySpec(center = "ua", left = "uai", up = "uan", right = "uang", down = "o", zone = KeyZone.Yunmu),
        FlickKeySpec(center = "。", left = "，", up = "！", right = "？", down = "er", zone = KeyZone.Yunmu)
    )
}
