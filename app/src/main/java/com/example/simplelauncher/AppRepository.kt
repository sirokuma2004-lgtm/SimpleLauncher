package com.example.simplelauncher

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
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

    /**
     * アイコンを読み込む。重いので IO スレッドから呼ぶこと。
     * 白黒への変換は読み込み時に一度だけ済ませ、描画のたびに色を計算しない。
     */
    fun loadIcon(app: AppEntry): ImageBitmap? = icons[app.key] ?: runCatching {
        val src = context.packageManager
            .getActivityIcon(ComponentName(app.packageName, app.activityName))
            .toBitmap(ICON_PX, ICON_PX)
        val gray = Bitmap.createBitmap(ICON_PX, ICON_PX, Bitmap.Config.ARGB_8888)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(0f) })
        }
        Canvas(gray).drawBitmap(src, 0f, 0f, paint)
        gray.prepareToDraw()
        gray.asImageBitmap()
    }.getOrNull()?.also { icons[app.key] = it }

    /** 一覧を開く前に、全アプリのアイコンを読み込んでおく。 */
    fun preloadIcons(apps: List<AppEntry>) = apps.forEach { loadIcon(it) }

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
        private const val ICON_PX = 108
    }
}
