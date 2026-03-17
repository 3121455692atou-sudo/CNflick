package com.example.flickime.model

enum class FlickDirection {
    Center, Left, Up, Right, Down, UpLeft, UpRight, DownLeft, DownRight
}

enum class KeyZone {
    Shengmu, Yunmu
}

data class FlickKeySpec(
    val center: String,
    val left: String,
    val up: String,
    val right: String,
    val down: String,
    val upLeft: String = "",
    val upRight: String = "",
    val downLeft: String = "",
    val downRight: String = "",
    val zone: KeyZone
)
