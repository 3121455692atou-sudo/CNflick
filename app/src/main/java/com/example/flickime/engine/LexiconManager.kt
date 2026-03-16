package com.example.flickime.engine

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.Normalizer

private data class LexiconRecord(
    val id: String,
    val name: String,
    val builtIn: Boolean,
    val path: String?
)

data class LexiconInfo(
    val id: String,
    val name: String,
    val isBuiltIn: Boolean
)

object LexiconManager {
    private const val PREFS = "flick_ime"
    private const val KEY_IMPORTED_LEXICONS = "imported_lexicons"
    private const val KEY_ENABLED_LEXICONS = "enabled_lexicons"
    private const val KEY_LEXICON_VERSION = "enabled_lexicon_version"
    private const val KEY_BUILTIN_MIGRATED = "enabled_lexicon_builtin_migrated_v1"

    private const val BUILTIN_COMMON_ID = "lexicon.builtin.common"
    private const val BUILTIN_ANIME_ID = "lexicon.builtin.anime_cn"

    private val baseCommon = mapOf(
        "zhendong" to listOf("震动", "振动", "真懂"),
        "nihao" to listOf("你好", "拟好"),
        "gaoxing" to listOf("高兴"),
        "shouji" to listOf("手机", "收集"),
        "xiexie" to listOf("谢谢"),
        "duibuqi" to listOf("对不起"),
        "meiguanxi" to listOf("没关系"),
        "haode" to listOf("好的"),
        "ok" to listOf("OK"),
        "cao" to listOf("草", "槽"),
        "wocao" to listOf("卧槽"),
        "niubi" to listOf("牛逼", "牛啤"),
        "zhenbang" to listOf("真棒"),
        "haochi" to listOf("好吃"),
        "haokan" to listOf("好看"),
        "xiaohongshu" to listOf("小红书"),
        "douyin" to listOf("抖音"),
        "weixin" to listOf("微信")
    )

    private val builtIns = listOf(
        LexiconRecord(BUILTIN_COMMON_ID, "内置常用与网络词", true, null),
        LexiconRecord(BUILTIN_ANIME_ID, "开源动漫词库（拼音）", true, "asset://lexicons/anime_character_cn.tsv")
    )

    private val cache = mutableMapOf<String, Map<String, List<String>>>()

    fun getAllLexicons(context: Context): List<LexiconInfo> {
        return (builtIns + loadImported(context)).map { LexiconInfo(it.id, it.name, it.builtIn) }
    }

    fun getEnabledLexiconIds(context: Context): Set<String> {
        val prefs = prefs(context)
        val stored = prefs.getStringSet(KEY_ENABLED_LEXICONS, null)
        if (stored.isNullOrEmpty()) {
            return builtIns.map { it.id }.toSet()
        }
        if (!prefs.getBoolean(KEY_BUILTIN_MIGRATED, false)) {
            val migrated = stored.toMutableSet().apply { addAll(builtIns.map { it.id }) }
            prefs.edit()
                .putStringSet(KEY_ENABLED_LEXICONS, migrated)
                .putBoolean(KEY_BUILTIN_MIGRATED, true)
                .apply()
            return migrated
        }
        return stored.toSet()
    }

    fun setLexiconEnabled(context: Context, lexiconId: String, enabled: Boolean) {
        val current = getEnabledLexiconIds(context).toMutableSet()
        if (enabled) current += lexiconId else current -= lexiconId
        prefs(context).edit().putStringSet(KEY_ENABLED_LEXICONS, current).apply()
        bumpVersion(context)
    }

    fun resetToDefault(context: Context) {
        prefs(context).edit()
            .putStringSet(KEY_ENABLED_LEXICONS, builtIns.map { it.id }.toSet())
            .putBoolean(KEY_BUILTIN_MIGRATED, true)
            .apply()
        bumpVersion(context)
    }

    fun importLexicon(context: Context, uri: Uri): LexiconInfo {
        val lexiconDir = File(context.filesDir, "lexicons").apply { mkdirs() }
        val file = File(lexiconDir, "lex_${System.currentTimeMillis()}.txt")
        context.contentResolver.openInputStream(uri)?.use { input ->
            file.outputStream().use { output -> input.copyTo(output) }
        } ?: error("无法读取词库文件")

        val parsed = parseLexiconRaw(file.readText(Charsets.UTF_8))
        if (parsed.isEmpty()) error("词库为空或格式无法识别")

        val info = LexiconRecord(
            id = "lexicon.custom.${file.nameWithoutExtension}",
            name = "自定义词库 ${file.nameWithoutExtension.takeLast(6)}",
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

        setLexiconEnabled(context, info.id, true)
        bumpVersion(context)
        return LexiconInfo(info.id, info.name, false)
    }

    fun queryCandidates(context: Context, pinyin: String, limit: Int): List<String> {
        val key = normalizePinyin(pinyin)
        if (key.isBlank() || limit <= 0) return emptyList()

        val enabled = getEnabledLexiconIds(context)
        if (enabled.isEmpty()) return emptyList()

        val all = builtIns + loadImported(context)
        val out = ArrayList<String>()
        all.forEach { item ->
            if (!enabled.contains(item.id)) return@forEach
            val dict = resolveLexiconMap(context, item)
            val cands = dict[key].orEmpty()
            cands.forEach { c ->
                if (!out.contains(c)) out += c
            }
        }
        return out.take(limit)
    }

    fun warmup(context: Context) {
        val enabled = getEnabledLexiconIds(context)
        val all = builtIns + loadImported(context)
        all.forEach { item ->
            if (!enabled.contains(item.id)) return@forEach
            runCatching { resolveLexiconMap(context, item) }
        }
    }

    fun getVersion(context: Context): Int {
        return prefs(context).getInt(KEY_LEXICON_VERSION, 0)
    }

    private fun resolveLexiconMap(context: Context, item: LexiconRecord): Map<String, List<String>> {
        synchronized(cache) {
            cache[item.id]?.let { return it }
        }
        val parsed = when (item.id) {
            BUILTIN_COMMON_ID -> baseCommon
            BUILTIN_ANIME_ID -> {
                val raw = context.assets.open("lexicons/anime_character_cn.tsv")
                    .bufferedReader(Charsets.UTF_8)
                    .use { it.readText() }
                parseLexiconRaw(raw)
            }
            else -> {
                val path = item.path
                if (path.isNullOrBlank()) emptyMap()
                else {
                    val f = File(path)
                    if (!f.exists()) emptyMap() else parseLexiconRaw(f.readText(Charsets.UTF_8))
                }
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
        return if (text.startsWith("{") || text.startsWith("[")) {
            parseJsonLexicon(text)
        } else {
            parsePlainLexicon(text)
        }
    }

    private fun parseJsonLexicon(raw: String): Map<String, List<String>> {
        val out = LinkedHashMap<String, MutableList<String>>()
        val trimmed = raw.trim()
        if (trimmed.startsWith("{")) {
            val root = JSONObject(trimmed)
            root.keys().forEach { py ->
                val p = normalizePinyin(py)
                if (p.isBlank()) return@forEach
                val value = root.opt(py)
                when (value) {
                    is JSONArray -> {
                        for (i in 0 until value.length()) {
                            addCandidate(out, p, value.optString(i).trim())
                        }
                    }
                    is String -> addCandidate(out, p, value.trim())
                }
            }
        } else {
            val arr = JSONArray(trimmed)
            for (i in 0 until arr.length()) {
                val item = arr.optJSONObject(i) ?: continue
                val p = normalizePinyin(item.optString("pinyin"))
                if (p.isBlank()) continue
                val candidates = item.optJSONArray("candidates")
                    ?: item.optJSONArray("words")
                    ?: item.optJSONArray("hanzi")
                if (candidates != null) {
                    for (j in 0 until candidates.length()) {
                        addCandidate(out, p, candidates.optString(j).trim())
                    }
                } else {
                    val single = item.optString("hanzi", item.optString("word", "")).trim()
                    addCandidate(out, p, single)
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

            val p = normalizePinyin(pair[0])
            if (p.isBlank()) return@forEach

            pair[1]
                .split(Regex("[,，;；|/]"))
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .forEach { addCandidate(out, p, it) }
        }
        return out.mapValues { it.value.toList() }
    }

    private fun addCandidate(out: LinkedHashMap<String, MutableList<String>>, pinyin: String, candidate: String) {
        if (candidate.isBlank()) return
        val bucket = out.getOrPut(pinyin) { mutableListOf() }
        if (!bucket.contains(candidate)) bucket += candidate
    }

    private fun normalizePinyin(value: String): String {
        val toned = value.lowercase().replace("u:", "v").replace("ü", "v")
        val noMarks = Normalizer.normalize(toned, Normalizer.Form.NFD)
            .replace("\\p{M}+".toRegex(), "")
        return noMarks.filter { it in 'a'..'z' || it == 'v' }
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun loadImported(context: Context): List<LexiconRecord> {
        val arr = loadImportedJson(context)
        val out = mutableListOf<LexiconRecord>()
        for (i in 0 until arr.length()) {
            val item = arr.optJSONObject(i) ?: continue
            val id = item.optString("id")
            val name = item.optString("name", "自定义词库")
            val path = item.optString("path")
            if (id.isBlank() || path.isBlank()) continue
            if (!File(path).exists()) continue
            out += LexiconRecord(id, name, false, path)
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
