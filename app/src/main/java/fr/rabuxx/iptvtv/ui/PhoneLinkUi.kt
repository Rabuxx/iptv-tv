package fr.rabuxx.iptvtv.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import fr.rabuxx.iptvtv.data.PhoneLink
import fr.rabuxx.iptvtv.data.PhoneLinkMode

/** QR code net (sans lissage) à partir d'un texte. */
@Composable
fun QrCode(text: String, size: Dp, modifier: Modifier = Modifier) {
    val bitmap = remember(text) {
        val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 0, 0, mapOf(EncodeHintType.MARGIN to 1))
        val w = matrix.width
        val h = matrix.height
        val pixels = IntArray(w * h) { i -> if (matrix.get(i % w, i / w)) android.graphics.Color.BLACK else android.graphics.Color.WHITE }
        Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888).asImageBitmap()
    }
    Image(
        bitmap = bitmap,
        contentDescription = null,
        filterQuality = FilterQuality.None,
        modifier = modifier
            .size(size)
            .background(Color.White, RoundedCornerShape(10.dp))
            .padding(6.dp),
    )
}

/**
 * Démarre le serveur local tant que l'écran est affiché.
 * @return l'adresse à ouvrir sur le téléphone, ou null (démarrage en cours ou TV hors réseau local).
 */
@Composable
fun rememberPhoneLinkUrl(mode: PhoneLinkMode, onSubmit: (Map<String, String>) -> Unit): String? {
    var url by remember(mode) { mutableStateOf<String?>(null) }
    val callback by rememberUpdatedState(onSubmit)
    DisposableEffect(mode) {
        val link = PhoneLink(mode) { form -> callback(form) }
        // Ouverture du port hors du fil principal
        Thread { url = link.start() }.start()
        onDispose { link.stop() }
    }
    return url
}

/** Encadré « Depuis ton téléphone » : QR code + adresse. */
@Composable
fun PhoneLinkCard(url: String?, title: String, modifier: Modifier = Modifier) {
    Column(
        modifier
            .background(Glass, RoundedCornerShape(28.dp))
            .padding(horizontal = 28.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        if (url != null) {
            QrCode(url, size = 170.dp)
            Text(
                "Scanne avec l'appareil photo de ton téléphone,\nconnecté au même Wi-Fi que la TV.",
                color = TextSecondary,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
            )
            Text(url, color = Color(0xCCFFFFFF), fontSize = 12.sp, textAlign = TextAlign.Center)
        } else {
            Box(Modifier.size(170.dp), contentAlignment = Alignment.Center) {
                Text(
                    "Indisponible : la TV n'a pas d'adresse sur le réseau local.",
                    color = TextSecondary,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
