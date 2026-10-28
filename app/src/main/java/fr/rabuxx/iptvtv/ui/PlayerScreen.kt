package fr.rabuxx.iptvtv.ui

import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackGroup
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import fr.rabuxx.iptvtv.data.Channel
import fr.rabuxx.iptvtv.data.USER_AGENT
import kotlinx.coroutines.delay
import java.util.Locale

// ============================== Commun ==============================

@OptIn(UnstableApi::class)
@Composable
private fun rememberIptvPlayer(): ExoPlayer {
    val context = LocalContext.current
    return remember {
        val http = DefaultHttpDataSource.Factory()
            .setUserAgent(USER_AGENT)
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(30_000)
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(http))
            .setSeekBackIncrementMs(10_000)
            .setSeekForwardIncrementMs(10_000)
            .build()
            .apply {
                playWhenReady = true
                // Piste audio française par défaut quand elle existe
                trackSelectionParameters = trackSelectionParameters.buildUpon()
                    .setPreferredAudioLanguages("fr", "fre", "fra")
                    .build()
            }
    }
}

@Composable
private fun VideoSurface(player: ExoPlayer) {
    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                useController = false
                isFocusable = false
                isFocusableInTouchMode = false
                keepScreenOn = true
                this.player = player
            }
        },
        modifier = Modifier.fillMaxSize(),
    )
}

/** Boîte plein écran qui capte les touches de la télécommande. */
@Composable
private fun KeyCatcher(focus: FocusRequester, onKey: (KeyEvent) -> Boolean, content: @Composable () -> Unit) {
    LaunchedEffect(Unit) { focus.requestFocus() }
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onKeyEvent { ev -> ev.type == KeyEventType.KeyDown && onKey(ev) }
            .focusRequester(focus)
            .focusable(),
    ) { content() }
}

@Composable
private fun StatusText(text: String, modifier: Modifier) {
    Text(
        text,
        modifier = modifier.background(Glass, RoundedCornerShape(14.dp)).padding(horizontal = 18.dp, vertical = 10.dp),
        color = Color(0xE6FFFFFF),
        fontSize = 15.sp,
    )
}

@Composable
private fun ErrorBox(message: String, hint: String?, modifier: Modifier) {
    Column(
        modifier.background(Glass, RoundedCornerShape(20.dp)).padding(horizontal = 28.dp, vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(message, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        if (hint != null) Text(hint, color = TextSecondary, fontSize = 12.sp)
    }
}

// ============================== Direct ==============================

private const val LIVE_MAX_RETRIES = 5

/**
 * Lecteur TV en direct.
 * ↑ / ↓ (ou CH+ / CH−) : zapper · OK : afficher le bandeau · Retour : revenir.
 * Chaque chaîne est lue en MPEG-TS ; en cas d'échec, nouvel essai en HLS, puis reconnexions automatiques.
 */
@OptIn(UnstableApi::class)
@Composable
fun LivePlayerScreen(
    channels: List<Channel>,
    startIndex: Int,
    urlFor: (Channel, String) -> String,
    onZapped: (Int) -> Unit,
) {
    val player = rememberIptvPlayer()
    var index by remember { mutableIntStateOf(startIndex.coerceIn(channels.indices)) }
    var useHls by remember { mutableStateOf(false) }
    var showInfo by remember { mutableStateOf(true) }
    var buffering by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var retries by remember { mutableIntStateOf(0) }
    var pendingRetry by remember { mutableStateOf(false) }
    var retryTick by remember { mutableIntStateOf(0) }

    DisposableEffect(Unit) {
        fun scheduleRetry() {
            if (retries < LIVE_MAX_RETRIES) {
                retries++
                pendingRetry = true
            } else {
                error = "Cette chaîne ne répond pas"
            }
        }
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                buffering = state == Player.STATE_BUFFERING
                when (state) {
                    Player.STATE_READY -> retries = 0
                    // Un flux direct qui « se termine » est un flux coupé : on se reconnecte
                    Player.STATE_ENDED -> scheduleRetry()
                }
            }

            override fun onPlayerError(e: PlaybackException) {
                when {
                    e.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW -> {
                        player.seekToDefaultPosition()
                        player.prepare()
                    }
                    !useHls && retries == 0 -> useHls = true
                    else -> scheduleRetry()
                }
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    val channel = channels[index]

    LaunchedEffect(pendingRetry) {
        if (pendingRetry) {
            delay(3_000)
            pendingRetry = false
            retryTick++
        }
    }

    LaunchedEffect(index, useHls, retryTick) {
        error = null
        val ext = if (useHls) "m3u8" else "ts"
        val item = MediaItem.Builder()
            .setUri(urlFor(channel, ext))
            .apply { if (useHls) setMimeType(MimeTypes.APPLICATION_M3U8) }
            .build()
        player.setMediaItem(item)
        player.prepare()
    }

    LaunchedEffect(index) { onZapped(index) }

    LaunchedEffect(index, showInfo) {
        if (showInfo) {
            delay(5_000)
            showInfo = false
        }
    }

    fun zap(delta: Int) {
        if (channels.size < 2) return
        index = (index + delta).mod(channels.size)
        useHls = false
        retries = 0
        pendingRetry = false
        showInfo = true
    }

    val focus = remember { FocusRequester() }
    KeyCatcher(focus, onKey = { ev ->
        when (ev.key) {
            Key.DirectionUp, Key.ChannelDown -> { zap(-1); true }
            Key.DirectionDown, Key.ChannelUp -> { zap(+1); true }
            Key.DirectionCenter, Key.Enter, Key.NumPadEnter, Key.Info -> { showInfo = !showInfo; true }
            else -> false
        }
    }) {
        Box(Modifier.fillMaxSize()) {
            VideoSurface(player)

            when {
                error != null -> ErrorBox(error!!, "↑ / ↓ pour changer de chaîne", Modifier.align(Alignment.Center))
                pendingRetry || (retries > 0 && buffering) -> StatusText("Reconnexion…", Modifier.align(Alignment.Center))
                buffering -> StatusText("Chargement…", Modifier.align(Alignment.Center))
            }

            AnimatedVisibility(
                visible = showInfo,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.BottomStart),
            ) {
                Row(
                    Modifier
                        .padding(start = 48.dp, bottom = 36.dp)
                        .background(Glass, RoundedCornerShape(18.dp))
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Box(
                        Modifier.size(width = 88.dp, height = 50.dp).background(Color(0x1AFFFFFF), RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (channel.icon != null) {
                            AsyncImage(
                                model = channel.icon,
                                contentDescription = null,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize().padding(8.dp),
                            )
                        }
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            listOfNotNull(channel.number.takeIf { it.isNotBlank() }, "${index + 1} / ${channels.size}").joinToString("  ·  "),
                            color = TextSecondary,
                            fontSize = 12.sp,
                        )
                        Text(channel.name, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("↑ ↓ pour zapper", color = TextSecondary, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

// ============================== Films / épisodes / replay ==============================

private const val VOD_MAX_RETRIES = 3
private const val UP_NEXT_BEFORE_END_MS = 20_000L
private const val UP_NEXT_COUNTDOWN_S = 10

/**
 * Lecteur VOD façon tvOS.
 * OK / ⏯ : pause · ← / → : −10 s / +10 s · ↑ : commandes · ↓ : audio et sous-titres · Retour : quitter.
 * La position est mémorisée à la sortie ; en fin d'épisode, l'épisode suivant est proposé puis lancé.
 */
@OptIn(UnstableApi::class)
@Composable
fun VodPlayerScreen(
    url: String,
    title: String,
    subtitle: String?,
    startPositionMs: Long,
    nextLabel: String?,
    onNext: (() -> Unit)?,
    onSavePosition: (positionMs: Long, durationMs: Long) -> Unit,
    onFinished: () -> Unit,
) {
    val player = rememberIptvPlayer()
    var playing by remember { mutableStateOf(true) }
    var buffering by remember { mutableStateOf(true) }
    var ended by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var retries by remember { mutableIntStateOf(0) }
    var pendingRetry by remember { mutableStateOf(false) }
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var controlsVisible by remember { mutableStateOf(true) }
    var interaction by remember { mutableIntStateOf(0) }
    var tracks by remember { mutableStateOf(Tracks.EMPTY) }
    var tracksOpen by remember { mutableStateOf(false) }
    var upNextDismissed by remember { mutableStateOf(false) }
    var countdown by remember { mutableIntStateOf(UP_NEXT_COUNTDOWN_S) }

    DisposableEffect(Unit) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                playing = isPlaying
            }

            override fun onPlaybackStateChanged(state: Int) {
                buffering = state == Player.STATE_BUFFERING
                if (state == Player.STATE_READY) retries = 0
                if (state == Player.STATE_ENDED) ended = true
            }

            override fun onTracksChanged(newTracks: Tracks) {
                tracks = newTracks
            }

            override fun onPlayerError(e: PlaybackException) {
                // Coupure réseau : on relance depuis la position courante
                if (retries < VOD_MAX_RETRIES) {
                    retries++
                    pendingRetry = true
                } else {
                    error = "Lecture impossible (${e.errorCodeName})"
                }
            }
        }
        player.addListener(listener)
        onDispose {
            val dur = player.duration.takeIf { it != C.TIME_UNSET && it > 0 } ?: 0L
            onSavePosition(player.currentPosition, dur)
            player.removeListener(listener)
            player.release()
        }
    }

    LaunchedEffect(url) {
        player.setMediaItem(MediaItem.fromUri(url), startPositionMs.coerceAtLeast(0))
        player.prepare()
    }

    LaunchedEffect(pendingRetry) {
        if (pendingRetry) {
            delay(2_000)
            pendingRetry = false
            player.prepare()
        }
    }

    // Rafraîchit la barre de progression
    LaunchedEffect(Unit) {
        while (true) {
            position = player.currentPosition
            duration = player.duration.takeIf { it != C.TIME_UNSET && it > 0 } ?: 0L
            delay(500)
        }
    }

    // Les commandes disparaissent après 5 s d'inactivité pendant la lecture
    LaunchedEffect(controlsVisible, interaction, playing) {
        if (controlsVisible && playing) {
            delay(5_000)
            controlsVisible = false
        }
    }

    // ---- Épisode suivant ----
    val nearEnd = duration > 0 && position > 0 && duration - position <= UP_NEXT_BEFORE_END_MS
    val upNextVisible = onNext != null && nextLabel != null && !upNextDismissed && (ended || nearEnd)
    val upNextFocus = remember { FocusRequester() }
    val focus = remember { FocusRequester() }

    LaunchedEffect(upNextVisible) {
        if (upNextVisible) {
            withFrameNanos { }
            runCatching { upNextFocus.requestFocus() }
        }
    }

    LaunchedEffect(ended) {
        if (!ended) return@LaunchedEffect
        if (onNext == null || upNextDismissed) {
            onFinished()
            return@LaunchedEffect
        }
        for (s in UP_NEXT_COUNTDOWN_S downTo 1) {
            countdown = s
            delay(1_000)
        }
        onNext()
    }

    // Retour : ferme d'abord le panneau des pistes, puis la proposition d'épisode suivant
    BackHandler(enabled = tracksOpen) {
        tracksOpen = false
        runCatching { focus.requestFocus() }
    }
    BackHandler(enabled = upNextVisible && !ended && !tracksOpen) {
        upNextDismissed = true
        runCatching { focus.requestFocus() }
    }

    fun touch() {
        controlsVisible = true
        interaction++
    }

    KeyCatcher(focus, onKey = { ev ->
        when {
            ev.key == Key.MediaPlayPause -> {
                if (player.isPlaying) player.pause() else player.play()
                touch(); true
            }
            // Panneau des pistes ouvert : les flèches servent à y naviguer
            tracksOpen -> false
            else -> when (ev.key) {
                Key.DirectionCenter, Key.Enter, Key.NumPadEnter, Key.Spacebar -> {
                    if (player.isPlaying) player.pause() else player.play()
                    touch(); true
                }
                Key.MediaPlay -> { player.play(); touch(); true }
                Key.MediaPause -> { player.pause(); touch(); true }
                Key.DirectionLeft, Key.MediaRewind -> { player.seekBack(); position = player.currentPosition; touch(); true }
                Key.DirectionRight, Key.MediaFastForward -> { player.seekForward(); position = player.currentPosition; touch(); true }
                Key.DirectionDown -> { tracksOpen = true; touch(); true }
                Key.DirectionUp, Key.Info -> { touch(); true }
                else -> false
            }
        }
    }) {
        Box(Modifier.fillMaxSize()) {
            VideoSurface(player)

            when {
                error != null -> ErrorBox(error!!, "Retour pour revenir à la fiche", Modifier.align(Alignment.Center))
                pendingRetry || (retries > 0 && buffering) -> StatusText("Reconnexion…", Modifier.align(Alignment.Center))
                buffering -> StatusText("Chargement…", Modifier.align(Alignment.Center))
                !playing && !ended -> Box(
                    // Icône de pause au centre, comme sur Apple TV
                    Modifier.align(Alignment.Center).size(80.dp).background(Glass, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("❚❚", fontSize = 28.sp, fontWeight = FontWeight.Bold)
                }
            }

            AnimatedVisibility(
                visible = (controlsVisible || !playing) && !tracksOpen,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.fillMaxSize(),
            ) {
                Box(Modifier.fillMaxSize()) {
                    // Titre en haut à gauche
                    Column(
                        Modifier
                            .align(Alignment.TopStart)
                            .fillMaxWidth()
                            .background(Brush.verticalGradient(listOf(Color(0xB3000000), Color.Transparent)))
                            .padding(start = 48.dp, end = 48.dp, top = 28.dp, bottom = 44.dp),
                    ) {
                        Text(title, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (subtitle != null) {
                            Text(subtitle, color = Color(0xCCFFFFFF), fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }

                    // Barre de progression en bas
                    Column(
                        Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xB3000000))))
                            .padding(start = 48.dp, end = 48.dp, top = 44.dp, bottom = 28.dp),
                    ) {
                        val fraction = if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .background(Color(0x4DFFFFFF), CircleShape),
                        ) {
                            Box(
                                Modifier
                                    .fillMaxWidth(fraction)
                                    .fillMaxHeight()
                                    .background(Color.White, CircleShape),
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(formatClock(position), color = Color(0xCCFFFFFF), fontSize = 13.sp)
                            Text("↓  Audio et sous-titres", color = Color(0x99FFFFFF), fontSize = 12.sp)
                            Text(
                                if (duration > 0) "-" + formatClock(duration - position) else "",
                                color = Color(0xCCFFFFFF),
                                fontSize = 13.sp,
                            )
                        }
                    }
                }
            }

            if (tracksOpen) {
                TracksPanel(player, tracks, Modifier.align(Alignment.BottomCenter))
            }

            if (upNextVisible && !tracksOpen) {
                Column(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 48.dp, bottom = 72.dp)
                        .width(300.dp)
                        .background(Glass, RoundedCornerShape(18.dp))
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("Épisode suivant", color = TextSecondary, fontSize = 12.sp)
                    Text(nextLabel!!, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    ActionButton(
                        label = if (ended) "▶  Lecture dans $countdown s" else "▶  Lire maintenant",
                        onClick = { onNext?.invoke() },
                        modifier = Modifier.focusRequester(upNextFocus),
                    )
                }
            }
        }
    }
}

// ============================== Pistes audio / sous-titres ==============================

private data class TrackOption(val label: String, val group: TrackGroup, val index: Int, val selected: Boolean)

@OptIn(UnstableApi::class)
private fun trackOptions(tracks: Tracks, type: Int): List<TrackOption> =
    tracks.groups.filter { it.type == type }.flatMap { g ->
        (0 until g.length).filter { g.isTrackSupported(it) }.map { i ->
            TrackOption(trackLabel(g.getTrackFormat(i), type, i), g.mediaTrackGroup, i, g.isTrackSelected(i))
        }
    }

private fun trackLabel(format: Format, type: Int, index: Int): String {
    val language = format.language
        ?.takeIf { it.isNotBlank() && it != "und" }
        ?.let { Locale.forLanguageTag(it).getDisplayLanguage(Locale.FRANCE) }
        ?.takeIf { it.isNotBlank() }
        ?.replaceFirstChar { it.uppercase() }
    val name = format.label?.takeIf { it.isNotBlank() && !it.equals(language, ignoreCase = true) }
    val channels = if (type == C.TRACK_TYPE_AUDIO) {
        when (format.channelCount) {
            1 -> "mono"
            2 -> "stéréo"
            6 -> "5.1"
            8 -> "7.1"
            else -> null
        }
    } else {
        null
    }
    val base = listOfNotNull(language, name).joinToString(" · ").ifBlank { "Piste ${index + 1}" }
    return listOfNotNull(base, channels).joinToString(" · ")
}

@Composable
private fun TracksPanel(player: ExoPlayer, tracks: Tracks, modifier: Modifier) {
    val audio = trackOptions(tracks, C.TRACK_TYPE_AUDIO)
    val text = trackOptions(tracks, C.TRACK_TYPE_TEXT)
    val textOff = text.none { it.selected }
    val firstFocus = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { firstFocus.requestFocus() }
    }

    fun select(option: TrackOption, type: Int) {
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(type, false)
            .setOverrideForType(TrackSelectionOverride(option.group, option.index))
            .build()
    }

    Column(
        modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xE6000000))))
            .padding(top = 60.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text("Audio", modifier = Modifier.padding(start = 48.dp), fontSize = 14.sp, fontWeight = FontWeight.Bold)
        LazyRow(
            contentPadding = PaddingValues(horizontal = 48.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (audio.isEmpty()) {
                item {
                    ChipButton("Piste par défaut", selected = true, onClick = {}, modifier = Modifier.focusRequester(firstFocus))
                }
            }
            itemsIndexed(audio) { i, option ->
                ChipButton(
                    label = option.label,
                    selected = option.selected,
                    onClick = { select(option, C.TRACK_TYPE_AUDIO) },
                    modifier = if (i == 0) Modifier.focusRequester(firstFocus) else Modifier,
                )
            }
        }

        Text("Sous-titres", modifier = Modifier.padding(start = 48.dp), fontSize = 14.sp, fontWeight = FontWeight.Bold)
        LazyRow(
            contentPadding = PaddingValues(horizontal = 48.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                ChipButton(
                    label = "Désactivés",
                    selected = textOff,
                    onClick = {
                        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                            .build()
                    },
                )
            }
            itemsIndexed(text) { _, option ->
                ChipButton(
                    label = option.label,
                    selected = option.selected,
                    onClick = { select(option, C.TRACK_TYPE_TEXT) },
                )
            }
        }
        Text(
            "Retour pour fermer",
            modifier = Modifier.padding(start = 48.dp, top = 4.dp),
            color = TextSecondary,
            fontSize = 11.sp,
        )
    }
}
