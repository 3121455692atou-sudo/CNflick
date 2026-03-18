package com.example.flickime.data

import com.example.flickime.model.FlickKeySpec
import com.example.flickime.model.KeyZone

object DefaultJapaneseKeyMap {
    // 参照常见日语12键 flick 布局，右下角为「゛゜小」变换键。
    val keys: List<FlickKeySpec> = listOf(
        FlickKeySpec(center = "あ", left = "い", up = "う", right = "え", down = "お", zone = KeyZone.Yunmu),
        FlickKeySpec(center = "か", left = "き", up = "く", right = "け", down = "こ", zone = KeyZone.Yunmu),
        FlickKeySpec(center = "さ", left = "し", up = "す", right = "せ", down = "そ", zone = KeyZone.Yunmu),
        FlickKeySpec(center = "た", left = "ち", up = "つ", right = "て", down = "と", zone = KeyZone.Yunmu),
        FlickKeySpec(center = "な", left = "に", up = "ぬ", right = "ね", down = "の", zone = KeyZone.Yunmu),

        FlickKeySpec(center = "は", left = "ひ", up = "ふ", right = "へ", down = "ほ", zone = KeyZone.Yunmu),
        FlickKeySpec(center = "ま", left = "み", up = "む", right = "め", down = "も", zone = KeyZone.Yunmu),
        FlickKeySpec(center = "や", left = "ゃ", up = "ゆ", right = "ょ", down = "よ", zone = KeyZone.Yunmu),
        FlickKeySpec(center = "ら", left = "り", up = "る", right = "れ", down = "ろ", zone = KeyZone.Yunmu),
        FlickKeySpec(center = "、", left = "。", up = "？", right = "！", down = "…", zone = KeyZone.Yunmu),
        FlickKeySpec(center = "わ", left = "を", up = "ん", right = "ー", down = "～", zone = KeyZone.Yunmu),
        FlickKeySpec(center = "゛゜小", left = "", up = "", right = "", down = "", zone = KeyZone.Yunmu)
    )
}
