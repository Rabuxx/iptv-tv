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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import fr.rabuxx.iptvtv.Detail
import fr.rabuxx.iptvtv.data.Channel
import fr.rabuxx.iptvtv.data.SportProgram
import fr.rabuxx.iptvtv.data.SportTonight
import fr.rabuxx.iptvtv.data.progress
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** « Sport ce soir » : ce qui passe en ce moment puis ce qui arrive, sur les chaînes sport FR. */
@Composable
fun SportScreen(
    detail: Detail<SportTonight>,
    onPlay: (channels: List<Channel>, index: Int) -> Unit,
    onReload: () -> Unit,
) {
    val data = detail.data
    val firstFocus = remember { FocusRequester() }
    val listState = rememberLazyListState()

    LaunchedEffect(data != null) {
        if (data != null) {
            withFrameNanos { }
            runCatching { firstFocus.requestFocus() }
        }
    }

    Column(Modifier.fillMaxSize().padding(top = 26.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 48.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Sport ce soir", fontSize = 28.sp, fontWeight = FontWeight.Bold)
                Text(
                    SimpleDateFormat("EEEE d MMMM", Locale.FRANCE).format(Date()).replaceFirstChar { it.uppercase() },
                    color = TextSecondary,
                    fontSize = 13.sp,
                )
            }
            ChipButton(
                label = if (detail.loading) "Chargement…" else "↻ Actualiser",
                selected = detail.loading,
                onClick = { if (!detail.loading) onReload() },
            )
        }

        when {
            data == null && detail.loading -> CenteredMessage("Chargement du programme des chaînes sport…")
            data == null && detail.error != null -> CenteredMessage(detail.error, ErrorRed)
            data == null -> CenteredMessage("Aucun programme")
            data.channels.isEmpty() -> CenteredMessage("Aucune catégorie sport trouvée chez ton fournisseur.")
            data.live.isEmpty() && data.upcoming.isEmpty() -> CenteredMessage(
                "Le guide TV de tes chaînes sport est vide.\nTon fournisseur ne le renseigne peut-être pas.",
            )
            else -> LazyColumn(
                state = listState,
                contentPadding = PaddingValues(start = 48.dp, end = 48.dp, top = 16.dp, bottom = 40.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (data.live.isNotEmpty()) {
                    item("live-title") { GroupTitle("En direct") }
                    itemsIndexed(data.live, key = { i, p -> "live-${p.channel.streamId}-$i" }) { i, p ->
                        SportRow(
                            p = p,
                            live = true,
                            modifier = if (i == 0) Modifier.focusRequester(firstFocus) else Modifier,
                            onClick = { onPlay(data.channels, data.channels.indexOf(p.channel).coerceAtLeast(0)) },
                        )
                    }
                }
                if (data.upcoming.isNotEmpty()) {
                    item("next-title") { GroupTitle("À venir") }
                    itemsIndexed(data.upcoming, key = { i, p -> "next-${p.channel.streamId}-${p.program.startTs}-$i" }) { i, p ->
                        SportRow(
                            p = p,
                            live = false,
                            modifier = if (data.live.isEmpty() && i == 0) Modifier.focusRequester(firstFocus) else Modifier,
                            onClick = { onPlay(data.channels, data.channels.indexOf(p.channel).coerceAtLeast(0)) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GroupTitle(text: String) {
    Text(text, modifier = Modifier.padding(top = 10.dp, bottom = 4.dp), fontSize = 16.sp, fontWeight = FontWeight.Bold)
}

@Composable
private fun SportRow(p: SportProgram, live: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color(0x14FFFFFF),
            contentColor = TextPrimary,
            focusedContainerColor = FocusFill,
            focusedContentColor = FocusText,
            pressedContainerColor = FocusFill,
            pressedContentColor = FocusText,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                Modifier.size(width = 64.dp, height = 36.dp).background(Color(0x1AFFFFFF), RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center,
            ) {
                if (p.channel.icon != null) {
                    AsyncImage(
                        model = p.channel.icon,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize().padding(5.dp),
                    )
                }
            }
            Text(
                if (live) "● Direct" else clock(p.program.startTs),
                modifier = Modifier.width(64.dp),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (live) Color(0xFFFF453A) else LocalContentColor.current.copy(alpha = 0.75f),
            )
            Column(Modifier.weight(1f)) {
                Text(p.program.title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    p.channel.name + if (live) "  ·  jusqu'à ${clock(p.program.stopTs)}" else "",
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = LocalContentColor.current.copy(alpha = 0.65f),
                )
            }
            if (live) {
                Box(
                    Modifier
                        .size(width = 70.dp, height = 4.dp)
                        .background(LocalContentColor.current.copy(alpha = 0.25f), RoundedCornerShape(2.dp)),
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(p.program.progress())
                            .fillMaxHeight()
                            .background(LocalContentColor.current, RoundedCornerShape(2.dp)),
                    )
                }
            }
        }
    }
}

private fun clock(ts: Long) = SimpleDateFormat("HH:mm", Locale.FRANCE).format(Date(ts * 1000))
