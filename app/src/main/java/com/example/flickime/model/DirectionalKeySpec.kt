package com.example.flickime.model

data class DirectionalKeySpec(
    val center: String,
    val left: String,
    val up: String,
    val right: String,
    val down: String,
    val upLeft: String = "",
    val upRight: String = "",
    val downLeft: String = "",
    val downRight: String = ""
)
