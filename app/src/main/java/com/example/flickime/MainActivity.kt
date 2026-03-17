package com.example.flickime

import android.content.Intent
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { SetupScreen() }
    }
}

@Composable
private fun SetupScreen() {
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }
    val text = remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF8FAFC))
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(text = "CNflick 安装指引", fontSize = 26.sp)
        Text(text = "1. 点击“启用输入法”进入系统设置，打开 CNflick")
        Text(text = "2. 回来后点击“切换输入法”选择 CNflick")
        Text(text = "3. 在任意输入框测试：先滑声母，再滑韵母，点候选上屏")
        Text(text = "使用小技巧：")
        Text(text = "• 删除键长按可连删，长按时上滑可一键清空当前输入")
        Text(text = "• 拼音/符号八方向可以分别开关：拼音默认关，符号默认开")
        Text(text = "• 想用斜向滑动：先开八方向，再到映射页三级菜单配置斜向映射")

        OutlinedTextField(
            value = text.value,
            onValueChange = { text.value = it },
            label = { Text("测试输入框") },
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester)
        )

        Button(
            onClick = {
                focusRequester.requestFocus()
                keyboardController?.show()
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("强制弹出键盘")
        }

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = {
                context.startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("启用输入法")
        }

        Button(
            onClick = {
                val imm = context.getSystemService(InputMethodManager::class.java)
                imm?.showInputMethodPicker()
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("切换输入法")
        }
    }
}
