package com.example.flickime.theme

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.zip.ZipFile

data class ImportedThemePack(
    val packId: String,
    val packName: String,
    val version: String,
    val themeId: String,
    val fontId: String?,
    val imeBackgroundId: String?,
    val keyBackgroundId: String?,
    val soundPath: String?,
    val zipPath: String,
    val importedAt: Long
)

private data class ThemePackAssets(
    val font: String?,
    val imeBackground: String?,
    val keyBackground: String?,
    val keySound: String?
)

private data class ThemePackManifest(
    val packId: String,
    val packName: String,
    val version: String,
    val themeId: String?,
    val assets: ThemePackAssets
)

object ThemePackManager {
    private const val KEY_IMPORTED_THEME_PACKS = "imported_theme_packs"
    private const val KEY_CURRENT_THEME_PACK_ID = "current_theme_pack_id"
    private const val MANIFEST_FILE = "theme-pack.json"
    private val PACK_ID_REGEX = Regex("^[a-zA-Z0-9._-]{3,64}$")

    private const val PRESET_DEFAULT_PACK_ID = "preset.default"
    private const val PRESET_MIKU_PACK_ID = "preset.miku"

    private const val THEME_DEFAULT_LIGHT = "cnflick.theme.default_light"
    private const val THEME_MIKU = "com.cnflick.theme.hatsune_miku_teal"

    private val presetPacks = listOf(
        ImportedThemePack(
            packId = PRESET_DEFAULT_PACK_ID,
            packName = "纯默认主题包",
            version = "builtin",
            themeId = THEME_DEFAULT_LIGHT,
            fontId = "font.system",
            imeBackgroundId = null,
            keyBackgroundId = null,
            soundPath = null,
            zipPath = "builtin://default",
            importedAt = 0L
        ),
        ImportedThemePack(
            packId = PRESET_MIKU_PACK_ID,
            packName = "初音主题包",
            version = "builtin",
            themeId = THEME_MIKU,
            fontId = "font.jetbrains_mono",
            imeBackgroundId = "bg.ime.miku",
            keyBackgroundId = null,
            soundPath = null,
            zipPath = "builtin://miku",
            importedAt = 0L
        )
    )

    fun getAvailablePacks(context: Context): List<ImportedThemePack> {
        return presetPacks + getImportedPacks(context)
    }

    fun getImportedPacks(context: Context): List<ImportedThemePack> {
        val arr = loadPackJson(context)
        val out = mutableListOf<ImportedThemePack>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val packId = o.optString("packId").trim()
            val packName = o.optString("packName").trim()
            val version = o.optString("version").trim()
            val themeId = o.optString("themeId").trim()
            val zipPath = o.optString("zipPath").trim()
            if (packId.isBlank() || packName.isBlank() || version.isBlank() || themeId.isBlank() || zipPath.isBlank()) continue
            out += ImportedThemePack(
                packId = packId,
                packName = packName,
                version = version,
                themeId = themeId,
                fontId = o.optString("fontId").trim().ifBlank { null },
                imeBackgroundId = o.optString("imeBackgroundId").trim().ifBlank { null },
                keyBackgroundId = o.optString("keyBackgroundId").trim().ifBlank { null },
                soundPath = o.optString("soundPath").trim().ifBlank { null },
                zipPath = zipPath,
                importedAt = o.optLong("importedAt", 0L)
            )
        }
        return out.sortedByDescending { it.importedAt }
    }

    fun getCurrentPackId(context: Context): String {
        return UiPrefs.prefs(context).getString(KEY_CURRENT_THEME_PACK_ID, "").orEmpty()
    }

    fun clearCurrentPack(context: Context) {
        UiPrefs.prefs(context).edit().remove(KEY_CURRENT_THEME_PACK_ID).apply()
    }

    fun importThemePack(context: Context, uri: Uri): ImportedThemePack {
        val now = System.currentTimeMillis()
        val packDir = File(context.filesDir, "theme_packs").apply { mkdirs() }
        val tempZip = File(packDir, "pack_src_$now.zip")
        context.contentResolver.openInputStream(uri)?.use { input ->
            tempZip.outputStream().use { output -> input.copyTo(output) }
        } ?: error("无法读取主题包")

        val manifest = parseManifest(tempZip)
        val theme = ThemeManager.importThemeZipFile(context, tempZip)
        val importedZipPath = theme.sourceZipPath ?: error("主题包导入失败：未生成主题 zip")
        val importedZip = File(importedZipPath)

        if (!manifest.themeId.isNullOrBlank() && manifest.themeId != theme.id) {
            error("theme-pack.json 的 themeId 与 theme.json 的 id 不一致")
        }

        val fontIdConst = "font.pack.${manifest.packId}"
        val imeBgIdConst = "bg.ime.pack.${manifest.packId}"
        val keyBgIdConst = "bg.key.pack.${manifest.packId}"

        var fontId: String? = null
        var imeBackgroundId: String? = null
        var keyBackgroundId: String? = null
        var soundPath: String? = null

        ZipFile(importedZip).use { zip ->
            manifest.assets.font?.let { entryPath ->
                val file = extractZipEntry(
                    zip = zip,
                    entryPath = entryPath,
                    outDir = File(context.filesDir, "fonts").apply { mkdirs() },
                    filePrefix = "pack_${safeFileTag(manifest.packId)}_font"
                )
                val font = FontManager.registerFontFile(
                    context = context,
                    id = fontIdConst,
                    name = "${manifest.packName} 字体",
                    filePath = file.absolutePath,
                    activate = false
                )
                fontId = font.id
            }

            manifest.assets.imeBackground?.let { entryPath ->
                val file = extractZipEntry(
                    zip = zip,
                    entryPath = entryPath,
                    outDir = File(context.filesDir, "backgrounds").apply { mkdirs() },
                    filePrefix = "pack_${safeFileTag(manifest.packId)}_ime"
                )
                val option = BackgroundImageManager.registerImeBackgroundFile(
                    context = context,
                    id = imeBgIdConst,
                    name = "${manifest.packName} 背景",
                    filePath = file.absolutePath
                )
                imeBackgroundId = option.id
            }

            manifest.assets.keyBackground?.let { entryPath ->
                val file = extractZipEntry(
                    zip = zip,
                    entryPath = entryPath,
                    outDir = File(context.filesDir, "backgrounds").apply { mkdirs() },
                    filePrefix = "pack_${safeFileTag(manifest.packId)}_key"
                )
                val option = BackgroundImageManager.registerKeyBackgroundFile(
                    context = context,
                    id = keyBgIdConst,
                    name = "${manifest.packName} 按键图",
                    filePath = file.absolutePath
                )
                keyBackgroundId = option.id
            }

            manifest.assets.keySound?.let { entryPath ->
                val file = extractZipEntry(
                    zip = zip,
                    entryPath = entryPath,
                    outDir = File(context.filesDir, "custom").apply { mkdirs() },
                    filePrefix = "pack_${safeFileTag(manifest.packId)}_sound"
                )
                soundPath = file.absolutePath
            }
        }

        runCatching { tempZip.delete() }

        val record = ImportedThemePack(
            packId = manifest.packId,
            packName = manifest.packName,
            version = manifest.version,
            themeId = theme.id,
            fontId = fontId,
            imeBackgroundId = imeBackgroundId,
            keyBackgroundId = keyBackgroundId,
            soundPath = soundPath,
            zipPath = importedZip.absolutePath,
            importedAt = now
        )

        upsertPack(context, record)
        applyRecord(context, record)
        return record
    }

    fun applyThemePack(context: Context, packId: String): ImportedThemePack {
        val record = getAvailablePacks(context).firstOrNull { it.packId == packId }
            ?: error("未找到主题包：$packId")
        applyRecord(context, record)
        return record
    }

    private fun applyRecord(context: Context, record: ImportedThemePack) {
        val allThemes = ThemeManager.getAllThemes(context)
        val fallbackThemeId = ThemeManager.getBuiltInThemes().firstOrNull()?.id ?: "cnflick.theme.default_light"
        val themeId = if (allThemes.any { it.id == record.themeId }) record.themeId else fallbackThemeId
        ThemeManager.setCurrentTheme(context, themeId)

        val allFonts = FontManager.getAllFonts(context).map { it.id }.toSet()
        if (!record.fontId.isNullOrBlank() && allFonts.contains(record.fontId)) {
            FontManager.setCurrentFontId(context, record.fontId)
        } else {
            FontManager.setCurrentFontId(context, "font.system")
        }

        val imeOptions = BackgroundImageManager.getImeOptions(context).map { it.id }.toSet()
        if (!record.imeBackgroundId.isNullOrBlank() && imeOptions.contains(record.imeBackgroundId)) {
            BackgroundImageManager.selectImeBackground(context, record.imeBackgroundId)
        } else {
            BackgroundImageManager.selectDefaultImeBackground(context)
        }

        val keyOptions = BackgroundImageManager.getKeyOptions(context).map { it.id }.toSet()
        if (!record.keyBackgroundId.isNullOrBlank() && keyOptions.contains(record.keyBackgroundId)) {
            BackgroundImageManager.selectKeyBackground(context, record.keyBackgroundId)
        } else {
            BackgroundImageManager.selectDefaultKeyBackground(context)
        }

        val soundValid = !record.soundPath.isNullOrBlank() && File(record.soundPath).exists()
        UiPrefs.prefs(context).edit()
            .putBoolean(UiPrefs.KEY_USE_CUSTOM_SOUND, soundValid)
            .putString(UiPrefs.KEY_CUSTOM_SOUND_PATH, if (soundValid) record.soundPath else "")
            .putString(KEY_CURRENT_THEME_PACK_ID, record.packId)
            .apply()
    }

    private fun parseManifest(zipFile: File): ThemePackManifest {
        ZipFile(zipFile).use { zip ->
            val entry = zip.getEntry(MANIFEST_FILE)
                ?: error("主题包格式错误：缺少 $MANIFEST_FILE")
            val manifestRaw = zip.getInputStream(entry).bufferedReader(Charsets.UTF_8).use { it.readText() }
            val root = runCatching { JSONObject(manifestRaw) }
                .getOrElse { throw IllegalArgumentException("$MANIFEST_FILE 不是有效 JSON") }

            val packId = root.optString("packId").trim()
            val packName = root.optString("packName").trim()
            val version = root.optString("version").trim()
            val themeId = root.optString("themeId").trim().ifBlank { null }

            if (!PACK_ID_REGEX.matches(packId)) {
                error("theme-pack.json 的 packId 非法，需匹配 ${PACK_ID_REGEX.pattern}")
            }
            if (packName.isBlank()) error("theme-pack.json 的 packName 不能为空")
            if (version.isBlank()) error("theme-pack.json 的 version 不能为空")

            val assets = root.optJSONObject("assets") ?: JSONObject()
            val manifest = ThemePackManifest(
                packId = packId,
                packName = packName,
                version = version,
                themeId = themeId,
                assets = ThemePackAssets(
                    font = readAssetPath(assets, "font"),
                    imeBackground = readAssetPath(assets, "imeBackground"),
                    keyBackground = readAssetPath(assets, "keyBackground"),
                    keySound = readAssetPath(assets, "keySound")
                )
            )
            listOfNotNull(
                manifest.assets.font,
                manifest.assets.imeBackground,
                manifest.assets.keyBackground,
                manifest.assets.keySound
            ).forEach { path ->
                if (zip.getEntry(path) == null) error("主题包资源不存在：$path")
            }
            return manifest
        }
    }

    private fun readAssetPath(root: JSONObject, key: String): String? {
        val value = root.optString(key).trim()
        if (value.isBlank()) return null
        if (value.startsWith("/") || value.startsWith("\\") || value.contains("..")) {
            error("theme-pack.json 的 assets.$key 路径非法")
        }
        return value
    }

    private fun extractZipEntry(
        zip: ZipFile,
        entryPath: String,
        outDir: File,
        filePrefix: String
    ): File {
        val entry = zip.getEntry(entryPath) ?: error("主题包资源不存在：$entryPath")
        if (entry.isDirectory) error("主题包资源不能是目录：$entryPath")

        outDir.mkdirs()
        val ext = File(entryPath).extension.takeIf { it.isNotBlank() }?.let { ".$it" } ?: ""
        val outFile = File(outDir, "${filePrefix}_${System.currentTimeMillis()}$ext")
        zip.getInputStream(entry).use { input ->
            outFile.outputStream().use { output -> input.copyTo(output) }
        }
        return outFile
    }

    private fun upsertPack(context: Context, record: ImportedThemePack) {
        val arr = JSONArray()
        getImportedPacks(context)
            .filter { it.packId != record.packId }
            .forEach { arr.put(recordToJson(it)) }
        arr.put(recordToJson(record))
        savePackJson(context, arr)
    }

    private fun recordToJson(record: ImportedThemePack): JSONObject {
        return JSONObject().apply {
            put("packId", record.packId)
            put("packName", record.packName)
            put("version", record.version)
            put("themeId", record.themeId)
            put("fontId", record.fontId ?: "")
            put("imeBackgroundId", record.imeBackgroundId ?: "")
            put("keyBackgroundId", record.keyBackgroundId ?: "")
            put("soundPath", record.soundPath ?: "")
            put("zipPath", record.zipPath)
            put("importedAt", record.importedAt)
        }
    }

    private fun loadPackJson(context: Context): JSONArray {
        val raw = UiPrefs.prefs(context).getString(KEY_IMPORTED_THEME_PACKS, "[]").orEmpty()
        return runCatching { JSONArray(raw) }.getOrDefault(JSONArray())
    }

    private fun savePackJson(context: Context, arr: JSONArray) {
        UiPrefs.prefs(context).edit().putString(KEY_IMPORTED_THEME_PACKS, arr.toString()).apply()
    }

    private fun safeFileTag(value: String): String {
        return value.lowercase()
            .map { if (it.isLetterOrDigit()) it else '_' }
            .joinToString("")
            .trim('_')
            .ifBlank { "pack" }
    }
}
