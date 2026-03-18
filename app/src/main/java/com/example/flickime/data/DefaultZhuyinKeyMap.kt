package com.example.flickime.data

import com.example.flickime.model.FlickKeySpec
import com.example.flickime.model.KeyZone

object DefaultZhuyinKeyMap {
    // 12键布局（1-9,*,0,#），按用户指定最终键位：
    // 1: 双唇音, 2: 舌尖音, 3: 后舌音, 4: 平舌音, 5: 卷舌音
    // 6: 核心元音, 7: 介音扩展, 8: 鼻韵尾, 9: 基础复合韵母
    // *: i系复合, 0: i鼻化, #: u/ü系复合
    val keys: List<FlickKeySpec> = listOf(
        FlickKeySpec(center = "ㄅ", left = "ㄇ", up = "ㄆ", right = "ㄈ", down = "", zone = KeyZone.Shengmu),
        FlickKeySpec(center = "ㄉ", left = "ㄋ", up = "ㄊ", right = "ㄌ", down = "", zone = KeyZone.Shengmu),
        FlickKeySpec(center = "ㄍ", left = "ㄏ", up = "ㄎ", right = "ㄐ", down = "ㄑ", zone = KeyZone.Shengmu),
        FlickKeySpec(center = "ㄗ", left = "ㄙ", up = "ㄘ", right = "", down = "", zone = KeyZone.Shengmu),
        FlickKeySpec(center = "ㄓ", left = "ㄕ", up = "ㄔ", right = "ㄖ", down = "", zone = KeyZone.Shengmu),

        FlickKeySpec(center = "ㄚ", left = "ㄜ", up = "ㄛ", right = "ㄝ", down = "ㄧ", zone = KeyZone.Yunmu),
        FlickKeySpec(center = "ㄨ", left = "", up = "ㄩ", right = "", down = "ㄒ", zone = KeyZone.Yunmu),
        FlickKeySpec(center = "ㄢ", left = "ㄤ", up = "ㄣ", right = "ㄥ", down = "ㄦ", zone = KeyZone.Yunmu),
        FlickKeySpec(center = "ㄞ", left = "ㄠ", up = "ㄟ", right = "ㄡ", down = "", zone = KeyZone.Yunmu),
        FlickKeySpec(center = "ㄧㄚ", left = "ㄧㄠ", up = "ㄧㄝ", right = "ㄧㄡ", down = "ㄧㄢ", zone = KeyZone.Yunmu),
        FlickKeySpec(center = "ㄧㄣ", left = "ㄧㄥ", up = "ㄧㄤ", right = "", down = "", zone = KeyZone.Yunmu),
        FlickKeySpec(center = "ㄨㄚ", left = "ㄨㄞ", up = "ㄨㄛ", right = "ㄨㄟ", down = "ㄨㄢ", zone = KeyZone.Yunmu)
    )
}
