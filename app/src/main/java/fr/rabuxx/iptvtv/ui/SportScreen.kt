package fr.rabuxx.iptvtv.ui

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
    hasReminder: (SportProgram) -> Boolean,
    onToggleReminder: (SportProgram) -> Unit,
    onMultiView: ((List<Channel>) -> Unit)?,
) {
    val data = detail.data
    // Filtre par chaîne : « Tout », « Canal+ », « beIN Sports »…
    var group by rememberSaveable { mutableStateOf("") }
    val live = data?.live.orEmpty().filter { group.isEmpty() || it.group == group }
    val upcoming = data?.upcoming.orEmpty().filter { group.isEmpty() || it.group == group }
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
                    SimpleDateFormat("EEEE d MMMM", Locale.FRANCE).format(Date()).replaceFirstChar { it.uppercase() } +
                        "  ·  maintiens OK sur un match à venir pour être prévenu",
                    color = TextSecondary,
                    fontSize = 13.sp,
                )
            }
            if (onMultiView != null && data != null && data.live.isNotEmpty()) {
                ChipButton(
                    label = "▦ Multi-écran",
                    selected = false,
                    // Les chaînes sport qui diffusent un match en ce moment
                    onClick = { onMultiView(data.live.map { it.channel }.distinctBy { it.streamId }) },
                )
                Spacer(Modifier.width(8.dp))
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
            else -> Column {
                if (data.groups.size > 1) {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 48.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(top = 12.dp),
                    ) {
                        item { ChipButton(label = "Tout", selected = group.isEmpty(), onClick = { group = "" }) }
                        items(data.groups) { g -> ChipButton(label = g, selected = group == g, onClick = { group = g }) }
                    }
                }
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(start = 48.dp, end = 48.dp, top = 16.dp, bottom = 40.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (live.isEmpty() && upcoming.isEmpty()) {
                        item("none") { Text("Rien sur ces chaînes dans les 12 prochaines heures.", color = TextSecondary, fontSize = 14.sp) }
                    }
                    if (live.isNotEmpty()) {
                        item("live-title") { GroupTitle("En direct") }
                        itemsIndexed(live, key = { i, p -> "live-${p.channel.streamId}-$i" }) { i, p ->
                            SportRow(
                                p = p,
                                live = true,
                                modifier = if (i == 0) Modifier.focusRequester(firstFocus) else Modifier,
                                onClick = { onPlay(data.channels, data.channels.indexOf(p.channel).coerceAtLeast(0)) },
                            )
                        }
                    }
                    if (upcoming.isNotEmpty()) {
                        // Regroupé par heure de début : « 21:00 », plusieurs matchs en même temps restent ensemble
                        var lastHour = ""
                        upcoming.forEachIndexed { i, p ->
                            val hour = dayAndHour(p.program.startTs)
                            if (hour != lastHour) {
                                lastHour = hour
                                item("next-title-$i") { GroupTitle(hour) }
                            }
                            item("next-${p.channel.streamId}-${p.program.startTs}-$i") {
                                SportRow(
                                    p = p,
                                    live = false,
                                    reminder = hasReminder(p),
                                    modifier = if (live.isEmpty() && i == 0) Modifier.focusRequester(firstFocus) else Modifier,
                                    onClick = { onPlay(data.channels, data.channels.indexOf(p.channel).coerceAtLeast(0)) },
                                    onLongClick = { onToggleReminder(p) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** « À 21:00 », ou « Demain à 01:00 » après minuit. */
private fun dayAndHour(ts: Long): String {
    val day = SimpleDateFormat("yyyyMMdd", Locale.FRANCE)
    val today = day.format(Date())
    val time = SimpleDateFormat("HH:mm", Locale.FRANCE).format(Date(ts * 1000))
    return if (day.format(Date(ts * 1000)) == today) "À $time" else "Demain à $time"
}

@Composable
private fun GroupTitle(text: String) {
    Text(text, modifier = Modifier.padding(top = 10.dp, bottom = 4.dp), fontSize = 16.sp, fontWeight = FontWeight.Bold)
}

@Composable
private fun SportRow(
    p: SportProgram,
    live: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
    reminder: Boolean = false,
    onLongClick: (() -> Unit)? = null,
) {
    Surface(
        onClick = onClick,
        onLongClick = onLongClick,
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
                when {
                    live -> "● Direct"
                    reminder -> "🔔 " + clock(p.program.startTs)
                    else -> clock(p.program.startTs)
                },
                modifier = Modifier.width(84.dp),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (live) Color(0xFFFF453A) else LocalContentColor.current.copy(alpha = 0.75f),
            )
            Column(Modifier.weight(1f)) {
                Text(p.program.title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    p.label + if (live) "  ·  jusqu'à ${clock(p.program.stopTs)}" else "",
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
