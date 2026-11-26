package com.example.simplelauncher

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlin.math.roundToInt

/**
 * ウィジェットを 6 列の格子に並べる。
 * 編集中は、本体をドラッグで移動、右下の白丸で大きさ変更、左上の赤丸で削除。
 * 時計はタップで見た目を切り替える。離した位置が重なる・はみ出す場合は元に戻す。
 */
@Composable
fun HomeGrid(
    layout: List<PlacedWidget>,
    editing: Boolean,
    onChange: (List<PlacedWidget>) -> Unit,
    onRows: (Int) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (PlacedWidget) -> Unit,
) {
    BoxWithConstraints(modifier) {
        val cell = maxWidth / GRID_COLS
        val rows = (maxHeight / cell).toInt()
        val cellPx = with(LocalDensity.current) { cell.toPx() }
        LaunchedEffect(rows) { onRows(rows) }

        if (editing) GridDots(rows, cell)
        layout.forEach { w ->
            key(w.id) {
                WidgetFrame(w, layout, cell, cellPx, rows, editing, onChange) { content(w) }
            }
        }
    }
}

@Composable
private fun GridDots(rows: Int, cell: Dp) {
    Canvas(Modifier.fillMaxSize()) {
        val c = cell.toPx()
        for (y in 0..rows) for (x in 0..GRID_COLS) {
            drawCircle(Color.White.copy(alpha = 0.25f), 1.5.dp.toPx(), Offset(x * c, y * c))
        }
    }
}

@Composable
private fun WidgetFrame(
    w: PlacedWidget,
    all: List<PlacedWidget>,
    cell: Dp,
    cellPx: Float,
    rows: Int,
    editing: Boolean,
    onChange: (List<PlacedWidget>) -> Unit,
    content: @Composable () -> Unit,
) {
    var drag by remember { mutableStateOf(Offset.Zero) }
    var resize by remember { mutableStateOf(Offset.Zero) }
    val current by rememberUpdatedState(w)
    val others by rememberUpdatedState(all)
    val density = LocalDensity.current

    fun commit(c: PlacedWidget) {
        if (others.fits(c, rows)) onChange(others.map { if (it.id == c.id) c else it })
    }

    val width = with(density) { (w.w * cellPx + resize.x).coerceAtLeast(w.type.minW * cellPx).toDp() }
    val height = with(density) { (w.h * cellPx + resize.y).coerceAtLeast(w.type.minH * cellPx).toDp() }
    val moving = drag != Offset.Zero || resize != Offset.Zero

    Box(
        Modifier
            .zIndex(if (moving) 1f else 0f)
            .offset { IntOffset((w.x * cellPx + drag.x).roundToInt(), (w.y * cellPx + drag.y).roundToInt()) }
            .size(width, height)
            .padding(4.dp)
    ) {
        content()
        if (!editing) return@Box

        // 編集中は中身の操作を止めて、枠全体でドラッグとタップを受ける
        Box(
            Modifier
                .matchParentSize()
                .background(Color.Black.copy(alpha = if (moving) 0.5f else 0.25f), RoundedCornerShape(16.dp))
                .border(1.dp, if (moving) Accent else Color.White.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
                .pointerInput(w.id) {
                    detectDragGestures(
                        onDragEnd = {
                            commit(
                                current.copy(
                                    x = current.x + (drag.x / cellPx).roundToInt(),
                                    y = current.y + (drag.y / cellPx).roundToInt(),
                                )
                            )
                            drag = Offset.Zero
                        },
                        onDragCancel = { drag = Offset.Zero },
                    ) { change, d -> change.consume(); drag += d }
                }
                .pointerInput(w.id) {
                    detectTapGestures(onTap = {
                        if (current.type == WidgetType.CLOCK) {
                            commit(current.copy(style = (current.style + 1) % ClockStyle.entries.size))
                        }
                    })
                }
        )
        val styleName = if (w.type == WidgetType.CLOCK) {
            "tap: ${ClockStyle.entries[w.style % ClockStyle.entries.size].title} · "
        } else ""
        Label(
            "$styleName${w.w}×${w.h}",
            color = if (styleName.isEmpty()) Dim else Fg,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp)
                .background(Color.Black, RoundedCornerShape(50))
                .padding(horizontal = 8.dp, vertical = 4.dp),
        )

        // 削除
        Box(
            Modifier
                .align(Alignment.TopStart)
                .offset((-6).dp, (-6).dp)
                .size(24.dp)
                .background(Accent, CircleShape)
                .clickable { onChange(others.filter { it.id != current.id }) },
            contentAlignment = Alignment.Center,
        ) { Text("×", color = Color.White, fontSize = 14.sp) }

        // 大きさ変更
        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .offset(6.dp, 6.dp)
                .size(24.dp)
                .background(Fg, CircleShape)
                .pointerInput(w.id) {
                    detectDragGestures(
                        onDragEnd = {
                            commit(
                                current.copy(
                                    w = (current.w + (resize.x / cellPx).roundToInt())
                                        .coerceIn(current.type.minW, GRID_COLS),
                                    h = (current.h + (resize.y / cellPx).roundToInt())
                                        .coerceAtLeast(current.type.minH),
                                )
                            )
                            resize = Offset.Zero
                        },
                        onDragCancel = { resize = Offset.Zero },
                    ) { change, d -> change.consume(); resize += d }
                },
        )
    }
}
