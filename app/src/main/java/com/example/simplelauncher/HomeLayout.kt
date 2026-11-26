package com.example.simplelauncher

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** ホーム画面は横 6 マスの格子。縦のマス数は画面の高さで決まる。 */
const val GRID_COLS = 6

enum class WidgetType(
    val title: String,
    val w: Int, val h: Int,       // 追加したときの大きさ
    val minW: Int, val minH: Int, // 縮められる下限
) {
    CLOCK("時計", 6, 3, 3, 2),
    GREETING("あいさつ", 6, 1, 3, 1),
    BATTERY("電池", 2, 2, 2, 2),
    DATE("日付", 2, 2, 2, 2),
    STORAGE("ストレージ", 2, 2, 2, 2),
    STATS("システム", 6, 2, 4, 2),
    FAVORITES("お気に入り", 6, 4, 3, 1),
}

data class PlacedWidget(
    val id: Long,
    val type: WidgetType,
    val x: Int, val y: Int,
    val w: Int, val h: Int,
    /** 時計の見た目の番号（時計以外では使わない） */
    val style: Int = 0,
) {
    fun overlaps(o: PlacedWidget) =
        x < o.x + o.w && o.x < x + w && y < o.y + o.h && o.y < y + h
}

/** 枠内に収まり、他のウィジェットと重ならないか。 */
fun List<PlacedWidget>.fits(c: PlacedWidget, rows: Int): Boolean =
    c.x >= 0 && c.y >= 0 && c.x + c.w <= GRID_COLS && c.y + c.h <= rows &&
        none { it.id != c.id && it.overlaps(c) }

/** 新しいウィジェットを置ける場所を、上から順に探す。入らなければ小さくして再挑戦。 */
fun List<PlacedWidget>.findSpot(type: WidgetType, rows: Int): PlacedWidget? {
    val id = System.currentTimeMillis()
    for (h in type.h downTo type.minH) for (w in type.w downTo type.minW) {
        for (y in 0..rows - h) for (x in 0..GRID_COLS - w) {
            val c = PlacedWidget(id, type, x, y, w, h)
            if (fits(c, rows)) return c
        }
    }
    return null
}

val DEFAULT_LAYOUT = listOf(
    PlacedWidget(1, WidgetType.CLOCK, 0, 0, 6, 3),
    PlacedWidget(2, WidgetType.GREETING, 0, 3, 6, 1),
    PlacedWidget(3, WidgetType.BATTERY, 0, 4, 2, 2),
    PlacedWidget(4, WidgetType.DATE, 2, 4, 2, 2),
    PlacedWidget(5, WidgetType.STORAGE, 4, 4, 2, 2),
    PlacedWidget(6, WidgetType.STATS, 0, 6, 6, 2),
    PlacedWidget(7, WidgetType.FAVORITES, 0, 8, 6, 4),
)

class LayoutStore(context: Context) {
    private val prefs = context.getSharedPreferences("launcher", Context.MODE_PRIVATE)

    fun load(): List<PlacedWidget> = runCatching {
        val raw = prefs.getString(KEY, null) ?: return DEFAULT_LAYOUT
        val arr = JSONArray(raw)
        List(arr.length()) { i ->
            val o = arr.getJSONObject(i)
            PlacedWidget(
                id = o.getLong("id"),
                type = WidgetType.valueOf(o.getString("type")),
                x = o.getInt("x"), y = o.getInt("y"),
                w = o.getInt("w"), h = o.getInt("h"),
                style = o.optInt("style", 0),
            )
        }
    }.getOrDefault(DEFAULT_LAYOUT)

    fun save(list: List<PlacedWidget>) {
        val arr = JSONArray()
        list.forEach {
            arr.put(
                JSONObject()
                    .put("id", it.id).put("type", it.type.name)
                    .put("x", it.x).put("y", it.y).put("w", it.w).put("h", it.h)
                    .put("style", it.style)
            )
        }
        prefs.edit().putString(KEY, arr.toString()).apply()
    }

    private companion object { const val KEY = "layout" }
}
