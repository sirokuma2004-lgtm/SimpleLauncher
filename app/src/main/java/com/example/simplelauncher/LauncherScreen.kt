package com.example.simplelauncher

import android.content.Intent
import android.provider.AlarmClock
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val Fg = Color(0xFFF2F2F2)
private val Dim = Color(0x99F2F2F2)

@Composable
fun LauncherScreen(
    apps: List<AppEntry>,
    favoriteKeys: List<String>,
    homePressed: Int,
    onLaunch: (AppEntry) -> Unit,
    onAppInfo: (AppEntry) -> Unit,
    onToggleFavorite: (AppEntry) -> Unit,
) {
    var drawerOpen by remember { mutableStateOf(false) }
    LaunchedEffect(homePressed) { drawerOpen = false }
    BackHandler(enabled = drawerOpen) { drawerOpen = false }

    val byKey = remember(apps) { apps.associateBy { it.key } }
    val favorites = favoriteKeys.mapNotNull { byKey[it] }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0x66000000)) // 壁紙の上に薄い黒を敷いて文字を読みやすく
    ) {
        HomePane(
            favorites = favorites,
            onLaunch = onLaunch,
            onLongPress = onToggleFavorite,
            onSwipeUp = { drawerOpen = true },
        )
        AnimatedVisibility(
            visible = drawerOpen,
            enter = slideInVertically { it / 3 } + fadeIn(),
            exit = slideOutVertically { it / 3 } + fadeOut(),
        ) {
            AppDrawer(
                apps = apps,
                favoriteKeys = favoriteKeys,
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
private fun HomePane(
    favorites: List<AppEntry>,
    onLaunch: (AppEntry) -> Unit,
    onLongPress: (AppEntry) -> Unit,
    onSwipeUp: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .pointerInput(Unit) {
                var total = 0f
                detectVerticalDragGestures(
                    onDragStart = { total = 0f },
                    onDragEnd = { if (total < -120f) onSwipeUp() },
                    onVerticalDrag = { _, dy -> total += dy },
                )
            }
            .padding(horizontal = 32.dp, vertical = 48.dp),
    ) {
        Clock()
        Spacer(Modifier.weight(1f))
        if (favorites.isEmpty()) {
            Text(
                "上にスワイプしてアプリ一覧へ。\n長押しでホームに追加できます。",
                color = Dim, fontSize = 15.sp, lineHeight = 22.sp,
            )
        }
        favorites.forEach { app -> FavoriteRow(app, onLaunch, onLongPress) }
        Spacer(Modifier.height(32.dp))
        Text(
            "︿",
            color = Dim,
            fontSize = 18.sp,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .clickable { onSwipeUp() }
                .padding(8.dp),
        )
    }
}

@Composable
private fun Clock() {
    val context = LocalContext.current
    val now by produceState(Date()) {
        while (true) {
            value = Date()
            delay(1_000L * (60 - (System.currentTimeMillis() / 1000 % 60)))
        }
    }
    val time = remember(now) { SimpleDateFormat("HH:mm", Locale.getDefault()).format(now) }
    val date = remember(now) { SimpleDateFormat("M月d日 (E)", Locale.JAPAN).format(now) }

    Column(
        Modifier.clickable {
            runCatching {
                context.startActivity(
                    Intent(AlarmClock.ACTION_SHOW_ALARMS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
        }
    ) {
        Text(time, color = Fg, fontSize = 72.sp, fontWeight = FontWeight.Thin)
        Text(date, color = Dim, fontSize = 18.sp)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FavoriteRow(
    app: AppEntry,
    onLaunch: (AppEntry) -> Unit,
    onLongPress: (AppEntry) -> Unit,
) {
    Text(
        app.label,
        color = Fg,
        fontSize = 26.sp,
        fontWeight = FontWeight.Light,
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = { onLaunch(app) }, onLongClick = { onLongPress(app) })
            .padding(vertical = 10.dp),
    )
}

/* ---------------- アプリ一覧 ---------------- */

@Composable
private fun AppDrawer(
    apps: List<AppEntry>,
    favoriteKeys: List<String>,
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

    // 一致が1件になったら、そのまま起動する
    LaunchedEffect(filtered, query) {
        if (query.isNotBlank() && filtered.size == 1) {
            delay(250)
            keyboard?.hide()
            onLaunch(filtered.first())
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Color(0xF0101010))
            .systemBarsPadding()
            .imePadding()
            .padding(horizontal = 32.dp),
    ) {
        Spacer(Modifier.height(32.dp))
        BasicTextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            textStyle = TextStyle(color = Fg, fontSize = 22.sp),
            cursorBrush = SolidColor(Fg),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = { filtered.firstOrNull()?.let(onLaunch) }),
            modifier = Modifier.fillMaxWidth().focusRequester(focus),
            decorationBox = { inner ->
                Box {
                    if (query.isEmpty()) Text("検索", color = Dim, fontSize = 22.sp)
                    inner()
                }
            },
        )
        Spacer(Modifier.height(16.dp))
        LazyColumn(
            Modifier
                .weight(1f)
                .pointerInput(Unit) {
                    // 一番上で下にスワイプしたら閉じる（簡易版）
                    var total = 0f
                    detectVerticalDragGestures(
                        onDragStart = { total = 0f },
                        onDragEnd = { if (total > 200f) onClose() },
                        onVerticalDrag = { _, dy -> total += dy },
                    )
                },
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            items(filtered, key = { it.key }) { app ->
                DrawerRow(
                    app = app,
                    isFavorite = app.key in favoriteKeys,
                    onLaunch = onLaunch,
                    onAppInfo = onAppInfo,
                    onToggleFavorite = onToggleFavorite,
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DrawerRow(
    app: AppEntry,
    isFavorite: Boolean,
    onLaunch: (AppEntry) -> Unit,
    onAppInfo: (AppEntry) -> Unit,
    onToggleFavorite: (AppEntry) -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Box {
        Text(
            app.label,
            color = if (isFavorite) Fg else Fg.copy(alpha = 0.85f),
            fontSize = 20.sp,
            fontWeight = if (isFavorite) FontWeight.Medium else FontWeight.Light,
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(onClick = { onLaunch(app) }, onLongClick = { menu = true })
                .padding(vertical = 10.dp),
        )
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
