package com.example.flickime.data

import android.content.Context
import android.content.SharedPreferences
import com.example.flickime.model.DirectionalKeySpec
import com.example.flickime.model.FlickKeySpec

object KeyMapStore {
    private const val PREFS = "flick_keymap"

    private const val PINYIN_SCHEMA_VERSION = "pinyin_schema_version"
    private const val PINYIN_SCHEMA_V4 = 4
    private const val ZHUYIN_SCHEMA_VERSION = "zhuyin_schema_version"
    private const val ZHUYIN_SCHEMA_V3 = 3
    private const val JAPANESE_SCHEMA_VERSION = "japanese_schema_version"
    private const val JAPANESE_SCHEMA_V3 = 3

    private const val SYMBOL_SCHEMA_VERSION = "symbol_schema_version"
    private const val SYMBOL_SCHEMA_V3 = 3

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
                zone = def.zone
            )
        }
    }

    fun savePinyinKeys(context: Context, keys: List<FlickKeySpec>) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val editor = prefs.edit()
        keys.forEachIndexed { index, k ->
            editor.putString("pinyin_${index}_center", k.center.trim().lowercase())
            editor.putString("pinyin_${index}_left", k.left.trim().lowercase())
            editor.putString("pinyin_${index}_up", k.up.trim().lowercase())
            editor.putString("pinyin_${index}_right", k.right.trim().lowercase())
            editor.putString("pinyin_${index}_down", k.down.trim().lowercase())
            editor.putString("pinyin_${index}_up_left", k.upLeft.trim().lowercase())
            editor.putString("pinyin_${index}_up_right", k.upRight.trim().lowercase())
            editor.putString("pinyin_${index}_down_left", k.downLeft.trim().lowercase())
            editor.putString("pinyin_${index}_down_right", k.downRight.trim().lowercase())
        }
        editor.putInt(PINYIN_SCHEMA_VERSION, PINYIN_SCHEMA_V4)
        editor.apply()
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
                zone = def.zone
            )
        }
    }

    fun saveZhuyinKeys(context: Context, keys: List<FlickKeySpec>) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val editor = prefs.edit()
        keys.forEachIndexed { index, k ->
            editor.putString("zhuyin_${index}_center", k.center.trim())
            editor.putString("zhuyin_${index}_left", k.left.trim())
            editor.putString("zhuyin_${index}_up", k.up.trim())
            editor.putString("zhuyin_${index}_right", k.right.trim())
            editor.putString("zhuyin_${index}_down", k.down.trim())
            editor.putString("zhuyin_${index}_up_left", k.upLeft.trim())
            editor.putString("zhuyin_${index}_up_right", k.upRight.trim())
            editor.putString("zhuyin_${index}_down_left", k.downLeft.trim())
            editor.putString("zhuyin_${index}_down_right", k.downRight.trim())
        }
        editor.putInt(ZHUYIN_SCHEMA_VERSION, ZHUYIN_SCHEMA_V3)
        editor.apply()
    }

    private fun ensureZhuyinSchema(prefs: SharedPreferences, context: Context) {
        val current = prefs.getInt(ZHUYIN_SCHEMA_VERSION, 0)
        if (current >= ZHUYIN_SCHEMA_V3) return
        val k1Center = prefs.getString("zhuyin_0_center", null)
        val k1Down = prefs.getString("zhuyin_0_down", null)
        val k7Center = prefs.getString("zhuyin_6_center", null)
        val k12Center = prefs.getString("zhuyin_11_center", null)
        val looksLegacyV1 = k1Center == "ㄅ" && k1Down == "ㄉ" && k12Center == "ㄩㄣ"
        val looksLegacyV2 = k1Center == "ㄅ" && (k7Center == "ㄨ" || k7Center == "ㄠ") && (k12Center == "ㄨㄚ" || k12Center == "ㄩㄣ")
        val looksLegacyDefault = looksLegacyV1 || looksLegacyV2
        if (looksLegacyDefault) {
            saveZhuyinKeys(context, DefaultZhuyinKeyMap.keys)
        }
        prefs.edit().putInt(ZHUYIN_SCHEMA_VERSION, ZHUYIN_SCHEMA_V3).apply()
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
            editor.putString("japanese_${index}_center", k.center.trim())
            editor.putString("japanese_${index}_left", k.left.trim())
            editor.putString("japanese_${index}_up", k.up.trim())
            editor.putString("japanese_${index}_right", k.right.trim())
            editor.putString("japanese_${index}_down", k.down.trim())
            editor.putString("japanese_${index}_up_left", k.upLeft.trim())
            editor.putString("japanese_${index}_up_right", k.upRight.trim())
            editor.putString("japanese_${index}_down_left", k.downLeft.trim())
            editor.putString("japanese_${index}_down_right", k.downRight.trim())
        }
        editor.putInt(JAPANESE_SCHEMA_VERSION, JAPANESE_SCHEMA_V3)
        editor.apply()
    }

    private fun ensureJapaneseSchema(prefs: SharedPreferences, context: Context) {
        val current = prefs.getInt(JAPANESE_SCHEMA_VERSION, 0)
        if (current >= JAPANESE_SCHEMA_V3) return
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
        val looksLegacyDefault = (looksLegacyV1 || looksLegacyV2) || (k12 == "゛゜小" && hasDirectionalModifier)
        if (looksLegacyDefault) {
            saveJapaneseKeys(context, DefaultJapaneseKeyMap.keys)
        }
        prefs.edit().putInt(JAPANESE_SCHEMA_VERSION, JAPANESE_SCHEMA_V3).apply()
    }

    private fun ensurePinyinSchema(prefs: SharedPreferences, context: Context) {
        val current = prefs.getInt(PINYIN_SCHEMA_VERSION, 0)
        if (current >= PINYIN_SCHEMA_V4) return

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

        if (looksLegacyUmlaut || looksLegacyPunc || looksPreviousSchema) {
            savePinyinKeys(context, DefaultKeyMap.keys)
        }
        prefs.edit().putInt(PINYIN_SCHEMA_VERSION, PINYIN_SCHEMA_V4).apply()
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
            editor.putString("symbol_${index}_center", k.center.trim())
            editor.putString("symbol_${index}_left", k.left.trim())
            editor.putString("symbol_${index}_up", k.up.trim())
            editor.putString("symbol_${index}_right", k.right.trim())
            editor.putString("symbol_${index}_down", k.down.trim())
            editor.putString("symbol_${index}_up_left", k.upLeft.trim())
            editor.putString("symbol_${index}_up_right", k.upRight.trim())
            editor.putString("symbol_${index}_down_left", k.downLeft.trim())
            editor.putString("symbol_${index}_down_right", k.downRight.trim())
        }
        editor.putInt(SYMBOL_SCHEMA_VERSION, SYMBOL_SCHEMA_V3)
        editor.apply()
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
        }
        editor.putInt(PINYIN_SCHEMA_VERSION, PINYIN_SCHEMA_V4)
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
        }
        editor.putInt(ZHUYIN_SCHEMA_VERSION, ZHUYIN_SCHEMA_V3)
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
        editor.putInt(JAPANESE_SCHEMA_VERSION, JAPANESE_SCHEMA_V3)
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
}
