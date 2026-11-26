package com.example.simplelauncher

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    private lateinit var repo: AppRepository
    private lateinit var store: HomeStore

    private val apps = mutableStateOf<List<AppEntry>>(emptyList())
    private val favorites = mutableStateOf<List<String>>(emptyList())
    private val dock = mutableStateOf<List<String?>>(List(DOCK_SIZE) { null })
    private val dockMode = mutableStateOf(DockMode.ICON)
    private val labels = mutableStateOf<Map<String, String>>(emptyMap())

    /** ホームボタンが押されるたびに増える。一覧や編集モードを閉じる合図に使う。 */
    private val homePressed = mutableStateOf(0)

    private val actions by lazy {
        LauncherActions(
            launch = repo::launch,
            appInfo = repo::openAppInfo,
            toggleFavorite = ::toggleFavorite,
            toggleDock = ::toggleDock,
            rename = ::rename,
            setDockMode = { dockMode.value = it; store.saveDockMode(it) },
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        repo = AppRepository(this)
        store = HomeStore(this)
        favorites.value = repo.loadFavorites()
        dock.value = store.loadDock()
        dockMode.value = store.loadDockMode()
        labels.value = store.loadLabels()

        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = Accent, surface = Card)) {
                LauncherScreen(
                    data = LauncherData(apps.value, favorites.value, dock.value, dockMode.value, labels.value),
                    homePressed = homePressed.value,
                    repo = repo,
                    store = store,
                    actions = actions,
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        lifecycleScope.launch {
            val list = withContext(Dispatchers.IO) { repo.loadApps() }
            apps.value = list
            // スクロール中にアイコンを作らずに済むよう、先に用意しておく
            withContext(Dispatchers.IO) { repo.preloadIcons(list) }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        homePressed.value++
    }

    private fun toggleFavorite(app: AppEntry) {
        val current = favorites.value
        val next = when {
            app.key in current -> current - app.key
            current.size >= AppRepository.MAX_FAVORITES -> {
                toast("お気に入りは ${AppRepository.MAX_FAVORITES} 件までです"); current
            }
            else -> current + app.key
        }
        favorites.value = next
        repo.saveFavorites(next)
    }

    private fun toggleDock(app: AppEntry) {
        val current = dock.value
        val next = if (app.key in current) {
            current.map { if (it == app.key) null else it }
        } else {
            val free = current.indexOfFirst { it == null }
            if (free < 0) { toast("ドックがいっぱいです"); return }
            current.toMutableList().also { it[free] = app.key }
        }
        dock.value = next
        store.saveDock(next)
    }

    private fun rename(app: AppEntry, label: String) {
        val trimmed = label.trim()
        val next = if (trimmed.isEmpty() || trimmed == app.label) {
            labels.value - app.key
        } else {
            labels.value + (app.key to trimmed)
        }
        labels.value = next
        store.saveLabels(next)
    }

    private fun toast(text: String) = Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
}
