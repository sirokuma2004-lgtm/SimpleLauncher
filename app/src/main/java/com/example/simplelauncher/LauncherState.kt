package com.example.simplelauncher

/** 画面に渡すデータ一式。 */
data class LauncherData(
    val apps: List<AppEntry>,
    val favoriteKeys: List<String>,
    val dock: List<String?>,
    val dockMode: DockMode,
    val labels: Map<String, String>,
) {
    val byKey: Map<String, AppEntry> = apps.associateBy { it.key }

    /** ユーザーが付けた表示名。なければアプリ本来の名前。 */
    fun labelOf(app: AppEntry): String = labels[app.key] ?: app.label

    val favorites: List<AppEntry> get() = favoriteKeys.mapNotNull { byKey[it] }
    val dockApps: List<AppEntry?> get() = dock.map { key -> key?.let { byKey[it] } }
}

/** 画面から呼ぶ操作一式。 */
class LauncherActions(
    val launch: (AppEntry) -> Unit,
    val appInfo: (AppEntry) -> Unit,
    val toggleFavorite: (AppEntry) -> Unit,
    val toggleDock: (AppEntry) -> Unit,
    val rename: (AppEntry, String) -> Unit,
    val setDockMode: (DockMode) -> Unit,
)
