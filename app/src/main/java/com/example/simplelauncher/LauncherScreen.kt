package com.example.simplelauncher

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
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@Composable
fun LauncherScreen(
    data: LauncherData,
    homePressed: Int,
    repo: AppRepository,
    store: HomeStore,
    actions: LauncherActions,
) {
    var drawerOpen by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<AppEntry?>(null) }
    LaunchedEffect(homePressed) { drawerOpen = false }
    BackHandler(enabled = drawerOpen) { drawerOpen = false }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        HalftoneBackground(Modifier.fillMaxSize())
        HomePane(
            data = data,
            homePressed = homePressed,
            repo = repo,
            store = store,
            actions = actions,
            onRename = { renaming = it },
            onOpenDrawer = { drawerOpen = true },
        )
        AnimatedVisibility(
            visible = drawerOpen,
            enter = fadeIn() + scaleIn(initialScale = 0.96f),
            exit = fadeOut() + scaleOut(targetScale = 0.96f),
        ) {
            AppDrawer(
                data = data,
                repo = repo,
                actions = actions,
                onLaunch = { drawerOpen = false; actions.launch(it) },
                onRename = { renaming = it },
                onClose = { drawerOpen = false },
            )
        }
    }

    renaming?.let { app ->
        RenameDialog(
            app = app,
            current = data.labelOf(app),
            onSave = { actions.rename(app, it); renaming = null },
            onDismiss = { renaming = null },
        )
    }
}

/* ---------------- 共通部品 ---------------- */

/** 白黒アイコンを非同期で読み込む。読み込み済みなら即座に返す。 */
@Composable
fun rememberIcon(repo: AppRepository, app: AppEntry): ImageBitmap? {
    val icon by produceState(repo.cachedIcon(app), app.key) {
        if (value == null) value = withContext(Dispatchers.IO) { repo.loadIcon(app) }
    }
    return icon
}

@Composable
fun AppIcon(repo: AppRepository, app: AppEntry, size: Dp) {
    Box(
        Modifier.size(size).background(Color(0xFF222222), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        rememberIcon(repo, app)?.let {
            Image(it, contentDescription = app.label, modifier = Modifier.size(size * 0.65f))
        }
    }
}

/** 長押しで出すメニュー。項目は (表示文字, 処理) の組。 */
@Composable
fun AppMenu(expanded: Boolean, onDismiss: () -> Unit, items: List<Pair<String, () -> Unit>>) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        items.forEach { (label, action) ->
            DropdownMenuItem(text = { Text(label) }, onClick = { onDismiss(); action() })
        }
    }
}

@Composable
private fun RenameDialog(app: AppEntry, current: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("表示名") },
        text = {
            Column {
                Text("元の名前：${app.label}", fontSize = 12.sp, color = Dim)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = text, onValueChange = { text = it }, singleLine = true)
            }
        },
        confirmButton = { TextButton(onClick = { onSave(text) }) { Text("保存") } },
        dismissButton = { TextButton(onClick = { onSave("") }) { Text("元に戻す") } },
    )
}

/* ---------------- アプリ一覧 ---------------- */

@Composable
private fun AppDrawer(
    data: LauncherData,
    repo: AppRepository,
    actions: LauncherActions,
    onLaunch: (AppEntry) -> Unit,
    onRename: (AppEntry) -> Unit,
    onClose: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val filtered = remember(data.apps, data.labels, query) {
        val q = query.trim()
        if (q.isEmpty()) data.apps
        else data.apps.filter {
            data.labelOf(it).contains(q, ignoreCase = true) || it.label.contains(q, ignoreCase = true)
        }
    }
    LaunchedEffect(Unit) { focus.requestFocus() }

    // 一覧の一番上でさらに下へ引いたら閉じる。
    // スクロールと奪い合わないよう、スクロールが使い切れなかった分だけを数える。
    val gridState = rememberLazyGridState()
    val close by rememberUpdatedState(onClose)
    val closeOnPull = remember {
        object : NestedScrollConnection {
            var pulled = 0f
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput) {
                    pulled = if (available.y > 0f) pulled + available.y else 0f
                }
                return Offset.Zero
            }
            override suspend fun onPreFling(available: Velocity): Velocity {
                val atTop = !gridState.canScrollBackward
                if (pulled > 150f || (atTop && available.y > 3000f)) close()
                pulled = 0f
                return Velocity.Zero
            }
        }
    }

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
                state = gridState,
                modifier = Modifier.weight(1f).nestedScroll(closeOnPull),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                items(filtered, key = { it.key }) { app ->
                    AppCell(
                        app = app,
                        label = data.labelOf(app),
                        isFavorite = app.key in data.favoriteKeys,
                        inDock = app.key in data.dock,
                        repo = repo,
                        actions = actions,
                        onLaunch = onLaunch,
                        onRename = onRename,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Label("tap open · hold menu · ↓ close", Modifier.align(Alignment.CenterHorizontally))
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppCell(
    app: AppEntry,
    label: String,
    isFavorite: Boolean,
    inDock: Boolean,
    repo: AppRepository,
    actions: LauncherActions,
    onLaunch: (AppEntry) -> Unit,
    onRename: (AppEntry) -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Box {
        Column(
            Modifier
                .fillMaxWidth()
                .combinedClickable(onClick = { onLaunch(app) }, onLongClick = { menu = true }),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(contentAlignment = Alignment.TopEnd) {
                AppIcon(repo, app, 52.dp)
                if (isFavorite || inDock) Box(Modifier.size(8.dp).background(Accent, CircleShape))
            }
            Spacer(Modifier.height(6.dp))
            Text(
                label,
                color = Fg.copy(alpha = 0.7f), fontFamily = Mono, fontSize = 9.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 2.dp),
            )
        }
        AppMenu(
            expanded = menu,
            onDismiss = { menu = false },
            items = listOf(
                (if (isFavorite) "ホームから外す" else "ホームに追加") to { actions.toggleFavorite(app) },
                (if (inDock) "ドックから外す" else "ドックに追加") to { actions.toggleDock(app) },
                "表示名を変更" to { onRename(app) },
                "アプリ情報" to { actions.appInfo(app) },
            ),
        )
    }
}
