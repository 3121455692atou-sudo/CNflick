package com.example.flickime

import android.app.Activity
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
import androidx.compose.material3.OutlinedTextField
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
import com.example.flickime.engine.JapaneseLexiconManager
import com.example.flickime.engine.LexiconManager
import com.example.flickime.engine.ShortcutEntry
import com.example.flickime.engine.ShortcutManager

class LexiconSettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { LexiconSettingsScreen() }
    }
}

private enum class LexiconSettingsPage { ROOT, CHINESE, JAPANESE }

@Composable
private fun LexiconSettingsScreen() {
    val context = LocalContext.current

    var page by remember { mutableStateOf(LexiconSettingsPage.ROOT) }

    var allLexicons by remember { mutableStateOf(LexiconManager.getAllLexicons(context)) }
    var enabledIds by remember { mutableStateOf(LexiconManager.getEnabledLexiconIds(context)) }

    var jpAllLexicons by remember { mutableStateOf(JapaneseLexiconManager.getAllLexicons(context)) }
    var jpEnabledIds by remember { mutableStateOf(JapaneseLexiconManager.getEnabledLexiconIds(context)) }

    var shortcuts by remember { mutableStateOf(ShortcutManager.getAll(context)) }
    var shortcutCode by remember { mutableStateOf("") }
    var shortcutText by remember { mutableStateOf("") }

    fun refreshChineseLexicons() {
        allLexicons = LexiconManager.getAllLexicons(context)
        enabledIds = LexiconManager.getEnabledLexiconIds(context)
    }

    fun refreshJapaneseLexicons() {
        jpAllLexicons = JapaneseLexiconManager.getAllLexicons(context)
        jpEnabledIds = JapaneseLexiconManager.getEnabledLexiconIds(context)
    }

    fun refreshShortcuts() {
        shortcuts = ShortcutManager.getAll(context)
    }

    val lexiconPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching { LexiconManager.importLexicon(context, uri) }
            .onSuccess {
                refreshChineseLexicons()
                Toast.makeText(context, "词库已导入并启用", Toast.LENGTH_SHORT).show()
            }
            .onFailure {
                Toast.makeText(context, "词库导入失败：${it.message}", Toast.LENGTH_SHORT).show()
            }
    }

    val japaneseLexiconPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching { JapaneseLexiconManager.importLexicon(context, uri) }
            .onSuccess {
                refreshJapaneseLexicons()
                Toast.makeText(context, "日语词库已导入并启用", Toast.LENGTH_SHORT).show()
            }
            .onFailure {
                Toast.makeText(context, "日语词库导入失败：${it.message}", Toast.LENGTH_SHORT).show()
            }
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
            LexiconSettingsPage.ROOT -> {
                Text("词库管理", fontSize = 22.sp)
                Text("中文（拼音/注音）与日语词库分开配置。")

                Button(onClick = { page = LexiconSettingsPage.CHINESE }, modifier = Modifier.fillMaxWidth()) {
                    Text("中文词库设置（拼音 + 注音共用）")
                }
                Button(onClick = { page = LexiconSettingsPage.JAPANESE }, modifier = Modifier.fillMaxWidth()) {
                    Text("日语词库设置（假名单独）")
                }
                OutlinedButton(onClick = { (context as? Activity)?.finish() }, modifier = Modifier.fillMaxWidth()) {
                    Text("返回")
                }
            }

            LexiconSettingsPage.CHINESE -> {
                Text("中文词库管理", fontSize = 22.sp)
                Text("拼音与注音共享中文词库。")

                Button(onClick = { lexiconPicker.launch(arrayOf("application/json", "text/*", "*/*")) }, modifier = Modifier.fillMaxWidth()) {
                    Text("导入词库（JSON/TXT/TSV）")
                }

                allLexicons.forEach { lexicon ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.White)
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        val label = if (lexicon.isBuiltIn) "${lexicon.name}（内置）" else lexicon.name
                        Text(label, modifier = Modifier.weight(1f))
                        Switch(
                            checked = enabledIds.contains(lexicon.id),
                            onCheckedChange = { checked ->
                                LexiconManager.setLexiconEnabled(context, lexicon.id, checked)
                                enabledIds = LexiconManager.getEnabledLexiconIds(context)
                            }
                        )
                    }
                }

                OutlinedButton(onClick = {
                    LexiconManager.resetToDefault(context)
                    enabledIds = LexiconManager.getEnabledLexiconIds(context)
                    Toast.makeText(context, "词库开关已恢复默认", Toast.LENGTH_SHORT).show()
                }, modifier = Modifier.fillMaxWidth()) { Text("恢复默认词库开关") }

                Text("快捷词库", fontSize = 22.sp)
                Text("示例：输入码 js -> 小学生。支持任意长度文本，可随时添加/删除。")

                OutlinedTextField(
                    value = shortcutCode,
                    onValueChange = { shortcutCode = it },
                    label = { Text("输入码（例如 js）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = shortcutText,
                    onValueChange = { shortcutText = it },
                    label = { Text("输出文本") },
                    modifier = Modifier.fillMaxWidth()
                )

                Button(onClick = {
                    val code = shortcutCode.trim()
                    val text = shortcutText.trim()
                    if (code.isBlank() || text.isBlank()) {
                        Toast.makeText(context, "请输入输入码和输出文本", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    ShortcutManager.upsert(context, code, text)
                    shortcutCode = ""
                    shortcutText = ""
                    refreshShortcuts()
                    Toast.makeText(context, "快捷词条已添加", Toast.LENGTH_SHORT).show()
                }, modifier = Modifier.fillMaxWidth()) {
                    Text("添加快捷词条")
                }

                shortcuts.forEach { entry: ShortcutEntry ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.White)
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("${entry.code} -> ${entry.text}", modifier = Modifier.weight(1f))
                        OutlinedButton(onClick = {
                            ShortcutManager.remove(context, entry)
                            refreshShortcuts()
                            Toast.makeText(context, "已删除快捷词条", Toast.LENGTH_SHORT).show()
                        }) { Text("删除") }
                    }
                }

                OutlinedButton(onClick = { page = LexiconSettingsPage.ROOT }, modifier = Modifier.fillMaxWidth()) { Text("返回") }
            }

            LexiconSettingsPage.JAPANESE -> {
                Text("日语词库管理", fontSize = 22.sp)
                Text("日语假名输入使用独立词库。")

                Button(
                    onClick = { japaneseLexiconPicker.launch(arrayOf("application/json", "text/*", "*/*")) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("导入日语词库（JSON/TXT/TSV）")
                }

                jpAllLexicons.forEach { lexicon ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.White)
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        val label = if (lexicon.isBuiltIn) "${lexicon.name}（内置）" else lexicon.name
                        Text(label, modifier = Modifier.weight(1f))
                        Switch(
                            checked = jpEnabledIds.contains(lexicon.id),
                            onCheckedChange = { checked ->
                                JapaneseLexiconManager.setLexiconEnabled(context, lexicon.id, checked)
                                jpEnabledIds = JapaneseLexiconManager.getEnabledLexiconIds(context)
                            }
                        )
                    }
                }

                OutlinedButton(onClick = {
                    JapaneseLexiconManager.resetToDefault(context)
                    jpEnabledIds = JapaneseLexiconManager.getEnabledLexiconIds(context)
                    Toast.makeText(context, "日语词库开关已恢复默认", Toast.LENGTH_SHORT).show()
                }, modifier = Modifier.fillMaxWidth()) { Text("恢复默认日语词库开关") }

                OutlinedButton(onClick = { page = LexiconSettingsPage.ROOT }, modifier = Modifier.fillMaxWidth()) { Text("返回") }
            }
        }
    }
}
