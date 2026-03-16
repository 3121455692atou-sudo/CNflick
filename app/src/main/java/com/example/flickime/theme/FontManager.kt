package com.example.flickime.theme

import android.content.Context
import android.graphics.Typeface
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class KeyboardFont(
    val id: String,
    val name: String,
    val filePath: String? = null
)

object FontManager {
    private const val PREFS = "flick_settings"
    private const val KEY_CURRENT_FONT_ID = "current_font_id"
    private const val KEY_IMPORTED_FONTS = "imported_fonts"

    private val builtinFonts = listOf(
        KeyboardFont(id = "font.system", name = "系统默认"),
        KeyboardFont(id = "font.jetbrains_mono", name = "JetBrains Mono")
    )

    fun getAllFonts(context: Context): List<KeyboardFont> {
        return builtinFonts + loadImportedFonts(context)
    }

    fun getCurrentFontId(context: Context): String {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_CURRENT_FONT_ID, "font.system")
            .orEmpty()
    }

    fun setCurrentFontId(context: Context, fontId: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_CURRENT_FONT_ID, fontId)
            .apply()
    }

    fun resolveTypeface(context: Context): Typeface {
        val id = getCurrentFontId(context)
        return when {
            id == "font.system" -> Typeface.DEFAULT
            id == "font.jetbrains_mono" -> runCatching {
                Typeface.createFromAsset(context.assets, "fonts/JetBrainsMono-Regular.ttf")
            }.getOrDefault(Typeface.MONOSPACE)
            id.startsWith("font.custom.") -> {
                val custom = loadImportedFonts(context).firstOrNull { it.id == id }
                val path = custom?.filePath
                if (path.isNullOrBlank()) Typeface.DEFAULT else runCatching {
                    Typeface.createFromFile(path)
                }.getOrDefault(Typeface.DEFAULT)
            }
            else -> Typeface.DEFAULT
        }
    }

    fun importFont(context: Context, uri: Uri): KeyboardFont {
        val fontsDir = File(context.filesDir, "fonts").apply { mkdirs() }
        val file = File(fontsDir, "font_${System.currentTimeMillis()}.ttf")
        context.contentResolver.openInputStream(uri)?.use { input ->
            file.outputStream().use { output -> input.copyTo(output) }
        } ?: error("无法读取字体文件")
        val font = KeyboardFont(
            id = "font.custom.${file.nameWithoutExtension}",
            name = "自定义字体 ${file.nameWithoutExtension.takeLast(6)}",
            filePath = file.absolutePath
        )
        val arr = loadImportedFontJson(context)
        arr.put(JSONObject().apply {
            put("id", font.id)
            put("name", font.name)
            put("path", font.filePath)
        })
        saveImportedFontJson(context, arr)
        setCurrentFontId(context, font.id)
        return font
    }

    private fun loadImportedFonts(context: Context): List<KeyboardFont> {
        val arr = loadImportedFontJson(context)
        val out = mutableListOf<KeyboardFont>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val path = o.optString("path")
            if (path.isBlank() || !File(path).exists()) continue
            out += KeyboardFont(
                id = o.optString("id"),
                name = o.optString("name", "自定义字体"),
                filePath = path
            )
        }
        return out
    }

    private fun loadImportedFontJson(context: Context): JSONArray {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_IMPORTED_FONTS, "[]")
            .orEmpty()
        return runCatching { JSONArray(raw) }.getOrDefault(JSONArray())
    }

    private fun saveImportedFontJson(context: Context, arr: JSONArray) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_IMPORTED_FONTS, arr.toString())
            .apply()
    }
}

