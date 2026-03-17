package com.example.flickime.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.flickime.model.FlickDirection
import com.example.flickime.model.FlickKeySpec
import kotlin.math.abs

@Composable
fun FlickKeyButton(
    spec: FlickKeySpec,
    showHintPopup: Boolean,
    onCommit: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var currentDirection by remember { mutableStateOf(FlickDirection.Center) }
    var isPressing by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .size(74.dp)
            .pointerInput(spec) {
                awaitPointerEventScope {
                    while (true) {
                        val down = awaitPointerEvent().changes.firstOrNull { it.pressed } ?: continue
                        val start = down.position
                        currentDirection = FlickDirection.Center
                        isPressing = true

                        while (true) {
                            val evt = awaitPointerEvent()
                            val ch = evt.changes.firstOrNull() ?: continue
                            val dx = ch.position.x - start.x
                            val dy = ch.position.y - start.y
                            currentDirection = detectDirection(dx, dy)
                            if (!ch.pressed) break
                        }

                        isPressing = false
                        onCommit(
                            when (currentDirection) {
                                FlickDirection.Center -> spec.center
                                FlickDirection.Left -> spec.left
                                FlickDirection.Up -> spec.up
                                FlickDirection.Right -> spec.right
                                FlickDirection.Down -> spec.down
                                FlickDirection.UpLeft -> spec.upLeft
                                FlickDirection.UpRight -> spec.upRight
                                FlickDirection.DownLeft -> spec.downLeft
                                FlickDirection.DownRight -> spec.downRight
                            }
                        )
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        if (showHintPopup && isPressing) {
            DirectionBubble(spec.left, currentDirection == FlickDirection.Left, Modifier.offset(x = (-46).dp))
            DirectionBubble(spec.up, currentDirection == FlickDirection.Up, Modifier.offset(y = (-46).dp))
            DirectionBubble(spec.right, currentDirection == FlickDirection.Right, Modifier.offset(x = 46.dp))
            DirectionBubble(spec.down, currentDirection == FlickDirection.Down, Modifier.offset(y = 46.dp))
        }

        Box(
            modifier = Modifier
                .size(74.dp)
                .border(1.dp, Color(0xFF94A3B8), CircleShape)
                .background(
                    if (isPressing && currentDirection == FlickDirection.Center) Color(0xFFCFE5FF) else Color(0xFFF1F5F9),
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = spec.center,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF0F172A)
            )
        }
    }
}

@Composable
private fun DirectionBubble(text: String, highlighted: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(38.dp)
            .border(1.dp, Color(0xFFCBD5E1), CircleShape)
            .background(if (highlighted) Color(0xFF93C5FD) else Color(0xFFE2E8F0), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(text = text, fontSize = 13.sp, color = Color(0xFF0F172A))
    }
}

private fun detectDirection(dx: Float, dy: Float): FlickDirection {
    val threshold = 22f
    if (abs(dx) < threshold && abs(dy) < threshold) return FlickDirection.Center
    return if (abs(dx) >= abs(dy)) {
        if (dx > 0) FlickDirection.Right else FlickDirection.Left
    } else {
        if (dy > 0) FlickDirection.Down else FlickDirection.Up
    }
}
