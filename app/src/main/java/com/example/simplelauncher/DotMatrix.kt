package com.example.simplelauncher

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** 5x7 のドットフォント。コロンは縦に2点。 */
private val GLYPHS: Map<Char, List<String>> = mapOf(
    '0' to listOf("01110", "10001", "10011", "10101", "11001", "10001", "01110"),
    '1' to listOf("00100", "01100", "00100", "00100", "00100", "00100", "01110"),
    '2' to listOf("01110", "10001", "00001", "00010", "00100", "01000", "11111"),
    '3' to listOf("11111", "00010", "00100", "00010", "00001", "10001", "01110"),
    '4' to listOf("00010", "00110", "01010", "10010", "11111", "00010", "00010"),
    '5' to listOf("11111", "10000", "11110", "00001", "00001", "10001", "01110"),
    '6' to listOf("00110", "01000", "10000", "11110", "10001", "10001", "01110"),
    '7' to listOf("11111", "00001", "00010", "00100", "01000", "01000", "01000"),
    '8' to listOf("01110", "10001", "10001", "01110", "10001", "10001", "01110"),
    '9' to listOf("01110", "10001", "10001", "01111", "00001", "00010", "01100"),
    ':' to listOf("0", "0", "1", "0", "1", "0", "0"),
    '%' to listOf("11001", "11010", "00010", "00100", "01000", "01011", "10011"),
    ' ' to listOf("0", "0", "0", "0", "0", "0", "0"),
)

/** コロンの2点の行位置（0〜6。小数可）。間を広げたいときはここを調整する。 */
private val COLON_ROWS = floatArrayOf(1.5f, 4.5f)

private fun glyph(c: Char) = GLYPHS[c] ?: GLYPHS.getValue(' ')

/**
 * 数字をドットで描く。[pitch] はドット間隔、ドット径はその 0.8 倍。
 * [dimColon] が true のとき、コロンを薄く描く（点滅用）。
 */
@Composable
fun DotText(
    text: String,
    pitch: Dp,
    modifier: Modifier = Modifier,
    color: Color = Color.White,
    dimColon: Boolean = false,
) {
    val glyphs = text.map { it to glyph(it) }
    val cols = glyphs.sumOf { it.second.first().length } + (glyphs.size - 1).coerceAtLeast(0)
    Canvas(modifier.size(pitch * cols, pitch * 7)) {
        val p = pitch.toPx()
        val r = p * 0.4f
        var x0 = 0f
        glyphs.forEach { (ch, rows) ->
            val c = if (ch == ':' && dimColon) color.copy(alpha = 0.25f) else color
            if (ch == ':') {
                // 行の格子に縛られず、中心から上下 1.5 行ずつ離して置く
                COLON_ROWS.forEach { row -> drawCircle(c, r, Offset(x0 + p / 2, row * p + p / 2)) }
                x0 += 2 * p
                return@forEach
            }
            rows.forEachIndexed { y, row ->
                row.forEachIndexed { x, bit ->
                    if (bit == '1') {
                        drawCircle(c, r, Offset(x0 + x * p + p / 2, y * p + p / 2))
                    }
                }
            }
            x0 += (rows.first().length + 1) * p
        }
    }
}

/** 画面全体に敷く、薄いドットの格子と右下の網点の円。 */
@Composable
fun HalftoneBackground(modifier: Modifier = Modifier, pitch: Dp = 12.dp) {
    Canvas(modifier) {
        val p = pitch.toPx()
        val center = Offset(size.width * 0.95f, size.height * 0.92f)
        val radius = size.width * 0.75f
        var y = p / 2
        while (y < size.height) {
            var x = p / 2
            while (x < size.width) {
                val d = (Offset(x, y) - center).getDistance()
                if (d < radius) {
                    val t = 1f - d / radius
                    drawCircle(Color.White.copy(alpha = 0.10f + 0.10f * t), p * (0.12f + 0.25f * t), Offset(x, y))
                } else {
                    drawCircle(Color.White.copy(alpha = 0.05f), p * 0.08f, Offset(x, y))
                }
                x += p
            }
            y += p
        }
    }
}
