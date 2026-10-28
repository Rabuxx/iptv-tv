package fr.rabuxx.iptvtv

import android.content.Intent
import android.os.Bundle
import android.view.inputmethod.InputMethodManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import fr.rabuxx.iptvtv.ui.AppDialogHost
import fr.rabuxx.iptvtv.ui.AppTheme
import fr.rabuxx.iptvtv.ui.Glass
import fr.rabuxx.iptvtv.ui.LivePlayerScreen
import fr.rabuxx.iptvtv.ui.LoginScreen
import fr.rabuxx.iptvtv.ui.MainScreen
import fr.rabuxx.iptvtv.ui.MovieDetailScreen
import fr.rabuxx.iptvtv.ui.ReplayScreen
import fr.rabuxx.iptvtv.ui.SeriesDetailScreen
import fr.rabuxx.iptvtv.ui.SportScreen
import fr.rabuxx.iptvtv.ui.TextSecondary
import fr.rabuxx.iptvtv.ui.VodPlayerScreen

class MainActivity : ComponentActivity() {

    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Lancement depuis la rangée « À suivre » de l'accueil Android TV
        if (savedInstanceState == null) vm.handleDeepLink(intent?.data)

        setContent {
            AppTheme {
                val state by vm.state.collectAsStateWithLifecycle()
                // Conserve l'état de chaque écran (défilement, saison…) quand on y revient
                val holder = rememberSaveableStateHolder()
                val route = state.route

                BackHandler(enabled = state.stack.size > 1) { vm.back() }

                // Filet de sécurité : le clavier ne survit jamais à un changement d'écran (lancement du direct…)
                val view = LocalView.current
                LaunchedEffect(route.key) {
                    view.context.getSystemService(InputMethodManager::class.java)
                        ?.hideSoftInputFromWindow(view.windowToken, 0)
                }

                // Fin du minuteur de veille : on ferme l'app (les lecteurs enregistrent leur position en se fermant)
                LaunchedEffect(state.sleepFired) {
                    if (state.sleepFired) {
                        vm.consumeSleepFired()
                        finish()
                    }
                }

                Box(
                    // Pendant l'avertissement de veille, n'importe quelle touche annule l'arrêt
                    Modifier.fillMaxSize().onPreviewKeyEvent { ev ->
                        if (state.sleepWarning != null && ev.type == KeyEventType.KeyDown) {
                            vm.cancelSleepTimer()
                            true
                        } else {
                            false
                        }
                    },
                ) {
                    holder.SaveableStateProvider(route.key) {
                        Screen(route, state)
                    }

                    state.sleepWarning?.let { seconds ->
                        Column(
                            Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = 28.dp)
                                .background(Glass, RoundedCornerShape(18.dp))
                                .padding(horizontal = 24.dp, vertical = 14.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text("Mise en veille dans $seconds s", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            Text("Appuie sur une touche pour continuer à regarder", color = TextSecondary, fontSize = 13.sp)
                        }
                    }
                }

                AppDialogHost(state.dialog, vm)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        vm.handleDeepLink(intent.data)
    }

    @androidx.compose.runtime.Composable
    private fun Screen(route: Route, state: UiState) {
        when (route) {
            Route.Login -> LoginScreen(
                initial = state.savedCredentials,
                loading = state.loggingIn,
                error = state.loginError,
                onSubmit = vm::login,
            )

            Route.Main -> MainScreen(state = state, vm = vm)

            is Route.MovieDetail -> MovieDetailScreen(
                movie = route.movie,
                detail = state.movieInfo,
                resumeMs = remember(route) { vm.resumePosition(vm.movieKey(route.movie)) },
                isFavorite = state.favorites.has(route.movie),
                onToggleFavorite = { vm.toggleFavorite(route.movie) },
                onPlay = { ext, fromStart -> vm.playMovie(route.movie, ext, fromStart) },
            )

            is Route.SeriesDetail -> SeriesDetailScreen(
                series = route.series,
                detail = state.seriesInfo,
                preferredEpisodeId = route.preferredEpisodeId,
                isFavorite = state.favorites.has(route.series),
                onToggleFavorite = { vm.toggleFavorite(route.series) },
                progressOf = { vm.progress(vm.episodeKey(it)) },
                onPlay = { vm.playEpisode(route.series, it) },
            )

            is Route.Replay -> ReplayScreen(
                channel = route.channel,
                detail = state.replay,
                onPlay = { vm.playReplay(route.channel, it) },
            )

            Route.Sport -> SportScreen(
                detail = state.sport,
                onPlay = { channels, index -> vm.playChannels(channels, index) },
                onReload = vm::reloadSport,
            )

            is Route.LivePlayer -> {
                if (route.channels.isEmpty()) {
                    LaunchedEffect(Unit) { vm.back() }
                } else {
                    val epg by vm.epg.collectAsStateWithLifecycle()
                    // Nouvelle liste (saisie d'un numéro) : on recrée le lecteur
                    key(route) {
                        LivePlayerScreen(
                            channels = route.channels,
                            startIndex = route.index,
                            urlFor = vm::liveUrl,
                            epg = epg,
                            onRequestEpg = vm::requestEpg,
                            onZapped = { vm.onZapped(route, it) },
                            onJumpToNumber = vm::jumpToChannelNumber,
                        )
                    }
                }
            }

            is Route.VodPlayer -> {
                val next = remember(route, state.seriesInfo) { vm.nextEpisode(route.series, route.episode) }
                VodPlayerScreen(
                    url = route.url,
                    title = route.title,
                    subtitle = route.subtitle,
                    startPositionMs = remember(route) {
                        if (route.fromStart) 0L else vm.resumePosition(route.resumeKey)
                    },
                    nextLabel = next?.let { "S${it.season} · É${it.number} · ${it.title}" },
                    onNext = next?.let { { vm.playNext(route) } },
                    onSavePosition = { pos, dur -> vm.savePosition(route, pos, dur) },
                    onFinished = { vm.back() },
                )
            }
        }
    }
}
