package com.example.flickime.data

import android.content.Context
import com.example.flickime.model.FlickKeySpec

object KeyMapStore {
    private const val PREFS = "flick_keymap"

    fun loadPinyinKeys(context: Context): List<FlickKeySpec> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return DefaultKeyMap.keys.mapIndexed { index, def ->
            FlickKeySpec(
                center = prefs.getString("${index}_center", def.center).orEmpty(),
                left = prefs.getString("${index}_left", def.left).orEmpty(),
                up = prefs.getString("${index}_up", def.up).orEmpty(),
                right = prefs.getString("${index}_right", def.right).orEmpty(),
                down = prefs.getString("${index}_down", def.down).orEmpty(),
                zone = def.zone
            )
        }
    }

    fun savePinyinKeys(context: Context, keys: List<FlickKeySpec>) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val editor = prefs.edit()
        keys.forEachIndexed { index, k ->
            editor.putString("${index}_center", k.center.trim().lowercase())
            editor.putString("${index}_left", k.left.trim().lowercase())
            editor.putString("${index}_up", k.up.trim().lowercase())
            editor.putString("${index}_right", k.right.trim().lowercase())
            editor.putString("${index}_down", k.down.trim().lowercase())
        }
        editor.apply()
    }

    fun resetPinyinKeys(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
    }
}
