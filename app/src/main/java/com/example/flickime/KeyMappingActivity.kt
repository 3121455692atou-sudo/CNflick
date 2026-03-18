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
import androidx.compose.material3.OutlinedButton
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
import com.example.flickime.data.DefaultJapaneseKeyMap
import com.example.flickime.data.DefaultKeyMap
import com.example.flickime.data.DefaultSymbolMap
import com.example.flickime.data.DefaultZhuyinKeyMap
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

private enum class MappingType { PINYIN, ZHUYIN, JAPANESE, SYMBOL }
private enum class MappingMenuLevel { MAIN, DIAGONAL }

private data class KeyEdit(
    var center: String,
    var left: String,
    var up: String,
    var right: String,
    var down: String,
    var upLeft: String,
    var upRight: String,
    var downLeft: String,
    var downRight: String,
    val zoneName: String
)

@Composable
private fun KeyMappingScreen() {
    val context = LocalContext.current
    val mappingType = remember {
        when ((context as? KeyMappingActivity)?.intent?.getStringExtra("map_type")) {
            "symbol" -> MappingType.SYMBOL
            "zhuyin" -> MappingType.ZHUYIN
            "japanese" -> MappingType.JAPANESE
            else -> MappingType.PINYIN
        }
    }
    val initial = remember {
        when (mappingType) {
            MappingType.PINYIN -> KeyMapStore.loadPinyinKeys(context).map {
                KeyEdit(it.center, it.left, it.up, it.right, it.down, it.upLeft, it.upRight, it.downLeft, it.downRight, it.zone.name)
            }
            MappingType.ZHUYIN -> KeyMapStore.loadZhuyinKeys(context).map {
                KeyEdit(it.center, it.left, it.up, it.right, it.down, it.upLeft, it.upRight, it.downLeft, it.downRight, it.zone.name)
            }
            MappingType.JAPANESE -> KeyMapStore.loadJapaneseKeys(context).map {
                KeyEdit(it.center, it.left, it.up, it.right, it.down, it.upLeft, it.upRight, it.downLeft, it.downRight, "Kana")
            }
            MappingType.SYMBOL -> KeyMapStore.loadSymbolKeys(context).map {
                KeyEdit(it.center, it.left, it.up, it.right, it.down, it.upLeft, it.upRight, it.downLeft, it.downRight, "Symbol")
            }
        }
    }
    var menuLevel by remember { mutableStateOf(MappingMenuLevel.MAIN) }
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
                } else if (mappingType == MappingType.ZHUYIN) {
                    if (i < 5) "Shengmu" else "Yunmu"
                } else if (mappingType == MappingType.JAPANESE) {
                    "Kana"
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
                        upLeft = o.optString("upLeft", ""),
                        upRight = o.optString("upRight", ""),
                        downLeft = o.optString("downLeft", ""),
                        downRight = o.optString("downRight", ""),
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
                        put("upLeft", it.upLeft)
                        put("upRight", it.upRight)
                        put("downLeft", it.downLeft)
                        put("downRight", it.downRight)
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
        val title = when (mappingType) {
            MappingType.PINYIN -> "自定义拼音12键映射"
            MappingType.ZHUYIN -> "自定义注音12键映射"
            MappingType.JAPANESE -> "自定义日语假名12键映射"
            MappingType.SYMBOL -> "自定义符号12键映射"
        }
        Text(title, fontSize = 22.sp)
        if (menuLevel == MappingMenuLevel.MAIN) {
            Text("二级菜单：正向映射（中/左/上/右/下）")
            Text("保存后立即生效。斜向映射默认留空，需在三级菜单单独配置。")
            OutlinedButton(onClick = { menuLevel = MappingMenuLevel.DIAGONAL }, modifier = Modifier.fillMaxWidth()) {
                Text("进入三级菜单：斜向映射（左上/右上/左下/右下）")
            }
        } else {
            Text("三级菜单：斜向映射（左上/右上/左下/右下）")
            Text("仅在对应输入类型的“八方向滑行输入”开启后生效。留空即该斜向不输出。")
            OutlinedButton(onClick = { menuLevel = MappingMenuLevel.MAIN }, modifier = Modifier.fillMaxWidth()) {
                Text("返回二级菜单：正向映射")
            }
        }

        edits.forEachIndexed { index, item ->
            if (menuLevel == MappingMenuLevel.MAIN) {
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
            } else {
                DiagonalEditCard(
                    index = index + 1,
                    zoneName = item.zoneName,
                    upLeft = item.upLeft,
                    upRight = item.upRight,
                    downLeft = item.downLeft,
                    downRight = item.downRight,
                    onUpLeft = { item.upLeft = it },
                    onUpRight = { item.upRight = it },
                    onDownLeft = { item.downLeft = it },
                    onDownRight = { item.downRight = it }
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Button(onClick = {
                edits.clear()
                if (mappingType == MappingType.PINYIN) {
                    val def = DefaultKeyMap.keys
                    def.forEach {
                        edits.add(KeyEdit(it.center, it.left, it.up, it.right, it.down, it.upLeft, it.upRight, it.downLeft, it.downRight, it.zone.name))
                    }
                } else if (mappingType == MappingType.ZHUYIN) {
                    DefaultZhuyinKeyMap.keys.forEach {
                        edits.add(KeyEdit(it.center, it.left, it.up, it.right, it.down, it.upLeft, it.upRight, it.downLeft, it.downRight, it.zone.name))
                    }
                } else if (mappingType == MappingType.JAPANESE) {
                    DefaultJapaneseKeyMap.keys.forEach {
                        edits.add(KeyEdit(it.center, it.left, it.up, it.right, it.down, it.upLeft, it.upRight, it.downLeft, it.downRight, "Kana"))
                    }
                } else {
                    val def = DefaultSymbolMap.keys
                    def.forEach {
                        edits.add(KeyEdit(it.center, it.left, it.up, it.right, it.down, it.upLeft, it.upRight, it.downLeft, it.downRight, "Symbol"))
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
                            center = e.center,
                            left = e.left,
                            up = e.up,
                            right = e.right,
                            down = e.down,
                            upLeft = e.upLeft,
                            upRight = e.upRight,
                            downLeft = e.downLeft,
                            downRight = e.downRight,
                            zone = old[i].zone
                        )
                    }
                    KeyMapStore.savePinyinKeys(context, newKeys)
                } else if (mappingType == MappingType.ZHUYIN) {
                    val old = KeyMapStore.loadZhuyinKeys(context)
                    val newKeys = edits.mapIndexed { i, e ->
                        FlickKeySpec(
                            center = e.center,
                            left = e.left,
                            up = e.up,
                            right = e.right,
                            down = e.down,
                            upLeft = e.upLeft,
                            upRight = e.upRight,
                            downLeft = e.downLeft,
                            downRight = e.downRight,
                            zone = old[i].zone
                        )
                    }
                    KeyMapStore.saveZhuyinKeys(context, newKeys)
                } else if (mappingType == MappingType.JAPANESE) {
                    val old = KeyMapStore.loadJapaneseKeys(context)
                    val newKeys = edits.mapIndexed { i, e ->
                        FlickKeySpec(
                            center = e.center,
                            left = e.left,
                            up = e.up,
                            right = e.right,
                            down = e.down,
                            upLeft = e.upLeft,
                            upRight = e.upRight,
                            downLeft = e.downLeft,
                            downRight = e.downRight,
                            zone = old[i].zone
                        )
                    }
                    KeyMapStore.saveJapaneseKeys(context, newKeys)
                } else {
                    val newKeys = edits.map { e ->
                        DirectionalKeySpec(
                            center = e.center,
                            left = e.left,
                            up = e.up,
                            right = e.right,
                            down = e.down,
                            upLeft = e.upLeft,
                            upRight = e.upRight,
                            downLeft = e.downLeft,
                            downRight = e.downRight
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
                    val fileName = when (mappingType) {
                        MappingType.PINYIN -> "cnflick_pinyin_keymap.json"
                        MappingType.ZHUYIN -> "cnflick_zhuyin_keymap.json"
                        MappingType.JAPANESE -> "cnflick_japanese_keymap.json"
                        MappingType.SYMBOL -> "cnflick_symbol_keymap.json"
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
private fun DiagonalEditCard(
    index: Int,
    zoneName: String,
    upLeft: String,
    upRight: String,
    downLeft: String,
    downRight: String,
    onUpLeft: (String) -> Unit,
    onUpRight: (String) -> Unit,
    onDownLeft: (String) -> Unit,
    onDownRight: (String) -> Unit
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
            SmallField("左上", upLeft, onUpLeft, Modifier.weight(1f))
            SmallField("右上", upRight, onUpRight, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            SmallField("左下", downLeft, onDownLeft, Modifier.weight(1f))
            SmallField("右下", downRight, onDownRight, Modifier.weight(1f))
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
