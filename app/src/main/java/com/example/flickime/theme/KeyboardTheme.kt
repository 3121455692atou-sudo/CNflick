package com.example.flickime.theme

data class ThemeColors(
    val keyboardBackground: String,
    val panelBackground: String,
    val keyBackground: String,
    val keyBorder: String,
    val keyText: String,
    val subKeyText: String,
    val accentKeyBackground: String,
    val accentKeyText: String,
    val selectedItemBackground: String,
    val selectedItemText: String,
    val hintText: String
)

data class KeyboardTheme(
    val id: String,
    val name: String,
    val colors: ThemeColors,
    val sourceZipPath: String? = null
)

