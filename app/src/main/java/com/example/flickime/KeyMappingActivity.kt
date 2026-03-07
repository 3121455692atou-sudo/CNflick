package com.example.flickime

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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.flickime.data.DefaultSymbolMap
import com.example.flickime.data.KeyMapStore
import com.example.flickime.model.DirectionalKeySpec
import com.example.flickime.model.FlickKeySpec
import org.json.JSONArray
import org.json.JSONObject

class KeyMappingActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { KeyMappingScreen() }
    }
}

private enum class MappingType { PINYIN, SYMBOL }

private data class KeyEdit(
    var center: String,
    var left: String,
    var up: String,
    var right: String,
    var down: String,
    val zoneName: String
)

@Composable
private fun KeyMappingScreen() {
    val context = LocalContext.current
    val mappingType = remember {
        when ((context as? KeyMappingActivity)?.intent?.getStringExtra("map_type")) {
            "symbol" -> MappingType.SYMBOL
            else -> MappingType.PINYIN
        }
    }
    val initial = remember {
        when (mappingType) {
            MappingType.PINYIN -> KeyMapStore.loadPinyinKeys(context).map {
                KeyEdit(it.center, it.left, it.up, it.right, it.down, it.zone.name)
            }
            MappingType.SYMBOL -> KeyMapStore.loadSymbolKeys(context).map {
                KeyEdit(it.center, it.left, it.up, it.right, it.down, "Symbol")
            }
        }
    }
    val edits = remember {
        mutableStateListOf<KeyEdit>().apply {
            addAll(initial)
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        try {
            val raw = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }.orEmpty()
            val arr = JSONArray(raw)
            if (arr.length() < 12) throw IllegalArgumentException("映射数量不足")
            edits.clear()
            for (i in 0 until 12) {
                val o = arr.getJSONObject(i)
                val zone = if (mappingType == MappingType.PINYIN) {
                    if (i < 5) "Shengmu" else "Yunmu"
                } else {
                    "Symbol"
                }
                edits.add(
                    KeyEdit(
                        center = o.optString("center", ""),
                        left = o.optString("left", ""),
                        up = o.optString("up", ""),
                        right = o.optString("right", ""),
                        down = o.optString("down", ""),
                        zoneName = zone
                    )
                )
            }
            Toast.makeText(context, "已导入映射 JSON", Toast.LENGTH_SHORT).show()
        } catch (e: Throwable) {
            Toast.makeText(context, "导入失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        try {
            val arr = JSONArray()
            edits.forEach {
                arr.put(
                    JSONObject().apply {
                        put("center", it.center)
                        put("left", it.left)
                        put("up", it.up)
                        put("right", it.right)
                        put("down", it.down)
                    }
                )
            }
            context.contentResolver.openOutputStream(uri)?.use { out ->
                out.write(arr.toString(2).toByteArray())
            }
            Toast.makeText(context, "已导出映射 JSON", Toast.LENGTH_SHORT).show()
        } catch (e: Throwable) {
            Toast.makeText(context, "导出失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF1F5F9))
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        val title = if (mappingType == MappingType.PINYIN) "自定义拼音12键映射" else "自定义符号12键映射"
        Text(title, fontSize = 22.sp)
        Text("每个键可改 5 个方向：中/左/上/右/下。保存后回到输入法立即生效。")

        edits.forEachIndexed { index, item ->
            KeyEditCard(
                index = index + 1,
                zoneName = item.zoneName,
                center = item.center,
                left = item.left,
                up = item.up,
                right = item.right,
                down = item.down,
                onCenter = { item.center = it },
                onLeft = { item.left = it },
                onUp = { item.up = it },
                onRight = { item.right = it },
                onDown = { item.down = it }
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Button(onClick = {
                edits.clear()
                if (mappingType == MappingType.PINYIN) {
                    val def = com.example.flickime.data.DefaultKeyMap.keys
                    def.forEach {
                        edits.add(KeyEdit(it.center, it.left, it.up, it.right, it.down, it.zone.name))
                    }
                } else {
                    val def = DefaultSymbolMap.keys
                    def.forEach {
                        edits.add(KeyEdit(it.center, it.left, it.up, it.right, it.down, "Symbol"))
                    }
                }
            }, modifier = Modifier.weight(1f)) {
                Text("恢复默认")
            }
            Button(onClick = {
                if (mappingType == MappingType.PINYIN) {
                    val old = KeyMapStore.loadPinyinKeys(context)
                    val newKeys = edits.mapIndexed { i, e ->
                        FlickKeySpec(
                            center = e.center.ifBlank { old[i].center },
                            left = e.left.ifBlank { old[i].left },
                            up = e.up.ifBlank { old[i].up },
                            right = e.right.ifBlank { old[i].right },
                            down = e.down.ifBlank { old[i].down },
                            zone = old[i].zone
                        )
                    }
                    KeyMapStore.savePinyinKeys(context, newKeys)
                } else {
                    val old = KeyMapStore.loadSymbolKeys(context)
                    val newKeys = edits.mapIndexed { i, e ->
                        DirectionalKeySpec(
                            center = e.center.ifBlank { old[i].center },
                            left = e.left.ifBlank { old[i].left },
                            up = e.up.ifBlank { old[i].up },
                            right = e.right.ifBlank { old[i].right },
                            down = e.down.ifBlank { old[i].down }
                        )
                    }
                    KeyMapStore.saveSymbolKeys(context, newKeys)
                }
                Toast.makeText(context, "已保存映射", Toast.LENGTH_SHORT).show()
            }, modifier = Modifier.weight(1f)) {
                Text("保存映射")
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = { importLauncher.launch(arrayOf("application/json", "*/*")) },
                modifier = Modifier.weight(1f)
            ) { Text("导入 JSON") }
            Button(
                onClick = {
                    val fileName = if (mappingType == MappingType.PINYIN) {
                        "cnflick_pinyin_keymap.json"
                    } else {
                        "cnflick_symbol_keymap.json"
                    }
                    exportLauncher.launch(fileName)
                },
                modifier = Modifier.weight(1f)
            ) { Text("导出 JSON") }
        }
    }
}

@Composable
private fun KeyEditCard(
    index: Int,
    zoneName: String,
    center: String,
    left: String,
    up: String,
    right: String,
    down: String,
    onCenter: (String) -> Unit,
    onLeft: (String) -> Unit,
    onUp: (String) -> Unit,
    onRight: (String) -> Unit,
    onDown: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("键 $index ($zoneName)")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            SmallField("中", center, onCenter, Modifier.weight(1f))
            SmallField("左", left, onLeft, Modifier.weight(1f))
            SmallField("上", up, onUp, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            SmallField("右", right, onRight, Modifier.weight(1f))
            SmallField("下", down, onDown, Modifier.weight(1f))
            Text("", modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun SmallField(label: String, value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    var v by remember(value) { mutableStateOf(value) }
    OutlinedTextField(
        value = v,
        onValueChange = {
            v = it
            onChange(it)
        },
        label = { Text(label) },
        singleLine = true,
        modifier = modifier
    )
}
