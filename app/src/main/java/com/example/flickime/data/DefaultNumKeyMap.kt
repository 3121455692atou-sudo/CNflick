package com.example.flickime.data

import com.example.flickime.model.DirectionalKeySpec

object DefaultNumKeyMap {
    // 数字区中心键不重复数字；四则运算保留在底行中心或相邻滑动位。
    val keys: List<DirectionalKeySpec> = listOf(
        DirectionalKeySpec(center = "1", left = "+", up = "", right = "", down = ""),
        DirectionalKeySpec(center = "2", left = "", up = "-", right = "", down = ""),
        DirectionalKeySpec(center = "3", left = "", up = "", right = "*", down = ""),
        DirectionalKeySpec(center = "4", left = "(", up = "", right = "", down = ""),
        DirectionalKeySpec(center = "5", left = "", up = ".", right = "", down = ""),
        DirectionalKeySpec(center = "6", left = "", up = "", right = "/", down = ""),
        DirectionalKeySpec(center = "7", left = "", up = "", right = "", down = ""),
        DirectionalKeySpec(center = "8", left = "", up = "=", right = "", down = ""),
        DirectionalKeySpec(center = "9", left = "", up = "", right = "", down = ""),
        DirectionalKeySpec(center = "+", left = "*", up = "(", right = "", down = ""),
        DirectionalKeySpec(center = "0", left = ".", up = "%", right = "", down = ""),
        DirectionalKeySpec(center = "-", left = "", up = ")", right = "/", down = "")
    )
}
