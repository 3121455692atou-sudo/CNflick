package com.example.flickime.data

import android.content.Context
import com.example.flickime.model.DirectionalKeySpec
import com.example.flickime.model.FlickKeySpec

object KeyMapStore {
    private const val PREFS = "flick_keymap"

    fun loadPinyinKeys(context: Context): List<FlickKeySpec> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return DefaultKeyMap.keys.mapIndexed { index, def ->
            FlickKeySpec(
                center = prefs.getString("pinyin_${index}_center", def.center).orEmpty(),
                left = prefs.getString("pinyin_${index}_left", def.left).orEmpty(),
                up = prefs.getString("pinyin_${index}_up", def.up).orEmpty(),
                right = prefs.getString("pinyin_${index}_right", def.right).orEmpty(),
                down = prefs.getString("pinyin_${index}_down", def.down).orEmpty(),
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
        }
        editor.apply()
    }

    fun loadSymbolKeys(context: Context): List<DirectionalKeySpec> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return DefaultSymbolMap.keys.mapIndexed { index, def ->
            DirectionalKeySpec(
                center = prefs.getString("symbol_${index}_center", def.center).orEmpty(),
                left = prefs.getString("symbol_${index}_left", def.left).orEmpty(),
                up = prefs.getString("symbol_${index}_up", def.up).orEmpty(),
                right = prefs.getString("symbol_${index}_right", def.right).orEmpty(),
                down = prefs.getString("symbol_${index}_down", def.down).orEmpty()
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
        }
        editor.apply()
    }

    fun resetPinyinKeys(context: Context) {
        val editor = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
        repeat(12) { index ->
            editor.remove("pinyin_${index}_center")
            editor.remove("pinyin_${index}_left")
            editor.remove("pinyin_${index}_up")
            editor.remove("pinyin_${index}_right")
            editor.remove("pinyin_${index}_down")
        }
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
        }
        editor.apply()
    }
}
