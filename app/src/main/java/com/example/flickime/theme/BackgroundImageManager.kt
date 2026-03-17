package com.example.flickime.theme

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import kotlin.math.roundToInt

data class BackgroundOption(
    val id: String,
    val name: String,
    val path: String,
    val builtIn: Boolean
)

object BackgroundImageManager {
    private const val KEY_IME_BG_OPTIONS = "ime_bg_options"
    private const val KEY_KEY_BG_OPTIONS = "key_bg_options"
    private const val KEY_SELECTED_IME_BG_ID = "selected_ime_bg_id"
    private const val KEY_SELECTED_KEY_BG_ID = "selected_key_bg_id"

    private const val IME_SOLID_ID = "bg.ime.solid"
    private const val IME_MIKU_ID = "bg.ime.miku"
    private const val KEY_SOLID_ID = "bg.key.solid"

    const val IME_RECOMMENDED_RATIO = "1:1"
    const val KEY_RECOMMENDED_RATIO = "2.45:1"

    fun getImeOptions(context: Context): List<BackgroundOption> {
        ensureInitialized(context)
        return imeOptionsRaw(context)
    }

    fun getKeyOptions(context: Context): List<BackgroundOption> {
        ensureInitialized(context)
        return keyOptionsRaw(context)
    }

    fun getSelectedImeId(context: Context): String {
        ensureInitialized(context)
        val selected = UiPrefs.prefs(context).getString(KEY_SELECTED_IME_BG_ID, IME_SOLID_ID).orEmpty()
        return getImeOptions(context).firstOrNull { it.id == selected }?.id ?: IME_SOLID_ID
    }

    fun getSelectedKeyId(context: Context): String {
        ensureInitialized(context)
        val selected = UiPrefs.prefs(context).getString(KEY_SELECTED_KEY_BG_ID, KEY_SOLID_ID).orEmpty()
        return getKeyOptions(context).firstOrNull { it.id == selected }?.id ?: KEY_SOLID_ID
    }

    fun selectImeBackground(context: Context, optionId: String) {
        ensureInitialized(context)
        val option = imeOptionsRaw(context).firstOrNull { it.id == optionId } ?: return
        UiPrefs.prefs(context).edit()
            .putString(KEY_SELECTED_IME_BG_ID, option.id)
            .putString(UiPrefs.KEY_IME_BG_IMAGE_PATH, option.path)
            .apply()
    }

    fun selectKeyBackground(context: Context, optionId: String) {
        ensureInitialized(context)
        val option = keyOptionsRaw(context).firstOrNull { it.id == optionId } ?: return
        UiPrefs.prefs(context).edit()
            .putString(KEY_SELECTED_KEY_BG_ID, option.id)
            .putString(UiPrefs.KEY_KEY_BG_IMAGE_PATH, option.path)
            .apply()
    }


    fun selectDefaultImeBackground(context: Context) {
        selectImeBackground(context, IME_SOLID_ID)
    }

    fun selectDefaultKeyBackground(context: Context) {
        selectKeyBackground(context, KEY_SOLID_ID)
    }

    fun registerImeBackgroundFile(
        context: Context,
        id: String,
        name: String,
        filePath: String
    ): BackgroundOption {
        ensureInitialized(context)
        val file = File(filePath)
        require(file.exists() && file.isFile) { "输入法背景图文件不存在" }
        val option = BackgroundOption(
            id = id,
            name = name.ifBlank { "主题包输入法背景" },
            path = file.absolutePath,
            builtIn = false
        )
        saveImportedOption(context, KEY_IME_BG_OPTIONS, option)
        return option
    }

    fun registerKeyBackgroundFile(
        context: Context,
        id: String,
        name: String,
        filePath: String
    ): BackgroundOption {
        ensureInitialized(context)
        val file = File(filePath)
        require(file.exists() && file.isFile) { "按键图文件不存在" }
        val option = BackgroundOption(
            id = id,
            name = name.ifBlank { "主题包按键图" },
            path = file.absolutePath,
            builtIn = false
        )
        saveImportedOption(context, KEY_KEY_BG_OPTIONS, option)
        return option
    }

    fun importImeBackground(context: Context, uri: Uri): BackgroundOption {
        ensureInitialized(context)
        val option = importAndRegister(
            context = context,
            uri = uri,
            aspect = 1f,
            tag = "ime",
            prefKey = KEY_IME_BG_OPTIONS,
            idPrefix = "bg.ime.custom."
        )
        selectImeBackground(context, option.id)
        return option
    }

    fun importKeyBackground(context: Context, uri: Uri): BackgroundOption {
        ensureInitialized(context)
        val option = importAndRegister(
            context = context,
            uri = uri,
            aspect = 2.45f,
            tag = "key",
            prefKey = KEY_KEY_BG_OPTIONS,
            idPrefix = "bg.key.custom."
        )
        selectKeyBackground(context, option.id)
        return option
    }

    fun resetToDefaults(context: Context) {
        UiPrefs.prefs(context).edit()
            .putString(KEY_SELECTED_IME_BG_ID, IME_SOLID_ID)
            .putString(KEY_SELECTED_KEY_BG_ID, KEY_SOLID_ID)
            .putString(UiPrefs.KEY_IME_BG_IMAGE_PATH, "")
            .putString(UiPrefs.KEY_KEY_BG_IMAGE_PATH, "")
            .apply()
    }

    private fun importAndRegister(
        context: Context,
        uri: Uri,
        aspect: Float,
        tag: String,
        prefKey: String,
        idPrefix: String
    ): BackgroundOption {
        val decoded = decodeUriBitmap(context, uri) ?: error("读取图片失败")
        val cropped = cropToAspectCenter(decoded, aspect)
        if (cropped !== decoded) decoded.recycle()

        val ts = System.currentTimeMillis()
        val outDir = File(context.filesDir, "backgrounds").apply { mkdirs() }
        val outFile = File(outDir, "${tag}_auto_crop_$ts.png")
        outFile.outputStream().use { os ->
            cropped.compress(Bitmap.CompressFormat.PNG, 100, os)
        }
        cropped.recycle()

        val option = BackgroundOption(
            id = "$idPrefix$ts",
            name = "自定义${if (tag == "ime") "背景" else "按键图"} $ts",
            path = outFile.absolutePath,
            builtIn = false
        )
        saveImportedOption(context, prefKey, option)
        return option
    }

    private fun decodeUriBitmap(context: Context, uri: Uri): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { input ->
            BitmapFactory.decodeStream(input, null, bounds)
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sample = 1
        val maxSide = maxOf(bounds.outWidth, bounds.outHeight)
        while (maxSide / sample > 2048) sample *= 2

        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return context.contentResolver.openInputStream(uri)?.use { input ->
            BitmapFactory.decodeStream(input, null, options)
        }
    }

    private fun cropToAspectCenter(src: Bitmap, targetAspect: Float): Bitmap {
        val w = src.width
        val h = src.height
        if (w <= 0 || h <= 0) return src
        val srcAspect = w.toFloat() / h.toFloat()
        if (kotlin.math.abs(srcAspect - targetAspect) < 0.01f) return src

        return if (srcAspect > targetAspect) {
            val cw = (h * targetAspect).roundToInt().coerceAtLeast(1).coerceAtMost(w)
            val x = ((w - cw) / 2f).roundToInt().coerceIn(0, w - cw)
            Bitmap.createBitmap(src, x, 0, cw, h)
        } else {
            val ch = (w / targetAspect).roundToInt().coerceAtLeast(1).coerceAtMost(h)
            val y = ((h - ch) / 2f).roundToInt().coerceIn(0, h - ch)
            Bitmap.createBitmap(src, 0, y, w, ch)
        }
    }

    private fun builtInImeOptions(): List<BackgroundOption> {
        return listOf(
            BackgroundOption(IME_SOLID_ID, "纯色背景（默认）", "", true),
            BackgroundOption(IME_MIKU_ID, "初音默认背景", UiPrefs.MIKU_BG_ASSET, true)
        )
    }

    private fun builtInKeyOptions(): List<BackgroundOption> {
        return listOf(
            BackgroundOption(KEY_SOLID_ID, "纯色按键图（默认）", "", true)
        )
    }

    private fun imeOptionsRaw(context: Context): List<BackgroundOption> {
        return builtInImeOptions() + loadImportedOptions(context, KEY_IME_BG_OPTIONS)
    }

    private fun keyOptionsRaw(context: Context): List<BackgroundOption> {
        return builtInKeyOptions() + loadImportedOptions(context, KEY_KEY_BG_OPTIONS)
    }

    private fun ensureInitialized(context: Context) {
        val prefs = UiPrefs.prefs(context)
        val editor = prefs.edit()

        val imeSelected = prefs.getString(KEY_SELECTED_IME_BG_ID, null)
        val keySelected = prefs.getString(KEY_SELECTED_KEY_BG_ID, null)
        val legacyImePath = prefs.getString(UiPrefs.KEY_IME_BG_IMAGE_PATH, "").orEmpty()
        val legacyKeyPath = prefs.getString(UiPrefs.KEY_KEY_BG_IMAGE_PATH, "").orEmpty()

        if (imeSelected.isNullOrBlank()) {
            val initial = when {
                legacyImePath.isBlank() -> IME_SOLID_ID
                legacyImePath == UiPrefs.MIKU_BG_ASSET -> IME_MIKU_ID
                else -> registerLegacyPath(context, KEY_IME_BG_OPTIONS, "bg.ime.custom.legacy.", "历史背景", legacyImePath)
            }
            editor.putString(KEY_SELECTED_IME_BG_ID, initial)
        }
        if (keySelected.isNullOrBlank()) {
            val initial = when {
                legacyKeyPath.isBlank() -> KEY_SOLID_ID
                else -> registerLegacyPath(context, KEY_KEY_BG_OPTIONS, "bg.key.custom.legacy.", "历史按键图", legacyKeyPath)
            }
            editor.putString(KEY_SELECTED_KEY_BG_ID, initial)
        }

        val imeId = prefs.getString(KEY_SELECTED_IME_BG_ID, IME_SOLID_ID).orEmpty()
        val imePath = imeOptionsRaw(context).firstOrNull { it.id == imeId }?.path ?: ""
        editor.putString(UiPrefs.KEY_IME_BG_IMAGE_PATH, imePath)

        val keyId = prefs.getString(KEY_SELECTED_KEY_BG_ID, KEY_SOLID_ID).orEmpty()
        val keyPath = keyOptionsRaw(context).firstOrNull { it.id == keyId }?.path ?: ""
        editor.putString(UiPrefs.KEY_KEY_BG_IMAGE_PATH, keyPath)

        editor.apply()
    }

    private fun registerLegacyPath(
        context: Context,
        prefKey: String,
        idPrefix: String,
        label: String,
        path: String
    ): String {
        val existing = loadImportedOptions(context, prefKey).firstOrNull { it.path == path }
        if (existing != null) return existing.id
        if (path.isBlank()) return if (prefKey == KEY_IME_BG_OPTIONS) IME_SOLID_ID else KEY_SOLID_ID
        if (!path.startsWith("asset://") && !File(path).exists()) return if (prefKey == KEY_IME_BG_OPTIONS) IME_SOLID_ID else KEY_SOLID_ID

        val option = BackgroundOption(
            id = "$idPrefix${System.currentTimeMillis()}",
            name = label,
            path = path,
            builtIn = false
        )
        saveImportedOption(context, prefKey, option)
        return option.id
    }

    private fun loadImportedOptions(context: Context, prefKey: String): List<BackgroundOption> {
        val raw = UiPrefs.prefs(context).getString(prefKey, "[]").orEmpty()
        val arr = runCatching { JSONArray(raw) }.getOrDefault(JSONArray())
        val out = mutableListOf<BackgroundOption>()
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val id = obj.optString("id")
            val name = obj.optString("name", "自定义图片")
            val path = obj.optString("path")
            if (id.isBlank()) continue
            if (path.isBlank()) continue
            if (!path.startsWith("asset://") && !File(path).exists()) continue
            out += BackgroundOption(id = id, name = name, path = path, builtIn = false)
        }
        return out
    }

    private fun saveImportedOption(context: Context, prefKey: String, option: BackgroundOption) {
        val current = loadImportedOptions(context, prefKey)
        val arr = JSONArray()
        current.filter { it.id != option.id }.forEach { item ->
            arr.put(
                JSONObject().apply {
                    put("id", item.id)
                    put("name", item.name)
                    put("path", item.path)
                }
            )
        }
        arr.put(
            JSONObject().apply {
                put("id", option.id)
                put("name", option.name)
                put("path", option.path)
            }
        )
        UiPrefs.prefs(context).edit().putString(prefKey, arr.toString()).apply()
    }
}
