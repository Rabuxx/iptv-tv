package fr.rabuxx.iptvtv.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import fr.rabuxx.iptvtv.cast.CastController

private const val VOLUME_STEP = 0.05
private const val SEEK_STEP_MS = 10_000L

/** Choix de la Chromecast, puis barre de contrôle tant qu'une lecture est en cours. */
@Composable
fun BoxScope.CastOverlay(cast: CastController) {
    val device = cast.connectedTo
    if (device != null || cast.error != null) {
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp)
                .width(720.dp)
                .background(Glass, RoundedCornerShape(16.dp))
                .padding(horizontal = 18.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                cast.error ?: (cast.title ?: "Connecté"),
                color = if (cast.error != null) ErrorRed else TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            device?.let { Text("Sur $it", color = TextSecondary, fontSize = 12.sp, maxLines = 1) }
            if (device != null && cast.durationMs > 0) CastProgress(cast)
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (device != null && cast.title != null) {
                    ChipButton(label = "-10 s", selected = false, onClick = { cast.seekBy(-SEEK_STEP_MS) })
                    ChipButton(label = if (cast.playing) "Pause" else "Lecture", selected = false, onClick = cast::togglePause)
                    ChipButton(label = "+10 s", selected = false, onClick = { cast.seekBy(SEEK_STEP_MS) })
                }
                Spacer(Modifier.weight(1f))
                if (device != null) {
                    ChipButton(label = "Vol -", selected = false, onClick = { cast.changeVolume(-VOLUME_STEP) })
                    Text("${(cast.volume * 100).toInt()} %", color = TextSecondary, fontSize = 13.sp)
                    ChipButton(label = "Vol +", selected = false, onClick = { cast.changeVolume(VOLUME_STEP) })
                    ChipButton(label = "Arrêter", selected = false, onClick = cast::stop)
                } else {
                    ChipButton(label = "Fermer", selected = false, onClick = cast::clearError)
                }
            }
        }
    }

    if (cast.pickerOpen) {
        BackHandler { cast.closePicker() }
        Box(Modifier.fillMaxSize().background(Color(0xB3000000)), contentAlignment = Alignment.Center) {
            Column(
                Modifier
                    .width(420.dp)
                    .background(Card, RoundedCornerShape(20.dp))
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("Envoyer vers", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                if (cast.devices.isEmpty()) {
                    Text(
                        "Recherche d'une Chromecast sur le Wi-Fi…",
                        color = TextSecondary,
                        fontSize = 14.sp,
                    )
                }
                cast.devices.forEach { route ->
                    ActionButton(route.name, onClick = { cast.connect(route) }, modifier = Modifier.fillMaxWidth())
                }
                ChipButton(label = "Annuler", selected = false, onClick = { cast.closePicker() })
            }
        }
    }
}

/** Barre de lecture de la Chromecast : toucher ou glisser pour se déplacer. */
@Composable
private fun CastProgress(cast: CastController) {
    var scrub by remember { mutableStateOf<Float?>(null) }
    val duration = cast.durationMs
    val fraction = scrub ?: (cast.positionMs.toFloat() / duration).coerceIn(0f, 1f)
    Column {
        Box(
            Modifier
                .fillMaxWidth()
                .height(28.dp)
                .pointerInput(duration) {
                    detectTapGestures { cast.seekTo(seekPosition(it.x, size.width, duration)) }
                }
                .pointerInput(duration) {
                    detectHorizontalDragGestures(
                        onDragStart = { scrub = (it.x / size.width).coerceIn(0f, 1f) },
                        onHorizontalDrag = { change, _ ->
                            change.consume()
                            scrub = (change.position.x / size.width).coerceIn(0f, 1f)
                        },
                        onDragEnd = {
                            scrub?.let { cast.seekTo((it * duration).toLong()) }
                            scrub = null
                        },
                        onDragCancel = { scrub = null },
                    )
                },
            contentAlignment = Alignment.CenterStart,
        ) {
            Box(Modifier.fillMaxWidth().height(6.dp).background(Color(0x4DFFFFFF), CircleShape)) {
                Box(Modifier.fillMaxWidth(fraction).fillMaxHeight().background(Color.White, CircleShape))
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatClock(scrub?.let { (it * duration).toLong() } ?: cast.positionMs), color = TextSecondary, fontSize = 12.sp)
            Text("-" + formatClock(duration - cast.positionMs), color = TextSecondary, fontSize = 12.sp)
        }
    }
}
