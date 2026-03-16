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
