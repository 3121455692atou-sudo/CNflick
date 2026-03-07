package com.example.flickime

import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class ImeSettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { ImeSettingsScreen() }
    }
}

@androidx.compose.runtime.Composable
private fun ImeSettingsScreen() {
    val context = LocalContext.current
    val prefs = context.getSharedPreferences("flick_settings", android.content.Context.MODE_PRIVATE)
    var soundEnabled by remember { mutableStateOf(prefs.getBoolean("sound_enabled", true)) }
    var vibrationEnabled by remember { mutableStateOf(prefs.getBoolean("vibration_enabled", false)) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Throwable) {
            }
            context.getSharedPreferences("flick_ime", android.content.Context.MODE_PRIVATE)
                .edit()
                .putString("last_import_uri", uri.toString())
                .apply()
            Toast.makeText(context, "已导入: $uri", Toast.LENGTH_SHORT).show()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF1F5F9))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(text = "CNflick 设置", fontSize = 24.sp)
        Text(text = "导入中心：可导入词库、主题、配置文件、扩展资源。")

        androidx.compose.foundation.layout.Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("按键音")
            Switch(
                checked = soundEnabled,
                onCheckedChange = {
                    soundEnabled = it
                    prefs.edit().putBoolean("sound_enabled", it).apply()
                }
            )
        }
        androidx.compose.foundation.layout.Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("按键震动")
            Switch(
                checked = vibrationEnabled,
                onCheckedChange = {
                    vibrationEnabled = it
                    prefs.edit().putBoolean("vibration_enabled", it).apply()
                }
            )
        }

        Button(
            onClick = { picker.launch(arrayOf("*/*")) },
            modifier = Modifier.fillMaxWidth()
        ) { Text("导入自定义词库") }

        Button(
            onClick = { picker.launch(arrayOf("*/*")) },
            modifier = Modifier.fillMaxWidth()
        ) { Text("导入主题包") }

        Button(
            onClick = { picker.launch(arrayOf("*/*")) },
            modifier = Modifier.fillMaxWidth()
        ) { Text("导入开源输入法配置") }

        Button(
            onClick = { picker.launch(arrayOf("*/*")) },
            modifier = Modifier.fillMaxWidth()
        ) { Text("导入插件/扩展") }

        OutlinedButton(
            onClick = {
                context.startActivity(
                    android.content.Intent(context, KeyMappingActivity::class.java)
                        .putExtra("map_type", "pinyin")
                        .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("自定义拼音映射") }

        OutlinedButton(
            onClick = {
                context.startActivity(
                    android.content.Intent(context, KeyMappingActivity::class.java)
                        .putExtra("map_type", "symbol")
                        .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("自定义符号映射") }

        OutlinedButton(
            onClick = {
                val i = android.content.Intent(
                    android.content.Intent.ACTION_VIEW,
                    android.net.Uri.parse("https://github.com/rime")
                ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(i)
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("下载/查看开源输入法开发文档") }

        OutlinedButton(
            onClick = {
                val i = android.content.Intent(
                    android.content.Intent.ACTION_VIEW,
                    android.net.Uri.parse("https://github.com/fkxxyz/rime-cloverpinyin")
                ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(i)
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("下载四叶草方案文档与配置") }
    }
}
