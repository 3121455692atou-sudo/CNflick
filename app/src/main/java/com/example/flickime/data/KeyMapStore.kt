package com.example.flickime.data

import android.content.Context
import android.content.SharedPreferences
import com.example.flickime.model.DirectionalKeySpec
import com.example.flickime.model.FlickKeySpec
import com.example.flickime.model.KeyZone

object KeyMapStore {
    private const val PREFS = "flick_keymap"

    private const val PINYIN_SCHEMA_VERSION = "pinyin_schema_version"
    private const val PINYIN_SCHEMA_V6 = 6
    private const val ZHUYIN_SCHEMA_VERSION = "zhuyin_schema_version"
    private const val ZHUYIN_SCHEMA_V5 = 5
    private const val JAPANESE_SCHEMA_VERSION = "japanese_schema_version"
    private const val JAPANESE_SCHEMA_V4 = 4

    private const val SYMBOL_SCHEMA_VERSION = "symbol_schema_version"
    private const val SYMBOL_SCHEMA_V3 = 3
    private const val ALPHA_SCHEMA_VERSION = "alpha_schema_version"
    private const val ALPHA_SCHEMA_V3 = 3
    private const val NUM_SCHEMA_VERSION = "num_schema_version"
    private const val NUM_SCHEMA_V2 = 2

    fun loadPinyinKeys(context: Context): List<FlickKeySpec> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        ensurePinyinSchema(prefs, context)
        return DefaultKeyMap.keys.mapIndexed { index, def ->
            FlickKeySpec(
                center = prefs.getString("pinyin_${index}_center", def.center).orEmpty(),
                left = prefs.getString("pinyin_${index}_left", def.left).orEmpty(),
                up = prefs.getString("pinyin_${index}_up", def.up).orEmpty(),
                right = prefs.getString("pinyin_${index}_right", def.right).orEmpty(),
                down = prefs.getString("pinyin_${index}_down", def.down).orEmpty(),
                upLeft = prefs.getString("pinyin_${index}_up_left", def.upLeft).orEmpty(),
                upRight = prefs.getString("pinyin_${index}_up_right", def.upRight).orEmpty(),
                downLeft = prefs.getString("pinyin_${index}_down_left", def.downLeft).orEmpty(),
                downRight = prefs.getString("pinyin_${index}_down_right", def.downRight).orEmpty(),
                zone = loadZone(prefs, "pinyin_${index}_zone", def.zone)
            )
        }
    }

    fun savePinyinKeys(context: Context, keys: List<FlickKeySpec>) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val editor = prefs.edit()
        keys.forEachIndexed { index, k ->
            editor.putString("pinyin_${index}_center", normalizePinyinMappingValue(k.center))
            editor.putString("pinyin_${index}_left", normalizePinyinMappingValue(k.left))
            editor.putString("pinyin_${index}_up", normalizePinyinMappingValue(k.up))
            editor.putString("pinyin_${index}_right", normalizePinyinMappingValue(k.right))
            editor.putString("pinyin_${index}_down", normalizePinyinMappingValue(k.down))
            editor.putString("pinyin_${index}_up_left", normalizePinyinMappingValue(k.upLeft))
            editor.putString("pinyin_${index}_up_right", normalizePinyinMappingValue(k.upRight))
            editor.putString("pinyin_${index}_down_left", normalizePinyinMappingValue(k.downLeft))
            editor.putString("pinyin_${index}_down_right", normalizePinyinMappingValue(k.downRight))
            editor.putString("pinyin_${index}_zone", k.zone.name)
        }
        editor.putInt(PINYIN_SCHEMA_VERSION, PINYIN_SCHEMA_V6)
        editor.apply()
    }

    private fun normalizePinyinMappingValue(value: String): String {
        if (value.isEmpty()) return value
        if (value.isBlank()) return value
        return value.lowercase()
    }

    private fun normalizeMappingValue(value: String): String {
        if (value.isEmpty()) return value
        if (value.isBlank()) return value
        return value.trim()
    }

    fun loadZhuyinKeys(context: Context): List<FlickKeySpec> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        ensureZhuyinSchema(prefs, context)
        return DefaultZhuyinKeyMap.keys.mapIndexed { index, def ->
            FlickKeySpec(
                center = prefs.getString("zhuyin_${index}_center", def.center).orEmpty(),
                left = prefs.getString("zhuyin_${index}_left", def.left).orEmpty(),
                up = prefs.getString("zhuyin_${index}_up", def.up).orEmpty(),
                right = prefs.getString("zhuyin_${index}_right", def.right).orEmpty(),
                down = prefs.getString("zhuyin_${index}_down", def.down).orEmpty(),
                upLeft = prefs.getString("zhuyin_${index}_up_left", def.upLeft).orEmpty(),
                upRight = prefs.getString("zhuyin_${index}_up_right", def.upRight).orEmpty(),
                downLeft = prefs.getString("zhuyin_${index}_down_left", def.downLeft).orEmpty(),
                downRight = prefs.getString("zhuyin_${index}_down_right", def.downRight).orEmpty(),
                zone = loadZone(prefs, "zhuyin_${index}_zone", def.zone)
            )
        }
    }

    fun saveZhuyinKeys(context: Context, keys: List<FlickKeySpec>) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val editor = prefs.edit()
        keys.forEachIndexed { index, k ->
            editor.putString("zhuyin_${index}_center", normalizeMappingValue(k.center))
            editor.putString("zhuyin_${index}_left", normalizeMappingValue(k.left))
            editor.putString("zhuyin_${index}_up", normalizeMappingValue(k.up))
            editor.putString("zhuyin_${index}_right", normalizeMappingValue(k.right))
            editor.putString("zhuyin_${index}_down", normalizeMappingValue(k.down))
            editor.putString("zhuyin_${index}_up_left", normalizeMappingValue(k.upLeft))
            editor.putString("zhuyin_${index}_up_right", normalizeMappingValue(k.upRight))
            editor.putString("zhuyin_${index}_down_left", normalizeMappingValue(k.downLeft))
            editor.putString("zhuyin_${index}_down_right", normalizeMappingValue(k.downRight))
            editor.putString("zhuyin_${index}_zone", k.zone.name)
        }
        editor.putInt(ZHUYIN_SCHEMA_VERSION, ZHUYIN_SCHEMA_V5)
        editor.apply()
    }

    private fun ensureZhuyinSchema(prefs: SharedPreferences, context: Context) {
        val current = prefs.getInt(ZHUYIN_SCHEMA_VERSION, 0)
        if (current >= ZHUYIN_SCHEMA_V5) return
        val k1Center = prefs.getString("zhuyin_0_center", null)
        val k1Down = prefs.getString("zhuyin_0_down", null)
        val k7Center = prefs.getString("zhuyin_6_center", null)
        val k12Center = prefs.getString("zhuyin_11_center", null)
        val looksLegacyV1 = k1Center == "ㄅ" && k1Down == "ㄉ" && k12Center == "ㄩㄣ"
        val looksLegacyV2 = k1Center == "ㄅ" && (k7Center == "ㄨ" || k7Center == "ㄠ") && (k12Center == "ㄨㄚ" || k12Center == "ㄩㄣ")
        val key11Center = prefs.getString("zhuyin_10_center", null)
        val looksLegacySpace = key11Center == "ㄧㄣ"
        val looksLegacyDefault = looksLegacyV1 || looksLegacyV2 || looksLegacySpace
        if (looksLegacyDefault) {
            saveZhuyinKeys(context, DefaultZhuyinKeyMap.keys)
        }
        prefs.edit().putInt(ZHUYIN_SCHEMA_VERSION, ZHUYIN_SCHEMA_V5).apply()
    }

    fun loadJapaneseKeys(context: Context): List<FlickKeySpec> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        ensureJapaneseSchema(prefs, context)
        return DefaultJapaneseKeyMap.keys.mapIndexed { index, def ->
            FlickKeySpec(
                center = prefs.getString("japanese_${index}_center", def.center).orEmpty(),
                left = prefs.getString("japanese_${index}_left", def.left).orEmpty(),
                up = prefs.getString("japanese_${index}_up", def.up).orEmpty(),
                right = prefs.getString("japanese_${index}_right", def.right).orEmpty(),
                down = prefs.getString("japanese_${index}_down", def.down).orEmpty(),
                upLeft = prefs.getString("japanese_${index}_up_left", def.upLeft).orEmpty(),
                upRight = prefs.getString("japanese_${index}_up_right", def.upRight).orEmpty(),
                downLeft = prefs.getString("japanese_${index}_down_left", def.downLeft).orEmpty(),
                downRight = prefs.getString("japanese_${index}_down_right", def.downRight).orEmpty(),
                zone = def.zone
            )
        }
    }

    fun saveJapaneseKeys(context: Context, keys: List<FlickKeySpec>) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val editor = prefs.edit()
        keys.forEachIndexed { index, k ->
            editor.putString("japanese_${index}_center", normalizeMappingValue(k.center))
            editor.putString("japanese_${index}_left", normalizeMappingValue(k.left))
            editor.putString("japanese_${index}_up", normalizeMappingValue(k.up))
            editor.putString("japanese_${index}_right", normalizeMappingValue(k.right))
            editor.putString("japanese_${index}_down", normalizeMappingValue(k.down))
            editor.putString("japanese_${index}_up_left", normalizeMappingValue(k.upLeft))
            editor.putString("japanese_${index}_up_right", normalizeMappingValue(k.upRight))
            editor.putString("japanese_${index}_down_left", normalizeMappingValue(k.downLeft))
            editor.putString("japanese_${index}_down_right", normalizeMappingValue(k.downRight))
        }
        editor.putInt(JAPANESE_SCHEMA_VERSION, JAPANESE_SCHEMA_V4)
        editor.apply()
    }

    private fun ensureJapaneseSchema(prefs: SharedPreferences, context: Context) {
        val current = prefs.getInt(JAPANESE_SCHEMA_VERSION, 0)
        if (current >= JAPANESE_SCHEMA_V4) return
        val k10 = prefs.getString("japanese_9_center", null)
        val k11 = prefs.getString("japanese_10_center", null)
        val k12 = prefs.getString("japanese_11_center", null)
        val k12Left = prefs.getString("japanese_11_left", null).orEmpty()
        val k12Up = prefs.getString("japanese_11_up", null).orEmpty()
        val k12Right = prefs.getString("japanese_11_right", null).orEmpty()
        val k12Down = prefs.getString("japanese_11_down", null).orEmpty()
        val looksLegacyV1 = k10 == "わ" && k11 == "、" && k12 == "゛゜小"
        val looksLegacyV2 = k10 == "、" && k11 == "わ" && k12 == "゛゜小"
        val hasDirectionalModifier = k12Left.isNotBlank() || k12Up.isNotBlank() || k12Right.isNotBlank() || k12Down.isNotBlank()
        val k11Center = prefs.getString("japanese_10_center", null)
        val looksLegacySpace = k11Center == "わ"
        val looksLegacyDefault = (looksLegacyV1 || looksLegacyV2) || (k12 == "゛゜小" && hasDirectionalModifier) || looksLegacySpace
        if (looksLegacyDefault) {
            saveJapaneseKeys(context, DefaultJapaneseKeyMap.keys)
        }
        prefs.edit().putInt(JAPANESE_SCHEMA_VERSION, JAPANESE_SCHEMA_V4).apply()
    }

    private fun ensurePinyinSchema(prefs: SharedPreferences, context: Context) {
        val current = prefs.getInt(PINYIN_SCHEMA_VERSION, 0)
        if (current >= PINYIN_SCHEMA_V6) return

        val key12Center = prefs.getString("pinyin_11_center", null)
        val old12Left = prefs.getString("pinyin_11_left", null)
        val old12Up = prefs.getString("pinyin_11_up", null)
        val old12Right = prefs.getString("pinyin_11_right", null)
        val looksLegacyUmlaut = key12Center == "ü" && old12Left == "üe" && old12Up == "ün" && old12Right == "üan"

        val key5Right = prefs.getString("pinyin_4_right", null)
        val key5Down = prefs.getString("pinyin_4_down", null)
        val looksLegacyPunc = key5Right == "，" && key5Down == "。"

        val key12Up = prefs.getString("pinyin_11_up", null)
        val key12Right = prefs.getString("pinyin_11_right", null)
        val key12Down = prefs.getString("pinyin_11_down", null)
        val looksPreviousSchema = key12Up == "ue" && key12Right == "？" && key12Down == "er"
        val key11Center = prefs.getString("pinyin_10_center", null)
        val looksLegacySpace = key11Center == "ua" || key12Center == "。"

        if (looksLegacyUmlaut || looksLegacyPunc || looksPreviousSchema || looksLegacySpace) {
            savePinyinKeys(context, DefaultKeyMap.keys)
        }
        prefs.edit().putInt(PINYIN_SCHEMA_VERSION, PINYIN_SCHEMA_V6).apply()
    }

    private fun loadZone(prefs: SharedPreferences, key: String, fallback: KeyZone): KeyZone {
        return when (prefs.getString(key, fallback.name)) {
            KeyZone.Shengmu.name -> KeyZone.Shengmu
            KeyZone.Yunmu.name -> KeyZone.Yunmu
            else -> fallback
        }
    }

    fun loadSymbolKeys(context: Context): List<DirectionalKeySpec> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        ensureSymbolSchema(prefs, context)
        return DefaultSymbolMap.keys.mapIndexed { index, def ->
            DirectionalKeySpec(
                center = prefs.getString("symbol_${index}_center", def.center).orEmpty(),
                left = prefs.getString("symbol_${index}_left", def.left).orEmpty(),
                up = prefs.getString("symbol_${index}_up", def.up).orEmpty(),
                right = prefs.getString("symbol_${index}_right", def.right).orEmpty(),
                down = prefs.getString("symbol_${index}_down", def.down).orEmpty(),
                upLeft = prefs.getString("symbol_${index}_up_left", def.upLeft).orEmpty(),
                upRight = prefs.getString("symbol_${index}_up_right", def.upRight).orEmpty(),
                downLeft = prefs.getString("symbol_${index}_down_left", def.downLeft).orEmpty(),
                downRight = prefs.getString("symbol_${index}_down_right", def.downRight).orEmpty()
            )
        }
    }

    fun saveSymbolKeys(context: Context, keys: List<DirectionalKeySpec>) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val editor = prefs.edit()
        keys.forEachIndexed { index, k ->
            editor.putString("symbol_${index}_center", normalizeMappingValue(k.center))
            editor.putString("symbol_${index}_left", normalizeMappingValue(k.left))
            editor.putString("symbol_${index}_up", normalizeMappingValue(k.up))
            editor.putString("symbol_${index}_right", normalizeMappingValue(k.right))
            editor.putString("symbol_${index}_down", normalizeMappingValue(k.down))
            editor.putString("symbol_${index}_up_left", normalizeMappingValue(k.upLeft))
            editor.putString("symbol_${index}_up_right", normalizeMappingValue(k.upRight))
            editor.putString("symbol_${index}_down_left", normalizeMappingValue(k.downLeft))
            editor.putString("symbol_${index}_down_right", normalizeMappingValue(k.downRight))
        }
        editor.putInt(SYMBOL_SCHEMA_VERSION, SYMBOL_SCHEMA_V3)
        editor.apply()
    }

    fun loadAlphaKeys(context: Context): List<DirectionalKeySpec> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        ensureAlphaSchema(prefs, context)
        return DefaultAlphaKeyMap.keys.mapIndexed { index, def ->
            DirectionalKeySpec(
                center = prefs.getString("alpha_${index}_center", def.center).orEmpty(),
                left = prefs.getString("alpha_${index}_left", def.left).orEmpty(),
                up = prefs.getString("alpha_${index}_up", def.up).orEmpty(),
                right = prefs.getString("alpha_${index}_right", def.right).orEmpty(),
                down = prefs.getString("alpha_${index}_down", def.down).orEmpty(),
                upLeft = prefs.getString("alpha_${index}_up_left", def.upLeft).orEmpty(),
                upRight = prefs.getString("alpha_${index}_up_right", def.upRight).orEmpty(),
                downLeft = prefs.getString("alpha_${index}_down_left", def.downLeft).orEmpty(),
                downRight = prefs.getString("alpha_${index}_down_right", def.downRight).orEmpty()
            )
        }
    }

    fun saveAlphaKeys(context: Context, keys: List<DirectionalKeySpec>) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val editor = prefs.edit()
        keys.forEachIndexed { index, k ->
            editor.putString("alpha_${index}_center", normalizeMappingValue(k.center))
            editor.putString("alpha_${index}_left", normalizeMappingValue(k.left))
            editor.putString("alpha_${index}_up", normalizeMappingValue(k.up))
            editor.putString("alpha_${index}_right", normalizeMappingValue(k.right))
            editor.putString("alpha_${index}_down", normalizeMappingValue(k.down))
            editor.putString("alpha_${index}_up_left", normalizeMappingValue(k.upLeft))
            editor.putString("alpha_${index}_up_right", normalizeMappingValue(k.upRight))
            editor.putString("alpha_${index}_down_left", normalizeMappingValue(k.downLeft))
            editor.putString("alpha_${index}_down_right", normalizeMappingValue(k.downRight))
        }
        editor.putInt(ALPHA_SCHEMA_VERSION, ALPHA_SCHEMA_V3)
        editor.apply()
    }

    fun loadNumKeys(context: Context): List<DirectionalKeySpec> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        ensureNumSchema(prefs, context)
        return DefaultNumKeyMap.keys.mapIndexed { index, def ->
            DirectionalKeySpec(
                center = prefs.getString("num_${index}_center", def.center).orEmpty(),
                left = prefs.getString("num_${index}_left", def.left).orEmpty(),
                up = prefs.getString("num_${index}_up", def.up).orEmpty(),
                right = prefs.getString("num_${index}_right", def.right).orEmpty(),
                down = prefs.getString("num_${index}_down", def.down).orEmpty(),
                upLeft = prefs.getString("num_${index}_up_left", def.upLeft).orEmpty(),
                upRight = prefs.getString("num_${index}_up_right", def.upRight).orEmpty(),
                downLeft = prefs.getString("num_${index}_down_left", def.downLeft).orEmpty(),
                downRight = prefs.getString("num_${index}_down_right", def.downRight).orEmpty()
            )
        }
    }

    fun saveNumKeys(context: Context, keys: List<DirectionalKeySpec>) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val editor = prefs.edit()
        keys.forEachIndexed { index, k ->
            editor.putString("num_${index}_center", normalizeMappingValue(k.center))
            editor.putString("num_${index}_left", normalizeMappingValue(k.left))
            editor.putString("num_${index}_up", normalizeMappingValue(k.up))
            editor.putString("num_${index}_right", normalizeMappingValue(k.right))
            editor.putString("num_${index}_down", normalizeMappingValue(k.down))
            editor.putString("num_${index}_up_left", normalizeMappingValue(k.upLeft))
            editor.putString("num_${index}_up_right", normalizeMappingValue(k.upRight))
            editor.putString("num_${index}_down_left", normalizeMappingValue(k.downLeft))
            editor.putString("num_${index}_down_right", normalizeMappingValue(k.downRight))
        }
        editor.putInt(NUM_SCHEMA_VERSION, NUM_SCHEMA_V2)
        editor.apply()
    }

    private fun ensureAlphaSchema(prefs: SharedPreferences, context: Context) {
        val current = prefs.getInt(ALPHA_SCHEMA_VERSION, 0)
        if (current >= ALPHA_SCHEMA_V3) return
        val k1Center = prefs.getString("alpha_0_center", null)
        val k9Center = prefs.getString("alpha_8_center", null)
        val k11Center = prefs.getString("alpha_10_center", null)
        val looksLegacyDefault = (k1Center == "b" && k9Center == "z") || k11Center == "大写锁定" || k11Center == ""
        if (looksLegacyDefault) {
            saveAlphaKeys(context, DefaultAlphaKeyMap.keys)
        }
        prefs.edit().putInt(ALPHA_SCHEMA_VERSION, ALPHA_SCHEMA_V3).apply()
    }

    private fun ensureNumSchema(prefs: SharedPreferences, context: Context) {
        val current = prefs.getInt(NUM_SCHEMA_VERSION, 0)
        if (current >= NUM_SCHEMA_V2) return
        val oldEightUp = prefs.getString("num_7_up", null)
        val oldZeroUp = prefs.getString("num_10_up", null)
        val hasSavedOldDefault = oldEightUp == "0" || oldZeroUp == "="
        if (hasSavedOldDefault) {
            saveNumKeys(context, DefaultNumKeyMap.keys)
            return
        }
        prefs.edit().putInt(NUM_SCHEMA_VERSION, NUM_SCHEMA_V2).apply()
    }

    private fun ensureSymbolSchema(prefs: SharedPreferences, context: Context) {
        val current = prefs.getInt(SYMBOL_SCHEMA_VERSION, 0)
        if (current >= SYMBOL_SCHEMA_V3) return

        val k1Center = prefs.getString("symbol_0_center", null)
        val k4Center = prefs.getString("symbol_3_center", null)
        val k4Left = prefs.getString("symbol_3_left", null)
        val k4Right = prefs.getString("symbol_3_right", null)
        val k12Center = prefs.getString("symbol_11_center", null)

        val looksLegacyDefault = (k1Center == "，" || k1Center == null) &&
            k4Center == "（" &&
            k4Left == "(" &&
            k4Right == "）" &&
            (k12Center == "+" || k12Center == "~" || k12Center == "\\")

        if (looksLegacyDefault) {
            saveSymbolKeys(context, DefaultSymbolMap.keys)
        }

        prefs.edit()
            .putInt(SYMBOL_SCHEMA_VERSION, SYMBOL_SCHEMA_V3)
            .remove("symbol_12_center")
            .remove("symbol_12_left")
            .remove("symbol_12_up")
            .remove("symbol_12_right")
            .remove("symbol_12_down")
            .remove("symbol_12_up_left")
            .remove("symbol_12_up_right")
            .remove("symbol_12_down_left")
            .remove("symbol_12_down_right")
            .apply()
    }

    fun resetPinyinKeys(context: Context) {
        val editor = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
        repeat(12) { index ->
            editor.remove("pinyin_${index}_center")
            editor.remove("pinyin_${index}_left")
            editor.remove("pinyin_${index}_up")
            editor.remove("pinyin_${index}_right")
            editor.remove("pinyin_${index}_down")
            editor.remove("pinyin_${index}_up_left")
            editor.remove("pinyin_${index}_up_right")
            editor.remove("pinyin_${index}_down_left")
            editor.remove("pinyin_${index}_down_right")
            editor.remove("pinyin_${index}_zone")
        }
        editor.putInt(PINYIN_SCHEMA_VERSION, PINYIN_SCHEMA_V6)
        editor.apply()
    }

    fun resetZhuyinKeys(context: Context) {
        val editor = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
        repeat(12) { index ->
            editor.remove("zhuyin_${index}_center")
            editor.remove("zhuyin_${index}_left")
            editor.remove("zhuyin_${index}_up")
            editor.remove("zhuyin_${index}_right")
            editor.remove("zhuyin_${index}_down")
            editor.remove("zhuyin_${index}_up_left")
            editor.remove("zhuyin_${index}_up_right")
            editor.remove("zhuyin_${index}_down_left")
            editor.remove("zhuyin_${index}_down_right")
            editor.remove("zhuyin_${index}_zone")
        }
        editor.putInt(ZHUYIN_SCHEMA_VERSION, ZHUYIN_SCHEMA_V5)
        editor.apply()
    }

    fun resetJapaneseKeys(context: Context) {
        val editor = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
        repeat(12) { index ->
            editor.remove("japanese_${index}_center")
            editor.remove("japanese_${index}_left")
            editor.remove("japanese_${index}_up")
            editor.remove("japanese_${index}_right")
            editor.remove("japanese_${index}_down")
            editor.remove("japanese_${index}_up_left")
            editor.remove("japanese_${index}_up_right")
            editor.remove("japanese_${index}_down_left")
            editor.remove("japanese_${index}_down_right")
        }
        editor.putInt(JAPANESE_SCHEMA_VERSION, JAPANESE_SCHEMA_V4)
        editor.apply()
    }

    fun resetSymbolKeys(context: Context) {
        val editor = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
        repeat(12) { index ->
            editor.remove("symbol_${index}_center")
            editor.remove("symbol_${index}_left")
            editor.remove("symbol_${index}_up")
            editor.remove("symbol_${index}_right")
            editor.remove("symbol_${index}_down")
            editor.remove("symbol_${index}_up_left")
            editor.remove("symbol_${index}_up_right")
            editor.remove("symbol_${index}_down_left")
            editor.remove("symbol_${index}_down_right")
        }
        editor.putInt(SYMBOL_SCHEMA_VERSION, SYMBOL_SCHEMA_V3)
        editor.apply()
    }

    fun resetAlphaKeys(context: Context) {
        val editor = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
        repeat(12) { index ->
            editor.remove("alpha_${index}_center")
            editor.remove("alpha_${index}_left")
            editor.remove("alpha_${index}_up")
            editor.remove("alpha_${index}_right")
            editor.remove("alpha_${index}_down")
            editor.remove("alpha_${index}_up_left")
            editor.remove("alpha_${index}_up_right")
            editor.remove("alpha_${index}_down_left")
            editor.remove("alpha_${index}_down_right")
        }
        editor.putInt(ALPHA_SCHEMA_VERSION, ALPHA_SCHEMA_V3)
        editor.apply()
    }

    fun resetNumKeys(context: Context) {
        val editor = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
        repeat(12) { index ->
            editor.remove("num_${index}_center")
            editor.remove("num_${index}_left")
            editor.remove("num_${index}_up")
            editor.remove("num_${index}_right")
            editor.remove("num_${index}_down")
            editor.remove("num_${index}_up_left")
            editor.remove("num_${index}_up_right")
            editor.remove("num_${index}_down_left")
            editor.remove("num_${index}_down_right")
        }
        editor.putInt(NUM_SCHEMA_VERSION, NUM_SCHEMA_V2)
        editor.apply()
    }
}
