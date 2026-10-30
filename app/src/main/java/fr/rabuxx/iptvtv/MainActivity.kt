package fr.rabuxx.iptvtv

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
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
import fr.rabuxx.iptvtv.ui.ChannelSource
import fr.rabuxx.iptvtv.ui.Glass
import fr.rabuxx.iptvtv.ui.LivePlayerScreen
import fr.rabuxx.iptvtv.ui.LoginScreen
import fr.rabuxx.iptvtv.ui.MainScreen
import fr.rabuxx.iptvtv.ui.MovieDetailScreen
import fr.rabuxx.iptvtv.ui.MultiViewScreen
import fr.rabuxx.iptvtv.ui.ReplayScreen
import fr.rabuxx.iptvtv.ui.Screensaver
import fr.rabuxx.iptvtv.ui.SeriesDetailScreen
import fr.rabuxx.iptvtv.ui.SportScreen
import fr.rabuxx.iptvtv.ui.StatsScreen
import fr.rabuxx.iptvtv.ui.TextSecondary
import fr.rabuxx.iptvtv.ui.VodPlayerScreen

class MainActivity : ComponentActivity() {

    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Lancement depuis l'accueil Android TV (« À suivre ») ou une notification de rappel
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

                // Android 13+ : autorisation des notifications au premier rappel créé
                val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
                LaunchedEffect(state.askNotificationPermission) {
                    if (state.askNotificationPermission) {
                        vm.consumeNotificationPermission()
                        if (Build.VERSION.SDK_INT >= 33) {
                            runCatching { notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS) }
                        }
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

                    AnimatedVisibility(
                        visible = state.screensaverActive,
                        enter = fadeIn(tween(1_200)),
                        exit = fadeOut(tween(400)),
                    ) {
                        Screensaver(images = state.screensaverImages, wide = state.screensaverWide)
                    }
                }

                AppDialogHost(state.dialog, vm)
            }
        }
    }

    /** Toute touche réveille l'économiseur d'écran, sans agir sur l'écran qu'il recouvre. */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (vm.state.value.screensaverActive) {
            if (event.action == KeyEvent.ACTION_UP) vm.onUserActivity()
            return true
        }
        vm.onUserActivity()
        return super.dispatchKeyEvent(event)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        vm.handleDeepLink(intent.data)
    }

    /** Bande-annonce : ouverte dans l'app YouTube de la TV. */
    private fun openTrailer(key: String) {
        val intents = listOf(
            Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/watch?v=$key")),
            Intent(Intent.ACTION_VIEW, Uri.parse("vnd.youtube:$key")),
        )
        for (intent in intents) {
            try {
                startActivity(intent)
                return
            } catch (e: ActivityNotFoundException) {
                // on essaie la forme suivante
            }
        }
        Toast.makeText(this, "Installe l'app YouTube sur la TV pour voir la bande-annonce", Toast.LENGTH_LONG).show()
    }

    @Composable
    private fun Screen(route: Route, state: UiState) {
        when (route) {
            Route.Login -> LoginScreen(
                initial = state.savedCredentials,
                loading = state.loggingIn,
                error = state.loginError,
                onSubmit = vm::login,
                onPhoneForm = { form -> vm.onPhoneForm(fr.rabuxx.iptvtv.data.PhoneLinkMode.Login, form) },
            )

            Route.Main -> MainScreen(state = state, vm = vm)

            is Route.MovieDetail -> MovieDetailScreen(
                movie = route.movie,
                detail = state.movieInfo,
                resumeMs = remember(route) { vm.resumePosition(vm.movieKey(route.movie)) },
                isFavorite = state.favorites.has(route.movie),
                onToggleFavorite = { vm.toggleFavorite(route.movie) },
                onPlay = { ext, fromStart -> vm.playMovie(route.movie, ext, fromStart) },
                tmdb = state.tmdb.data?.takeIf { state.tmdbFor == vm.movieKey(route.movie) },
                onTrailer = ::openTrailer,
                onOpenMovie = { vm.openMovie(it) },
            )

            is Route.SeriesDetail -> SeriesDetailScreen(
                series = route.series,
                detail = state.seriesInfo,
                preferredEpisodeId = route.preferredEpisodeId,
                isFavorite = state.favorites.has(route.series),
                onToggleFavorite = { vm.toggleFavorite(route.series) },
                progressOf = { vm.progress(vm.episodeKey(it)) },
                onPlay = { vm.playEpisode(route.series, it) },
                tmdb = state.tmdb.data?.takeIf { state.tmdbFor == vm.seriesKey(route.series) },
                onTrailer = ::openTrailer,
                onOpenSeries = { vm.openSeries(it) },
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
                hasReminder = { state.hasReminder(it.channel, it.program) },
                onToggleReminder = { vm.toggleReminder(it.channel, it.program) },
                onMultiView = if (state.multiViewAvailable) ({ list: List<fr.rabuxx.iptvtv.data.Channel> -> vm.openMultiView(list) }) else null,
            )

            Route.Stats -> StatsScreen(
                summary = state.stats,
                period = state.statsPeriod,
                onPeriod = vm::setStatsPeriod,
                onReset = vm::resetStats,
            )

            is Route.MultiView -> {
                val epg by vm.epg.collectAsStateWithLifecycle()
                val sportLive = state.sport.data?.live.orEmpty().map { it.channel }.distinctBy { it.streamId }
                MultiViewScreen(
                    initial = route.initial,
                    maxSlots = vm.maxMultiStreams(),
                    sources = listOf(
                        ChannelSource("Sport en direct", sportLive),
                        ChannelSource("Favoris", state.favorites.channels),
                        ChannelSource("Récentes", state.recentChannels),
                        ChannelSource("Chaînes sport", state.sport.data?.channels.orEmpty()),
                        ChannelSource("Catégorie en cours", state.live.items),
                    ),
                    urlFor = vm::liveUrl,
                    epg = epg,
                    onRequestEpg = vm::requestEpg,
                    onWatched = vm::recordLive,
                )
            }

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
                            timeshiftUrl = vm::timeshiftUrl,
                            epg = epg,
                            onRequestEpg = vm::requestEpg,
                            onZapped = { vm.onZapped(route, it) },
                            onJumpToNumber = vm::jumpToChannelNumber,
                            onWatched = vm::recordLive,
                            onMultiView = if (state.multiViewAvailable) ({ ch: fr.rabuxx.iptvtv.data.Channel -> vm.openMultiView(listOf(ch)) }) else null,
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
                    onWatched = { seconds -> vm.recordVod(route, seconds) },
                )
            }
        }
    }
}
