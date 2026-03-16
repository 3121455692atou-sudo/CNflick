package com.example.flickime.theme

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.zip.ZipFile

object ThemeManager {
    private const val PREFS = "flick_ime"
    private const val KEY_CURRENT_THEME_ID = "current_theme_id"
    private const val KEY_IMPORTED_THEMES = "imported_themes"

    private val defaultTheme = KeyboardTheme(
        id = "cnflick.theme.default_light",
        name = "默认浅色",
        colors = ThemeColors(
            keyboardBackground = "#AEB7C5",
            panelBackground = "#BBC4D2",
            keyBackground = "#EEF1F5",
            keyBorder = "#A6AFBC",
            keyText = "#111827",
            subKeyText = "#4B5563",
            accentKeyBackground = "#1677FF",
            accentKeyText = "#FFFFFF",
            selectedItemBackground = "#6B7280",
            selectedItemText = "#FFFFFF",
            hintText = "#6B7280"
        )
    )

    private val mikuTheme = KeyboardTheme(
        id = "com.cnflick.theme.hatsune_miku_teal",
        name = "初音未来 Teal",
        colors = ThemeColors(
            keyboardBackground = "#D7F4F1",
            panelBackground = "#BEEDE8",
            keyBackground = "#ECFBF9",
            keyBorder = "#5BBEB5",
            keyText = "#103A38",
            subKeyText = "#2A6C67",
            accentKeyBackground = "#39C5BB",
            accentKeyText = "#FFFFFF",
            selectedItemBackground = "#2FA59D",
            selectedItemText = "#FFFFFF",
            hintText = "#2A6C67"
        )
    )

    fun getBuiltInThemes(): List<KeyboardTheme> = listOf(defaultTheme, mikuTheme)

    fun getCurrentTheme(context: Context): KeyboardTheme {
        val currentId = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_CURRENT_THEME_ID, defaultTheme.id)
            .orEmpty()
        return getAllThemes(context).firstOrNull { it.id == currentId } ?: defaultTheme
    }

    fun setCurrentTheme(context: Context, themeId: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_CURRENT_THEME_ID, themeId)
            .apply()
    }

    fun getAllThemes(context: Context): List<KeyboardTheme> {
        val deduped = LinkedHashMap<String, KeyboardTheme>()
        getBuiltInThemes().forEach { theme -> deduped[theme.id] = theme }
        loadImportedThemes(context).forEach { theme ->
            // Keep the latest imported variant for the same id.
            deduped[theme.id] = theme
        }
        return deduped.values.toList()
    }

    fun importThemeZip(context: Context, uri: Uri): KeyboardTheme {
        val themesDir = File(context.filesDir, "themes").apply { mkdirs() }
        val copiedZip = File(themesDir, "imported_${System.currentTimeMillis()}.cnflick-theme.zip")
        context.contentResolver.openInputStream(uri)?.use { input ->
            copiedZip.outputStream().use { output -> input.copyTo(output) }
        } ?: error("无法读取主题包")

        val parsed = parseThemeZip(copiedZip)
        val imported = loadImportedThemeJson(context)

        val rewritten = JSONArray()
        for (i in 0 until imported.length()) {
            val item = imported.optJSONObject(i) ?: continue
            if (item.optString("id") == parsed.id) continue
            rewritten.put(item)
        }
        rewritten.put(
            JSONObject().apply {
                put("id", parsed.id)
                put("name", parsed.name)
                put("zipPath", copiedZip.absolutePath)
            }
        )

        saveImportedThemeJson(context, rewritten)
        setCurrentTheme(context, parsed.id)
        return parsed.copy(sourceZipPath = copiedZip.absolutePath)
    }

    private fun loadImportedThemes(context: Context): List<KeyboardTheme> {
        val arr = loadImportedThemeJson(context)
        val out = mutableListOf<KeyboardTheme>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val path = o.optString("zipPath")
            if (path.isBlank()) continue
            val file = File(path)
            if (!file.exists()) continue
            val parsed = runCatching { parseThemeZip(file) }.getOrNull() ?: continue
            out += parsed.copy(
                id = o.optString("id", parsed.id),
                name = o.optString("name", parsed.name),
                sourceZipPath = path
            )
        }
        return out
    }

    private fun parseThemeZip(file: File): KeyboardTheme {
        ZipFile(file).use { zip ->
            val entry = zip.getEntry("theme.json") ?: error("主题包缺少 theme.json")
            val json = zip.getInputStream(entry).bufferedReader(Charsets.UTF_8).use { it.readText() }
            val root = JSONObject(json)
            val colors = root.optJSONObject("colors") ?: JSONObject()
            val fallback = defaultTheme.colors
            return KeyboardTheme(
                id = root.optString("id", "theme.${file.nameWithoutExtension}"),
                name = root.optString("name", file.nameWithoutExtension),
                colors = ThemeColors(
                    keyboardBackground = colors.optString("keyboardBackground", fallback.keyboardBackground),
                    panelBackground = colors.optString("panelBackground", fallback.panelBackground),
                    keyBackground = colors.optString("keyBackground", fallback.keyBackground),
                    keyBorder = colors.optString("keyBorder", fallback.keyBorder),
                    keyText = colors.optString("keyText", fallback.keyText),
                    subKeyText = colors.optString("subKeyText", fallback.subKeyText),
                    accentKeyBackground = colors.optString("accentKeyBackground", fallback.accentKeyBackground),
                    accentKeyText = colors.optString("accentKeyText", fallback.accentKeyText),
                    selectedItemBackground = colors.optString("selectedItemBackground", fallback.selectedItemBackground),
                    selectedItemText = colors.optString("selectedItemText", fallback.selectedItemText),
                    hintText = colors.optString("hintText", fallback.hintText)
                )
            )
        }
    }

    private fun loadImportedThemeJson(context: Context): JSONArray {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_IMPORTED_THEMES, "[]")
            .orEmpty()
        return runCatching { JSONArray(raw) }.getOrDefault(JSONArray())
    }

    private fun saveImportedThemeJson(context: Context, arr: JSONArray) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_IMPORTED_THEMES, arr.toString())
            .apply()
    }
}

