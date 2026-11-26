package com.example.simplelauncher

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Calendar
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

enum class ClockStyle(val title: String) {
    DOT("Dot"),         // ドットの数字（標準）
    STACK("Stack"),     // 時と分を縦に積む
    THIN("Thin"),       // 細い書体の大きな数字
    SEGMENT("Segment"), // 7セグメント表示
    RING("Ring"),       // ドットのアナログ時計
    BINARY("Binary"),   // 2進数のドット
    WORDS("Words"),     // 英単語で読む
}

private fun Calendar.hh() = get(Calendar.HOUR_OF_DAY).toString().padStart(2, '0')
private fun Calendar.mm() = get(Calendar.MINUTE).toString().padStart(2, '0')
private fun Calendar.dateLine() = String.format(
    Locale.ENGLISH, "%tA · %<te %<tB · week %d", this, get(Calendar.WEEK_OF_YEAR),
)

@Composable
fun ClockFace(style: ClockStyle, now: Calendar, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier) {
        val w = maxWidth
        val h = maxHeight
        when (style) {
            ClockStyle.DOT -> Column {
                DotText("${now.hh()}:${now.mm()}", pitch = minOf(w / 25, (h - 26.dp) / 7))
                Spacer(Modifier.height(10.dp))
                Label(now.dateLine())
            }

            ClockStyle.STACK -> Row(verticalAlignment = Alignment.Bottom) {
                val p = minOf(h / 15, w * 0.6f / 11)
                Column(verticalArrangement = Arrangement.spacedBy(p)) {
                    DotText(now.hh(), pitch = p)
                    DotText(now.mm(), pitch = p)
                }
                Spacer(Modifier.width(16.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Label(String.format(Locale.ENGLISH, "%tA", now), color = Accent)
                    Label(String.format(Locale.ENGLISH, "%te %<tB", now))
                    Label("week ${now.get(Calendar.WEEK_OF_YEAR)}")
                }
            }

            ClockStyle.THIN -> Column {
                val size = minOf((h - 26.dp) * 0.85f, w / 2.9f)
                val sp = with(LocalDensity.current) { size.toSp() }
                Text(
                    "${now.hh()}:${now.mm()}",
                    color = Fg, fontSize = sp, lineHeight = sp, fontWeight = FontWeight.Thin,
                )
                Spacer(Modifier.height(6.dp))
                Label(now.dateLine())
            }

            ClockStyle.SEGMENT -> Column {
                val digitH = minOf(h - 26.dp, w / 2.9f)
                SegmentClock(now, digitH)
                Spacer(Modifier.height(10.dp))
                Label(now.dateLine())
            }

            ClockStyle.RING -> Row(verticalAlignment = Alignment.CenterVertically) {
                val d = minOf(h, w * 0.5f)
                RingClock(now, Modifier.size(d))
                Spacer(Modifier.width(20.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    DotText("${now.hh()}:${now.mm()}", pitch = 3.dp)
                    Label(String.format(Locale.ENGLISH, "%tA", now), color = Accent)
                    Label(String.format(Locale.ENGLISH, "%te %<tB", now))
                }
            }

            ClockStyle.BINARY -> Row(verticalAlignment = Alignment.CenterVertically) {
                BinaryClock(now, Modifier.size(minOf(h * 0.9f, w * 0.55f)))
                Spacer(Modifier.width(20.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Label("8 4 2 1")
                    Text("${now.hh()}:${now.mm()}", color = Fg, fontFamily = Mono, fontSize = 22.sp)
                    Label(String.format(Locale.ENGLISH, "%ta %<te %<tb", now))
                }
            }

            ClockStyle.WORDS -> Column {
                val lineH = (h - 26.dp) / 2.3f
                val sp = with(LocalDensity.current) { minOf(lineH, w / 7.5f).toSp() }
                Text(
                    numberWord(now.get(Calendar.HOUR_OF_DAY)).uppercase(Locale.ROOT),
                    color = Fg, fontSize = sp, lineHeight = sp, fontWeight = FontWeight.Light,
                )
                Text(
                    minuteWord(now.get(Calendar.MINUTE)).uppercase(Locale.ROOT),
                    color = Fg.copy(alpha = 0.45f), fontSize = sp, lineHeight = sp, fontWeight = FontWeight.Light,
                )
                Spacer(Modifier.height(6.dp))
                Label(now.dateLine())
            }
        }
    }
}

/* ---------- 7セグメント ---------- */

private val SEG = intArrayOf(0x3F, 0x06, 0x5B, 0x4F, 0x66, 0x6D, 0x7D, 0x07, 0x7F, 0x6F)

@Composable
private fun SegmentClock(now: Calendar, digitH: Dp) {
    val digits = (now.hh() + now.mm()).map { it - '0' }
    val dw = digitH * 0.5f
    val gap = dw * 0.3f
    val colonW = dw * 0.5f
    Canvas(Modifier.size(dw * 4 + gap * 4 + colonW, digitH)) {
        val wPx = dw.toPx()
        val g = gap.toPx()
        val t = wPx * 0.16f
        var x = 0f
        digits.forEachIndexed { i, d ->
            drawDigit(SEG[d], x, wPx, size.height, t)
            x += wPx + g
            if (i == 1) {
                val c = Accent
                val cx = x + colonW.toPx() / 2 - g / 2
                drawCircle(c, t * 0.7f, Offset(cx, size.height * 0.33f))
                drawCircle(c, t * 0.7f, Offset(cx, size.height * 0.67f))
                x += colonW.toPx() + g / 2
            }
        }
    }
}

private fun DrawScope.drawDigit(bits: Int, left: Float, w: Float, h: Float, t: Float) {
    val g = t * 0.25f
    val half = h / 2
    val vLen = half - t - 2 * g
    fun seg(bit: Int, x: Float, y: Float, sw: Float, sh: Float) = drawRoundRect(
        color = if ((bits shr bit) and 1 == 1) Fg else Color.White.copy(alpha = 0.07f),
        topLeft = Offset(x, y), size = Size(sw, sh), cornerRadius = CornerRadius(t / 2),
    )
    val hLen = w - 2 * t - 2 * g
    seg(0, left + t + g, 0f, hLen, t)                      // a
    seg(1, left + w - t, t / 2 + g, t, vLen)               // b
    seg(2, left + w - t, half + g, t, vLen)                // c
    seg(3, left + t + g, h - t, hLen, t)                   // d
    seg(4, left, half + g, t, vLen)                        // e
    seg(5, left, t / 2 + g, t, vLen)                       // f
    seg(6, left + t + g, half - t / 2, hLen, t)            // g
}

/* ---------- アナログ ---------- */

@Composable
private fun RingClock(now: Calendar, modifier: Modifier) {
    Canvas(modifier) {
        val c = Offset(size.width / 2, size.height / 2)
        val r = size.minDimension / 2 - 4.dp.toPx()
        val dot = 2.dp.toPx()
        fun at(deg: Double, rr: Float) =
            Offset(c.x + rr * cos(Math.toRadians(deg - 90)).toFloat(), c.y + rr * sin(Math.toRadians(deg - 90)).toFloat())
        for (i in 0 until 60) {
            val big = i % 5 == 0
            drawCircle(Color.White.copy(alpha = if (big) 0.6f else 0.15f), if (big) dot * 1.2f else dot * 0.7f, at(i * 6.0, r))
        }
        val m = now.get(Calendar.MINUTE)
        val hDeg = (now.get(Calendar.HOUR) + m / 60.0) * 30
        val mDeg = m * 6.0
        for (k in 1..5) drawCircle(Fg, dot * 1.3f, at(hDeg, r * 0.5f * k / 5))
        for (k in 1..8) drawCircle(Fg, dot, at(mDeg, r * 0.8f * k / 8))
        drawCircle(Accent, dot * 1.6f, at(0.0, r)) // 12時の目印
        drawCircle(Fg, dot * 2.2f, c)
    }
}

/* ---------- 2進数 ---------- */

@Composable
private fun BinaryClock(now: Calendar, modifier: Modifier) {
    val digits = (now.hh() + now.mm()).map { it - '0' }
    Canvas(modifier) {
        val cols = 5 // 時2列 + 区切り + 分2列
        val p = minOf(size.width / cols, size.height / 4)
        val r = p * 0.32f
        digits.forEachIndexed { i, d ->
            val col = if (i < 2) i else i + 1
            for (bit in 0 until 4) {
                val on = (d shr bit) and 1 == 1
                val color = when {
                    on && i == 3 && bit == 0 -> Accent
                    on -> Fg
                    else -> Color.White.copy(alpha = 0.1f)
                }
                drawCircle(color, r, Offset(col * p + p / 2, (3 - bit) * p + p / 2))
            }
        }
    }
}

/* ---------- 英単語 ---------- */

private val ONES = listOf(
    "zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten",
    "eleven", "twelve", "thirteen", "fourteen", "fifteen", "sixteen", "seventeen", "eighteen", "nineteen",
)
private val TENS = listOf("", "", "twenty", "thirty", "forty", "fifty")

private fun numberWord(n: Int): String =
    if (n < 20) ONES[n] else TENS[n / 10] + if (n % 10 == 0) "" else "-" + ONES[n % 10]

private fun minuteWord(m: Int): String = when {
    m == 0 -> "o'clock"
    m < 10 -> "oh-" + ONES[m]
    else -> numberWord(m)
}
