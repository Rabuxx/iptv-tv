package fr.rabuxx.iptvtv.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import fr.rabuxx.iptvtv.cast.CastController

private const val VOLUME_STEP = 0.05

/** Choix de la Chromecast, puis barre de contrôle tant qu'une lecture est en cours. */
@Composable
fun BoxScope.CastOverlay(cast: CastController) {
    val device = cast.connectedTo
    if (device != null || cast.error != null) {
        Row(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp)
                .width(640.dp)
                .background(Glass, RoundedCornerShape(16.dp))
                .padding(horizontal = 18.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    cast.error ?: (cast.title ?: "Connecté"),
                    color = if (cast.error != null) ErrorRed else TextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                device?.let { Text("Sur $it", color = TextSecondary, fontSize = 12.sp, maxLines = 1) }
            }
            if (device != null && cast.title != null) {
                ChipButton(label = if (cast.playing) "Pause" else "Lecture", selected = false, onClick = cast::togglePause)
            }
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
