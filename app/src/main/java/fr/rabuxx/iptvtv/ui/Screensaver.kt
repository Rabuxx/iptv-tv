package fr.rabuxx.iptvtv.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val SLIDE_MS = 9_000

/**
 * Économiseur d'écran : diaporama des images de fond (avec un lent zoom), ou mur d'affiches qui défile.
 * N'importe quelle touche le ferme (géré par MainActivity).
 */
@Composable
fun Screensaver(images: List<String>, wide: Boolean) {
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        when {
            images.isEmpty() -> Unit
            wide -> Slideshow(images)
            else -> PosterWall(images)
        }

        // Voile pour la lisibilité de l'heure
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(0.55f to Color.Transparent, 1f to Color(0xB3000000))),
        )

        var now by remember { mutableStateOf(Date()) }
        LaunchedEffect(Unit) {
            while (true) {
                now = Date()
                delay(10_000)
            }
        }
        Column(Modifier.align(Alignment.BottomEnd).padding(end = 56.dp, bottom = 40.dp), horizontalAlignment = Alignment.End) {
            Text(SimpleDateFormat("HH:mm", Locale.FRANCE).format(now), fontSize = 56.sp, fontWeight = FontWeight.Light)
            Text(
                SimpleDateFormat("EEEE d MMMM", Locale.FRANCE).format(now).replaceFirstChar { it.uppercase() },
                fontSize = 16.sp,
                color = Color(0xCCFFFFFF),
            )
        }
        Text(
            "Rabux TV",
            modifier = Modifier.align(Alignment.BottomStart).padding(start = 56.dp, bottom = 44.dp),
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color(0x99FFFFFF),
        )
    }
}

@Composable
private fun Slideshow(images: List<String>) {
    var index by remember { mutableIntStateOf(0) }
    LaunchedEffect(images) {
        while (true) {
            delay(SLIDE_MS.toLong())
            index = (index + 1) % images.size
        }
    }
    Crossfade(targetState = index, animationSpec = tween(1_500), label = "diaporama") { i ->
        // Zoom lent (effet Ken Burns), relancé à chaque image
        val transition = rememberInfiniteTransition(label = "zoom")
        val scale by transition.animateFloat(
            initialValue = 1f,
            targetValue = 1.12f,
            animationSpec = infiniteRepeatable(tween(SLIDE_MS + 1_500, easing = LinearEasing), RepeatMode.Reverse),
            label = "zoom",
        )
        AsyncImage(
            model = images[i % images.size],
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                },
        )
    }
}

@Composable
private fun PosterWall(images: List<String>) {
    val transition = rememberInfiniteTransition(label = "mur")
    val shift by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(60_000, easing = LinearEasing), RepeatMode.Reverse),
        label = "mur",
    )
    val rows = images.chunked((images.size / 3).coerceAtLeast(1)).take(3)
    Column(
        Modifier.fillMaxSize().padding(vertical = 16.dp).graphicsLayer { alpha = 0.75f },
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        rows.forEachIndexed { r, row ->
            // Les rangées glissent en sens inverse l'une de l'autre
            val dx = if (r % 2 == 0) -240f * shift else -240f * (1f - shift)
            Row(
                // Rangée plus large que l'écran : elle dépasse et glisse
                Modifier
                    .wrapContentWidth(align = Alignment.Start, unbounded = true)
                    .offset(x = dx.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                (row + row).forEach { url ->
                    AsyncImage(
                        model = url,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .width(116.dp)
                            .aspectRatio(2f / 3f)
                            .clip(RoundedCornerShape(10.dp)),
                    )
                }
            }
        }
    }
}
