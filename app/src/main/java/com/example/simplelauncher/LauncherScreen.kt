package com.example.simplelauncher

import android.content.Intent
import android.provider.AlarmClock
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.Locale

@Composable
fun LauncherScreen(
    apps: List<AppEntry>,
    favoriteKeys: List<String>,
    homePressed: Int,
    repo: AppRepository,
    onLaunch: (AppEntry) -> Unit,
    onAppInfo: (AppEntry) -> Unit,
    onToggleFavorite: (AppEntry) -> Unit,
) {
    var drawerOpen by remember { mutableStateOf(false) }
    LaunchedEffect(homePressed) { drawerOpen = false }
    BackHandler(enabled = drawerOpen) { drawerOpen = false }

    val byKey = remember(apps) { apps.associateBy { it.key } }
    val favorites = favoriteKeys.mapNotNull { byKey[it] }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        HalftoneBackground(Modifier.fillMaxSize())
        HomePane(
            favorites = favorites,
            homePressed = homePressed,
            onLaunch = onLaunch,
            onLongPress = onToggleFavorite,
            onOpenDrawer = { drawerOpen = true },
        )
        AnimatedVisibility(
            visible = drawerOpen,
            enter = fadeIn() + scaleIn(initialScale = 0.96f),
            exit = fadeOut() + scaleOut(targetScale = 0.96f),
        ) {
            AppDrawer(
                apps = apps,
                favoriteKeys = favoriteKeys,
                repo = repo,
                onLaunch = { drawerOpen = false; onLaunch(it) },
                onAppInfo = onAppInfo,
                onToggleFavorite = onToggleFavorite,
                onClose = { drawerOpen = false },
            )
        }
    }
}

/* ---------------- ホーム ---------------- */

@Composable
private fun rememberNow(): Calendar {
    val now by produceState(Calendar.getInstance()) {
        while (true) {
            value = Calendar.getInstance()
            delay(1_000)
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
private fun HomePane(
    favorites: List<AppEntry>,
    homePressed: Int,
    onLaunch: (AppEntry) -> Unit,
    onLongPress: (AppEntry) -> Unit,
    onOpenDrawer: () -> Unit,
) {
    val context = LocalContext.current
    val store = remember { LayoutStore(context) }
    var layout by remember { mutableStateOf(store.load()) }
    var editing by remember { mutableStateOf(false) }
    var rows by remember { mutableStateOf(12) }
    LaunchedEffect(homePressed) { editing = false }
    BackHandler(enabled = editing) { editing = false }

    val now = rememberNow()
    val stats = rememberSystemStats()
    fun update(next: List<PlacedWidget>) { layout = next; store.save(next) }

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
            .padding(horizontal = 24.dp, vertical = 20.dp),
    ) {
        if (editing) {
            EditBar(
                onAdd = { type ->
                    val spot = layout.findSpot(type, rows)
                    if (spot != null) update(layout + spot)
                    else Toast.makeText(context, "空きがありません", Toast.LENGTH_SHORT).show()
                },
                onReset = { update(DEFAULT_LAYOUT) },
                onDone = { editing = false },
            )
        }
        HomeGrid(
            layout = layout,
            editing = editing,
            onChange = ::update,
            onRows = { rows = it },
            modifier = Modifier.weight(1f),
        ) { w ->
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
                        Label("${favorites.size} pinned · ${stats.battery}% battery")
                    }
                }

                WidgetType.BATTERY -> DialCard(stats.battery.toString(), "bat", stats.battery, m, accent = true)
                WidgetType.DATE -> DateCard(now, m)
                WidgetType.STORAGE -> DialCard(stats.storagePct.toString(), "disk", stats.storagePct, m)
                WidgetType.STATS -> StatsCard(stats, m)
                WidgetType.FAVORITES -> Column(m.clipToBounds(), verticalArrangement = Arrangement.Bottom) {
                    if (favorites.isEmpty()) Label("swipe up · hold an app to pin it here")
                    favorites.forEach { FavoriteRow(it, onLaunch, onLongPress) }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Label(
            if (editing) "drag move · ○ resize · tap clock"
            else "↑ apps  ·  hold empty space to edit",
            modifier = Modifier.align(Alignment.CenterHorizontally).clickable(enabled = !editing) { onOpenDrawer() },
        )
    }
}

@Composable
private fun EditBar(onAdd: (WidgetType) -> Unit, onReset: () -> Unit, onDone: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().padding(bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(6.dp).background(Accent, CircleShape))
        Spacer(Modifier.width(8.dp))
        Label("edit home", color = Fg)
        Spacer(Modifier.weight(1f))
        Chip("reset", onClick = onReset)
        Spacer(Modifier.width(6.dp))
        Box {
            Chip("+ add") { menu = true }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                WidgetType.entries.forEach { t ->
                    DropdownMenuItem(text = { Text(t.title) }, onClick = { menu = false; onAdd(t) })
                }
            }
        }
        Spacer(Modifier.width(6.dp))
        Chip("done", filled = true, onClick = onDone)
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
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
    )
}

/** 押している間だけ赤い点が付き、文字が明るくなる行。 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FavoriteRow(
    app: AppEntry,
    onLaunch: (AppEntry) -> Unit,
    onLongPress: (AppEntry) -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    Row(
        Modifier
            .fillMaxWidth()
            .combinedClickable(
                interactionSource = source,
                indication = null,
                onLongClick = { onLongPress(app) },
                onClick = { onLaunch(app) },
            )
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(7.dp).background(if (pressed) Accent else Color.Transparent, CircleShape))
        Spacer(Modifier.width(10.dp))
        Text(
            app.label.uppercase(Locale.getDefault()),
            color = if (pressed) Fg else Fg.copy(alpha = 0.55f),
            fontFamily = Mono,
            fontSize = if (pressed) 24.sp else 19.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/* ---------------- アプリ一覧 ---------------- */

@Composable
private fun AppDrawer(
    apps: List<AppEntry>,
    favoriteKeys: List<String>,
    repo: AppRepository,
    onLaunch: (AppEntry) -> Unit,
    onAppInfo: (AppEntry) -> Unit,
    onToggleFavorite: (AppEntry) -> Unit,
    onClose: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val filtered = remember(apps, query) {
        val q = query.trim()
        if (q.isEmpty()) apps else apps.filter { it.label.contains(q, ignoreCase = true) }
    }
    LaunchedEffect(Unit) { focus.requestFocus() }

    // 一致が1件に絞れたら、そのまま起動する
    LaunchedEffect(filtered, query) {
        if (query.isNotBlank() && filtered.size == 1) {
            delay(300)
            keyboard?.hide()
            onLaunch(filtered.first())
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.75f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onClose() }
            .systemBarsPadding()
            .imePadding()
            .padding(16.dp),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(26.dp))
                .background(Card)
                // パネル内のタップで閉じないよう吸収する
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                .padding(16.dp),
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(Color.Black, RoundedCornerShape(50))
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(6.dp).background(Accent, CircleShape))
                Spacer(Modifier.width(12.dp))
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    textStyle = TextStyle(color = Fg, fontSize = 15.sp, fontFamily = Mono),
                    cursorBrush = SolidColor(Accent),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = { filtered.firstOrNull()?.let(onLaunch) }),
                    modifier = Modifier.weight(1f).focusRequester(focus),
                    decorationBox = { inner ->
                        Box {
                            if (query.isEmpty()) {
                                Text("Search apps", color = Dim, fontSize = 15.sp, fontFamily = Mono)
                            }
                            inner()
                        }
                    },
                )
                Text(
                    "APPS ${filtered.size}",
                    color = Color.Black, fontFamily = Mono, fontSize = 9.sp,
                    modifier = Modifier.background(Fg, RoundedCornerShape(50)).padding(horizontal = 8.dp, vertical = 3.dp),
                )
            }
            Spacer(Modifier.height(16.dp))
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                modifier = Modifier
                    .weight(1f)
                    .pointerInput(Unit) {
                        var total = 0f
                        detectVerticalDragGestures(
                            onDragStart = { total = 0f },
                            onDragEnd = { if (total > 250f) onClose() },
                            onVerticalDrag = { _, dy -> total += dy },
                        )
                    },
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                items(filtered, key = { it.key }) { app ->
                    AppCell(
                        app = app,
                        isFavorite = app.key in favoriteKeys,
                        repo = repo,
                        onLaunch = onLaunch,
                        onAppInfo = onAppInfo,
                        onToggleFavorite = onToggleFavorite,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Label("tap open · hold pin · ↓ close", Modifier.align(Alignment.CenterHorizontally))
        }
    }
}

private val Grayscale = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppCell(
    app: AppEntry,
    isFavorite: Boolean,
    repo: AppRepository,
    onLaunch: (AppEntry) -> Unit,
    onAppInfo: (AppEntry) -> Unit,
    onToggleFavorite: (AppEntry) -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    val icon by produceState<ImageBitmap?>(repo.cachedIcon(app), app.key) {
        if (value == null) value = withContext(Dispatchers.IO) { repo.loadIcon(app) }
    }
    Box {
        Column(
            Modifier
                .fillMaxWidth()
                .combinedClickable(onClick = { onLaunch(app) }, onLongClick = { menu = true }),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(contentAlignment = Alignment.TopEnd) {
                Box(
                    Modifier.size(52.dp).background(Color(0xFF222222), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    icon?.let {
                        Image(it, contentDescription = app.label, colorFilter = Grayscale, modifier = Modifier.size(34.dp))
                    }
                }
                if (isFavorite) Box(Modifier.size(8.dp).background(Accent, CircleShape))
            }
            Spacer(Modifier.height(6.dp))
            Text(
                app.label,
                color = Fg.copy(alpha = 0.7f), fontFamily = Mono, fontSize = 9.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 2.dp),
            )
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(
                text = { Text(if (isFavorite) "ホームから外す" else "ホームに追加") },
                onClick = { menu = false; onToggleFavorite(app) },
            )
            DropdownMenuItem(
                text = { Text("アプリ情報") },
                onClick = { menu = false; onAppInfo(app) },
            )
        }
    }
}
