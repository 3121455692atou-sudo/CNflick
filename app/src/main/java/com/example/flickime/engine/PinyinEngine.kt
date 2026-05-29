package com.example.flickime.engine

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import java.io.File
import java.util.LinkedHashMap
import java.text.Normalizer

class PinyinEngine(private val context: Context) {
    private val dbName = "pinyin_dict_v2.db"
    private val dbAssetVersion = 5

    private val db: SQLiteDatabase? by lazy {
        try {
            val dbFile = ensureDbCopied()
            SQLiteDatabase.openDatabase(dbFile.absolutePath, null, SQLiteDatabase.OPEN_READWRITE).also {
                it.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS user_choice (
                      pinyin TEXT NOT NULL,
                      hanzi TEXT NOT NULL,
                      boost INTEGER NOT NULL DEFAULT 1,
                      updated_at INTEGER NOT NULL,
                      PRIMARY KEY(pinyin, hanzi)
                    )
                    """.trimIndent()
                )
            }
        } catch (_: Throwable) {
            null
        }
    }

    private val singleCache = object : LinkedHashMap<String, List<String>>(320, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<String>>?): Boolean = size > 320
    }
    private val phraseCache = object : LinkedHashMap<String, List<String>>(240, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<String>>?): Boolean = size > 240
    }
    private val initialCache = object : LinkedHashMap<String, List<String>>(160, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<String>>?): Boolean = size > 160
    }

    fun queryCandidates(pinyin: String, limit: Int = 10): List<String> {
        val query = normalizePinyin(pinyin)
        if (query.isBlank()) return emptyList()
        val versionTag = "lv${LexiconManager.getVersion(context)}-sv${ShortcutManager.getVersion(context)}"
        val cacheKey = "$versionTag|$query#$limit"
        synchronized(singleCache) {
            singleCache[cacheKey]?.let { return it }
        }

        val shortcuts = ShortcutManager.query(context, query, limit * 4)
        val lexicon = LexiconManager.queryCandidates(context, query, limit * 4)
        val database = db
        if (database == null) {
            val out = (shortcuts + lexicon + fallbackCandidates(query, limit * 2)).distinct().take(limit)
            synchronized(singleCache) { singleCache[cacheKey] = out }
            return out
        }

        val sql = """
            SELECT hanzi
            FROM dict
            WHERE pinyin = ?
            ORDER BY freq DESC
            LIMIT 120
        """.trimIndent()

        val base = mutableListOf<String>()
        database.rawQuery(sql, arrayOf(query)).use { c ->
            while (c.moveToNext()) base += c.getString(0)
        }
        val learned = mutableListOf<String>()
        database.rawQuery(
            """
            SELECT hanzi
            FROM user_choice
            WHERE pinyin = ?
            ORDER BY boost DESC, updated_at DESC
            LIMIT 60
            """.trimIndent(),
            arrayOf(query)
        ).use { c ->
            while (c.moveToNext()) learned += c.getString(0)
        }
        if (base.isEmpty() && learned.isEmpty() && lexicon.isEmpty() && shortcuts.isEmpty()) {
            val out = fallbackCandidates(query, limit)
            synchronized(singleCache) { singleCache[cacheKey] = out }
            return out
        }

        // learned > custom-shortcuts > enabled-lexicons > fallback-common > base-dict
        val common = fallbackCandidates(query, limit * 2)
        val out = (learned + shortcuts + lexicon + common + base).distinct().take(limit)
        synchronized(singleCache) { singleCache[cacheKey] = out }
        return out
    }

    fun queryCandidatesForSyllables(syllables: List<String>, limit: Int = 10): List<String> {
        val clean = syllables.map { normalizePinyin(it) }.filter { it.isNotBlank() }
        if (clean.isEmpty()) return emptyList()
        if (clean.size == 1) return queryCandidates(clean.first(), limit)
        val versionTag = "lv${LexiconManager.getVersion(context)}-sv${ShortcutManager.getVersion(context)}"
        val cacheKey = versionTag + "|" + clean.joinToString("'") + "#$limit"
        synchronized(phraseCache) {
            phraseCache[cacheKey]?.let { return it }
        }

        val joined = clean.joinToString("")
        val database = db
        val phraseFromLexicon = LexiconManager.queryCandidates(context, joined, limit * 6)

        val phraseFromDict = mutableListOf<String>()
        database?.rawQuery(
            """
            SELECT hanzi
            FROM dict
            WHERE pinyin = ?
            ORDER BY freq DESC
            LIMIT 120
            """.trimIndent(),
            arrayOf(joined)
        )?.use { c ->
            while (c.moveToNext()) phraseFromDict += c.getString(0)
        }

        val learned = mutableListOf<String>()
        database?.rawQuery(
            """
            SELECT hanzi
            FROM user_choice
            WHERE pinyin = ?
            ORDER BY boost DESC, updated_at DESC
            LIMIT 60
            """.trimIndent(),
            arrayOf(joined)
        )?.use { c ->
            while (c.moveToNext()) learned += c.getString(0)
        }

        // Beam search: combine top choices per syllable into multi-char phrases.
        var phrases = listOf("")
        clean.forEach { syl ->
            val chars = queryCandidates(syl, 6).ifEmpty { fallbackCandidates(syl, 6) }
            val next = ArrayList<String>(phrases.size * chars.size)
            for (prefix in phrases) {
                for (c in chars.take(3)) next += prefix + c
            }
            phrases = next.take(limit * 4)
        }

        val commonWhole = fallbackCandidates(joined, limit * 3)
        val out = (learned + phraseFromLexicon + phraseFromDict + commonWhole + phrases).distinct().take(limit)
        synchronized(phraseCache) { phraseCache[cacheKey] = out }
        return out
    }

    fun queryInitialCandidates(initials: List<String>, limit: Int = 10): List<String> {
        val clean = initials.map { normalizePinyin(it) }.filter { it.isNotBlank() }
        if (clean.isEmpty()) return emptyList()
        val compact = clean.joinToString("") { initialCode(it) }
        val versionTag = "lv${LexiconManager.getVersion(context)}-sv${ShortcutManager.getVersion(context)}"
        val cacheKey = "$versionTag|initial:$compact#$limit"
        synchronized(initialCache) {
            initialCache[cacheKey]?.let { return it }
        }

        val shortcuts = ShortcutManager.query(context, compact, limit * 3)
        val database = db
        if (database == null) {
            val out = shortcuts.distinct().take(limit)
            synchronized(initialCache) { initialCache[cacheKey] = out }
            return out
        }

        val codes = clean.map { initialCode(it) }
        val fromDict = if (codes.size == 1) {
            querySingleInitialCandidates(database, codes.first(), limit * 8)
        } else {
            queryInitialCandidatesFromFullPinyin(database, codes, limit * 8)
        }
        val out = (shortcuts + fromDict).distinct().take(limit)
        synchronized(initialCache) { initialCache[cacheKey] = out }
        return out
    }

    private fun queryInitialCandidatesFromFullPinyin(
        database: SQLiteDatabase,
        codes: List<String>,
        limit: Int
    ): List<String> {
        if (codes.isEmpty()) return emptyList()
        val fromIndex = queryInitialMaterializedIndex(database, codes, limit)
        if (fromIndex.isNotEmpty()) return fromIndex
        val pattern = buildInitialLikePattern(codes)
        val scanLimit = (limit * 80).coerceAtLeast(2000).coerceAtMost(30000)
        return try {
            val out = mutableListOf<String>()
            database.rawQuery(
                """
                SELECT pinyin, hanzi
                FROM dict
                WHERE pinyin LIKE ? AND length(hanzi) >= ${codes.size}
                ORDER BY freq DESC
                LIMIT $scanLimit
                """.trimIndent(),
                arrayOf(pattern)
            ).use { c ->
                while (c.moveToNext()) {
                    val pinyin = c.getString(0)
                    val hanzi = c.getString(1)
                    if (matchesInitialCodes(pinyin, codes) && !out.contains(hanzi)) {
                        out += hanzi
                        if (out.size >= limit) break
                    }
                }
            }
            out
        } catch (_: Throwable) {
            emptyList()
        }
    }

    private fun queryInitialMaterializedIndex(
        database: SQLiteDatabase,
        codes: List<String>,
        limit: Int
    ): List<String> {
        val compact = codes.joinToString("")
        return try {
            val out = mutableListOf<String>()
            database.rawQuery(
                """
                SELECT pinyin, hanzi
                FROM initial_index
                WHERE initials = ? AND length(hanzi) >= ${codes.size}
                ORDER BY freq DESC
                LIMIT ${limit * 4}
                """.trimIndent(),
                arrayOf(compact)
            ).use { c ->
                while (c.moveToNext()) {
                    val pinyin = c.getString(0)
                    val hanzi = c.getString(1)
                    if (matchesInitialCodes(pinyin, codes) && !out.contains(hanzi)) {
                        out += hanzi
                        if (out.size >= limit) break
                    }
                }
            }
            out
        } catch (_: Throwable) {
            emptyList()
        }
    }

    private fun buildInitialLikePattern(codes: List<String>): String {
        return buildString {
            append(codes.first())
            codes.drop(1).forEach { code ->
                append('%')
                append(code)
            }
            append('%')
        }
    }

    private fun querySingleInitialCandidates(
        database: SQLiteDatabase,
        code: String,
        limit: Int
    ): List<String> {
        return try {
            val fromIndex = mutableListOf<String>()
            val scanLimit = (limit * 40).coerceAtLeast(800).coerceAtMost(12000)
            database.rawQuery(
                """
                SELECT pinyin, hanzi
                FROM dict
                WHERE pinyin LIKE ? AND length(hanzi) = 1
                ORDER BY freq DESC
                LIMIT $scanLimit
                """.trimIndent(),
                arrayOf("$code%")
            ).use { c ->
                while (c.moveToNext()) {
                    val pinyin = c.getString(0)
                    val hanzi = c.getString(1)
                    if (matchesInitialCodes(pinyin, listOf(code)) && !fromIndex.contains(hanzi)) {
                        fromIndex += hanzi
                        if (fromIndex.size >= limit) break
                    }
                }
            }
            fromIndex
        } catch (_: Throwable) {
            emptyList()
        }
    }

    fun recordUserChoice(pinyin: String, hanzi: String) {
        val query = normalizePinyin(pinyin)
        if (query.isBlank() || hanzi.isBlank()) return
        val database = db ?: return
        val now = System.currentTimeMillis()
        database.execSQL(
            """
            INSERT INTO user_choice (pinyin, hanzi, boost, updated_at)
            VALUES (?, ?, 1, ?)
            ON CONFLICT(pinyin, hanzi)
            DO UPDATE SET boost = boost + 1, updated_at = excluded.updated_at
            """.trimIndent(),
            arrayOf(query, hanzi, now)
        )
        synchronized(singleCache) { singleCache.clear() }
        synchronized(phraseCache) { phraseCache.clear() }
        synchronized(initialCache) { initialCache.clear() }
    }

    private fun ensureDbCopied(): File {
        val outFile = File(context.filesDir, dbName)
        val prefs = context.getSharedPreferences("flick_ime", Context.MODE_PRIVATE)
        val copiedVersion = prefs.getInt("dict_asset_version", 0)
        val needsRefresh = copiedVersion < dbAssetVersion || !outFile.exists() || outFile.length() <= 0L
        if (!needsRefresh) return outFile
        context.assets.open(dbName).use { input ->
            outFile.outputStream().use { output -> input.copyTo(output) }
        }
        prefs.edit().putInt("dict_asset_version", dbAssetVersion).apply()
        return outFile
    }

    private fun fallbackCandidates(pinyin: String, limit: Int): List<String> {
        val common = mapOf(
            "zhong" to listOf("中", "种", "重", "钟", "终"),
            "guo" to listOf("国", "过", "果", "锅", "郭"),
            "ren" to listOf("人", "认", "任", "仁", "忍"),
            "shi" to listOf("是", "时", "事", "市", "使"),
            "de" to listOf("的", "得", "德"),
            "chuang" to listOf("窗", "床", "创", "闯", "幢"),
            "zhang" to listOf("张", "章", "长", "掌", "账"),
            "zhe" to listOf("这", "着", "者", "折"),
            "wo" to listOf("我", "握", "窝", "卧", "沃"),
            "ni" to listOf("你", "呢", "泥", "拟", "逆"),
            "ta" to listOf("他", "她", "它", "塔"),
            "ma" to listOf("吗", "妈", "马", "嘛"),
            "le" to listOf("了", "乐", "勒"),
            "ai" to listOf("爱", "矮", "哎"),
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
        return (common[pinyin] ?: emptyList()).take(limit)
    }

    private fun initialCode(initial: String): String {
        return when (initial) {
            "zh", "ch", "sh" -> initial.first().toString()
            else -> initial
        }
    }

    private fun matchesInitialCodes(pinyin: String, codes: List<String>): Boolean {
        val normalized = normalizePinyin(pinyin)
        if (normalized.isBlank() || codes.isEmpty()) return false
        val normalizedCodes = codes.map { initialCode(it) }
        val memo = HashSet<Pair<Int, Int>>()
        fun dfs(offset: Int, index: Int): Boolean {
            if (index == normalizedCodes.size) return offset == normalized.length
            val state = offset to index
            if (!memo.add(state)) return false
            val syllables = syllablesByInitialCode[normalizedCodes[index]].orEmpty()
            for (syllable in syllables) {
                if (normalized.startsWith(syllable, offset) && dfs(offset + syllable.length, index + 1)) {
                    return true
                }
            }
            return false
        }
        return dfs(0, 0)
    }

    private fun normalizePinyin(value: String): String {
        val toned = value.lowercase().replace("u:", "v").replace("ü", "v")
        val noMarks = Normalizer.normalize(toned, Normalizer.Form.NFD)
            .replace("\\p{M}+".toRegex(), "")
        return noMarks.filter { it in 'a'..'z' || it == 'v' }
    }

    companion object {
        private val pinyinInitialsByCode = mapOf(
            "b" to listOf("b"),
            "p" to listOf("p"),
            "m" to listOf("m"),
            "f" to listOf("f"),
            "d" to listOf("d"),
            "t" to listOf("t"),
            "n" to listOf("n"),
            "l" to listOf("l"),
            "g" to listOf("g"),
            "k" to listOf("k"),
            "h" to listOf("h"),
            "j" to listOf("j"),
            "q" to listOf("q"),
            "x" to listOf("x"),
            "r" to listOf("r"),
            "z" to listOf("zh", "z"),
            "c" to listOf("ch", "c"),
            "s" to listOf("sh", "s"),
            "y" to listOf("y"),
            "w" to listOf("w")
        )
        private val pinyinFinals = listOf(
            "a", "ai", "an", "ang", "ao",
            "o", "ong", "ou",
            "e", "ei", "en", "eng", "er",
            "i", "ia", "ian", "iang", "iao", "ie", "in", "ing", "iong", "iu",
            "u", "ua", "uai", "uan", "uang", "ue", "ui", "un", "uo",
            "v", "ve", "van", "vn"
        )
        private val syllablesByInitialCode: Map<String, List<String>> = pinyinInitialsByCode.mapValues { (_, initials) ->
            initials.flatMap { initial ->
                pinyinFinals.map { initial + it }
                    .plus(if (initial in setOf("m", "n")) listOf(initial) else emptyList())
            }.distinct().sortedByDescending { it.length }
        }
    }
}
