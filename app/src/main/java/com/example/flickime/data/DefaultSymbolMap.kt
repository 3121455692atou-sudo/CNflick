package com.example.flickime.data

import com.example.flickime.model.DirectionalKeySpec

object DefaultSymbolMap {
    // 12 键 * 9 向。符号默认支持八方向，括号类保证成对且方向规律一致。
    val keys: List<DirectionalKeySpec> = listOf(
        DirectionalKeySpec(center = "，", left = ",", up = "、", right = "。", down = ".", upLeft = "；", upRight = "：", downLeft = "！", downRight = "？"),
        DirectionalKeySpec(center = "“", left = "‘", up = "\"", right = "”", down = "'", upLeft = "…", upRight = "—", downLeft = "·", downRight = "※"),

        DirectionalKeySpec(center = "@", left = "(", up = "（", right = ")", down = "）", upLeft = "[", upRight = "]", downLeft = "【", downRight = "】"),
        DirectionalKeySpec(center = "#", left = "{", up = "｛", right = "}", down = "｝", upLeft = "<", upRight = ">", downLeft = "《", downRight = "》"),
        DirectionalKeySpec(center = "$", left = "〔", up = "〈", right = "〕", down = "〉", upLeft = "「", upRight = "」", downLeft = "『", downRight = "』"),

        DirectionalKeySpec(center = "+", left = "-", up = "=", right = "*", down = "/", upLeft = "≤", upRight = "≥", downLeft = "≠", downRight = "±"),
        DirectionalKeySpec(center = "￥", left = "$", up = "€", right = "£", down = "¥", upLeft = "₩", upRight = "₽", downLeft = "¢", downRight = "฿"),
        DirectionalKeySpec(center = "%", left = "&", up = "^", right = "|", down = "_", upLeft = "°", upRight = "℃", downLeft = "‰", downRight = "‱"),

        DirectionalKeySpec(center = "\\", left = "／", up = "｜", right = "/", down = "`", upLeft = "¦", upRight = "‖", downLeft = "←", downRight = "→"),
        DirectionalKeySpec(center = ":", left = ";", up = "=", right = ">", down = "<", upLeft = "->", upRight = "=>", downLeft = "<-", downRight = "<="),
        DirectionalKeySpec(center = "§", left = "©", up = "®", right = "™", down = "℗", upLeft = "✓", upRight = "✗", downLeft = "★", downRight = "☆"),
        DirectionalKeySpec(center = "~", left = "·", up = "•", right = "…", down = "※", upLeft = "¿", upRight = "¡", downLeft = "○", downRight = "●")
    )
}
