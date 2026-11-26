package com.example.simplelauncher

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf

class MainActivity : ComponentActivity() {

    private lateinit var repo: AppRepository
    private val apps = mutableStateOf<List<AppEntry>>(emptyList())
    private val favorites = mutableStateOf<List<String>>(emptyList())
    /** ホームボタンが押されるたびに増える。ドロワーを閉じる合図に使う。 */
    private val homePressed = mutableStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        repo = AppRepository(this)
        favorites.value = repo.loadFavorites()

        setContent {
            LauncherScreen(
                apps = apps.value,
                favoriteKeys = favorites.value,
                homePressed = homePressed.value,
                onLaunch = repo::launch,
                onAppInfo = repo::openAppInfo,
                onToggleFavorite = ::toggleFavorite,
            )
        }
    }

    override fun onResume() {
        super.onResume()
        // アプリの追加・削除を反映するため、戻ってくるたびに読み直す
        apps.value = repo.loadApps()
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        homePressed.value++
    }

    private fun toggleFavorite(app: AppEntry) {
        val current = favorites.value
        val next = when {
            app.key in current -> current - app.key
            current.size >= AppRepository.MAX_FAVORITES -> current
            else -> current + app.key
        }
        favorites.value = next
        repo.saveFavorites(next)
    }
}
