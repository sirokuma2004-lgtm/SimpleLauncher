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

/** 最初のページの配置。縦 11 マスに収まるようにしている。 */
val DEFAULT_LAYOUT = listOf(
    PlacedWidget(1, WidgetType.CLOCK, 0, 0, 6, 3),
    PlacedWidget(2, WidgetType.GREETING, 0, 3, 6, 1),
    PlacedWidget(3, WidgetType.BATTERY, 0, 4, 2, 2),
    PlacedWidget(4, WidgetType.DATE, 2, 4, 2, 2),
    PlacedWidget(5, WidgetType.STORAGE, 4, 4, 2, 2),
    PlacedWidget(6, WidgetType.STATS, 0, 6, 6, 2),
    PlacedWidget(7, WidgetType.FAVORITES, 0, 8, 6, 3),
)

/** 形の決まったページ。選ぶと配置が固定され、時計の見た目だけ変えられる。 */
enum class PageTemplate(val title: String, val widgets: List<PlacedWidget>) {
    CLOCK(
        "Clock",
        listOf(
            PlacedWidget(101, WidgetType.CLOCK, 0, 0, 6, 5),
            PlacedWidget(102, WidgetType.GREETING, 0, 5, 6, 1),
            PlacedWidget(103, WidgetType.DATE, 0, 7, 2, 2),
            PlacedWidget(104, WidgetType.BATTERY, 2, 7, 2, 2),
            PlacedWidget(105, WidgetType.STORAGE, 4, 7, 2, 2),
        ),
    ),
    FOCUS(
        "Focus",
        listOf(
            PlacedWidget(201, WidgetType.CLOCK, 0, 0, 6, 3, style = ClockStyle.STACK.ordinal),
            PlacedWidget(202, WidgetType.FAVORITES, 0, 4, 6, 7),
        ),
    ),
    STATUS(
        "Status",
        listOf(
            PlacedWidget(301, WidgetType.CLOCK, 0, 0, 6, 3, style = ClockStyle.RING.ordinal),
            PlacedWidget(302, WidgetType.BATTERY, 0, 4, 2, 2),
            PlacedWidget(303, WidgetType.DATE, 2, 4, 2, 2),
            PlacedWidget(304, WidgetType.STORAGE, 4, 4, 2, 2),
            PlacedWidget(305, WidgetType.STATS, 0, 6, 6, 2),
        ),
    ),
}

/** ホームの1ページ。[template] が null なら自由配置。 */
data class HomePage(val id: Long, val template: PageTemplate?, val widgets: List<PlacedWidget>) {
    val locked: Boolean get() = template != null
}

val DEFAULT_PAGES = listOf(
    HomePage(1, null, DEFAULT_LAYOUT),
    HomePage(2, PageTemplate.FOCUS, PageTemplate.FOCUS.widgets),
)

enum class DockMode { ICON, TEXT }

const val DOCK_SIZE = 5

/** ホーム画面まわりの設定をまとめて保存する。 */
class HomeStore(context: Context) {
    private val prefs = context.getSharedPreferences("launcher", Context.MODE_PRIVATE)

    /* ---- ページ ---- */

    fun loadPages(): List<HomePage> = runCatching {
        val raw = prefs.getString(KEY_PAGES, null)
        if (raw == null) {
            // 旧版の 1 ページ配置があれば、それを 1 ページ目に引き継ぐ
            val old = prefs.getString(KEY_OLD_LAYOUT, null) ?: return DEFAULT_PAGES
            return listOf(HomePage(1, null, parseWidgets(JSONArray(old)))) + DEFAULT_PAGES.drop(1)
        }
        val arr = JSONArray(raw)
        List(arr.length()) { i ->
            val o = arr.getJSONObject(i)
            HomePage(
                id = o.getLong("id"),
                template = o.optString("template").takeIf { it.isNotEmpty() }?.let { PageTemplate.valueOf(it) },
                widgets = parseWidgets(o.getJSONArray("widgets")),
            )
        }.ifEmpty { DEFAULT_PAGES }
    }.getOrDefault(DEFAULT_PAGES)

    fun savePages(pages: List<HomePage>) {
        val arr = JSONArray()
        pages.forEach {
            arr.put(
                JSONObject()
                    .put("id", it.id)
                    .put("template", it.template?.name ?: "")
                    .put("widgets", widgetsJson(it.widgets))
            )
        }
        prefs.edit().putString(KEY_PAGES, arr.toString()).apply()
    }

    /* ---- ドック ---- */

    fun loadDock(): List<String?> {
        val raw = prefs.getString(KEY_DOCK, null)?.split("\n") ?: emptyList()
        return List(DOCK_SIZE) { i -> raw.getOrNull(i)?.takeIf { it.isNotBlank() } }
    }

    fun saveDock(slots: List<String?>) {
        prefs.edit().putString(KEY_DOCK, slots.joinToString("\n") { it ?: "" }).apply()
    }

    fun loadDockMode(): DockMode =
        runCatching { DockMode.valueOf(prefs.getString(KEY_DOCK_MODE, null)!!) }.getOrDefault(DockMode.ICON)

    fun saveDockMode(mode: DockMode) {
        prefs.edit().putString(KEY_DOCK_MODE, mode.name).apply()
    }

    /* ---- 表示名 ---- */

    fun loadLabels(): Map<String, String> = runCatching {
        val o = JSONObject(prefs.getString(KEY_LABELS, "{}")!!)
        o.keys().asSequence().associateWith { o.getString(it) }
    }.getOrDefault(emptyMap())

    fun saveLabels(labels: Map<String, String>) {
        prefs.edit().putString(KEY_LABELS, JSONObject(labels).toString()).apply()
    }

    private fun parseWidgets(arr: JSONArray) = List(arr.length()) { i ->
        val o = arr.getJSONObject(i)
        PlacedWidget(
            id = o.getLong("id"),
            type = WidgetType.valueOf(o.getString("type")),
            x = o.getInt("x"), y = o.getInt("y"),
            w = o.getInt("w"), h = o.getInt("h"),
            style = o.optInt("style", 0),
        )
    }

    private fun widgetsJson(list: List<PlacedWidget>) = JSONArray().also { arr ->
        list.forEach {
            arr.put(
                JSONObject()
                    .put("id", it.id).put("type", it.type.name)
                    .put("x", it.x).put("y", it.y).put("w", it.w).put("h", it.h)
                    .put("style", it.style)
            )
        }
    }

    private companion object {
        const val KEY_PAGES = "pages"
        const val KEY_OLD_LAYOUT = "layout"
        const val KEY_DOCK = "dock"
        const val KEY_DOCK_MODE = "dock_mode"
        const val KEY_LABELS = "labels"
    }
}
