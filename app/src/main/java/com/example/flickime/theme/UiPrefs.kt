package com.example.flickime.theme

import android.content.Context
import android.graphics.Color
import com.example.flickime.model.InputLanguage

object UiPrefs {
    private const val PREFS = "flick_settings"

    const val KEY_CENTER_TEXT_SP = "center_text_sp"
    const val KEY_SIDE_TEXT_SP = "side_text_sp"
    const val KEY_KEY_TEXT_ALPHA = "key_text_alpha"
    const val KEY_KEY_IMAGE_ALPHA = "key_image_alpha"
    const val KEY_KEY_BG_ALPHA = "key_bg_alpha"
    const val KEY_KEY_SIZE_SCALE = "key_size_scale"
    const val KEY_KEY_GAP_DP = "key_gap_dp"
    const val KEY_KEY_BG_IMAGE_PATH = "key_bg_image_path"
    const val KEY_IME_BG_IMAGE_PATH = "ime_bg_image_path"
    const val KEY_USE_CUSTOM_SOUND = "use_custom_sound"
    const val KEY_CUSTOM_SOUND_PATH = "custom_sound_path"
    const val KEY_SHOW_FLICK_HINT_OVERLAY = "show_flick_hint_overlay"
    const val KEY_ENABLE_EIGHT_DIRECTION_FLICK = "enable_eight_direction_flick"
    const val KEY_ENABLE_EIGHT_DIRECTION_PINYIN = "enable_eight_direction_pinyin"
    const val KEY_ENABLE_EIGHT_DIRECTION_SYMBOL = "enable_eight_direction_symbol"
    const val KEY_SHOW_CENTER_KEY_TEXT = "show_center_key_text"
    const val KEY_SHOW_SIDE_KEY_TEXT = "show_side_key_text"
    const val KEY_GLOBE_KEY_MODE = "globe_key_mode"
    const val KEY_CURRENT_INPUT_LANGUAGE = "current_input_language"
    const val KEY_ENABLED_INPUT_LANGUAGES = "enabled_input_languages"
    const val KEY_SHAPE_LANGUAGE_MIGRATED = "shape_language_migrated"
    const val KEY_GLOBE_LANGUAGE_SWITCH_ENABLED = "globe_language_switch_enabled"
    const val KEY_ACTION_KEY_ORDER = "action_key_order"
    const val KEY_FONT_COLOR_HEX = "font_color_hex"

    const val GLOBE_KEY_MODE_NORMAL = "normal"
    const val GLOBE_KEY_MODE_HIDDEN = "hidden"
    const val GLOBE_KEY_MODE_DISABLED = "disabled"

    const val LANG_PINYIN = "pinyin"
    const val LANG_ZHUYIN = "zhuyin"
    const val LANG_JAPANESE = "japanese"
    const val LANG_SHAPE = "shape"

    const val ACTION_KEY_BACKSPACE = "backspace"
    const val ACTION_KEY_SPACE = "space"
    const val ACTION_KEY_VOICE = "voice"
    const val ACTION_KEY_ENTER = "enter"
    const val ACTION_KEY_FUNC = "func"

    const val MIKU_BG_ASSET = "asset://backgrounds/default_miku.jpg"
    private const val DEFAULT_CENTER_TEXT_SP = 18f
    private const val DEFAULT_SIDE_TEXT_SP = 10f
    private const val DEFAULT_KEY_TEXT_ALPHA = 1f
    private const val DEFAULT_KEY_IMAGE_ALPHA = 0.9f
    private const val DEFAULT_KEY_BG_ALPHA = 0.85f
    private const val DEFAULT_KEY_SIZE_SCALE = 1f
    private const val DEFAULT_KEY_GAP_DP = 4f
    private val DEFAULT_ACTION_KEY_ORDER = listOf(
        ACTION_KEY_BACKSPACE,
        ACTION_KEY_VOICE,
        ACTION_KEY_FUNC,
        ACTION_KEY_ENTER
    )
    fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun getCenterTextSp(context: Context): Float = prefs(context).getFloat(KEY_CENTER_TEXT_SP, DEFAULT_CENTER_TEXT_SP)
    fun getSideTextSp(context: Context): Float = prefs(context).getFloat(KEY_SIDE_TEXT_SP, DEFAULT_SIDE_TEXT_SP)
    fun getKeyTextAlpha(context: Context): Float = prefs(context).getFloat(KEY_KEY_TEXT_ALPHA, DEFAULT_KEY_TEXT_ALPHA)
    fun getKeyImageAlpha(context: Context): Float = prefs(context).getFloat(KEY_KEY_IMAGE_ALPHA, DEFAULT_KEY_IMAGE_ALPHA)
    fun getKeyBgAlpha(context: Context): Float = prefs(context).getFloat(KEY_KEY_BG_ALPHA, DEFAULT_KEY_BG_ALPHA)
    fun getKeySizeScale(context: Context): Float = prefs(context).getFloat(KEY_KEY_SIZE_SCALE, DEFAULT_KEY_SIZE_SCALE)
    fun getKeyGapDp(context: Context): Float = prefs(context).getFloat(KEY_KEY_GAP_DP, DEFAULT_KEY_GAP_DP)
    fun getKeyBgImagePath(context: Context): String = prefs(context).getString(KEY_KEY_BG_IMAGE_PATH, "").orEmpty()
    fun getImeBgImagePath(context: Context): String = prefs(context).getString(KEY_IME_BG_IMAGE_PATH, "").orEmpty()
    fun getUseCustomSound(context: Context): Boolean = prefs(context).getBoolean(KEY_USE_CUSTOM_SOUND, false)
    fun getCustomSoundPath(context: Context): String = prefs(context).getString(KEY_CUSTOM_SOUND_PATH, "").orEmpty()
    fun getShowFlickHintOverlay(context: Context): Boolean = prefs(context).getBoolean(KEY_SHOW_FLICK_HINT_OVERLAY, true)
    fun getEnableEightDirectionPinyin(context: Context): Boolean {
        val p = prefs(context)
        return if (p.contains(KEY_ENABLE_EIGHT_DIRECTION_PINYIN)) {
            p.getBoolean(KEY_ENABLE_EIGHT_DIRECTION_PINYIN, false)
        } else {
            p.getBoolean(KEY_ENABLE_EIGHT_DIRECTION_FLICK, false)
        }
    }

    fun getEnableEightDirectionSymbol(context: Context): Boolean {
        val p = prefs(context)
        return if (p.contains(KEY_ENABLE_EIGHT_DIRECTION_SYMBOL)) {
            p.getBoolean(KEY_ENABLE_EIGHT_DIRECTION_SYMBOL, true)
        } else {
            true
        }
    }

    fun getShowCenterKeyText(context: Context): Boolean = prefs(context).getBoolean(KEY_SHOW_CENTER_KEY_TEXT, true)
    fun getShowSideKeyText(context: Context): Boolean = prefs(context).getBoolean(KEY_SHOW_SIDE_KEY_TEXT, true)
    fun getFontColorHex(context: Context): String = prefs(context).getString(KEY_FONT_COLOR_HEX, "").orEmpty()
    fun setFontColorHex(context: Context, hex: String) {
        prefs(context).edit().putString(KEY_FONT_COLOR_HEX, normalizeColorHex(hex)).apply()
    }

    fun clearFontColorHex(context: Context) {
        prefs(context).edit().putString(KEY_FONT_COLOR_HEX, "").apply()
    }

    fun resolveFontColor(context: Context): Int? {
        val raw = getFontColorHex(context)
        if (raw.isBlank()) return null
        return try {
            Color.parseColor(raw)
        } catch (_: Throwable) {
            null
        }
    }
    fun getActionKeyOrder(context: Context): List<String> {
        val raw = prefs(context).getString(KEY_ACTION_KEY_ORDER, "").orEmpty()
        val parsed = raw.split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        return normalizeActionKeyOrder(parsed)
    }

    fun setActionKeyOrder(context: Context, order: List<String>) {
        val normalized = normalizeActionKeyOrder(order)
        prefs(context).edit()
            .putString(KEY_ACTION_KEY_ORDER, normalized.joinToString(","))
            .apply()
    }

    fun defaultActionKeyOrder(): List<String> = DEFAULT_ACTION_KEY_ORDER.toList()

    fun getGlobeLanguageSwitchEnabled(context: Context): Boolean {
        return prefs(context).getBoolean(KEY_GLOBE_LANGUAGE_SWITCH_ENABLED, true)
    }

    fun setGlobeLanguageSwitchEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_GLOBE_LANGUAGE_SWITCH_ENABLED, enabled).apply()
    }

    fun getCurrentInputLanguage(context: Context): InputLanguage {
        val raw = prefs(context).getString(KEY_CURRENT_INPUT_LANGUAGE, LANG_PINYIN).orEmpty()
        return InputLanguage.fromId(raw)
    }

    fun setCurrentInputLanguage(context: Context, language: InputLanguage) {
        prefs(context).edit().putString(KEY_CURRENT_INPUT_LANGUAGE, language.id).apply()
    }

    fun getEnabledInputLanguages(context: Context): Set<InputLanguage> {
        val defaults = setOf(LANG_PINYIN, LANG_ZHUYIN, LANG_JAPANESE, LANG_SHAPE)
        val p = prefs(context)
        val rawStored = p.getStringSet(KEY_ENABLED_INPUT_LANGUAGES, null)
        val raw = rawStored ?: defaults
        val mapped = raw.map { InputLanguage.fromId(it) }.toMutableSet()
        if (mapped.isEmpty()) mapped += InputLanguage.PINYIN
        if (rawStored != null && !p.getBoolean(KEY_SHAPE_LANGUAGE_MIGRATED, false)) {
            mapped += InputLanguage.SHAPE
            p.edit()
                .putStringSet(KEY_ENABLED_INPUT_LANGUAGES, mapped.map { it.id }.toSet())
                .putBoolean(KEY_SHAPE_LANGUAGE_MIGRATED, true)
                .apply()
        }
        return mapped
    }

    fun setEnabledInputLanguages(context: Context, languages: Set<InputLanguage>) {
        val safe = if (languages.isEmpty()) setOf(InputLanguage.PINYIN) else languages
        prefs(context).edit()
            .putStringSet(KEY_ENABLED_INPUT_LANGUAGES, safe.map { it.id }.toSet())
            .apply()
    }

    fun getGlobeKeyMode(context: Context): String {
        val raw = prefs(context).getString(KEY_GLOBE_KEY_MODE, GLOBE_KEY_MODE_NORMAL).orEmpty()
        return when (raw) {
            GLOBE_KEY_MODE_HIDDEN, GLOBE_KEY_MODE_DISABLED -> raw
            else -> GLOBE_KEY_MODE_NORMAL
        }
    }

    fun setGlobeKeyMode(context: Context, mode: String) {
        val normalized = when (mode) {
            GLOBE_KEY_MODE_HIDDEN, GLOBE_KEY_MODE_DISABLED -> mode
            else -> GLOBE_KEY_MODE_NORMAL
        }
        prefs(context).edit().putString(KEY_GLOBE_KEY_MODE, normalized).apply()
    }

    fun setShowFlickHintOverlay(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_SHOW_FLICK_HINT_OVERLAY, enabled).apply()
    }

    fun setEnableEightDirectionPinyin(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_ENABLE_EIGHT_DIRECTION_PINYIN, enabled).apply()
    }

    fun setEnableEightDirectionSymbol(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_ENABLE_EIGHT_DIRECTION_SYMBOL, enabled).apply()
    }

    fun setShowCenterKeyText(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_SHOW_CENTER_KEY_TEXT, enabled).apply()
    }

    fun setShowSideKeyText(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_SHOW_SIDE_KEY_TEXT, enabled).apply()
    }

    fun resetAppearance(context: Context) {
        prefs(context).edit()
            .putFloat(KEY_CENTER_TEXT_SP, DEFAULT_CENTER_TEXT_SP)
            .putFloat(KEY_SIDE_TEXT_SP, DEFAULT_SIDE_TEXT_SP)
            .putFloat(KEY_KEY_TEXT_ALPHA, DEFAULT_KEY_TEXT_ALPHA)
            .putFloat(KEY_KEY_IMAGE_ALPHA, DEFAULT_KEY_IMAGE_ALPHA)
            .putFloat(KEY_KEY_BG_ALPHA, DEFAULT_KEY_BG_ALPHA)
            .putFloat(KEY_KEY_SIZE_SCALE, DEFAULT_KEY_SIZE_SCALE)
            .putFloat(KEY_KEY_GAP_DP, DEFAULT_KEY_GAP_DP)
            .putString(KEY_KEY_BG_IMAGE_PATH, "")
            .putString(KEY_IME_BG_IMAGE_PATH, "")
            .putBoolean(KEY_SHOW_FLICK_HINT_OVERLAY, true)
            .putBoolean(KEY_ENABLE_EIGHT_DIRECTION_FLICK, false)
            .putBoolean(KEY_ENABLE_EIGHT_DIRECTION_PINYIN, false)
            .putBoolean(KEY_ENABLE_EIGHT_DIRECTION_SYMBOL, true)
            .putBoolean(KEY_SHOW_CENTER_KEY_TEXT, true)
            .putBoolean(KEY_SHOW_SIDE_KEY_TEXT, true)
            .putBoolean(KEY_GLOBE_LANGUAGE_SWITCH_ENABLED, true)
            .putString(KEY_CURRENT_INPUT_LANGUAGE, LANG_PINYIN)
            .putStringSet(KEY_ENABLED_INPUT_LANGUAGES, setOf(LANG_PINYIN, LANG_ZHUYIN, LANG_JAPANESE, LANG_SHAPE))
            .putBoolean(KEY_SHAPE_LANGUAGE_MIGRATED, true)
            .putString(KEY_ACTION_KEY_ORDER, DEFAULT_ACTION_KEY_ORDER.joinToString(","))
            .putString(KEY_FONT_COLOR_HEX, "")
            .apply()
    }

    fun resetSound(context: Context) {
        prefs(context).edit()
            .putBoolean("sound_enabled", true)
            .putBoolean("vibration_enabled", false)
            .putBoolean(KEY_USE_CUSTOM_SOUND, false)
            .putString(KEY_CUSTOM_SOUND_PATH, "")
            .apply()
    }

    private fun normalizeActionKeyOrder(raw: List<String>): List<String> {
        val allowed = setOf(ACTION_KEY_BACKSPACE, ACTION_KEY_VOICE, ACTION_KEY_ENTER, ACTION_KEY_FUNC)
        val distinct = raw.map { if (it == ACTION_KEY_SPACE) ACTION_KEY_VOICE else it }
            .filter { it in allowed }
            .distinct()
            .toMutableList()
        if (distinct.size == DEFAULT_ACTION_KEY_ORDER.size) return distinct
        DEFAULT_ACTION_KEY_ORDER.forEach { key ->
            if (key !in distinct) distinct += key
        }
        return distinct.take(DEFAULT_ACTION_KEY_ORDER.size)
    }

    private fun normalizeColorHex(raw: String): String {
        val v = raw.trim().uppercase()
        if (v.isBlank()) return ""
        val withPrefix = if (v.startsWith("#")) v else "#$v"
        val body = withPrefix.removePrefix("#")
        return if (body.length == 6 || body.length == 8) withPrefix else ""
    }
}
