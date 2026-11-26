package com.example.simplelauncher

import android.content.Intent
import android.provider.AlarmClock
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Locale

/** 1 分ごとに更新される現在時刻。秒は使わないので、分の変わり目にだけ起きる。 */
@Composable
private fun rememberNow(): Calendar {
    val now by produceState(Calendar.getInstance()) {
        while (true) {
            value = Calendar.getInstance()
            val ms = System.currentTimeMillis()
            delay(60_000 - ms % 60_000 + 50)
        }
    }
    return now
}

private fun greeting(hour: Int) = when (hour) {
    in 5..10 -> "Good morning."
    in 11..16 -> "Good afternoon."
    in 17..21 -> "Good evening."
    else -> "Good night."
}

@Composable
fun HomePane(
    data: LauncherData,
    homePressed: Int,
    repo: AppRepository,
    store: HomeStore,
    actions: LauncherActions,
    onRename: (AppEntry) -> Unit,
    onOpenDrawer: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pages by remember { mutableStateOf(store.loadPages()) }
    var editing by remember { mutableStateOf(false) }
    var rows by remember { mutableStateOf(11) }
    val pager = rememberPagerState { pages.size }

    LaunchedEffect(homePressed) {
        editing = false
        if (homePressed > 0) pager.animateScrollToPage(0) // ホームボタンで最初のページへ
    }
    BackHandler(enabled = editing) { editing = false }

    val now = rememberNow()
    val stats = rememberSystemStats()

    fun savePages(next: List<HomePage>) { pages = next; store.savePages(next) }
    fun updatePage(id: Long, f: (HomePage) -> HomePage) = savePages(pages.map { if (it.id == id) f(it) else it })
    fun toast(t: String) = Toast.makeText(context, t, Toast.LENGTH_SHORT).show()

    Column(
        Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .pointerInput(editing) {
                if (editing) return@pointerInput
                var total = 0f
                detectVerticalDragGestures(
                    onDragStart = { total = 0f },
                    onDragEnd = { if (total < -120f) onOpenDrawer() },
                    onVerticalDrag = { _, dy -> total += dy },
                )
            }
            // 何もないところを長押しで編集モード
            .pointerInput(Unit) { detectTapGestures(onLongPress = { editing = true }) }
            .padding(horizontal = 24.dp, vertical = 16.dp),
    ) {
        val current = pages.getOrNull(pager.currentPage) ?: pages.first()

        if (editing) {
            EditBar(
                page = current,
                pageIndex = pager.currentPage,
                pageCount = pages.size,
                dockMode = data.dockMode,
                onAddWidget = { type ->
                    when (val spot = current.widgets.findSpot(type, rows)) {
                        null -> toast("空きがありません")
                        else -> updatePage(current.id) { it.copy(widgets = it.widgets + spot) }
                    }
                },
                onTemplate = { t ->
                    updatePage(current.id) {
                        if (t == null) it.copy(template = null) else it.copy(template = t, widgets = t.widgets)
                    }
                },
                onResetPage = { updatePage(current.id) { it.copy(widgets = it.template?.widgets ?: DEFAULT_LAYOUT) } },
                onAddPage = {
                    val id = System.currentTimeMillis()
                    savePages(pages + HomePage(id, null, emptyList()))
                    scope.launch { delay(50); pager.animateScrollToPage(pages.lastIndex) }
                },
                onRemovePage = {
                    if (pages.size > 1) {
                        val i = pager.currentPage
                        savePages(pages.filterIndexed { idx, _ -> idx != i })
                    }
                },
                onDockMode = actions.setDockMode,
                onDone = { editing = false },
            )
        }

        HorizontalPager(
            state = pager,
            userScrollEnabled = !editing,
            key = { pages.getOrNull(it)?.id ?: it },
            modifier = Modifier.weight(1f),
        ) { i ->
            val page = pages.getOrNull(i) ?: return@HorizontalPager
            HomeGrid(
                layout = page.widgets,
                editing = editing && i == pager.currentPage,
                locked = page.locked,
                onChange = { w -> updatePage(page.id) { it.copy(widgets = w) } },
                onRows = { rows = it },
                modifier = Modifier.fillMaxSize(),
            ) { w ->
                WidgetContent(w, data, now, stats, actions, onRename)
            }
        }

        PageDots(pages.size, pager.currentPage) { scope.launch { pager.animateScrollToPage(it) } }
        Spacer(Modifier.height(10.dp))
        Dock(data, repo, actions, onRename)
        if (editing) {
            Spacer(Modifier.height(8.dp))
            Label(
                if (current.locked) "template · tap clock to change style"
                else "drag move · ○ resize · tap clock",
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }
    }
}

@Composable
private fun WidgetContent(
    w: PlacedWidget,
    data: LauncherData,
    now: Calendar,
    stats: SystemStats,
    actions: LauncherActions,
    onRename: (AppEntry) -> Unit,
) {
    val context = LocalContext.current
    val m = Modifier.fillMaxSize()
    when (w.type) {
        WidgetType.CLOCK -> Box(
            m.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                runCatching {
                    context.startActivity(
                        Intent(AlarmClock.ACTION_SHOW_ALARMS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
            }
        ) { ClockFace(ClockStyle.entries[w.style % ClockStyle.entries.size], now, m) }

        WidgetType.GREETING -> Column(m) {
            Text(greeting(now.get(Calendar.HOUR_OF_DAY)), color = Fg, fontSize = 22.sp, fontWeight = FontWeight.Light)
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(5.dp).background(Accent, CircleShape))
                Spacer(Modifier.width(6.dp))
                Label("${data.favoriteKeys.size} pinned · ${stats.battery}% battery")
            }
        }

        WidgetType.BATTERY -> DialCard(stats.battery.toString(), "bat", stats.battery, m, accent = true)
        WidgetType.DATE -> DateCard(now, m)
        WidgetType.STORAGE -> DialCard(stats.storagePct.toString(), "disk", stats.storagePct, m)
        WidgetType.STATS -> StatsCard(stats, m)
        WidgetType.FAVORITES -> Column(m.clipToBounds(), verticalArrangement = Arrangement.Bottom) {
            val favorites = data.favorites
            if (favorites.isEmpty()) Label("swipe up · hold an app · ホームに追加")
            favorites.forEach { app ->
                FavoriteRow(
                    app = app,
                    label = data.labelOf(app),
                    onLaunch = actions.launch,
                    menu = listOf(
                        "表示名を変更" to { onRename(app) },
                        "ホームから外す" to { actions.toggleFavorite(app) },
                    ),
                )
            }
        }
    }
}

/** 押している間だけ赤い点が付き、文字が明るくなる行。長押しでメニュー。 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FavoriteRow(
    app: AppEntry,
    label: String,
    onLaunch: (AppEntry) -> Unit,
    menu: List<Pair<String, () -> Unit>>,
) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    var open by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier
                .fillMaxWidth()
                .combinedClickable(
                    interactionSource = source,
                    indication = null,
                    onLongClick = { open = true },
                    onClick = { onLaunch(app) },
                )
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(7.dp).background(if (pressed || open) Accent else Color.Transparent, CircleShape))
            Spacer(Modifier.width(10.dp))
            Text(
                label.uppercase(Locale.getDefault()),
                color = if (pressed || open) Fg else Fg.copy(alpha = 0.55f),
                fontFamily = Mono,
                fontSize = if (pressed) 24.sp else 19.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        AppMenu(expanded = open, onDismiss = { open = false }, items = menu)
    }
}

/* ---------------- ページの点 ---------------- */

@Composable
private fun PageDots(count: Int, current: Int, onSelect: (Int) -> Unit) {
    if (count <= 1) return
    Row(
        Modifier.fillMaxWidth().padding(top = 8.dp),
        horizontalArrangement = Arrangement.Center,
    ) {
        repeat(count) { i ->
            Box(
                Modifier
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onSelect(i) }
                    .padding(horizontal = 5.dp, vertical = 6.dp)
                    .size(if (i == current) 6.dp else 5.dp)
                    .background(if (i == current) Accent else Color.White.copy(alpha = 0.3f), CircleShape)
            )
        }
    }
}

/* ---------------- ドック ---------------- */

@Composable
private fun Dock(data: LauncherData, repo: AppRepository, actions: LauncherActions, onRename: (AppEntry) -> Unit) {
    val slots = data.dockApps
    if (slots.all { it == null }) {
        Label("hold an app in the list · ドックに追加", Modifier.fillMaxWidth().padding(vertical = 14.dp))
        return
    }
    Row(
        Modifier
            .fillMaxWidth()
            .background(Card.copy(alpha = 0.85f), RoundedCornerShape(26.dp))
            .padding(horizontal = 8.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        slots.forEach { app ->
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                if (app == null) {
                    Box(Modifier.size(4.dp).background(Color.White.copy(alpha = 0.2f), CircleShape))
                } else {
                    DockItem(app, data.labelOf(app), data.dockMode, repo, actions, onRename)
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DockItem(
    app: AppEntry,
    label: String,
    mode: DockMode,
    repo: AppRepository,
    actions: LauncherActions,
    onRename: (AppEntry) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        Box(
            Modifier.combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { actions.launch(app) },
                onLongClick = { open = true },
            ),
        ) {
            when (mode) {
                DockMode.ICON -> AppIcon(repo, app, 48.dp)
                DockMode.TEXT -> Text(
                    label.uppercase(Locale.getDefault()),
                    color = if (open) Accent else Fg,
                    fontFamily = Mono, fontSize = 12.sp, letterSpacing = 1.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(vertical = 14.dp, horizontal = 2.dp),
                )
            }
        }
        AppMenu(
            expanded = open,
            onDismiss = { open = false },
            items = listOf(
                "表示名を変更" to { onRename(app) },
                "ドックから外す" to { actions.toggleDock(app) },
            ),
        )
    }
}

/* ---------------- 編集バー ---------------- */

@Composable
private fun EditBar(
    page: HomePage,
    pageIndex: Int,
    pageCount: Int,
    dockMode: DockMode,
    onAddWidget: (WidgetType) -> Unit,
    onTemplate: (PageTemplate?) -> Unit,
    onResetPage: () -> Unit,
    onAddPage: () -> Unit,
    onRemovePage: () -> Unit,
    onDockMode: (DockMode) -> Unit,
    onDone: () -> Unit,
) {
    var widgetMenu by remember { mutableStateOf(false) }
    var templateMenu by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(6.dp).background(Accent, CircleShape))
            Spacer(Modifier.width(8.dp))
            Label("edit · page ${pageIndex + 1}/$pageCount", color = Fg)
            Spacer(Modifier.weight(1f))
            Chip("done", filled = true, onClick = onDone)
        }
        Spacer(Modifier.height(8.dp))
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (!page.locked) {
                Box {
                    Chip("+ widget") { widgetMenu = true }
                    AppMenu(
                        expanded = widgetMenu,
                        onDismiss = { widgetMenu = false },
                        items = WidgetType.entries.map { t -> t.title to { onAddWidget(t) } },
                    )
                }
            }
            Box {
                Chip("page: ${page.template?.title ?: "free"} ▾") { templateMenu = true }
                AppMenu(
                    expanded = templateMenu,
                    onDismiss = { templateMenu = false },
                    items = listOf<Pair<String, () -> Unit>>("自由配置" to { onTemplate(null) }) +
                        PageTemplate.entries.map { t -> "テンプレート：${t.title}" to { onTemplate(t) } },
                )
            }
            Chip("reset", onClick = onResetPage)
            Chip("+ page", onClick = onAddPage)
            if (pageCount > 1) Chip("− page", onClick = onRemovePage)
            Chip("dock: ${dockMode.name.lowercase(Locale.ROOT)}") {
                onDockMode(if (dockMode == DockMode.ICON) DockMode.TEXT else DockMode.ICON)
            }
        }
    }
}

@Composable
private fun Chip(text: String, filled: Boolean = false, onClick: () -> Unit) {
    Text(
        text.uppercase(Locale.ROOT),
        color = if (filled) Color.Black else Fg,
        fontFamily = Mono, fontSize = 10.sp, letterSpacing = 1.sp,
        modifier = Modifier
            .background(if (filled) Fg else Card, RoundedCornerShape(50))
            .border(1.dp, if (filled) Fg else Color.White.copy(alpha = 0.15f), RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
    )
}
