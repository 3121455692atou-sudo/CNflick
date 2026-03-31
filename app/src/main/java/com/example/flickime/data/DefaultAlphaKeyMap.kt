package com.example.flickime.data

import com.example.flickime.model.DirectionalKeySpec

object DefaultAlphaKeyMap {
    // 12 键英文映射（传统 ABC 分组），仅使用左/中/右。
    val keys: List<DirectionalKeySpec> = listOf(
        DirectionalKeySpec(center = "b", left = "a", up = "", right = "c", down = ""),
        DirectionalKeySpec(center = "e", left = "d", up = "", right = "f", down = ""),
        DirectionalKeySpec(center = "h", left = "g", up = "", right = "i", down = ""),
        DirectionalKeySpec(center = "k", left = "j", up = "", right = "l", down = ""),
        DirectionalKeySpec(center = "n", left = "m", up = "", right = "o", down = ""),
        DirectionalKeySpec(center = "q", left = "p", up = "", right = "r", down = ""),
        DirectionalKeySpec(center = "t", left = "s", up = "", right = "u", down = ""),
        DirectionalKeySpec(center = "w", left = "v", up = "", right = "x", down = ""),
        DirectionalKeySpec(center = "z", left = "y", up = "", right = "'", down = ""),
        DirectionalKeySpec(center = ",", left = ";", up = "", right = ".", down = ""),
        DirectionalKeySpec(center = "大写锁定", left = "", up = "", right = "", down = ""),
        DirectionalKeySpec(center = "?", left = "!", up = "", right = "\"", down = "")
    )
}
