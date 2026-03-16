package com.example.flickime.engine

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class ShortcutEntry(
    val code: String,
    val text: String
)

object ShortcutManager {
    private const val PREFS = "flick_ime"
    private const val KEY_SHORTCUTS = "custom_shortcuts"
    private const val KEY_SHORTCUT_VERSION = "custom_shortcut_version"

    fun getAll(context: Context): List<ShortcutEntry> {
        val arr = loadArray(context)
        val out = mutableListOf<ShortcutEntry>()
        for (i in 0 until arr.length()) {
            val item = arr.optJSONObject(i) ?: continue
            val code = normalizeCode(item.optString("code"))
            val text = item.optString("text").trim()
            if (code.isBlank() || text.isBlank()) continue
            out += ShortcutEntry(code = code, text = text)
        }
        return out
    }

    fun query(context: Context, code: String, limit: Int): List<String> {
        if (limit <= 0) return emptyList()
        val normalized = normalizeCode(code)
        if (normalized.isBlank()) return emptyList()
        val out = mutableListOf<String>()
        getAll(context).forEach { entry ->
            if (entry.code != normalized) return@forEach
            if (!out.contains(entry.text)) out += entry.text
        }
        return out.take(limit)
    }

    fun upsert(context: Context, code: String, text: String) {
        val normalized = normalizeCode(code)
        val value = text.trim()
        if (normalized.isBlank() || value.isBlank()) return
        val current = getAll(context).toMutableList()
        current.removeAll { it.code == normalized && it.text == value }
        current.add(0, ShortcutEntry(code = normalized, text = value))
        saveAll(context, current)
    }

    fun remove(context: Context, entry: ShortcutEntry) {
        val current = getAll(context).toMutableList()
        current.removeAll { it.code == entry.code && it.text == entry.text }
        saveAll(context, current)
    }

    fun getVersion(context: Context): Int {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(KEY_SHORTCUT_VERSION, 0)
    }

    private fun bumpVersion(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit().putInt(KEY_SHORTCUT_VERSION, prefs.getInt(KEY_SHORTCUT_VERSION, 0) + 1).apply()
    }

    private fun saveAll(context: Context, list: List<ShortcutEntry>) {
        val arr = JSONArray()
        list.forEach { entry ->
            arr.put(
                JSONObject().apply {
                    put("code", entry.code)
                    put("text", entry.text)
                }
            )
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_SHORTCUTS, arr.toString())
            .apply()
        bumpVersion(context)
    }

    private fun loadArray(context: Context): JSONArray {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_SHORTCUTS, "[]").orEmpty()
        return runCatching { JSONArray(raw) }.getOrDefault(JSONArray())
    }

    private fun normalizeCode(code: String): String {
        return code.lowercase().filter { it in 'a'..'z' || it in '0'..'9' || it == '_' }
    }
}
