package com.example.flickime.data

import com.example.flickime.model.DirectionalKeySpec

object DefaultSymbolMap {
    // 12 键 * 5 向，共 60 常用符号；中英文符号混排。
    val keys: List<DirectionalKeySpec> = listOf(
        DirectionalKeySpec(center = "，", left = ",", up = "。", right = ".", down = "、"),
        DirectionalKeySpec(center = "？", left = "?", up = "！", right = "!", down = "…"),
        DirectionalKeySpec(center = "：", left = ":", up = "；", right = ";", down = "·"),
        DirectionalKeySpec(center = "（", left = "(", up = "）", right = ")", down = "—"),
        DirectionalKeySpec(center = "“", left = "\"", up = "”", right = "'", down = "‘"),

        DirectionalKeySpec(center = "’", left = "@", up = "#", right = "&", down = "%"),
        DirectionalKeySpec(center = "￥", left = "$", up = "€", right = "£", down = "¥"),
        DirectionalKeySpec(center = "【", left = "[", up = "】", right = "]", down = "{"),
        DirectionalKeySpec(center = "}", left = "<", up = "《", right = ">", down = "》"),
        DirectionalKeySpec(center = "、", left = "\\", up = "/", right = "|", down = "_"),

        DirectionalKeySpec(center = "+", left = "-", up = "=", right = "*", down = "^"),
        DirectionalKeySpec(center = "~", left = "`", up = "§", right = "©", down = "™")
    )
}

