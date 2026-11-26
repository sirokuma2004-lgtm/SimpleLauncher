package com.example.simplelauncher

import android.app.ActivityManager
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Environment
import android.os.StatFs
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.util.Calendar
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

val Accent = Color(0xFFE5243B)
val Card = Color(0xFF141414)
val Fg = Color(0xFFF2F2F2)
val Dim = Color(0x66F2F2F2)
val Mono = FontFamily.Monospace

@Composable
fun Label(text: String, modifier: Modifier = Modifier, color: Color = Dim) {
    Text(
        text.uppercase(Locale.ROOT),
        color = color, fontFamily = Mono, fontSize = 9.sp, letterSpacing = 1.5.sp,
        modifier = modifier,
    )
}

/* ---------------- 端末の状態 ---------------- */

data class SystemStats(
    val battery: Int = 0,
    val charging: Boolean = false,
    val tempC: Float = 0f,
    val ramPct: Int = 0,
    val ramUsedGb: Float = 0f,
    val storagePct: Int = 0,
    val ramHistory: List<Int> = emptyList(),
)

@Composable
fun rememberSystemStats(): SystemStats {
    val context = LocalContext.current
    val stats by produceState(SystemStats()) {
        val am = context.getSystemService(ActivityManager::class.java)
        while (true) {
            val bat = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val level = bat?.getIntExtra(BatteryManager.EXTRA_LEVEL, 0) ?: 0
            val scale = bat?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
            val status = bat?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
            val temp = (bat?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0) / 10f

            val mem = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
            val used = mem.totalMem - mem.availMem
            val fs = StatFs(Environment.getDataDirectory().path)
            val storage = 100 - (fs.availableBytes * 100 / fs.totalBytes).toInt()

            val ram = (used * 100 / mem.totalMem).toInt()
            value = SystemStats(
                battery = level * 100 / scale.coerceAtLeast(1),
                charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                    status == BatteryManager.BATTERY_STATUS_FULL,
                tempC = temp,
                ramPct = ram,
                ramUsedGb = used / 1_073_741_824f,
                storagePct = storage,
                ramHistory = (value.ramHistory + ram).takeLast(40),
            )
            delay(2_000)
        }
    }
    return stats
}

/* ---------------- カード類 ---------------- */

/** 丸いメーター。外周のドットが [pct] の分だけ点灯する。 */
@Composable
fun DialCard(value: String, label: String, pct: Int, modifier: Modifier = Modifier, accent: Boolean = false) {
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
    Box(
        Modifier.size(minOf(maxWidth, maxHeight)).background(Card, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize().padding(6.dp)) {
            val n = 36
            val r = this.size.minDimension / 2
            val c = Offset(this.size.width / 2, this.size.height / 2)
            val lit = (n * pct / 100f).toInt()
            for (i in 0 until n) {
                val a = Math.toRadians(-90.0 + i * 360.0 / n)
                val pos = Offset(c.x + r * cos(a).toFloat(), c.y + r * sin(a).toFloat())
                val col = when {
                    i >= lit -> Color.White.copy(alpha = 0.12f)
                    accent && i == lit - 1 -> Accent
                    else -> Fg
                }
                drawCircle(col, 1.6.dp.toPx(), pos)
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            DotText(value, pitch = 2.6.dp)
            Spacer(Modifier.height(5.dp))
            Label(label)
        }
    }
    }
}

/** 日付カード。大きなドット数字と、週の位置を示すドット列。 */
@Composable
fun DateCard(cal: Calendar, modifier: Modifier = Modifier) {
    val month = cal.getDisplayName(Calendar.MONTH, Calendar.LONG, Locale.ENGLISH) ?: ""
    val dow = cal.get(Calendar.DAY_OF_WEEK) // 1=日曜
    Column(
        modifier.background(Card, RoundedCornerShape(18.dp)).padding(12.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Label(month)
        DotText(cal.get(Calendar.DAY_OF_MONTH).toString().padStart(2, '0'), pitch = 3.6.dp)
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            for (d in 1..7) {
                Box(
                    Modifier.size(4.dp).background(
                        if (d == dow) Accent else Color.White.copy(alpha = 0.25f), CircleShape,
                    )
                )
            }
        }
    }
}

/** RAM・ストレージ・電池・温度と、RAM 使用率の小さなドットグラフ。 */
@Composable
fun StatsCard(s: SystemStats, modifier: Modifier = Modifier) {
    Row(
        modifier.background(Card, RoundedCornerShape(18.dp)).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                Stat("ram", "%.1fG".format(Locale.ROOT, s.ramUsedGb))
                Stat("disk", "${s.storagePct}%")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                Stat(if (s.charging) "bat +" else "bat", "${s.battery}%")
                Stat("temp", "%.0f°".format(Locale.ROOT, s.tempC))
            }
        }
        Spacer(Modifier.size(16.dp))
        Column(Modifier.weight(1f)) {
            Label("ram · ${s.ramPct}%")
            Spacer(Modifier.height(8.dp))
            DotGraph(s.ramHistory, Modifier.fillMaxWidth().height(40.dp))
        }
    }
}

@Composable
private fun Stat(label: String, value: String) {
    Column {
        Label(label)
        Text(value, color = Fg, fontFamily = Mono, fontSize = 17.sp)
    }
}

@Composable
private fun DotGraph(values: List<Int>, modifier: Modifier) {
    Canvas(modifier) {
        val cols = 40
        val rows = 8
        val px = size.width / cols
        val py = size.height / rows
        val r = minOf(px, py) * 0.28f
        val data = List(cols - values.size) { -1 } + values
        data.forEachIndexed { i, v ->
            val h = if (v < 0) 0 else ((v / 100f) * rows).toInt().coerceIn(1, rows)
            for (j in 0 until rows) {
                val on = j < h
                val col = when {
                    on && i == cols - 1 -> Accent
                    on -> Fg
                    else -> Color.White.copy(alpha = 0.08f)
                }
                drawCircle(col, r, Offset(i * px + px / 2, size.height - (j * py + py / 2)))
            }
        }
    }
}
