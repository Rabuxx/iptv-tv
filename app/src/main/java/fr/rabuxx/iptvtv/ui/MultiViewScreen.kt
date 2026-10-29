package fr.rabuxx.iptvtv.ui

import android.view.TextureView
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import fr.rabuxx.iptvtv.data.Channel
import fr.rabuxx.iptvtv.data.EpgProgram
import fr.rabuxx.iptvtv.data.nowPlaying
import kotlinx.coroutines.delay

/** Source de chaînes proposée dans le sélecteur du multi-écran. */
data class ChannelSource(val label: String, val channels: List<Channel>)

private data class Tile(val x: Dp, val y: Dp, val w: Dp, val h: Dp)

/**
 * Multi-écran : jusqu'à 4 chaînes en même temps (dans la limite des connexions de l'abonnement).
 * La case sélectionnée a le son · OK : plein écran · OK maintenu : changer ou retirer la chaîne · Retour : quitter.
 */
@Composable
fun MultiViewScreen(
    initial: List<Channel>,
    maxSlots: Int,
    sources: List<ChannelSource>,
    urlFor: (Channel, String) -> String,
    epg: Map<String, List<EpgProgram>>,
    onRequestEpg: (Channel) -> Unit,
    onWatched: (Channel, Long) -> Unit,
) {
    val slots = remember { mutableStateListOf<Channel>().apply { addAll(initial.take(maxSlots)) } }
    var active by remember { mutableIntStateOf(0) }
    var fullscreen by remember { mutableStateOf<Int?>(null) }
    /** Case à remplir dans le sélecteur : un index existant (remplacer) ou slots.size (ajouter). */
    var picking by remember { mutableStateOf<Int?>(if (initial.isEmpty()) 0 else null) }
    val tileFocus = remember { List(4) { FocusRequester() } }
    val addFocus = remember { FocusRequester() }

    fun focusTile(i: Int) {
        runCatching { if (i < slots.size) tileFocus[i].requestFocus() else addFocus.requestFocus() }
    }

    LaunchedEffect(picking) {
        if (picking == null) {
            withFrameNanos { }
            focusTile(active.coerceAtMost(slots.size))
        }
    }

    BackHandler(enabled = picking != null && slots.isNotEmpty()) { picking = null }
    BackHandler(enabled = picking == null && fullscreen != null) {
        fullscreen = null
    }

    val canAdd = slots.size < maxSlots

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            // En plein écran, les flèches ne doivent pas déplacer le focus vers une case cachée
            .onPreviewKeyEvent { ev ->
                fullscreen != null && picking == null && ev.type == KeyEventType.KeyDown &&
                    ev.key in setOf(Key.DirectionUp, Key.DirectionDown, Key.DirectionLeft, Key.DirectionRight)
            },
    ) {
        BoxWithConstraints(Modifier.fillMaxSize().padding(if (fullscreen == null) 24.dp else 0.dp)) {
            val tiles = layoutTiles(slots.size + if (canAdd) 1 else 0, maxWidth, maxHeight)
            val full = Tile(0.dp, 0.dp, maxWidth, maxHeight)

            slots.forEachIndexed { i, channel ->
                // Une clé par chaîne : le lecteur survit aux changements de disposition
                key(channel.streamId) {
                    val rect = when (val f = fullscreen) {
                        null -> tiles.getOrNull(i) ?: full
                        i -> full
                        else -> null // caché pendant le plein écran d'une autre case
                    }
                    SlotTile(
                        channel = channel,
                        rect = rect,
                        audible = i == active,
                        focused = i == active && picking == null,
                        programTitle = epg[channel.streamId]?.nowPlaying()?.title,
                        urlFor = urlFor,
                        onRequestEpg = onRequestEpg,
                        onWatched = onWatched,
                        focusRequester = tileFocus[i],
                        onFocused = { active = i },
                        onClick = { fullscreen = if (fullscreen == i) null else i },
                        onLongClick = { picking = i },
                    )
                }
            }

            if (canAdd && fullscreen == null) {
                val rect = tiles.getOrNull(slots.size) ?: full
                Surface(
                    onClick = { picking = slots.size },
                    modifier = Modifier
                        .offset(rect.x, rect.y)
                        .size(rect.w, rect.h)
                        .focusRequester(addFocus)
                        .onFocusChanged { if (it.isFocused) active = slots.size },
                    shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(14.dp)),
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = Color(0x1AFFFFFF),
                        contentColor = TextSecondary,
                        focusedContainerColor = Color(0x33FFFFFF),
                        focusedContentColor = TextPrimary,
                    ),
                    scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
                ) {
                    Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("+", fontSize = 40.sp, fontWeight = FontWeight.Light)
                        Text("Ajouter une chaîne", fontSize = 14.sp)
                        Text("${slots.size} / $maxSlots", fontSize = 11.sp, color = TextSecondary)
                    }
                }
            }
        }

        if (slots.isEmpty() && picking == null) {
            CenteredMessage("Aucune chaîne. Appuie sur OK pour en ajouter.")
        }

        if (fullscreen == null && picking == null && slots.isNotEmpty()) {
            Text(
                "Le son suit la case sélectionnée  ·  OK plein écran  ·  OK maintenu changer  ·  Retour quitter",
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 4.dp),
                color = Color(0x80FFFFFF),
                fontSize = 10.sp,
            )
        }
        if (maxSlots < 4 && fullscreen == null && picking == null) {
            Text(
                "Ton abonnement autorise $maxSlots connexion${if (maxSlots > 1) "s" else ""} simultanée${if (maxSlots > 1) "s" else ""}",
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 4.dp),
                color = Color(0x80FFFFFF),
                fontSize = 10.sp,
            )
        }

        picking?.let { target ->
            ChannelPicker(
                sources = sources,
                replacing = slots.getOrNull(target),
                exclude = slots.map { it.streamId }.toSet(),
                onPick = { ch ->
                    if (target < slots.size) slots[target] = ch else if (slots.size < maxSlots) slots.add(ch)
                    active = target.coerceAtMost(slots.size - 1)
                    picking = null
                },
                onRemove = {
                    if (target < slots.size) slots.removeAt(target)
                    active = (target - 1).coerceAtLeast(0)
                    fullscreen = null
                    picking = null
                },
                modifier = Modifier.align(Alignment.CenterStart),
            )
        }
    }
}

/** Disposition des cases : 1 plein écran, 2 côte à côte, 3-4 en grille 2 × 2. */
private fun layoutTiles(count: Int, width: Dp, height: Dp): List<Tile> {
    val gap = 10.dp
    return when {
        count <= 1 -> listOf(Tile(0.dp, 0.dp, width, height))
        count == 2 -> {
            val w = (width - gap) / 2
            val h = minOf(height, w * 9f / 16f)
            val y = (height - h) / 2
            listOf(Tile(0.dp, y, w, h), Tile(w + gap, y, w, h))
        }
        else -> {
            val w = (width - gap) / 2
            val h = (height - gap) / 2
            (0 until count.coerceAtMost(4)).map { i -> Tile(if (i % 2 == 0) 0.dp else w + gap, if (i < 2) 0.dp else h + gap, w, h) }
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun SlotTile(
    channel: Channel,
    rect: Tile?,
    audible: Boolean,
    focused: Boolean,
    programTitle: String?,
    urlFor: (Channel, String) -> String,
    onRequestEpg: (Channel) -> Unit,
    onWatched: (Channel, Long) -> Unit,
    focusRequester: FocusRequester,
    onFocused: () -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val player = rememberIptvPlayer()
    var useHls by remember { mutableStateOf(false) }
    var retries by remember { mutableIntStateOf(0) }
    var retryTick by remember { mutableIntStateOf(0) }
    var buffering by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    DisposableEffect(Unit) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                buffering = state == Player.STATE_BUFFERING
                if (state == Player.STATE_READY) {
                    retries = 0
                    error = null
                }
            }

            override fun onPlayerError(e: PlaybackException) {
                when {
                    // Trop de vidéos en même temps pour les décodeurs de la TV
                    e.errorCode == PlaybackException.ERROR_CODE_DECODER_INIT_FAILED ||
                        e.errorCode == PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED ->
                        error = "La TV ne peut pas décoder autant de vidéos à la fois"
                    !useHls && retries == 0 -> useHls = true
                    retries < 5 -> {
                        retries++
                        retryTick++
                    }
                    else -> error = "Cette chaîne ne répond pas"
                }
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    LaunchedEffect(useHls, retryTick) {
        if (retryTick > 0) delay(3_000)
        val ext = if (useHls) "m3u8" else "ts"
        player.setMediaItem(
            MediaItem.Builder()
                .setUri(urlFor(channel, ext))
                .apply { if (useHls) setMimeType(MimeTypes.APPLICATION_M3U8) }
                .build(),
        )
        player.prepare()
    }

    LaunchedEffect(audible) { player.volume = if (audible) 1f else 0f }
    LaunchedEffect(Unit) { onRequestEpg(channel) }

    // Bilan : seule la case qui a le son compte comme regardée
    LaunchedEffect(audible) {
        if (!audible) return@LaunchedEffect
        var seconds = 0L
        try {
            while (true) {
                delay(1_000)
                if (player.isPlaying) seconds++
                if (seconds >= 60) {
                    onWatched(channel, seconds)
                    seconds = 0
                }
            }
        } finally {
            if (seconds > 0) onWatched(channel, seconds)
        }
    }

    val r = rect ?: Tile(0.dp, 0.dp, 0.dp, 0.dp)
    Surface(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = Modifier
            .offset(r.x, r.y)
            .size(r.w, r.h)
            .focusRequester(focusRequester)
            .onFocusChanged { if (it.isFocused) onFocused() }
            .then(if (focused) Modifier.border(3.dp, Color.White, RoundedCornerShape(14.dp)) else Modifier),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(14.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Black,
            focusedContainerColor = Color.Black,
            pressedContainerColor = Color.Black,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
    ) {
        AndroidView(
            factory = { ctx -> TextureView(ctx).also { player.setVideoTextureView(it) } },
            onRelease = { player.clearVideoTextureView(it) },
            modifier = Modifier.fillMaxSize(),
        )
        when {
            error != null -> Text(
                error!!,
                modifier = Modifier.align(Alignment.Center).padding(16.dp),
                color = TextSecondary,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
            )
            buffering -> Text("Chargement…", modifier = Modifier.align(Alignment.Center), color = TextSecondary, fontSize = 13.sp)
        }
        // Nom de la chaîne et programme en cours
        Row(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xCC000000))))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (audible) Text("🔊", fontSize = 12.sp)
            Column(Modifier.weight(1f)) {
                Text(channel.name, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (programTitle != null) {
                    Text(programTitle, fontSize = 11.sp, color = Color(0xB3FFFFFF), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

/** Sélecteur de chaîne à gauche de l'écran : sources en pastilles, puis liste. */
@Composable
private fun ChannelPicker(
    sources: List<ChannelSource>,
    replacing: Channel?,
    exclude: Set<String>,
    onPick: (Channel) -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier,
) {
    val available = sources.filter { it.channels.isNotEmpty() }
    var sourceIndex by remember { mutableIntStateOf(0) }
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { firstFocus.requestFocus() }
    }
    val channels = available.getOrNull(sourceIndex)?.channels.orEmpty().filter { it.streamId !in exclude }

    Column(
        modifier
            .fillMaxHeight()
            .width(420.dp)
            .background(Brush.horizontalGradient(listOf(Color(0xF7000000), Color(0xE6000000))))
            .padding(top = 24.dp),
    ) {
        Text(
            if (replacing != null) "Remplacer ${replacing.name}" else "Ajouter une chaîne",
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 8.dp),
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (available.isEmpty()) {
            Text(
                "Aucune chaîne à proposer : ajoute des chaînes en favoris ou ouvre Sport ce soir.",
                modifier = Modifier.padding(24.dp),
                color = TextSecondary,
                fontSize = 13.sp,
            )
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            itemsIndexed(available) { i, source ->
                ChipButton(
                    label = source.label,
                    selected = i == sourceIndex,
                    onClick = { sourceIndex = i },
                    modifier = if (i == 0 && replacing == null) Modifier.focusRequester(firstFocus) else Modifier,
                )
            }
        }
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (replacing != null) {
                item("remove") {
                    PickerRow(
                        title = "Retirer cette chaîne",
                        icon = null,
                        onClick = onRemove,
                        modifier = Modifier.focusRequester(firstFocus),
                    )
                }
            }
            itemsIndexed(channels, key = { i, c -> "${c.streamId}-$i" }) { _, ch ->
                PickerRow(title = ch.name, icon = ch.icon, onClick = { onPick(ch) })
            }
        }
    }
}

@Composable
private fun PickerRow(title: String, icon: String?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
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
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(Modifier.size(width = 44.dp, height = 26.dp), contentAlignment = Alignment.Center) {
                if (icon != null) {
                    AsyncImage(model = icon, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
                }
            }
            Text(
                title,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = LocalContentColor.current,
            )
        }
    }
}
