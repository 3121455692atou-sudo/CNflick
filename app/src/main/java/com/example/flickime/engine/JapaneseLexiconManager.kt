package com.example.flickime.engine

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

private data class JapaneseLexiconRecord(
    val id: String,
    val name: String,
    val builtIn: Boolean,
    val path: String?
)

data class JapaneseLexiconInfo(
    val id: String,
    val name: String,
    val isBuiltIn: Boolean
)

object JapaneseLexiconManager {
    private const val PREFS = "flick_ime"
    private const val KEY_IMPORTED_LEXICONS = "jp_imported_lexicons"
    private const val KEY_ENABLED_LEXICONS = "jp_enabled_lexicons"
    private const val KEY_LEXICON_VERSION = "jp_enabled_lexicon_version"

    private const val BUILTIN_CORE_ID = "jp.lexicon.builtin.core"

    private val builtIns = listOf(
        JapaneseLexiconRecord(
            BUILTIN_CORE_ID,
            "内置日语词库（扩展版）",
            true,
            "asset://lexicons/japanese_keyboard_core.tsv"
        )
    )

    private val cache = mutableMapOf<String, Map<String, List<String>>>()

    fun getAllLexicons(context: Context): List<JapaneseLexiconInfo> {
        return (builtIns + loadImported(context)).map { JapaneseLexiconInfo(it.id, it.name, it.builtIn) }
    }

    fun getEnabledLexiconIds(context: Context): Set<String> {
        val prefs = prefs(context)
        val stored = prefs.getStringSet(KEY_ENABLED_LEXICONS, null)
        return if (stored.isNullOrEmpty()) {
            builtIns.map { it.id }.toSet()
        } else {
            stored.toSet()
        }
    }

    fun setLexiconEnabled(context: Context, lexiconId: String, enabled: Boolean) {
        val current = getEnabledLexiconIds(context).toMutableSet()
        if (enabled) current += lexiconId else current -= lexiconId
        if (current.isEmpty()) current += BUILTIN_CORE_ID
        prefs(context).edit().putStringSet(KEY_ENABLED_LEXICONS, current).apply()
        bumpVersion(context)
    }

    fun resetToDefault(context: Context) {
        prefs(context).edit().putStringSet(KEY_ENABLED_LEXICONS, builtIns.map { it.id }.toSet()).apply()
        bumpVersion(context)
    }

    fun importLexicon(context: Context, uri: Uri): JapaneseLexiconInfo {
        val lexiconDir = File(context.filesDir, "japanese_lexicons").apply { mkdirs() }
        val file = File(lexiconDir, "jp_lex_${System.currentTimeMillis()}.txt")
        context.contentResolver.openInputStream(uri)?.use { input ->
            file.outputStream().use { output -> input.copyTo(output) }
        } ?: error("无法读取词库文件")

        val parsed = parseLexiconRaw(file.readText(Charsets.UTF_8))
        if (parsed.isEmpty()) error("词库为空或格式无法识别")

        val info = JapaneseLexiconRecord(
            id = "jp.lexicon.custom.${file.nameWithoutExtension}",
            name = "日语自定义词库 ${file.nameWithoutExtension.takeLast(6)}",
            builtIn = false,
            path = file.absolutePath
        )

        val arr = loadImportedJson(context)
        arr.put(JSONObject().apply {
            put("id", info.id)
            put("name", info.name)
            put("path", info.path)
        })
        saveImportedJson(context, arr)

        synchronized(cache) { cache[info.id] = parsed }
        setLexiconEnabled(context, info.id, true)
        bumpVersion(context)
        return JapaneseLexiconInfo(info.id, info.name, false)
    }

    fun queryCandidates(context: Context, reading: String, limit: Int): List<String> {
        val key = normalizeReading(reading)
        if (key.isBlank() || limit <= 0) return emptyList()

        val enabled = getEnabledLexiconIds(context)
        if (enabled.isEmpty()) return emptyList()

        val out = ArrayList<String>()
        (builtIns + loadImported(context)).forEach { item ->
            if (!enabled.contains(item.id)) return@forEach
            val dict = resolveLexiconMap(context, item)
            dict[key].orEmpty().forEach { cand ->
                if (!out.contains(cand)) out += cand
            }
        }
        return out.take(limit)
    }

    fun warmup(context: Context) {
        val enabled = getEnabledLexiconIds(context)
        (builtIns + loadImported(context)).forEach { item ->
            if (!enabled.contains(item.id)) return@forEach
            runCatching { resolveLexiconMap(context, item) }
        }
    }

    fun getVersion(context: Context): Int {
        return prefs(context).getInt(KEY_LEXICON_VERSION, 0)
    }

    fun normalizeReading(value: String): String {
        val out = StringBuilder(value.length)
        value.forEach { c ->
            when {
                c == 'ヴ' -> out.append('ゔ')
                c in '\u30A1'..'\u30F6' -> out.append((c.code - 0x60).toChar())
                c in '\u3041'..'\u3096' || c == 'ー' || c == 'ゔ' -> out.append(c)
                c in 'a'..'z' || c in 'A'..'Z' || c in '0'..'9' -> out.append(c.lowercaseChar())
            }
        }
        return out.toString()
    }

    private fun resolveLexiconMap(context: Context, item: JapaneseLexiconRecord): Map<String, List<String>> {
        synchronized(cache) {
            cache[item.id]?.let { return it }
        }
        val parsed = if (item.builtIn) {
            val path = item.path.orEmpty().removePrefix("asset://")
            val raw = context.assets.open(path).bufferedReader(Charsets.UTF_8).use { it.readText() }
            parseLexiconRaw(raw)
        } else {
            val path = item.path
            if (path.isNullOrBlank()) emptyMap() else {
                val f = File(path)
                if (!f.exists()) emptyMap() else parseLexiconRaw(f.readText(Charsets.UTF_8))
            }
        }

        synchronized(cache) {
            cache[item.id] = parsed
        }
        return parsed
    }

    private fun parseLexiconRaw(raw: String): Map<String, List<String>> {
        val text = raw.trim()
        if (text.isBlank()) return emptyMap()
        return if (text.startsWith("{") || text.startsWith("[")) parseJsonLexicon(text) else parsePlainLexicon(text)
    }

    private fun parseJsonLexicon(raw: String): Map<String, List<String>> {
        val out = LinkedHashMap<String, MutableList<String>>()
        val trimmed = raw.trim()
        if (trimmed.startsWith("{")) {
            val root = JSONObject(trimmed)
            root.keys().forEach { readingRaw ->
                val reading = normalizeReading(readingRaw)
                if (reading.isBlank()) return@forEach
                when (val value = root.opt(readingRaw)) {
                    is JSONArray -> {
                        for (i in 0 until value.length()) {
                            addCandidate(out, reading, value.optString(i).trim())
                        }
                    }
                    is String -> addCandidate(out, reading, value.trim())
                }
            }
        } else {
            val arr = JSONArray(trimmed)
            for (i in 0 until arr.length()) {
                val item = arr.optJSONObject(i) ?: continue
                val reading = normalizeReading(item.optString("reading", item.optString("kana", item.optString("yomi"))))
                if (reading.isBlank()) continue
                val candidates = item.optJSONArray("candidates")
                    ?: item.optJSONArray("words")
                    ?: item.optJSONArray("kanji")
                if (candidates != null) {
                    for (j in 0 until candidates.length()) {
                        addCandidate(out, reading, candidates.optString(j).trim())
                    }
                } else {
                    val single = item.optString("word", item.optString("text", "")).trim()
                    addCandidate(out, reading, single)
                }
            }
        }
        return out.mapValues { it.value.toList() }
    }

    private fun parsePlainLexicon(raw: String): Map<String, List<String>> {
        val out = LinkedHashMap<String, MutableList<String>>()
        raw.lineSequence().forEach { lineRaw ->
            val line = lineRaw.trim()
            if (line.isBlank() || line.startsWith("#")) return@forEach
            val pair = when {
                line.contains('\t') -> line.split('\t', limit = 2)
                line.contains(' ') -> line.split(Regex("\\s+"), limit = 2)
                else -> emptyList()
            }
            if (pair.size < 2) return@forEach
            val reading = normalizeReading(pair[0])
            if (reading.isBlank()) return@forEach
            pair[1]
                .split(Regex("[,，;；|/]"))
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .forEach { addCandidate(out, reading, it) }
        }
        return out.mapValues { it.value.toList() }
    }

    private fun addCandidate(out: LinkedHashMap<String, MutableList<String>>, reading: String, candidate: String) {
        if (reading.isBlank() || candidate.isBlank()) return
        val bucket = out.getOrPut(reading) { mutableListOf() }
        if (!bucket.contains(candidate)) bucket += candidate
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun loadImported(context: Context): List<JapaneseLexiconRecord> {
        val arr = loadImportedJson(context)
        val out = mutableListOf<JapaneseLexiconRecord>()
        for (i in 0 until arr.length()) {
            val item = arr.optJSONObject(i) ?: continue
            val id = item.optString("id")
            val name = item.optString("name", "日语自定义词库")
            val path = item.optString("path")
            if (id.isBlank() || path.isBlank()) continue
            if (!File(path).exists()) continue
            out += JapaneseLexiconRecord(id, name, false, path)
        }
        return out
    }

    private fun loadImportedJson(context: Context): JSONArray {
        val raw = prefs(context).getString(KEY_IMPORTED_LEXICONS, "[]").orEmpty()
        return runCatching { JSONArray(raw) }.getOrDefault(JSONArray())
    }

    private fun saveImportedJson(context: Context, arr: JSONArray) {
        prefs(context).edit().putString(KEY_IMPORTED_LEXICONS, arr.toString()).apply()
    }

    private fun bumpVersion(context: Context) {
        val p = prefs(context)
        p.edit().putInt(KEY_LEXICON_VERSION, p.getInt(KEY_LEXICON_VERSION, 0) + 1).apply()
    }
}
