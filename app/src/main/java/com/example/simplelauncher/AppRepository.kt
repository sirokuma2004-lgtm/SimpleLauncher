package com.example.simplelauncher

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.net.Uri
import android.os.Process
import android.provider.Settings
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import java.text.Collator
import java.util.concurrent.ConcurrentHashMap

data class AppEntry(
    val label: String,
    val packageName: String,
    val activityName: String,
) {
    val key: String get() = "$packageName/$activityName"
}

/** インストール済みアプリの取得・起動と、ホームに置くお気に入りの保存を担当する。 */
class AppRepository(private val context: Context) {

    private val launcherApps = context.getSystemService(LauncherApps::class.java)
    private val prefs = context.getSharedPreferences("launcher", Context.MODE_PRIVATE)

    fun loadApps(): List<AppEntry> {
        val collator = Collator.getInstance()
        return launcherApps.getActivityList(null, Process.myUserHandle())
            .filter { it.applicationInfo.packageName != context.packageName }
            .map {
                AppEntry(
                    label = it.label.toString(),
                    packageName = it.componentName.packageName,
                    activityName = it.componentName.className,
                )
            }
            .sortedWith { a, b -> collator.compare(a.label, b.label) }
    }

    private val icons = ConcurrentHashMap<String, ImageBitmap>()

    fun cachedIcon(app: AppEntry): ImageBitmap? = icons[app.key]

    /** アイコンを読み込む。重いので IO スレッドから呼ぶこと。 */
    fun loadIcon(app: AppEntry): ImageBitmap? = icons[app.key] ?: runCatching {
        context.packageManager
            .getActivityIcon(ComponentName(app.packageName, app.activityName))
            .toBitmap(128, 128)
            .asImageBitmap()
    }.getOrNull()?.also { icons[app.key] = it }

    fun launch(app: AppEntry) {
        runCatching {
            launcherApps.startMainActivity(
                ComponentName(app.packageName, app.activityName),
                Process.myUserHandle(), null, null,
            )
        }
    }

    fun openAppInfo(app: AppEntry) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            .setData(Uri.parse("package:${app.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }

    fun loadFavorites(): List<String> =
        prefs.getString(KEY_FAVORITES, "")!!.split("\n").filter { it.isNotBlank() }

    fun saveFavorites(keys: List<String>) {
        prefs.edit().putString(KEY_FAVORITES, keys.joinToString("\n")).apply()
    }

    companion object {
        private const val KEY_FAVORITES = "favorites"
        const val MAX_FAVORITES = 7
    }
}
