package fr.rabuxx.iptvtv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import fr.rabuxx.iptvtv.data.DownloadItem
import fr.rabuxx.iptvtv.data.DownloadState
import fr.rabuxx.iptvtv.data.downloadDetail
import fr.rabuxx.iptvtv.data.formatBytes

@Composable
fun DownloadsScreen(
    items: List<DownloadItem>,
    freeBytes: Long,
    onPlay: (DownloadItem) -> Unit,
    onRetry: (DownloadItem) -> Unit,
    onRemove: (DownloadItem) -> Unit,
    onCast: ((DownloadItem) -> Unit)? = null,
) {
    Column(Modifier.fillMaxSize().padding(horizontal = 48.dp, vertical = 28.dp)) {
        Text("Téléchargements", fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Text(
            "${formatBytes(freeBytes)} libres · Wi-Fi uniquement · un téléchargement à la fois",
            color = TextSecondary,
            fontSize = 14.sp,
            modifier = Modifier.padding(top = 4.dp, bottom = 16.dp),
        )
        if (items.isEmpty()) {
            CenteredMessage("Télécharge un film ou un épisode depuis sa fiche pour le regarder hors ligne.")
        } else {
            LazyColumn(
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(items, key = { it.id }) { item ->
                    DownloadRow(item, { onPlay(item) }, { onRetry(item) }, { onRemove(item) }, onCast?.let { cast -> { cast(item) } })
                }
            }
        }
    }
}

@Composable
private fun DownloadRow(
    item: DownloadItem,
    onPlay: () -> Unit,
    onRetry: () -> Unit,
    onRemove: () -> Unit,
    onCast: (() -> Unit)?,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Glass, RoundedCornerShape(14.dp))
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(item.title, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            item.subtitle?.let {
                Text(it, color = TextSecondary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(downloadDetail(item), color = if (item.state == DownloadState.Failed) ErrorRed else TextSecondary, fontSize = 13.sp)
            if (item.state == DownloadState.Running) {
                Box(Modifier.fillMaxWidth().height(4.dp).background(Color(0x4DFFFFFF), CircleShape)) {
                    Box(Modifier.fillMaxWidth(item.progress).fillMaxHeight().background(Color.White, CircleShape))
                }
            }
        }
        when (item.state) {
            DownloadState.Done -> {
                ActionButton("▶  Lire", onClick = onPlay)
                onCast?.let { ActionButton("Chromecast", onClick = it) }
            }
            DownloadState.Failed -> ActionButton("Réessayer", onClick = onRetry)
            else -> Unit
        }
        ChipButton(label = "Supprimer", selected = false, onClick = onRemove)
    }
}
