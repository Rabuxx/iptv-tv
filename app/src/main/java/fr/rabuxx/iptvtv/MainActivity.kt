package fr.rabuxx.iptvtv

import android.os.Bundle
import android.view.inputmethod.InputMethodManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import fr.rabuxx.iptvtv.ui.AppTheme
import fr.rabuxx.iptvtv.ui.LivePlayerScreen
import fr.rabuxx.iptvtv.ui.LoginScreen
import fr.rabuxx.iptvtv.ui.MainScreen
import fr.rabuxx.iptvtv.ui.MovieDetailScreen
import fr.rabuxx.iptvtv.ui.ReplayScreen
import fr.rabuxx.iptvtv.ui.SeriesDetailScreen
import fr.rabuxx.iptvtv.ui.VodPlayerScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AppTheme {
                val vm: MainViewModel = viewModel()
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

                holder.SaveableStateProvider(route.key) {
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

                        is Route.LivePlayer -> {
                            if (route.channels.isEmpty()) {
                                LaunchedEffect(Unit) { vm.back() }
                            } else {
                                LivePlayerScreen(
                                    channels = route.channels,
                                    startIndex = route.index,
                                    urlFor = vm::liveUrl,
                                    onZapped = { vm.onZapped(route, it) },
                                )
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
        }
    }
}
