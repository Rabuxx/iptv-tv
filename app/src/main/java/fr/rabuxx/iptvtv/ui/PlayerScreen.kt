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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.Surface
import fr.rabuxx.iptvtv.data.EpgProgram
import fr.rabuxx.iptvtv.data.nowPlaying
import fr.rabuxx.iptvtv.data.progress
import fr.rabuxx.iptvtv.data.upNext
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
internal fun rememberIptvPlayer(): ExoPlayer {
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

/** En dessous de cette durée, une pause reprend simplement là où elle s'est arrêtée. */
private const val SHORT_PAUSE_S = 20L

/**
 * Lecteur TV en direct.
 * ↑ / ↓ (ou CH+ / CH−) : zapper · ← : chaîne précédente · → : liste des chaînes · 0-9 : numéro de chaîne
 * OK : barre de commandes (pause, retour en arrière, direct, multi-écran) · Retour : revenir.
 * Pause et retour en arrière utilisent le différé du serveur sur les chaînes avec replay.
 */
@OptIn(UnstableApi::class)
@Composable
fun LivePlayerScreen(
    channels: List<Channel>,
    startIndex: Int,
    urlFor: (Channel, String) -> String,
    timeshiftUrl: (Channel, Long) -> String?,
    epg: Map<String, List<EpgProgram>>,
    onRequestEpg: (Channel) -> Unit,
    onZapped: (Int) -> Unit,
    onJumpToNumber: (String) -> Boolean,
    onWatched: (Channel, Long) -> Unit,
    onMultiView: (Channel) -> Unit,
) {
    val player = rememberIptvPlayer()
    var index by remember { mutableIntStateOf(startIndex.coerceIn(channels.indices)) }
    var previousIndex by remember { mutableStateOf<Int?>(null) }
    var useHls by remember { mutableStateOf(false) }
    var showInfo by remember { mutableStateOf(true) }
    var buffering by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    var retries by remember { mutableIntStateOf(0) }
    var pendingRetry by remember { mutableStateOf(false) }
    var retryTick by remember { mutableIntStateOf(0) }
    var digits by remember { mutableStateOf("") }
    var digitsError by remember { mutableStateOf<String?>(null) }
    var listOpen by remember { mutableStateOf(false) }
    var controlsOpen by remember { mutableStateOf(false) }
    // Pause et différé
    var paused by remember { mutableStateOf(false) }
    var pausedAt by remember { mutableStateOf<Long?>(null) }
    /** Début (secondes Unix, à la minute) du flux différé en cours ; null = direct. */
    var shiftStart by remember { mutableStateOf<Long?>(null) }
    var shiftTick by remember { mutableIntStateOf(0) }
    var positionSec by remember { mutableLongStateOf(0L) }
    // Rafraîchit l'avancement des programmes affichés
    var clockTick by remember { mutableIntStateOf(0) }

    val channel = channels[index]
    val hasArchive = channel.archiveDays > 0

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
                    Player.STATE_ENDED -> if (shiftStart != null) {
                        shiftStart = null // fin du différé disponible : retour au direct
                    } else {
                        scheduleRetry()
                    }
                }
            }

            override fun onPlayerError(e: PlaybackException) {
                when {
                    // Différé refusé par le serveur : retour au direct plutôt qu'un écran d'erreur
                    shiftStart != null -> {
                        shiftStart = null
                        notice = "Différé indisponible sur cette chaîne : retour au direct"
                    }
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

    /** Instant (secondes Unix) de ce qui est à l'écran : maintenant en direct, plus tôt en différé. */
    fun playbackTs(): Long {
        val now = System.currentTimeMillis() / 1000
        val start = shiftStart ?: return now
        return (start + player.currentPosition / 1000).coerceAtMost(now)
    }

    val programs = epg[channel.streamId].orEmpty()
    val nowSec = remember(clockTick, index) { System.currentTimeMillis() / 1000 }
    val current = programs.nowPlaying(nowSec)
    val next = programs.upNext(nowSec)
    val delaySec = shiftStart?.let { (System.currentTimeMillis() / 1000 - (it + positionSec)).coerceAtLeast(0) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            clockTick++
        }
    }

    // Position dans le différé, pour afficher le retard
    LaunchedEffect(shiftStart) {
        while (shiftStart != null) {
            positionSec = player.currentPosition / 1000
            delay(1_000)
        }
    }

    LaunchedEffect(pendingRetry) {
        if (pendingRetry) {
            delay(3_000)
            pendingRetry = false
            retryTick++
        }
    }

    LaunchedEffect(notice) {
        if (notice != null) {
            delay(4_000)
            notice = null
        }
    }

    LaunchedEffect(index, useHls, retryTick, shiftStart, shiftTick) {
        error = null
        val start = shiftStart
        val shiftUrl = start?.let { timeshiftUrl(channel, it) }
        val item = if (shiftUrl != null) {
            MediaItem.fromUri(shiftUrl)
        } else {
            val ext = if (useHls) "m3u8" else "ts"
            MediaItem.Builder()
                .setUri(urlFor(channel, ext))
                .apply { if (useHls) setMimeType(MimeTypes.APPLICATION_M3U8) }
                .build()
        }
        player.setMediaItem(item)
        player.prepare()
        player.playWhenReady = !paused
    }

    LaunchedEffect(index) {
        onZapped(index)
        onRequestEpg(channel)
    }
    // Le programme en cours vient de se terminer : on recharge le guide
    LaunchedEffect(clockTick) { if (programs.isNotEmpty() && current == null) onRequestEpg(channel) }

    // Bilan de visionnage : secondes réellement regardées sur cette chaîne
    LaunchedEffect(index) {
        val watched = channel
        var seconds = 0L
        try {
            while (true) {
                delay(1_000)
                if (player.isPlaying) seconds++
                if (seconds >= 60) {
                    onWatched(watched, seconds)
                    seconds = 0
                }
            }
        } finally {
            if (seconds > 0) onWatched(watched, seconds)
        }
    }

    LaunchedEffect(index, showInfo, controlsOpen, paused) {
        if (showInfo && !controlsOpen && !paused) {
            delay(5_000)
            showInfo = false
        }
    }

    fun goTo(newIndex: Int) {
        if (newIndex == index || newIndex !in channels.indices) return
        previousIndex = index
        index = newIndex
        useHls = false
        retries = 0
        pendingRetry = false
        shiftStart = null
        paused = false
        pausedAt = null
        showInfo = true
    }

    fun zap(delta: Int) {
        if (channels.size < 2) return
        goTo((index + delta).mod(channels.size))
    }

    fun pause() {
        if (paused) return
        pausedAt = playbackTs()
        paused = true
        player.pause()
        showInfo = true
    }

    fun resume() {
        if (!paused) return
        val at = pausedAt
        paused = false
        pausedAt = null
        val now = System.currentTimeMillis() / 1000
        if (at != null && hasArchive && now - at > SHORT_PAUSE_S) {
            // Longue pause : on reprend en différé depuis le moment de la pause (à la minute près)
            shiftStart = (at / 60) * 60
            shiftTick++
        } else {
            player.play()
            if (!hasArchive && at != null && now - at > SHORT_PAUSE_S) {
                notice = "Pas de replay sur cette chaîne : la pause peut revenir au direct"
            }
        }
    }

    fun rewind(minutes: Int) {
        if (!hasArchive) {
            notice = "Retour en arrière impossible : pas de replay sur cette chaîne"
            return
        }
        val now = System.currentTimeMillis() / 1000
        val oldest = now - channel.archiveDays * 86_400L
        val target = (playbackTs() - minutes * 60L).coerceAtLeast(oldest)
        paused = false
        pausedAt = null
        shiftStart = (target / 60) * 60
        shiftTick++
    }

    fun goLive() {
        paused = false
        pausedAt = null
        if (shiftStart != null) shiftStart = null else player.play()
    }

    // Saisie d'un numéro : on attend la fin de la frappe, puis on cherche dans la liste, sinon partout
    LaunchedEffect(digits) {
        if (digits.isEmpty()) return@LaunchedEffect
        delay(if (digits.length >= 4) 300 else 1_800)
        val entered = digits
        digits = ""
        val wanted = entered.trimStart('0').ifEmpty { "0" }
        val local = channels.indexOfFirst { it.number.trimStart('0').ifEmpty { "0" } == wanted }
        when {
            local >= 0 -> goTo(local)
            onJumpToNumber(entered) -> Unit // l'écran est recréé sur la liste complète des chaînes
            else -> {
                digitsError = "Chaîne $entered introuvable"
                delay(2_000)
                digitsError = null
            }
        }
    }

    val focus = remember { FocusRequester() }
    val controlsFocus = remember { FocusRequester() }

    LaunchedEffect(controlsOpen) {
        if (controlsOpen) {
            withFrameNanos { }
            runCatching { controlsFocus.requestFocus() }
        }
    }

    fun closeControls() {
        controlsOpen = false
        runCatching { focus.requestFocus() }
    }

    BackHandler(enabled = listOpen) {
        listOpen = false
        runCatching { focus.requestFocus() }
    }
    BackHandler(enabled = !listOpen && controlsOpen) { closeControls() }
    BackHandler(enabled = !listOpen && !controlsOpen && digits.isNotEmpty()) { digits = "" }

    KeyCatcher(focus, onKey = { ev ->
        val digit = digitOf(ev.key)
        when {
            ev.key == Key.MediaPlayPause -> { if (paused) resume() else pause(); true }
            ev.key == Key.MediaPause -> { pause(); true }
            ev.key == Key.MediaPlay -> { resume(); true }
            ev.key == Key.MediaRewind -> { rewind(1); true }
            ev.key == Key.MediaFastForward -> { goLive(); true }
            // Liste ou barre de commandes ouverte : les flèches servent à y naviguer
            listOpen || controlsOpen -> false
            digit != null -> {
                if (digits.length < 4) digits += digit
                true
            }
            ev.key == Key.DirectionUp || ev.key == Key.ChannelDown -> { zap(-1); true }
            ev.key == Key.DirectionDown || ev.key == Key.ChannelUp -> { zap(+1); true }
            ev.key == Key.DirectionLeft || ev.key == Key.LastChannel -> {
                previousIndex?.let { goTo(it) }
                true
            }
            ev.key == Key.DirectionRight || ev.key == Key.Guide -> { listOpen = true; true }
            ev.key == Key.DirectionCenter || ev.key == Key.Enter || ev.key == Key.NumPadEnter -> {
                controlsOpen = true
                showInfo = true
                true
            }
            ev.key == Key.Info -> { showInfo = !showInfo; true }
            else -> false
        }
    }) {
        Box(Modifier.fillMaxSize()) {
            VideoSurface(player)

            when {
                error != null -> ErrorBox(error!!, "↑ / ↓ pour changer de chaîne", Modifier.align(Alignment.Center))
                paused -> Box(
                    Modifier.align(Alignment.Center).size(80.dp).background(Glass, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("❚❚", fontSize = 28.sp, fontWeight = FontWeight.Bold)
                }
                pendingRetry || (retries > 0 && buffering) -> StatusText("Reconnexion…", Modifier.align(Alignment.Center))
                buffering -> StatusText("Chargement…", Modifier.align(Alignment.Center))
            }

            // Différé : retard sur le direct, en haut à gauche
            if (delaySec != null || paused) {
                val label = when {
                    paused -> "❚❚  En pause"
                    delaySec != null && delaySec >= 60 -> "⏪  Différé · −${delaySec / 60} min"
                    else -> "⏪  Différé"
                }
                Text(
                    label,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(top = 32.dp, start = 48.dp)
                        .background(Glass, RoundedCornerShape(14.dp))
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            notice?.let { StatusText(it, Modifier.align(Alignment.TopCenter).padding(top = 32.dp)) }

            // Numéro en cours de saisie
            val digitsText = if (digits.isNotEmpty()) digits + "_" else digitsError
            if (digitsText != null) {
                Text(
                    digitsText,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 32.dp, end = 48.dp)
                        .background(Glass, RoundedCornerShape(14.dp))
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                    fontSize = if (digits.isNotEmpty()) 34.sp else 16.sp,
                    fontWeight = FontWeight.Bold,
                )
            }

            AnimatedVisibility(
                visible = (showInfo || controlsOpen) && !listOpen,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.BottomStart),
            ) {
                Column(
                    Modifier.padding(start = 48.dp, end = 48.dp, bottom = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(
                        Modifier
                            .widthIn(max = 620.dp)
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
                        Column(Modifier.weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                listOfNotNull(channel.number.takeIf { it.isNotBlank() }, channel.name).joinToString("  ·  "),
                                color = TextSecondary,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                current?.title ?: channel.name,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (current != null) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Text("${hhmm(current.startTs)} – ${hhmm(current.stopTs)}", color = TextSecondary, fontSize = 12.sp)
                                    Box(Modifier.size(width = 120.dp, height = 4.dp).background(Color(0x4DFFFFFF), CircleShape)) {
                                        Box(Modifier.fillMaxWidth(current.progress(nowSec)).fillMaxHeight().background(Color.White, CircleShape))
                                    }
                                }
                            }
                            if (next != null) {
                                Text(
                                    "Ensuite · ${hhmm(next.startTs)}  ${next.title}",
                                    color = TextSecondary,
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            if (!controlsOpen) {
                                Text("OK commandes  ·  ↑↓ zapper  ·  ← précédente  ·  → liste  ·  0-9 numéro", color = Color(0x80FFFFFF), fontSize = 10.sp)
                            }
                        }
                    }

                    if (controlsOpen) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ChipButton(
                                label = if (paused) "▶  Reprendre" else "❚❚  Pause",
                                selected = false,
                                onClick = { if (paused) resume() else pause() },
                                modifier = Modifier.focusRequester(controlsFocus),
                            )
                            if (hasArchive) {
                                ChipButton("⏪  −1 min", selected = false, onClick = { rewind(1) })
                                ChipButton("⏪  −5 min", selected = false, onClick = { rewind(5) })
                            }
                            if (shiftStart != null || paused) {
                                ChipButton("⏩  Direct", selected = false, onClick = { goLive() })
                            }
                            ChipButton("▦  Multi-écran", selected = false, onClick = {
                                closeControls()
                                onMultiView(channel)
                            })
                            ChipButton("☰  Chaînes", selected = false, onClick = {
                                controlsOpen = false
                                listOpen = true
                            })
                        }
                    }
                }
            }

            if (listOpen) {
                ChannelListPanel(
                    channels = channels,
                    currentIndex = index,
                    epg = epg,
                    nowSec = nowSec,
                    onRequestEpg = onRequestEpg,
                    onPick = { i ->
                        listOpen = false
                        goTo(i)
                        runCatching { focus.requestFocus() }
                    },
                    modifier = Modifier.align(Alignment.CenterStart),
                )
            }
        }
    }
}

/** Liste des chaînes en surimpression, à gauche, avec le programme en cours. */
@Composable
private fun ChannelListPanel(
    channels: List<Channel>,
    currentIndex: Int,
    epg: Map<String, List<EpgProgram>>,
    nowSec: Long,
    onRequestEpg: (Channel) -> Unit,
    onPick: (Int) -> Unit,
    modifier: Modifier,
) {
    val listState = rememberLazyListState()
    val currentFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        listState.scrollToItem((currentIndex - 3).coerceAtLeast(0))
        withFrameNanos { }
        runCatching { currentFocus.requestFocus() }
    }
    Column(
        modifier
            .fillMaxHeight()
            .width(380.dp)
            .background(Brush.horizontalGradient(listOf(Color(0xF2000000), Color(0xB3000000))))
            .padding(top = 24.dp),
    ) {
        Text("Chaînes", modifier = Modifier.padding(start = 28.dp, bottom = 8.dp), fontSize = 18.sp, fontWeight = FontWeight.Bold)
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            itemsIndexed(channels, key = { i, c -> "${c.streamId}-$i" }) { i, ch ->
                LaunchedEffect(ch.streamId) { onRequestEpg(ch) }
                val now = epg[ch.streamId]?.nowPlaying(nowSec)
                Surface(
                    onClick = { onPick(i) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(if (i == currentIndex) Modifier.focusRequester(currentFocus) else Modifier),
                    shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = if (i == currentIndex) Color(0x33FFFFFF) else Color.Transparent,
                        contentColor = TextPrimary,
                        focusedContainerColor = FocusFill,
                        focusedContentColor = FocusText,
                        pressedContainerColor = FocusFill,
                        pressedContentColor = FocusText,
                    ),
                    scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
                ) {
                    Row(
                        Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(ch.number, modifier = Modifier.width(34.dp), fontSize = 12.sp, color = LocalContentColor.current.copy(alpha = 0.6f))
                        Box(Modifier.size(width = 44.dp, height = 26.dp), contentAlignment = Alignment.Center) {
                            if (ch.icon != null) {
                                AsyncImage(model = ch.icon, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
                            }
                        }
                        Column(Modifier.weight(1f)) {
                            Text(ch.name, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (now != null) {
                                Text(
                                    now.title,
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = LocalContentColor.current.copy(alpha = 0.65f),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun hhmm(ts: Long) = java.text.SimpleDateFormat("HH:mm", Locale.FRANCE).format(java.util.Date(ts * 1000))

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
    onWatched: (seconds: Long) -> Unit,
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

    // Bilan de visionnage : secondes réellement regardées
    LaunchedEffect(Unit) {
        var seconds = 0L
        try {
            while (true) {
                delay(1_000)
                if (player.isPlaying) seconds++
                if (seconds >= 60) {
                    onWatched(seconds)
                    seconds = 0
                }
            }
        } finally {
            if (seconds > 0) onWatched(seconds)
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
