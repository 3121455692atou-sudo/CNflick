package com.example.flickime

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.flickime.theme.BackgroundImageManager
import com.example.flickime.theme.FontManager
import com.example.flickime.theme.ThemeManager
import com.example.flickime.theme.UiPrefs
import java.io.File

class ImeSettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { ImeSettingsScreen() }
    }
}

private enum class SettingsPage { ROOT, THEME, FONT, APPEARANCE, SOUND }

@Composable
private fun ImeSettingsScreen() {
    val context = LocalContext.current
    val prefs = UiPrefs.prefs(context)

    var page by remember { mutableStateOf(SettingsPage.ROOT) }
    var soundEnabled by remember { mutableStateOf(prefs.getBoolean("sound_enabled", true)) }
    var vibrationEnabled by remember { mutableStateOf(prefs.getBoolean("vibration_enabled", false)) }

    var allThemes by remember { mutableStateOf(ThemeManager.getAllThemes(context)) }
    var currentThemeId by remember { mutableStateOf(ThemeManager.getCurrentTheme(context).id) }

    var allFonts by remember { mutableStateOf(FontManager.getAllFonts(context)) }
    var currentFontId by remember { mutableStateOf(FontManager.getCurrentFontId(context)) }

    var imeBgOptions by remember { mutableStateOf(BackgroundImageManager.getImeOptions(context)) }
    var keyBgOptions by remember { mutableStateOf(BackgroundImageManager.getKeyOptions(context)) }
    var selectedImeBgId by remember { mutableStateOf(BackgroundImageManager.getSelectedImeId(context)) }
    var selectedKeyBgId by remember { mutableStateOf(BackgroundImageManager.getSelectedKeyId(context)) }

    var centerSp by remember { mutableStateOf(UiPrefs.getCenterTextSp(context)) }
    var sideSp by remember { mutableStateOf(UiPrefs.getSideTextSp(context)) }
    var keyTextAlpha by remember { mutableStateOf(UiPrefs.getKeyTextAlpha(context)) }
    var keyImageAlpha by remember { mutableStateOf(UiPrefs.getKeyImageAlpha(context)) }
    var keyBgAlpha by remember { mutableStateOf(UiPrefs.getKeyBgAlpha(context)) }
    var keySizeScale by remember { mutableStateOf(UiPrefs.getKeySizeScale(context)) }
    var keyGapDp by remember { mutableStateOf(UiPrefs.getKeyGapDp(context)) }
    var useCustomSound by remember { mutableStateOf(UiPrefs.getUseCustomSound(context)) }

    fun refreshBgOptions() {
        imeBgOptions = BackgroundImageManager.getImeOptions(context)
        keyBgOptions = BackgroundImageManager.getKeyOptions(context)
        selectedImeBgId = BackgroundImageManager.getSelectedImeId(context)
        selectedKeyBgId = BackgroundImageManager.getSelectedKeyId(context)
    }

    fun persistUri(uri: Uri) {
        try {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (_: Throwable) {
        }
    }

    val themePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching { ThemeManager.importThemeZip(context, uri) }
            .onSuccess {
                allThemes = ThemeManager.getAllThemes(context)
                currentThemeId = it.id
                Toast.makeText(context, "主题已导入并切换：${it.name}", Toast.LENGTH_SHORT).show()
            }
            .onFailure { Toast.makeText(context, "主题导入失败：${it.message}", Toast.LENGTH_SHORT).show() }
    }

    val fontPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching { FontManager.importFont(context, uri) }
            .onSuccess {
                allFonts = FontManager.getAllFonts(context)
                currentFontId = it.id
                Toast.makeText(context, "字体已导入并切换", Toast.LENGTH_SHORT).show()
            }
            .onFailure { Toast.makeText(context, "字体导入失败：${it.message}", Toast.LENGTH_SHORT).show() }
    }

    val imeBgPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        persistUri(uri)
        runCatching { BackgroundImageManager.importImeBackground(context, uri) }
            .onSuccess {
                refreshBgOptions()
                Toast.makeText(context, "已自动裁切并加入背景列表（推荐比例 ${BackgroundImageManager.IME_RECOMMENDED_RATIO}）", Toast.LENGTH_SHORT).show()
            }
            .onFailure { Toast.makeText(context, "背景导入失败：${it.message}", Toast.LENGTH_SHORT).show() }
    }

    val keyBgPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        persistUri(uri)
        runCatching { BackgroundImageManager.importKeyBackground(context, uri) }
            .onSuccess {
                refreshBgOptions()
                Toast.makeText(context, "已自动裁切并加入按键图列表（推荐比例 ${BackgroundImageManager.KEY_RECOMMENDED_RATIO}）", Toast.LENGTH_SHORT).show()
            }
            .onFailure { Toast.makeText(context, "按键图导入失败：${it.message}", Toast.LENGTH_SHORT).show() }
    }

    val soundPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        persistUri(uri)
        val dst = File(context.filesDir, "custom/key_sound_${System.currentTimeMillis()}.audio").apply { parentFile?.mkdirs() }
        context.contentResolver.openInputStream(uri)?.use { input -> dst.outputStream().use { output -> input.copyTo(output) } }
        prefs.edit().putString(UiPrefs.KEY_CUSTOM_SOUND_PATH, dst.absolutePath).apply()
        useCustomSound = true
        prefs.edit().putBoolean(UiPrefs.KEY_USE_CUSTOM_SOUND, true).apply()
        Toast.makeText(context, "按键音效已导入", Toast.LENGTH_SHORT).show()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF1F5F9))
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        when (page) {
            SettingsPage.ROOT -> {
                Text(text = "CNflick 设置", fontSize = 24.sp)
                Text(text = "高级自定义中心")

                Button(onClick = { page = SettingsPage.THEME }, modifier = Modifier.fillMaxWidth()) { Text("主题设置") }
                Button(onClick = { page = SettingsPage.FONT }, modifier = Modifier.fillMaxWidth()) { Text("字体设置") }
                Button(onClick = { page = SettingsPage.APPEARANCE }, modifier = Modifier.fillMaxWidth()) { Text("外观设置") }
                Button(onClick = { page = SettingsPage.SOUND }, modifier = Modifier.fillMaxWidth()) { Text("按键音效设置") }

                OutlinedButton(
                    onClick = {
                        context.startActivity(
                            Intent(context, LexiconSettingsActivity::class.java)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("词库管理（导入 / 多词库启用）") }

                OutlinedButton(
                    onClick = {
                        context.startActivity(
                            Intent(context, KeyMappingActivity::class.java)
                                .putExtra("map_type", "pinyin")
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("自定义拼音映射") }

                OutlinedButton(
                    onClick = {
                        context.startActivity(
                            Intent(context, KeyMappingActivity::class.java)
                                .putExtra("map_type", "symbol")
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("自定义符号映射") }
            }

            SettingsPage.THEME -> {
                Text("主题设置", fontSize = 22.sp)
                Button(onClick = { themePicker.launch(arrayOf("application/zip", "*/*")) }, modifier = Modifier.fillMaxWidth()) { Text("导入主题包") }
                allThemes.forEach { theme ->
                    val label = if (theme.id == currentThemeId) "✓ ${theme.name}" else theme.name
                    OutlinedButton(onClick = {
                        ThemeManager.setCurrentTheme(context, theme.id)
                        currentThemeId = theme.id
                    }, modifier = Modifier.fillMaxWidth()) { Text(label) }
                }
                OutlinedButton(onClick = {
                    ThemeManager.setCurrentTheme(context, "cnflick.theme.default_light")
                    currentThemeId = ThemeManager.getCurrentTheme(context).id
                    Toast.makeText(context, "主题已恢复默认", Toast.LENGTH_SHORT).show()
                }, modifier = Modifier.fillMaxWidth()) { Text("恢复默认主题") }
                OutlinedButton(onClick = { page = SettingsPage.ROOT }, modifier = Modifier.fillMaxWidth()) { Text("返回") }
            }

            SettingsPage.FONT -> {
                Text("字体设置", fontSize = 22.sp)
                Button(onClick = { fontPicker.launch(arrayOf("font/ttf", "font/otf", "application/octet-stream", "*/*")) }, modifier = Modifier.fillMaxWidth()) { Text("导入字体") }
                allFonts.forEach { font ->
                    val label = if (font.id == currentFontId) "✓ ${font.name}" else font.name
                    OutlinedButton(onClick = {
                        FontManager.setCurrentFontId(context, font.id)
                        currentFontId = font.id
                    }, modifier = Modifier.fillMaxWidth()) { Text(label) }
                }
                OutlinedButton(onClick = {
                    FontManager.setCurrentFontId(context, "font.system")
                    currentFontId = FontManager.getCurrentFontId(context)
                    Toast.makeText(context, "字体已恢复默认", Toast.LENGTH_SHORT).show()
                }, modifier = Modifier.fillMaxWidth()) { Text("恢复默认字体") }
                OutlinedButton(onClick = { page = SettingsPage.ROOT }, modifier = Modifier.fillMaxWidth()) { Text("返回") }
            }

            SettingsPage.APPEARANCE -> {
                Text("外观设置", fontSize = 22.sp)
                Text("推荐输入法背景比例：${BackgroundImageManager.IME_RECOMMENDED_RATIO}（导入后自动裁切）")
                Text("推荐按键图片比例：${BackgroundImageManager.KEY_RECOMMENDED_RATIO}（导入后自动裁切）")
                Button(onClick = { imeBgPicker.launch(arrayOf("image/*")) }, modifier = Modifier.fillMaxWidth()) { Text("导入输入法背景图（自动裁切并加入可选）") }
                Button(onClick = { keyBgPicker.launch(arrayOf("image/*")) }, modifier = Modifier.fillMaxWidth()) { Text("导入按键图片（自动裁切并加入可选）") }

                Text("输入法背景选择")
                imeBgOptions.forEach { option ->
                    val label = if (option.id == selectedImeBgId) "✓ ${option.name}" else option.name
                    OutlinedButton(onClick = {
                        BackgroundImageManager.selectImeBackground(context, option.id)
                        selectedImeBgId = BackgroundImageManager.getSelectedImeId(context)
                    }, modifier = Modifier.fillMaxWidth()) { Text(label) }
                }

                Text("按键图片选择")
                keyBgOptions.forEach { option ->
                    val label = if (option.id == selectedKeyBgId) "✓ ${option.name}" else option.name
                    OutlinedButton(onClick = {
                        BackgroundImageManager.selectKeyBackground(context, option.id)
                        selectedKeyBgId = BackgroundImageManager.getSelectedKeyId(context)
                    }, modifier = Modifier.fillMaxWidth()) { Text(label) }
                }

                Text("中间字体大小: ${"%.1f".format(centerSp)}sp")
                Slider(value = centerSp, onValueChange = {
                    centerSp = it
                    prefs.edit().putFloat(UiPrefs.KEY_CENTER_TEXT_SP, it).apply()
                }, valueRange = 12f..32f)

                Text("四周字体大小: ${"%.1f".format(sideSp)}sp")
                Slider(value = sideSp, onValueChange = {
                    sideSp = it
                    prefs.edit().putFloat(UiPrefs.KEY_SIDE_TEXT_SP, it).apply()
                }, valueRange = 6f..24f)

                Text("按键文字透明度: ${"%.2f".format(keyTextAlpha)}")
                Slider(value = keyTextAlpha, onValueChange = {
                    keyTextAlpha = it
                    prefs.edit().putFloat(UiPrefs.KEY_KEY_TEXT_ALPHA, it).apply()
                }, valueRange = 0f..1f)

                Text("按键图片透明度: ${"%.2f".format(keyImageAlpha)}")
                Slider(value = keyImageAlpha, onValueChange = {
                    keyImageAlpha = it
                    prefs.edit().putFloat(UiPrefs.KEY_KEY_IMAGE_ALPHA, it).apply()
                }, valueRange = 0f..1f)

                Text("按键底色透明度: ${"%.2f".format(keyBgAlpha)}")
                Slider(value = keyBgAlpha, onValueChange = {
                    keyBgAlpha = it
                    prefs.edit().putFloat(UiPrefs.KEY_KEY_BG_ALPHA, it).apply()
                }, valueRange = 0f..1f)

                Text("按键整体大小: ${"%.2f".format(keySizeScale)}x")
                Slider(value = keySizeScale, onValueChange = {
                    keySizeScale = it
                    prefs.edit().putFloat(UiPrefs.KEY_KEY_SIZE_SCALE, it).apply()
                }, valueRange = 0.75f..1.25f)

                Text("按键间距: ${"%.1f".format(keyGapDp)}dp")
                Slider(value = keyGapDp, onValueChange = {
                    keyGapDp = it
                    prefs.edit().putFloat(UiPrefs.KEY_KEY_GAP_DP, it).apply()
                }, valueRange = 0f..14f)

                OutlinedButton(onClick = {
                    UiPrefs.resetAppearance(context)
                    BackgroundImageManager.resetToDefaults(context)
                    refreshBgOptions()
                    centerSp = UiPrefs.getCenterTextSp(context)
                    sideSp = UiPrefs.getSideTextSp(context)
                    keyTextAlpha = UiPrefs.getKeyTextAlpha(context)
                    keyImageAlpha = UiPrefs.getKeyImageAlpha(context)
                    keyBgAlpha = UiPrefs.getKeyBgAlpha(context)
                    keySizeScale = UiPrefs.getKeySizeScale(context)
                    keyGapDp = UiPrefs.getKeyGapDp(context)
                    Toast.makeText(context, "外观设置已恢复默认", Toast.LENGTH_SHORT).show()
                }, modifier = Modifier.fillMaxWidth()) { Text("恢复默认外观") }

                OutlinedButton(onClick = { page = SettingsPage.ROOT }, modifier = Modifier.fillMaxWidth()) { Text("返回") }
            }

            SettingsPage.SOUND -> {
                Text("按键音效", fontSize = 22.sp)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("系统按键音")
                    Switch(checked = soundEnabled, onCheckedChange = {
                        soundEnabled = it
                        prefs.edit().putBoolean("sound_enabled", it).apply()
                    })
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("按键震动")
                    Switch(checked = vibrationEnabled, onCheckedChange = {
                        vibrationEnabled = it
                        prefs.edit().putBoolean("vibration_enabled", it).apply()
                    })
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("自定义音效")
                    Switch(checked = useCustomSound, onCheckedChange = {
                        useCustomSound = it
                        prefs.edit().putBoolean(UiPrefs.KEY_USE_CUSTOM_SOUND, it).apply()
                    })
                }
                Button(onClick = { soundPicker.launch(arrayOf("audio/*", "*/*")) }, modifier = Modifier.fillMaxWidth()) { Text("导入按键音效") }
                OutlinedButton(onClick = {
                    UiPrefs.resetSound(context)
                    soundEnabled = prefs.getBoolean("sound_enabled", true)
                    vibrationEnabled = prefs.getBoolean("vibration_enabled", false)
                    useCustomSound = UiPrefs.getUseCustomSound(context)
                    Toast.makeText(context, "音效设置已恢复默认", Toast.LENGTH_SHORT).show()
                }, modifier = Modifier.fillMaxWidth()) { Text("恢复默认音效") }
                OutlinedButton(onClick = { page = SettingsPage.ROOT }, modifier = Modifier.fillMaxWidth()) { Text("返回") }
            }
        }
    }
}
