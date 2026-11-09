package fr.rabuxx.home

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private const val RABUX_PACKAGE = "fr.rabuxx.iptvtv"
private const val DOCK_SIZE = 7
private const val ROTATE_MS = 9_000L

private val Tile = Color(0xFF2C2C2E)
private val GlassFill = Color(0x66000000)
private val GlassEdge = Color(0x33FFFFFF)
private val Secondary = Color(0xFFD1D1D6)
private val IconShape = RoundedCornerShape(18.dp)
private val ThumbShape = RoundedCornerShape(12.dp)
private val SidePadding = 56.dp

/** Ce que montre le grand fond : un contenu Rabux TV à reprendre, ou un titre populaire d'une plateforme. */
private data class HeroItem(
    val title: String,
    val label: String,
    val overview: String?,
    val image: String,
    val thumbnail: String,
    val logo: String?,
    val badge: String?,
    val content: ShelfItem?,
)

/**
 * Accueil façon Apple TV : le fond montre ce qui est populaire sur l'app sélectionnée dans le dock
 * (ou tes contenus en cours pour Rabux TV), une rangée de vignettes, le dock d'icônes en bas,
 * et l'écran de veille photo quand la TV reste inactive.
 */
@Composable
fun HomeScreen(
    apps: List<AppEntry>,
    shelf: List<ShelfItem>,
    spotlights: Map<String, List<Spotlight>>,
    weather: Weather?,
    idle: Boolean,
    onOpen: (AppEntry) -> Unit,
    onOpenContent: (ShelfItem) -> Unit,
    onSettings: () -> Unit,
) {
    var focusedPackage by remember { mutableStateOf<String?>(null) }
    var heroIndex by remember { mutableIntStateOf(0) }
    var dockFocused by remember { mutableStateOf(true) }
    var gridOpen by remember { mutableStateOf(false) }
    val firstIcon = remember { FocusRequester() }

    val dock = apps.take(DOCK_SIZE)
    val current = apps.firstOrNull { it.packageName == focusedPackage } ?: dock.firstOrNull()
    val heroes = remember(current?.packageName, shelf, spotlights) { heroItemsFor(current, shelf, spotlights) }
    val hero = heroes.getOrNull(heroIndex.coerceAtMost((heroes.size - 1).coerceAtLeast(0)))

    // Défilement automatique tant qu'on reste sur le dock
    LaunchedEffect(current?.packageName, heroes.size, dockFocused) {
        if (!dockFocused) return@LaunchedEffect
        while (heroes.size > 1) {
            delay(ROTATE_MS)
            heroIndex = (heroIndex + 1) % heroes.size
        }
    }

    LaunchedEffect(apps.map { it.packageName }) {
        if (apps.isNotEmpty() && apps.none { it.packageName == focusedPackage }) {
            runCatching { firstIcon.requestFocus() }
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        Backdrop(hero, current)

        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().weight(1f)) {
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(start = SidePadding, top = 48.dp),
                ) {
                    HeroText(hero, current, onOpen, onOpenContent)
                }
                Clock(weather, Modifier.padding(top = 40.dp, end = SidePadding))
            }

            if (heroes.size > 1) {
                Text(
                    if (current?.packageName == RABUX_PACKAGE) "Reprendre" else "Populaire en ce moment",
                    modifier = Modifier.padding(start = SidePadding, bottom = 6.dp),
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                LazyRow(
                    contentPadding = PaddingValues(horizontal = SidePadding, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    itemsIndexed(heroes, key = { i, item -> "$i:${item.title}" }) { index, item ->
                        Thumbnail(
                            item = item,
                            onFocused = {
                                dockFocused = false
                                heroIndex = index
                            },
                            onClick = {
                                val content = item.content
                                if (content != null) onOpenContent(content) else current?.let(onOpen)
                            },
                        )
                    }
                }
            }

            Dock(
                apps = dock,
                focused = current?.packageName,
                firstIcon = firstIcon,
                onFocused = { app ->
                    dockFocused = true
                    if (app.packageName != focusedPackage) {
                        focusedPackage = app.packageName
                        heroIndex = 0
                    }
                },
                onOpen = onOpen,
                onAllApps = { gridOpen = true },
            )
        }

        if (gridOpen) {
            AllApps(
                apps = apps,
                onOpen = { gridOpen = false; onOpen(it) },
                onSettings = { gridOpen = false; onSettings() },
                onClose = { gridOpen = false },
            )
        }

        if (idle) {
            Screensaver(
                images = remember(spotlights, shelf) {
                    (spotlights.values.flatten().map { it.backdrop } + shelf.map { it.image }).distinct().shuffled()
                },
                weather = weather,
            )
        }
    }
}

private fun heroItemsFor(app: AppEntry?, shelf: List<ShelfItem>, spotlights: Map<String, List<Spotlight>>): List<HeroItem> {
    if (app == null) return emptyList()
    if (app.packageName == RABUX_PACKAGE) {
        return shelf.map {
            HeroItem(it.title, "Rabux TV · À reprendre", null, it.image, it.image, null, it.subtitle, it)
        }
    }
    val provider = PROVIDERS[app.packageName]?.first ?: return emptyList()
    return spotlights[app.packageName].orEmpty().map {
        HeroItem(
            title = it.title,
            label = "$provider · ${if (it.isSeries) "Série" else "Film"} populaire en France",
            overview = it.overview,
            image = it.backdrop,
            thumbnail = it.thumbnail,
            logo = it.logo,
            badge = null,
            content = null,
        )
    }
}

/** Image plein écran, ou à défaut la couleur et la bannière de l'app sélectionnée. */
@Composable
private fun Backdrop(hero: HeroItem?, app: AppEntry?) {
    val tint by animateColorAsState(
        targetValue = lerp(app?.accent ?: Tile, Color.Black, 0.5f),
        animationSpec = tween(450),
        label = "tint",
    )
    Crossfade(targetState = hero?.image, animationSpec = tween(700), label = "backdrop") { image ->
        Box(Modifier.fillMaxSize()) {
            if (image != null) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current).data(image).crossfade(true).build(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to tint, 0.85f to Color.Black)))
                if (app != null && app.hasBanner) {
                    Image(
                        bitmap = app.image,
                        contentDescription = null,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(bottom = 120.dp)
                            .width(380.dp)
                            .clip(ThumbShape),
                        contentScale = ContentScale.Fit,
                    )
                }
            }
        }
    }
    // Voiles : le texte à gauche et le dock en bas restent lisibles sur n'importe quelle image
    Box(
        Modifier.fillMaxSize().background(
            Brush.horizontalGradient(0f to Color(0xD9000000), 0.55f to Color(0x33000000), 1f to Color.Transparent),
        ),
    )
    Box(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(0f to Color(0x66000000), 0.3f to Color.Transparent, 0.6f to Color.Transparent, 1f to Color(0xF2000000)),
        ),
    )
}

@Composable
private fun HeroText(
    hero: HeroItem?,
    app: AppEntry?,
    onOpen: (AppEntry) -> Unit,
    onOpenContent: (ShelfItem) -> Unit,
) {
    Crossfade(targetState = hero, animationSpec = tween(500), label = "hero-text") { shown ->
        Column(Modifier.widthIn(max = 560.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (shown == null) {
                Text(
                    app?.label.orEmpty(),
                    color = Color.White,
                    fontSize = 44.sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                return@Column
            }
            Text(shown.label, color = Secondary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            if (shown.logo != null) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current).data(shown.logo).crossfade(true).build(),
                    contentDescription = shown.title,
                    modifier = Modifier.heightIn(max = 110.dp).widthIn(max = 440.dp),
                    contentScale = ContentScale.Fit,
                    alignment = Alignment.CenterStart,
                )
            } else {
                Text(
                    shown.title,
                    color = Color.White,
                    fontSize = 44.sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            shown.overview?.let {
                Text(it, color = Color(0xFFE5E5EA), fontSize = 15.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                val content = shown.content
                Pill(
                    text = if (content != null) "▶  Reprendre" else "▶  Ouvrir ${app?.label.orEmpty()}",
                    onClick = { if (content != null) onOpenContent(content) else app?.let(onOpen) },
                )
                shown.badge?.let { Tag(it) }
            }
        }
    }
}

@Composable
private fun Pill(text: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(CircleShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color(0x4DFFFFFF),
            contentColor = Color.White,
            focusedContainerColor = Color.White,
            focusedContentColor = Color.Black,
            pressedContainerColor = Color.White,
            pressedContentColor = Color.Black,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.08f),
    ) {
        Text(text, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp), fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun Tag(text: String) {
    Text(
        text,
        modifier = Modifier
            .clip(CircleShape)
            .background(GlassFill)
            .padding(horizontal = 14.dp, vertical = 7.dp),
        color = Color.White,
        fontSize = 14.sp,
    )
}

@Composable
private fun Clock(weather: Weather?, modifier: Modifier = Modifier) {
    val now = rememberNow()
    Column(modifier, horizontalAlignment = Alignment.End) {
        Text(now.format(DateTimeFormatter.ofPattern("HH:mm")), color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.SemiBold)
        Text(
            now.format(DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.FRENCH)).replaceFirstChar { it.uppercase() },
            color = Secondary,
            fontSize = 13.sp,
        )
        weather?.let {
            Text(
                "${weatherIcon(it.code)}  ${it.temperature}°  ${WeatherApi.CITY}",
                color = Secondary,
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

@Composable
private fun rememberNow(): LocalDateTime {
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = LocalDateTime.now()
            delay((60 - now.second) * 1000L)
        }
    }
    return now
}

@Composable
private fun Thumbnail(item: HeroItem, onFocused: () -> Unit, onClick: () -> Unit) {
    Column(Modifier.width(196.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Surface(
            onClick = onClick,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .onFocusChanged { if (it.isFocused) onFocused() },
            shape = ClickableSurfaceDefaults.shape(ThumbShape),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = Tile,
                focusedContainerColor = Tile,
                pressedContainerColor = Tile,
            ),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.1f),
            border = ClickableSurfaceDefaults.border(
                focusedBorder = Border(border = BorderStroke(2.dp, Color.White), shape = ThumbShape),
            ),
        ) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current).data(item.thumbnail).crossfade(true).build(),
                contentDescription = item.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }
        Text(item.title, color = Secondary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Dock d'icônes centré : l'icône sélectionnée grossit et affiche son nom. */
@Composable
private fun Dock(
    apps: List<AppEntry>,
    focused: String?,
    firstIcon: FocusRequester,
    onFocused: (AppEntry) -> Unit,
    onOpen: (AppEntry) -> Unit,
    onAllApps: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 28.dp),
        horizontalArrangement = Arrangement.spacedBy(22.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.Top,
    ) {
        apps.forEachIndexed { index, app ->
            DockIcon(
                label = app.label,
                selected = app.packageName == focused,
                onClick = { onOpen(app) },
                modifier = (if (index == 0) Modifier.focusRequester(firstIcon) else Modifier)
                    .onFocusChanged { if (it.isFocused) onFocused(app) },
            ) {
                Image(bitmap = app.icon, contentDescription = app.label, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            }
        }
        DockIcon(label = "Toutes les apps", selected = false, onClick = onAllApps) {
            Box(Modifier.fillMaxSize().background(Tile), contentAlignment = Alignment.Center) {
                Text("▦", color = Color.White, fontSize = 26.sp)
            }
        }
    }
}

@Composable
private fun DockIcon(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    var hasFocus by remember { mutableStateOf(false) }
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(96.dp)) {
        Surface(
            onClick = onClick,
            modifier = modifier
                .size(64.dp)
                .onFocusChanged { hasFocus = it.isFocused },
            shape = ClickableSurfaceDefaults.shape(IconShape),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = Tile,
                focusedContainerColor = Tile,
                pressedContainerColor = Tile,
            ),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.3f),
            border = ClickableSurfaceDefaults.border(
                focusedBorder = Border(border = BorderStroke(2.dp, Color.White), shape = IconShape),
            ),
        ) {
            content()
        }
        Spacer(Modifier.height(14.dp))
        Text(
            if (hasFocus || selected) label else "",
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Toutes les apps installées, en grille, avec les réglages en dernier. */
@Composable
private fun AllApps(apps: List<AppEntry>, onOpen: (AppEntry) -> Unit, onSettings: () -> Unit, onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
    Box(Modifier.fillMaxSize().background(Color(0xF0000000))) {
        Column(Modifier.fillMaxSize().padding(horizontal = SidePadding, vertical = 36.dp)) {
            Text("Toutes les apps", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(20.dp))
            LazyVerticalGrid(
                columns = GridCells.Fixed(6),
                horizontalArrangement = Arrangement.spacedBy(18.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
                contentPadding = PaddingValues(12.dp),
            ) {
                items(apps.size + 1) { index ->
                    val app = apps.getOrNull(index)
                    Surface(
                        onClick = { if (app != null) onOpen(app) else onSettings() },
                        modifier = (if (index == 0) Modifier.focusRequester(first) else Modifier).aspectRatio(16f / 9f),
                        shape = ClickableSurfaceDefaults.shape(ThumbShape),
                        colors = ClickableSurfaceDefaults.colors(
                            containerColor = Tile,
                            focusedContainerColor = Tile,
                            pressedContainerColor = Tile,
                        ),
                        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.1f),
                        border = ClickableSurfaceDefaults.border(
                            focusedBorder = Border(border = BorderStroke(2.dp, Color.White), shape = ThumbShape),
                        ),
                    ) {
                        when {
                            app == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("Réglages", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                            }
                            app.hasBanner -> Image(app.image, app.label, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                            else -> Image(app.image, app.label, Modifier.fillMaxSize().padding(14.dp), contentScale = ContentScale.Fit)
                        }
                    }
                }
            }
        }
    }
}

/** Écran de veille : photos plein écran qui zooment lentement, grande heure et météo. */
@Composable
private fun Screensaver(images: List<String>, weather: Weather?) {
    var index by remember { mutableIntStateOf(0) }
    LaunchedEffect(images.size) {
        while (images.size > 1) {
            delay(20_000)
            index = (index + 1) % images.size
        }
    }
    val zoom by rememberInfiniteTransition(label = "zoom").animateFloat(
        initialValue = 1f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(tween(20_000, easing = LinearEasing), RepeatMode.Reverse),
        label = "zoom",
    )
    val now = rememberNow()
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        Crossfade(targetState = images.getOrNull(index), animationSpec = tween(1_500), label = "saver") { image ->
            if (image != null) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current).data(image).build(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize().scale(zoom),
                    contentScale = ContentScale.Crop,
                )
            }
        }
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.55f to Color.Transparent, 1f to Color(0xB3000000))))
        Column(Modifier.align(Alignment.BottomStart).padding(start = SidePadding, bottom = 48.dp)) {
            Text(now.format(DateTimeFormatter.ofPattern("HH:mm")), color = Color.White, fontSize = 96.sp, fontWeight = FontWeight.Bold)
            weather?.let {
                Text(
                    "${WeatherApi.CITY}  ·  ${it.temperature}°  ·  ${weatherLabel(it.code)}",
                    color = Color.White,
                    fontSize = 20.sp,
                )
            }
        }
    }
}
