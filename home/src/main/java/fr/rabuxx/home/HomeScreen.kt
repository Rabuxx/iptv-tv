package fr.rabuxx.home

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private val Background = Color(0xFF000000)
private val Tile = Color(0xFF2C2C2E)
private val Secondary = Color(0xFFD1D1D6)
private val TileShape = RoundedCornerShape(14.dp)

/** Accueil façon tvOS : une grande zone en haut qui suit l'app ayant le focus, et la rangée d'apps en bas. */
@Composable
fun HomeScreen(apps: List<AppEntry>, onOpen: (AppEntry) -> Unit, onSettings: () -> Unit) {
    var focusedPackage by remember { mutableStateOf<String?>(null) }
    val firstTile = remember { FocusRequester() }
    val current = apps.firstOrNull { it.packageName == focusedPackage } ?: apps.firstOrNull()

    // Au démarrage, ou si l'app qui avait le focus vient d'être désinstallée
    LaunchedEffect(apps.map { it.packageName }) {
        if (apps.isNotEmpty() && apps.none { it.packageName == focusedPackage }) {
            runCatching { firstTile.requestFocus() }
        }
    }

    // Le fond reste assez sombre pour que le texte blanc soit lisible, quelle que soit l'app
    val tint by animateColorAsState(
        targetValue = lerp(current?.accent ?: Tile, Background, 0.55f),
        animationSpec = tween(450),
        label = "tint",
    )

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(0f to tint, 0.72f to Background)),
    ) {
        Column(Modifier.fillMaxSize()) {
            TopBar(onSettings)
            Hero(current, Modifier.weight(1f).fillMaxWidth())
            LazyRow(
                modifier = Modifier.fillMaxWidth().height(170.dp),
                contentPadding = PaddingValues(horizontal = 58.dp, vertical = 22.dp),
                horizontalArrangement = Arrangement.spacedBy(22.dp),
            ) {
                itemsIndexed(apps, key = { _, app -> app.packageName }) { index, app ->
                    AppTile(
                        app = app,
                        onClick = { onOpen(app) },
                        onFocused = { focusedPackage = app.packageName },
                        modifier = if (index == 0) Modifier.focusRequester(firstTile) else Modifier,
                    )
                }
            }
        }
    }
}

@Composable
private fun TopBar(onSettings: () -> Unit) {
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = LocalDateTime.now()
            delay((60 - now.second) * 1000L)
        }
    }
    Row(
        Modifier.fillMaxWidth().padding(start = 58.dp, end = 58.dp, top = 30.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            now.format(DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.FRENCH)).replaceFirstChar { it.uppercase() },
            color = Secondary,
            fontSize = 16.sp,
        )
        Spacer(Modifier.weight(1f))
        Surface(
            onClick = onSettings,
            shape = ClickableSurfaceDefaults.shape(CircleShape),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = Color(0x33FFFFFF),
                contentColor = Color.White,
                focusedContainerColor = Color.White,
                focusedContentColor = Color.Black,
                pressedContainerColor = Color.White,
                pressedContentColor = Color.Black,
            ),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.08f),
        ) {
            Text(
                "Réglages",
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 7.dp),
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Spacer(Modifier.width(24.dp))
        Text(
            now.format(DateTimeFormatter.ofPattern("HH:mm")),
            color = Color.White,
            fontSize = 26.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun Hero(app: AppEntry?, modifier: Modifier = Modifier) {
    Crossfade(targetState = app, modifier = modifier, animationSpec = tween(300), label = "hero") { shown ->
        Row(
            Modifier.fillMaxSize().padding(horizontal = 58.dp, vertical = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(48.dp),
        ) {
            if (shown == null) {
                Text("Chargement…", color = Secondary, fontSize = 22.sp)
                return@Row
            }
            Text(
                shown.label,
                modifier = Modifier.weight(1f),
                color = Color.White,
                fontSize = 52.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Box(
                Modifier.fillMaxHeight().width(420.dp),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    bitmap = shown.image,
                    contentDescription = null,
                    modifier = Modifier.clip(TileShape),
                    contentScale = ContentScale.Fit,
                )
            }
        }
    }
}

@Composable
private fun AppTile(app: AppEntry, onClick: () -> Unit, onFocused: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        modifier = modifier
            .size(width = 196.dp, height = 110.dp)
            .onFocusChanged { if (it.isFocused) onFocused() },
        shape = ClickableSurfaceDefaults.shape(TileShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Tile,
            focusedContainerColor = Tile,
            pressedContainerColor = Tile,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.14f),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(border = BorderStroke(3.dp, Color.White), shape = TileShape),
        ),
    ) {
        Image(
            bitmap = app.image,
            contentDescription = app.label,
            modifier = if (app.hasBanner) Modifier.fillMaxSize() else Modifier.fillMaxSize().padding(22.dp),
            contentScale = if (app.hasBanner) ContentScale.Crop else ContentScale.Fit,
        )
    }
}
