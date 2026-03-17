package com.example.flickime.theme

import android.content.Context

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

    const val GLOBE_KEY_MODE_NORMAL = "normal"
    const val GLOBE_KEY_MODE_HIDDEN = "hidden"
    const val GLOBE_KEY_MODE_DISABLED = "disabled"

    const val MIKU_BG_ASSET = "asset://backgrounds/default_miku.jpg"
    private const val DEFAULT_CENTER_TEXT_SP = 18f
    private const val DEFAULT_SIDE_TEXT_SP = 10f
    private const val DEFAULT_KEY_TEXT_ALPHA = 1f
    private const val DEFAULT_KEY_IMAGE_ALPHA = 0.9f
    private const val DEFAULT_KEY_BG_ALPHA = 0.85f
    private const val DEFAULT_KEY_SIZE_SCALE = 1f
    private const val DEFAULT_KEY_GAP_DP = 4f
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
}
