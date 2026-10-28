package fr.rabuxx.iptvtv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import fr.rabuxx.iptvtv.data.EpgProgram
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Programmes passés d'une chaîne, regroupés par jour, à relancer comme une vidéo. */
@Composable
fun ReplayScreen(channel: Channel, detail: Detail<List<EpgProgram>>, onPlay: (EpgProgram) -> Unit) {
    val programs = detail.data.orEmpty()
    val byDay = remember(programs) { programs.groupBy { dayKey(it.startTs) } } // déjà trié du plus récent au plus ancien

    var selectedDay by rememberSaveable { mutableStateOf<String?>(null) }
    var lastPlayed by rememberSaveable { mutableStateOf<Long?>(null) }
    val day = selectedDay?.takeIf { it in byDay } ?: byDay.keys.firstOrNull()
    val dayPrograms = day?.let { byDay[it] }.orEmpty()

    val focus = remember { FocusRequester() }
    val listState = rememberLazyListState()
    val focusIndex = dayPrograms.indexOfFirst { it.startTs == lastPlayed }.takeIf { it >= 0 } ?: 0

    LaunchedEffect(dayPrograms.isNotEmpty()) {
        if (dayPrograms.isNotEmpty()) {
            listState.scrollToItem(focusIndex)
            withFrameNanos { }
            runCatching { focus.requestFocus() }
        }
    }

    Column(Modifier.fillMaxSize().padding(top = 28.dp)) {
        // En-tête : logo + nom de la chaîne
        Row(
            Modifier.padding(horizontal = 48.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(
                Modifier.size(width = 88.dp, height = 50.dp).background(Card, RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center,
            ) {
                if (channel.icon != null) {
                    AsyncImage(
                        model = channel.icon,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize().padding(8.dp),
                    )
                }
            }
            Column {
                Text(channel.name, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                Text("Replay · ${channel.archiveDays} jour${if (channel.archiveDays > 1) "s" else ""}", color = TextSecondary, fontSize = 13.sp)
            }
        }

        when {
            detail.loading -> CenteredMessage("Chargement des programmes…")
            detail.error != null -> CenteredMessage(detail.error, ErrorRed)
            programs.isEmpty() -> CenteredMessage("Aucun programme disponible en replay pour cette chaîne.")
            else -> {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 48.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(byDay.keys.toList()) { key ->
                        ChipButton(label = dayLabel(key), selected = key == day, onClick = { selectedDay = key })
                    }
                }
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(start = 48.dp, end = 48.dp, top = 4.dp, bottom = 40.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(dayPrograms.size, key = { "${dayPrograms[it].startTs}-$it" }) { i ->
                        val program = dayPrograms[i]
                        ProgramRow(
                            program = program,
                            modifier = if (i == focusIndex) Modifier.focusRequester(focus) else Modifier,
                            onClick = {
                                lastPlayed = program.startTs
                                onPlay(program)
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProgramRow(program: EpgProgram, modifier: Modifier, onClick: () -> Unit) {
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
            Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Text(
                "${clock(program.startTs)} – ${clock(program.stopTs)}",
                modifier = Modifier.width(96.dp),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = LocalContentColor.current.copy(alpha = 0.7f),
            )
            Column(Modifier.weight(1f)) {
                Text(program.title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                program.description?.let {
                    Text(
                        it,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = LocalContentColor.current.copy(alpha = 0.65f),
                    )
                }
            }
        }
    }
}

private fun clock(ts: Long) = SimpleDateFormat("HH:mm", Locale.FRANCE).format(Date(ts * 1000))

private fun dayKey(ts: Long) = SimpleDateFormat("yyyy-MM-dd", Locale.FRANCE).format(Date(ts * 1000))

/** « Aujourd'hui », « Hier », sinon « lundi 26 oct. ». */
private fun dayLabel(key: String): String {
    val cal = Calendar.getInstance()
    val today = SimpleDateFormat("yyyy-MM-dd", Locale.FRANCE).format(cal.time)
    cal.add(Calendar.DAY_OF_YEAR, -1)
    val yesterday = SimpleDateFormat("yyyy-MM-dd", Locale.FRANCE).format(cal.time)
    return when (key) {
        today -> "Aujourd'hui"
        yesterday -> "Hier"
        else -> runCatching {
            val date = SimpleDateFormat("yyyy-MM-dd", Locale.FRANCE).parse(key)!!
            SimpleDateFormat("EEEE d MMM", Locale.FRANCE).format(date).replaceFirstChar { it.uppercase() }
        }.getOrDefault(key)
    }
}
