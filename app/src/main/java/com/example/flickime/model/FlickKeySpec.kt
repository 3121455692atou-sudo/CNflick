package com.example.flickime.model

enum class FlickDirection {
    Center, Left, Up, Right, Down
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
    val zone: KeyZone
)
