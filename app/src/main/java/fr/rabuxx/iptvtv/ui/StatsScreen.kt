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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
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
import fr.rabuxx.iptvtv.data.Ranked
import fr.rabuxx.iptvtv.data.StatsPeriod
import fr.rabuxx.iptvtv.data.StatsSummary
import fr.rabuxx.iptvtv.data.WatchItem
import fr.rabuxx.iptvtv.data.WatchStatsCalc.formatHours
import fr.rabuxx.iptvtv.data.WatchType

/** « Mon bilan » : temps passé, répartition, palmarès et activité jour par jour. */
@Composable
fun StatsScreen(
    summary: StatsSummary?,
    period: StatsPeriod,
    onPeriod: (StatsPeriod) -> Unit,
    onReset: () -> Unit,
) {
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { first.requestFocus() }
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 48.dp, end = 48.dp, top = 28.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item("header") {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Mon bilan", fontSize = 28.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                StatsPeriod.entries.forEachIndexed { i, p ->
                    ChipButton(
                        label = p.label,
                        selected = p == period,
                        onClick = { onPeriod(p) },
                        modifier = if (i == 0) Modifier.focusRequester(first) else Modifier,
                    )
                }
                ChipButton(label = "Réinitialiser", selected = false, onClick = {}, onLongClick = onReset)
            }
            Text(
                "Pour réinitialiser, maintiens OK sur « Réinitialiser ».",
                color = Color(0x66FFFFFF),
                fontSize = 10.sp,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        if (summary == null) {
            item("loading") { Text("Calcul du bilan…", color = TextSecondary, fontSize = 14.sp) }
            return@LazyColumn
        }
        if (summary.totalSeconds < 60) {
            item("empty") {
                Text(
                    "Rien de regardé sur cette période pour l'instant.\nLe bilan se remplit au fil de tes visionnages.",
                    color = TextSecondary,
                    fontSize = 14.sp,
                )
            }
            return@LazyColumn
        }

        item("tiles") {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile("Au total", formatHours(summary.totalSeconds), Modifier.weight(1f))
                StatTile("Direct", formatHours(summary.byType[WatchType.Live] ?: 0L), Modifier.weight(1f))
                StatTile("Films", formatHours(summary.byType[WatchType.Movie] ?: 0L), Modifier.weight(1f))
                StatTile("Séries", formatHours(summary.byType[WatchType.Series] ?: 0L), Modifier.weight(1f))
                StatTile(
                    "Jours actifs",
                    "${summary.activeDays}",
                    Modifier.weight(1f),
                    hint = "record : ${summary.longestStreak} j d'affilée",
                )
            }
        }

        item("chart") { ActivityChart(summary, period) }

        item("tops") {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                TopList("Chaînes", summary.topChannels, wideImage = true, modifier = Modifier.weight(1f))
                TopList("Séries", summary.topSeries, wideImage = false, modifier = Modifier.weight(1f))
                TopList("Films", summary.topMovies, wideImage = false, modifier = Modifier.weight(1f))
            }
        }

        if (summary.topGenres.isNotEmpty()) {
            item("genres") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Genres préférés", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        summary.topGenres.forEach { g ->
                            ChipButton(label = "${g.value} · ${formatHours(g.seconds)}", selected = false, onClick = {})
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier, hint: String? = null) {
    Column(
        modifier.background(Card, RoundedCornerShape(14.dp)).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(label, color = TextSecondary, fontSize = 12.sp)
        Text(value, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        if (hint != null) Text(hint, color = TextSecondary, fontSize = 10.sp, maxLines = 1)
    }
}

/** Barres par jour (7 ou 30 jours) ou par mois (12 mois). Focusable pour faire défiler l'écran. */
@Composable
private fun ActivityChart(summary: StatsSummary, period: StatsPeriod) {
    val bars: List<Ranked<String>> = if (period == StatsPeriod.Year) {
        // Regroupement par mois : « 2026-10 »
        summary.perDay.groupBy { it.value.take(7) }.map { (month, days) -> Ranked(month, days.sumOf { it.seconds }) }
    } else {
        summary.perDay
    }
    val max = bars.maxOfOrNull { it.seconds }?.coerceAtLeast(1) ?: 1
    Surface(
        onClick = {},
        modifier = Modifier.fillMaxWidth(),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(14.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Card,
            contentColor = TextPrimary,
            focusedContainerColor = Color(0xFF3A3A3C),
            focusedContentColor = TextPrimary,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                if (period == StatsPeriod.Year) "Temps regardé par mois" else "Temps regardé par jour",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Row(
                Modifier.fillMaxWidth().height(110.dp),
                horizontalArrangement = Arrangement.spacedBy(if (bars.size > 14) 3.dp else 8.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                bars.forEach { bar ->
                    val fraction = bar.seconds.toFloat() / max
                    Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.BottomCenter) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .fillMaxHeight(fraction.coerceAtLeast(if (bar.seconds > 0) 0.03f else 0.01f))
                                .background(
                                    if (bar.seconds > 0) Color.White else Color(0x22FFFFFF),
                                    RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp),
                                ),
                        )
                    }
                }
            }
            // Repères : premier, milieu et dernier libellé
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                listOfNotNull(bars.firstOrNull(), bars.getOrNull(bars.size / 2), bars.lastOrNull()).forEach {
                    Text(axisLabel(it.value, period), color = TextSecondary, fontSize = 10.sp)
                }
            }
        }
    }
}

private val MONTHS = listOf("janv.", "févr.", "mars", "avr.", "mai", "juin", "juil.", "août", "sept.", "oct.", "nov.", "déc.")

/** « 2026-10-29 » → « 29 oct. », « 2026-10 » → « oct. 2026 ». */
private fun axisLabel(key: String, period: StatsPeriod): String {
    val parts = key.split('-')
    val month = parts.getOrNull(1)?.toIntOrNull()?.let { MONTHS.getOrNull(it - 1) } ?: return key
    return if (period == StatsPeriod.Year) "$month ${parts[0]}" else "${parts.getOrNull(2)?.trimStart('0')} $month"
}

@Composable
private fun TopList(title: String, items: List<Ranked<WatchItem>>, wideImage: Boolean, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        if (items.isEmpty()) {
            Text("—", color = TextSecondary, fontSize = 13.sp)
        }
        items.forEachIndexed { i, r ->
            Surface(
                onClick = {},
                modifier = Modifier.fillMaxWidth(),
                shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
                colors = ClickableSurfaceDefaults.colors(
                    containerColor = Color(0x14FFFFFF),
                    contentColor = TextPrimary,
                    focusedContainerColor = FocusFill,
                    focusedContentColor = FocusText,
                ),
                scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
            ) {
                Row(
                    Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text("${i + 1}", fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(14.dp))
                    Box(
                        Modifier
                            .size(width = if (wideImage) 48.dp else 26.dp, height = if (wideImage) 28.dp else 38.dp)
                            .background(Color(0x22000000), RoundedCornerShape(4.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        r.value.image?.let {
                            AsyncImage(
                                model = it,
                                contentDescription = null,
                                contentScale = if (wideImage) ContentScale.Fit else ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                    Column(Modifier.weight(1f)) {
                        Text(r.value.name, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(formatHours(r.seconds), fontSize = 11.sp, color = LocalContentColor.current.copy(alpha = 0.65f))
                    }
                }
            }
        }
    }
}
