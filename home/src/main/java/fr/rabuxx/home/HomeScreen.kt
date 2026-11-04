package fr.rabuxx.home

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import java.time.LocalTime
import java.time.format.DateTimeFormatter

private const val RABUX_PACKAGE = "fr.rabuxx.iptvtv"
private const val COLUMNS = 6
private const val SHELF_DELAY_MS = 9_000L

private val Tile = Color(0xFF2C2C2E)
private val GlassFill = Color(0x73000000)
private val GlassEdge = Color(0x33FFFFFF)
private val TileShape = RoundedCornerShape(16.dp)
private val DockShape = RoundedCornerShape(30.dp)
private val SidePadding = 40.dp
private val DockPadding = 14.dp
private val TileGap = 14.dp

/** Accueil façon tvOS : image plein écran, dock en verre avec les six premières apps, grille en dessous. */
@Composable
fun HomeScreen(
    apps: List<AppEntry>,
    shelf: List<ShelfItem>,
    weather: Weather?,
    onOpen: (AppEntry) -> Unit,
    onOpenContent: (ShelfItem) -> Unit,
    onSettings: () -> Unit,
) {
    var focusedPackage by remember { mutableStateOf<String?>(null) }
    var shelfIndex by remember { mutableIntStateOf(0) }
    val firstTile = remember { FocusRequester() }
    val scroll = rememberScrollState()
    val current = apps.firstOrNull { it.packageName == focusedPackage } ?: apps.firstOrNull()
    val shelfItem = shelf.takeIf { it.isNotEmpty() && current?.packageName == RABUX_PACKAGE }
        ?.let { it[shelfIndex % it.size] }

    LaunchedEffect(shelf.size) {
        while (shelf.size > 1) {
            delay(SHELF_DELAY_MS)
            shelfIndex = (shelfIndex + 1) % shelf.size
        }
    }

    // Au démarrage, ou si l'app qui avait le focus vient d'être désinstallée
    LaunchedEffect(apps.map { it.packageName }) {
        if (apps.isNotEmpty() && apps.none { it.packageName == focusedPackage }) {
            runCatching { firstTile.requestFocus() }
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black)) {
        val tileWidth = (maxWidth - SidePadding * 2 - DockPadding * 2 - TileGap * (COLUMNS - 1)) / COLUMNS
        val tileHeight = tileWidth * 0.6f
        val dockTop = maxHeight * 0.64f

        HeroBackground(current, shelfItem)
        Column(Modifier.fillMaxSize()) {
            TopBar(shelfItem, current?.label, weather, onOpenContent)
        }

        // Tuiles de la grille : les apps, puis Réglages en dernier
        val rows = (apps.map<AppEntry, AppEntry?> { it } + null).chunked(COLUMNS)
        Column(Modifier.fillMaxSize().verticalScroll(scroll)) {
            Spacer(Modifier.height(dockTop))
            rows.forEachIndexed { rowIndex, row ->
                val rowContent: @Composable () -> Unit = {
                    Row(horizontalArrangement = Arrangement.spacedBy(TileGap)) {
                        row.forEachIndexed { columnIndex, app ->
                            val modifier = Modifier.size(tileWidth, tileHeight)
                            if (app == null) {
                                SettingsTile(onSettings, modifier)
                            } else {
                                AppTile(
                                    app = app,
                                    onClick = { onOpen(app) },
                                    onFocused = { focusedPackage = app.packageName },
                                    modifier = if (rowIndex == 0 && columnIndex == 0) {
                                        modifier.focusRequester(firstTile)
                                    } else {
                                        modifier
                                    },
                                )
                            }
                        }
                    }
                }
                if (rowIndex == 0) {
                    Dock(rowContent)
                } else {
                    Box(Modifier.padding(start = SidePadding + DockPadding, top = 24.dp)) { rowContent() }
                }
            }
            Spacer(Modifier.height(40.dp))
        }
    }
}

@Composable
private fun Dock(content: @Composable () -> Unit) {
    Box(
        Modifier
            .padding(horizontal = SidePadding)
            .fillMaxWidth()
            .clip(DockShape)
            .background(GlassFill)
            .border(1.dp, GlassEdge, DockShape)
            .padding(DockPadding),
    ) {
        content()
    }
}

/** Sans contenu Rabux, le fond reprend la couleur et la bannière de l'app sélectionnée. */
@Composable
private fun HeroBackground(app: AppEntry?, item: ShelfItem?) {
    val tint by animateColorAsState(
        targetValue = lerp(app?.accent ?: Tile, Color.Black, 0.5f),
        animationSpec = tween(450),
        label = "tint",
    )
    Crossfade(targetState = item, animationSpec = tween(600), label = "hero") { shown ->
        Box(Modifier.fillMaxSize()) {
            if (shown != null) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current).data(shown.image).crossfade(true).build(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to tint, 0.8f to Color.Black)))
                if (app != null && app.hasBanner) {
                    Image(
                        bitmap = app.image,
                        contentDescription = null,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 90.dp)
                            .width(360.dp)
                            .clip(TileShape),
                        contentScale = ContentScale.Fit,
                    )
                }
            }
        }
    }
    // Voiles sombres : le texte et le dock restent lisibles sur n'importe quelle image
    Box(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(0f to Color(0x99000000), 0.35f to Color.Transparent, 1f to Color(0xCC000000)),
        ),
    )
}

@Composable
private fun TopBar(item: ShelfItem?, appLabel: String?, weather: Weather?, onOpenContent: (ShelfItem) -> Unit) {
    var now by remember { mutableStateOf(LocalTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = LocalTime.now()
            delay((60 - now.second) * 1000L)
        }
    }
    Row(
        Modifier.fillMaxWidth().padding(start = 52.dp, end = 52.dp, top = 34.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                item?.title ?: appLabel.orEmpty(),
                color = Color.White,
                fontSize = 44.sp,
                fontWeight = FontWeight.Black,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            item?.subtitle?.let {
                Text(it, color = Color(0xFFE5E5EA), fontSize = 17.sp, modifier = Modifier.padding(top = 6.dp))
            }
            if (item != null) {
                Spacer(Modifier.height(14.dp))
                ResumeButton { onOpenContent(item) }
            }
        }
        Spacer(Modifier.width(32.dp))
        weather?.let {
            Pill("${weatherIcon(it.code)}  ${it.temperature}°  ${WeatherApi.CITY}")
            Spacer(Modifier.width(10.dp))
        }
        Pill(now.format(DateTimeFormatter.ofPattern("HH:mm")))
    }
}

@Composable
private fun Pill(text: String) {
    Text(
        text,
        modifier = Modifier
            .clip(CircleShape)
            .background(GlassFill)
            .border(1.dp, GlassEdge, CircleShape)
            .padding(horizontal = 16.dp, vertical = 7.dp),
        color = Color.White,
        fontSize = 17.sp,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
private fun ResumeButton(onClick: () -> Unit) {
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
        Text(
            "▶  Reprendre",
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun AppTile(app: AppEntry, onClick: () -> Unit, onFocused: () -> Unit, modifier: Modifier = Modifier) {
    TileSurface(onClick, modifier.onFocusChanged { if (it.isFocused) onFocused() }) {
        Image(
            bitmap = app.image,
            contentDescription = app.label,
            modifier = if (app.hasBanner) Modifier.fillMaxSize() else Modifier.fillMaxSize().padding(16.dp),
            contentScale = if (app.hasBanner) ContentScale.Crop else ContentScale.Fit,
        )
    }
}

@Composable
private fun SettingsTile(onClick: () -> Unit, modifier: Modifier = Modifier) {
    TileSurface(onClick, modifier) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Réglages", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun TileSurface(onClick: () -> Unit, modifier: Modifier, content: @Composable () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = ClickableSurfaceDefaults.shape(TileShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Tile,
            focusedContainerColor = Tile,
            pressedContainerColor = Tile,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.12f),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(border = BorderStroke(2.dp, Color.White), shape = TileShape),
        ),
    ) {
        content()
    }
}
