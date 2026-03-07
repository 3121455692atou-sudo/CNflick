package com.example.flickime.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.flickime.data.DefaultKeyMap
import com.example.flickime.engine.SyllableComposer
import com.example.flickime.model.KeyZone

enum class KeyboardMode {
    Flick, Alphabet, Numpad
}

@Composable
fun FlickKeyboardView(
    onQueryCandidates: (String, Int) -> List<String>,
    onCommitText: (String) -> Unit,
    onBackspace: () -> Unit,
    onMoveCursorLeft: () -> Unit,
    onMoveCursorRight: () -> Unit,
    onSpace: () -> Unit,
    onEnter: () -> Unit
) {
    val composer = remember { SyllableComposer() }
    var composingText by remember { mutableStateOf("") }
    var allCandidates by remember { mutableStateOf(emptyList<String>()) }
    var mode by remember { mutableStateOf(KeyboardMode.Flick) }
    var showHintPopup by remember { mutableStateOf(true) }
    var showExpandedCandidates by remember { mutableStateOf(false) }
    var showSymbolsPanel by remember { mutableStateOf(false) }

    fun refreshCandidates(pinyin: String) {
        allCandidates = onQueryCandidates(pinyin, 200)
    }

    fun clearComposing() {
        composer.reset()
        composingText = ""
        allCandidates = emptyList()
    }

    fun selectCandidate(text: String) {
        onCommitText(text)
        clearComposing()
        showExpandedCandidates = false
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFFF8FAFC))
            .padding(6.dp)
    ) {
        CandidateStrip(
            composingText = composingText,
            candidates = allCandidates.take(10),
            onCandidateClick = ::selectCandidate,
            onExpandClick = { if (allCandidates.isNotEmpty()) showExpandedCandidates = true }
        )

        when (mode) {
            KeyboardMode.Flick -> {
                FlickGrid(
                    showHintPopup = showHintPopup,
                    onCommit = { zone, part ->
                        if (part == "，" || part == "。") {
                            onCommitText(part)
                            clearComposing()
                            return@FlickGrid
                        }

                        when (zone) {
                            KeyZone.Shengmu -> {
                                composingText = composer.onShengmu(part)
                                allCandidates = emptyList()
                            }

                            KeyZone.Yunmu -> {
                                val full = composer.onYunmu(part)
                                composingText = full
                                refreshCandidates(full)
                            }
                        }
                    }
                )
            }

            KeyboardMode.Alphabet -> AlphabetKeyboard(onKey = {
                onCommitText(it)
                clearComposing()
            })

            KeyboardMode.Numpad -> NumpadKeyboard(onKey = {
                onCommitText(it)
                clearComposing()
            })
        }

        ActionRow(
            mode = mode,
            showHintPopup = showHintPopup,
            onTapMode = {
                mode = when (mode) {
                    KeyboardMode.Flick -> KeyboardMode.Alphabet
                    KeyboardMode.Alphabet -> KeyboardMode.Numpad
                    KeyboardMode.Numpad -> KeyboardMode.Flick
                }
            },
            onLongPressMode = { showSymbolsPanel = true },
            onToggleHint = { showHintPopup = !showHintPopup },
            onSpace = {
                onSpace()
                clearComposing()
            },
            onCursorLeft = onMoveCursorLeft,
            onCursorRight = onMoveCursorRight,
            onBackspace = {
                if (composingText.isNotBlank()) {
                    clearComposing()
                } else {
                    onBackspace()
                }
            },
            onEnter = {
                onEnter()
                clearComposing()
            }
        )
    }

    if (showExpandedCandidates) {
        CandidateFullScreenDialog(
            pinyin = composingText,
            candidates = allCandidates,
            onClose = { showExpandedCandidates = false },
            onCandidateClick = ::selectCandidate
        )
    }

    if (showSymbolsPanel) {
        SymbolsDialog(
            onClose = { showSymbolsPanel = false },
            onSymbolClick = {
                onCommitText(it)
                clearComposing()
            }
        )
    }
}

@Composable
private fun CandidateStrip(
    composingText: String,
    candidates: List<String>,
    onCandidateClick: (String) -> Unit,
    onExpandClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
            .border(1.dp, Color(0xFFCBD5E1))
            .background(Color.White)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text = if (composingText.isBlank()) "拼音显影区" else composingText,
            color = if (composingText.isBlank()) Color(0xFF94A3B8) else Color(0xFF1D4ED8),
            fontSize = 13.sp,
            maxLines = 1
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                candidates.forEach { c ->
                    Text(
                        text = c,
                        fontSize = 22.sp,
                        color = Color(0xFF0F172A),
                        modifier = Modifier.clickable { onCandidateClick(c) }
                    )
                }
            }
            Text(
                text = "˅",
                fontSize = 22.sp,
                color = Color(0xFF334155),
                modifier = Modifier
                    .width(28.dp)
                    .clickable { onExpandClick() }
            )
        }
    }
}

@Composable
private fun FlickGrid(
    showHintPopup: Boolean,
    onCommit: (KeyZone, String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        DefaultKeyMap.keys.chunked(4).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                row.forEach { spec ->
                    FlickKeyButton(
                        spec = spec,
                        showHintPopup = showHintPopup,
                        onCommit = { part -> onCommit(spec.zone, part) },
                        modifier = Modifier.size(76.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun AlphabetKeyboard(onKey: (String) -> Unit) {
    val rows = listOf("qwertyuiop", "asdfghjkl", "zxcvbnm")
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        rows.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                row.forEach { ch ->
                    KeyCell(text = ch.toString(), onClick = { onKey(ch.toString()) })
                }
            }
        }
    }
}

@Composable
private fun NumpadKeyboard(onKey: (String) -> Unit) {
    val rows = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf("0")
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        rows.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                row.forEach { item ->
                    KeyCell(text = item, onClick = { onKey(item) }, isLarge = true)
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ActionRow(
    mode: KeyboardMode,
    showHintPopup: Boolean,
    onTapMode: () -> Unit,
    onLongPressMode: () -> Unit,
    onToggleHint: () -> Unit,
    onSpace: () -> Unit,
    onCursorLeft: () -> Unit,
    onCursorRight: () -> Unit,
    onBackspace: () -> Unit,
    onEnter: () -> Unit
) {
    val modeLabel = when (mode) {
        KeyboardMode.Flick -> "中"
        KeyboardMode.Alphabet -> "ABC"
        KeyboardMode.Numpad -> "123"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .height(44.dp)
                .width(56.dp)
                .border(1.dp, Color(0xFF94A3B8))
                .background(Color(0xFFE2E8F0))
                .combinedClickable(
                    onClick = onTapMode,
                    onLongClick = onLongPressMode
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(text = modeLabel, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }

        KeyCell(
            text = if (showHintPopup) "提示ON" else "提示OFF",
            onClick = onToggleHint,
            widthDp = 76
        )
        KeyCell(text = "<", onClick = onCursorLeft)
        KeyCell(text = ">", onClick = onCursorRight)
        KeyCell(text = "空格", onClick = onSpace, widthDp = 96)
        KeyCell(text = "⌫", onClick = onBackspace)
        KeyCell(text = "↵", onClick = onEnter)
    }
}

@Composable
private fun KeyCell(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isLarge: Boolean = false,
    widthDp: Int? = null
) {
    val keyWidth = widthDp?.dp ?: if (isLarge) 82.dp else 34.dp
    val base = if (modifier == Modifier) modifier.width(keyWidth) else modifier
    Box(
        modifier = base
            .height(if (isLarge) 54.dp else 42.dp)
            .padding(horizontal = 2.dp)
            .border(1.dp, Color(0xFF94A3B8))
            .background(Color(0xFFF1F5F9))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(text = text, fontSize = if (isLarge) 22.sp else 15.sp, color = Color(0xFF0F172A))
    }
}

@Composable
private fun CandidateFullScreenDialog(
    pinyin: String,
    candidates: List<String>,
    onClose: () -> Unit,
    onCandidateClick: (String) -> Unit
) {
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White)
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "候选：$pinyin", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text(text = "收起", color = Color(0xFF1D4ED8), modifier = Modifier.clickable { onClose() })
            }

            LazyVerticalGrid(
                columns = GridCells.Adaptive(72.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(candidates) { item ->
                    Box(
                        modifier = Modifier
                            .height(56.dp)
                            .border(1.dp, Color(0xFFCBD5E1))
                            .background(Color(0xFFF8FAFC))
                            .clickable { onCandidateClick(item) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = item, fontSize = 30.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun SymbolsDialog(
    onClose: () -> Unit,
    onSymbolClick: (String) -> Unit
) {
    val symbols = listOf(
        "?", "!", "@", "#", "$", "%", "&", "*", "(", ")", "-", "+", "=", "/", "\\", "_", ":", ";",
        "'", "\"", "[", "]", "{", "}", "<", ">", "~", "`", "😊", "😂", "👍", "❤️", "🙏", "🎉", "🔥", "😄"
    )

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White)
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "符号 / Emoji", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text(text = "关闭", color = Color(0xFF1D4ED8), modifier = Modifier.clickable { onClose() })
            }

            LazyVerticalGrid(
                columns = GridCells.Adaptive(56.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(symbols) { item ->
                    Box(
                        modifier = Modifier
                            .height(52.dp)
                            .border(1.dp, Color(0xFFCBD5E1))
                            .background(Color(0xFFF8FAFC))
                            .clickable {
                                onSymbolClick(item)
                                onClose()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = item, fontSize = 22.sp)
                    }
                }
            }
        }
    }
}
