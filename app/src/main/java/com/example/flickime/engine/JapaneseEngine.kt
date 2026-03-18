package com.example.flickime.engine

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import java.io.File
import java.util.LinkedHashMap

class JapaneseEngine(private val context: Context) {
    private val dbName = "japanese_user_choice.db"

    private val db: SQLiteDatabase? by lazy {
        try {
            val dbFile = File(context.filesDir, dbName)
            val database = SQLiteDatabase.openOrCreateDatabase(dbFile, null)
            database.execSQL(
                """
                CREATE TABLE IF NOT EXISTS user_choice (
                  reading TEXT NOT NULL,
                  word TEXT NOT NULL,
                  boost INTEGER NOT NULL DEFAULT 1,
                  updated_at INTEGER NOT NULL,
                  PRIMARY KEY(reading, word)
                )
                """.trimIndent()
            )
            database
        } catch (_: Throwable) {
            null
        }
    }

    private val cache = object : LinkedHashMap<String, List<String>>(256, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<String>>?): Boolean = size > 256
    }

    fun queryCandidates(reading: String, limit: Int = 10): List<String> {
        val key = JapaneseLexiconManager.normalizeReading(reading)
        if (key.isBlank()) return emptyList()

        val versionTag = "jv${JapaneseLexiconManager.getVersion(context)}"
        val cacheKey = "$versionTag|$key#$limit"
        synchronized(cache) { cache[cacheKey]?.let { return it } }

        val learned = mutableListOf<String>()
        db?.rawQuery(
            """
            SELECT word
            FROM user_choice
            WHERE reading = ?
            ORDER BY boost DESC, updated_at DESC
            LIMIT 60
            """.trimIndent(),
            arrayOf(key)
        )?.use { c ->
            while (c.moveToNext()) learned += c.getString(0)
        }

        val lexicon = JapaneseLexiconManager.queryCandidates(context, key, limit * 8)
        val fallback = fallbackCandidates(key)

        val out = (learned + lexicon + fallback).distinct().take(limit)
        synchronized(cache) { cache[cacheKey] = out }
        return out
    }

    fun recordUserChoice(reading: String, word: String) {
        val key = JapaneseLexiconManager.normalizeReading(reading)
        if (key.isBlank() || word.isBlank()) return
        val database = db ?: return
        database.execSQL(
            """
            INSERT INTO user_choice (reading, word, boost, updated_at)
            VALUES (?, ?, 1, ?)
            ON CONFLICT(reading, word)
            DO UPDATE SET boost = boost + 1, updated_at = excluded.updated_at
            """.trimIndent(),
            arrayOf(key, word, System.currentTimeMillis())
        )
        synchronized(cache) { cache.clear() }
    }

    private fun fallbackCandidates(reading: String): List<String> {
        val common = mapOf(
            "こんにちは" to listOf("こんにちは", "今日は"),
            "ありがとう" to listOf("ありがとう", "有難う"),
            "すみません" to listOf("すみません", "済みません"),
            "おはよう" to listOf("おはよう", "お早う"),
            "こんばんは" to listOf("こんばんは", "今晩は"),
            "わたし" to listOf("私", "わたし"),
            "にほん" to listOf("日本", "にほん"),
            "とうきょう" to listOf("東京", "とうきょう")
        )
        return common[reading].orEmpty()
    }
}
