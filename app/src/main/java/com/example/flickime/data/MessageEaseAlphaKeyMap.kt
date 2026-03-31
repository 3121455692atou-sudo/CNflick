package com.example.flickime.data

import com.example.flickime.model.DirectionalKeySpec

object MessageEaseAlphaKeyMap {
    // 12 键英文映射（MessagEase 风格）：中心键为 A N I / H O R / T E S。
    val keys: List<DirectionalKeySpec> = listOf(
        DirectionalKeySpec(center = "a", left = "", up = "", right = "", down = "", upLeft = "", upRight = "", downLeft = "", downRight = "v"),
        DirectionalKeySpec(center = "n", left = "", up = "", right = "", down = "u", upLeft = "", upRight = "", downLeft = "", downRight = ""),
        DirectionalKeySpec(center = "i", left = "", up = "", right = "", down = "", upLeft = "", upRight = "", downLeft = "g", downRight = ""),
        DirectionalKeySpec(center = "h", left = "", up = "", right = "f", down = "", upLeft = "", upRight = "", downLeft = "", downRight = ""),
        DirectionalKeySpec(center = "o", left = "c", up = "y", right = "l", down = "d", upLeft = "b", upRight = "p", downLeft = "x", downRight = "j"),
        DirectionalKeySpec(center = "r", left = "m", up = "", right = "", down = "", upLeft = "", upRight = "", downLeft = "", downRight = ""),
        DirectionalKeySpec(center = "t", left = "", up = "", right = "", down = "", upLeft = "", upRight = "w", downLeft = "", downRight = ""),
        DirectionalKeySpec(center = "e", left = "", up = "q", right = "z", down = "", upLeft = "", upRight = "", downLeft = "", downRight = ""),
        DirectionalKeySpec(center = "s", left = "", up = "", right = "", down = "", upLeft = "k", upRight = "", downLeft = "", downRight = ""),
        DirectionalKeySpec(center = ",", left = ";", up = "", right = "!", down = ""),
        DirectionalKeySpec(center = "大写锁定", left = "", up = "", right = "", down = ""),
        DirectionalKeySpec(center = "?", left = "\"", up = "", right = "-", down = "")
    )
}
