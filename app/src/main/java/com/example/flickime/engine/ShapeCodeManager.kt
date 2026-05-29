package com.example.flickime.engine

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.Normalizer

private data class ShapeCodeRecord(
    val id: String,
    val name: String,
    val builtIn: Boolean,
    val path: String?
)

data class ShapeCodeInfo(
    val id: String,
    val name: String,
    val isBuiltIn: Boolean
)

object ShapeCodeManager {
    private const val PREFS = "flick_ime"
    private const val KEY_IMPORTED = "imported_shape_code_tables"
    private const val KEY_ENABLED = "enabled_shape_code_tables"
    private const val KEY_VERSION = "shape_code_version"

    private const val BUILTIN_WUBI86_ID = "shape.builtin.wubi86"

    private val builtIns = listOf(
        ShapeCodeRecord(BUILTIN_WUBI86_ID, "内置五笔86", true, "asset://shape/wubi86.dict.yaml")
    )

    private val cache = mutableMapOf<String, Map<String, List<String>>>()

    fun getAllTables(context: Context): List<ShapeCodeInfo> {
        return (builtIns + loadImported(context)).map { ShapeCodeInfo(it.id, it.name, it.builtIn) }
    }

    fun getEnabledTableIds(context: Context): Set<String> {
        val stored = prefs(context).getStringSet(KEY_ENABLED, null)
        return if (stored.isNullOrEmpty()) {
            builtIns.map { it.id }.toSet()
        } else {
            stored.toSet()
        }
    }

    fun setTableEnabled(context: Context, tableId: String, enabled: Boolean) {
        val next = getEnabledTableIds(context).toMutableSet()
        if (enabled) next += tableId else next -= tableId
        prefs(context).edit().putStringSet(KEY_ENABLED, next).apply()
        bumpVersion(context)
    }

    fun resetToDefault(context: Context) {
        prefs(context).edit().putStringSet(KEY_ENABLED, builtIns.map { it.id }.toSet()).apply()
        bumpVersion(context)
    }

    fun importTable(context: Context, uri: Uri): ShapeCodeInfo {
        val dir = File(context.filesDir, "shape_code_tables").apply { mkdirs() }
        val file = File(dir, "shape_${System.currentTimeMillis()}.txt")
        context.contentResolver.openInputStream(uri)?.use { input ->
            file.outputStream().use { output -> input.copyTo(output) }
        } ?: error("无法读取码表文件")

        val parsed = parseRaw(file.readText(Charsets.UTF_8))
        if (parsed.isEmpty()) error("码表为空或格式无法识别")

        val info = ShapeCodeRecord(
            id = "shape.custom.${file.nameWithoutExtension}",
            name = "自定义形码 ${file.nameWithoutExtension.takeLast(6)}",
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

        synchronized(cache) {
            cache[info.id] = parsed
        }
        setTableEnabled(context, info.id, true)
        bumpVersion(context)
        return ShapeCodeInfo(info.id, info.name, false)
    }

    fun queryCandidates(context: Context, code: String, limit: Int): List<String> {
        val key = normalizeCode(code)
        if (key.isBlank() || limit <= 0) return emptyList()
        val enabled = getEnabledTableIds(context)
        if (enabled.isEmpty()) return emptyList()

        val out = ArrayList<String>(limit)
        (builtIns + loadImported(context)).forEach { table ->
            if (!enabled.contains(table.id)) return@forEach
            resolveTable(context, table)[key].orEmpty().forEach { text ->
                if (!out.contains(text)) out += text
                if (out.size >= limit) return out
            }
        }
        return out
    }

    fun warmup(context: Context) {
        val enabled = getEnabledTableIds(context)
        (builtIns + loadImported(context)).forEach { table ->
            if (enabled.contains(table.id)) runCatching { resolveTable(context, table) }
        }
    }

    fun getVersion(context: Context): Int {
        return prefs(context).getInt(KEY_VERSION, 0)
    }

    private fun resolveTable(context: Context, table: ShapeCodeRecord): Map<String, List<String>> {
        synchronized(cache) {
            cache[table.id]?.let { return it }
        }
        val raw = when {
            table.path.isNullOrBlank() -> ""
            table.path.startsWith("asset://") -> {
                context.assets.open(table.path.removePrefix("asset://"))
                    .bufferedReader(Charsets.UTF_8)
                    .use { it.readText() }
            }
            else -> {
                val f = File(table.path)
                if (!f.exists()) "" else f.readText(Charsets.UTF_8)
            }
        }
        val parsed = parseRaw(raw)
        synchronized(cache) {
            cache[table.id] = parsed
        }
        return parsed
    }

    private fun parseRaw(raw: String): Map<String, List<String>> {
        val exact = LinkedHashMap<String, MutableList<String>>()
        raw.lineSequence().forEach { lineRaw ->
            val line = lineRaw.trim()
            if (line.isBlank() || line.startsWith("#")) return@forEach
            if (line == "---" || line == "..." || line.contains(":")) return@forEach

            val parts = line.split(Regex("\\s+")).filter { it.isNotBlank() }
            if (parts.size < 2) return@forEach
            val first = parts[0].trim()
            val second = parts[1].trim()
            val word: String
            val code: String
            if (looksLikeCode(first) && !looksLikeCode(second)) {
                code = first
                word = second
            } else {
                word = first.removePrefix("#")
                code = second
            }
            addCandidate(exact, normalizeCode(code), word)
        }

        val indexed = LinkedHashMap<String, MutableList<String>>()
        exact.forEach { (code, words) ->
            val maxPrefix = code.length.coerceAtMost(8)
            for (i in 1..maxPrefix) {
                val prefix = code.take(i)
                val bucket = indexed.getOrPut(prefix) { mutableListOf() }
                words.forEach { word ->
                    if (!bucket.contains(word) && bucket.size < 160) bucket += word
                }
            }
        }
        return indexed.mapValues { it.value.toList() }
    }

    private fun addCandidate(out: MutableMap<String, MutableList<String>>, code: String, word: String) {
        if (code.isBlank() || word.isBlank()) return
        val bucket = out.getOrPut(code) { mutableListOf() }
        if (!bucket.contains(word)) bucket += word
    }

    private fun looksLikeCode(value: String): Boolean {
        val normalized = normalizeCode(value)
        return normalized.isNotBlank() && normalized.length == value.trim().length
    }

    private fun normalizeCode(raw: String): String {
        val noMarks = Normalizer.normalize(raw.lowercase(), Normalizer.Form.NFD)
            .replace("\\p{M}+".toRegex(), "")
        return noMarks.filter { it in 'a'..'z' || it == ';' || it == '\'' }
    }

    private fun loadImported(context: Context): List<ShapeCodeRecord> {
        val arr = loadImportedJson(context)
        val out = ArrayList<ShapeCodeRecord>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val id = o.optString("id").trim()
            val name = o.optString("name").trim()
            val path = o.optString("path").trim()
            if (id.isNotBlank() && name.isNotBlank() && path.isNotBlank()) {
                out += ShapeCodeRecord(id, name, false, path)
            }
        }
        return out
    }

    private fun loadImportedJson(context: Context): JSONArray {
        val raw = prefs(context).getString(KEY_IMPORTED, "[]").orEmpty()
        return try {
            JSONArray(raw)
        } catch (_: Throwable) {
            JSONArray()
        }
    }

    private fun saveImportedJson(context: Context, arr: JSONArray) {
        prefs(context).edit().putString(KEY_IMPORTED, arr.toString()).apply()
    }

    private fun bumpVersion(context: Context) {
        val p = prefs(context)
        p.edit().putInt(KEY_VERSION, p.getInt(KEY_VERSION, 0) + 1).apply()
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
