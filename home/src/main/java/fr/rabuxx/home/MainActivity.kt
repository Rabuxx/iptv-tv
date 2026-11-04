package fr.rabuxx.home

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    private lateinit var catalog: AppCatalog
    private var apps by mutableStateOf<List<AppEntry>>(emptyList())
    private var shelf by mutableStateOf<List<ShelfItem>>(emptyList())
    private var weather by mutableStateOf<Weather?>(null)
    private var weatherLoadedAt = 0L
    private var launchedAsHome by mutableStateOf(false)

    // Une app installée, mise à jour ou supprimée : la rangée se met à jour toute seule
    private val packagesChanged = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = reload()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        catalog = AppCatalog(applicationContext)
        launchedAsHome = isHomeIntent(intent)
        reload()

        ContextCompat.registerReceiver(
            this,
            packagesChanged,
            IntentFilter().apply {
                addAction(Intent.ACTION_PACKAGE_ADDED)
                addAction(Intent.ACTION_PACKAGE_REMOVED)
                addAction(Intent.ACTION_PACKAGE_CHANGED)
                addDataScheme("package")
            },
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )

        setContent {
            // En accueil, Retour ne doit mener nulle part. Ouverte comme une app, elle se ferme normalement.
            BackHandler(enabled = launchedAsHome) {}
            HomeScreen(
                apps = apps,
                shelf = shelf,
                weather = weather,
                onOpen = { catalog.launch(it) },
                onOpenContent = { catalog.openContent(it.uri) },
                onSettings = { catalog.openSettings() },
            )
        }
    }

    // Au retour sur l'accueil, l'historique de Rabux TV a pu changer
    override fun onResume() {
        super.onResume()
        lifecycleScope.launch {
            shelf = withContext(Dispatchers.IO) { TopShelf.load(applicationContext) }
        }
        // L'accueil reste ouvert des jours : la météo se rafraîchit toutes les 30 min, à chaque retour dessus
        val now = System.currentTimeMillis()
        if (now - weatherLoadedAt > WEATHER_REFRESH_MS) {
            lifecycleScope.launch {
                val fresh = withContext(Dispatchers.IO) { WeatherApi.load() }
                if (fresh != null) {
                    weather = fresh
                    weatherLoadedAt = now
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        launchedAsHome = isHomeIntent(intent)
    }

    override fun onDestroy() {
        unregisterReceiver(packagesChanged)
        super.onDestroy()
    }

    private companion object {
        const val WEATHER_REFRESH_MS = 30 * 60 * 1000L
    }

    private fun isHomeIntent(intent: Intent?) = intent?.hasCategory(Intent.CATEGORY_HOME) == true

    private fun reload() {
        lifecycleScope.launch {
            apps = withContext(Dispatchers.Default) { catalog.load() }
        }
    }
}
