package fr.rabuxx.iptvtv.ui

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import fr.rabuxx.iptvtv.Detail
import fr.rabuxx.iptvtv.data.Episode
import fr.rabuxx.iptvtv.data.Movie
import fr.rabuxx.iptvtv.data.MovieInfo
import fr.rabuxx.iptvtv.data.Series
import fr.rabuxx.iptvtv.data.SeriesInfo

/** Image de fond plein écran avec dégradés de lisibilité, comme les fiches de l'app TV d'Apple. */
@Composable
private fun Backdrop(backdrop: String?, fallbackPoster: String?) {
    val image = backdrop ?: fallbackPoster
    Box(Modifier.fillMaxSize()) {
        if (image != null) {
            // Si on n'a qu'une affiche (portrait), on la floute pour en faire un fond d'ambiance
            val blurred = backdrop == null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
            AsyncImage(
                model = image,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .then(if (blurred) Modifier.blur(40.dp) else Modifier),
                alpha = if (backdrop == null) 0.6f else 1f,
            )
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        0f to Color(0xF2000000),
                        0.45f to Color(0x99000000),
                        0.8f to Color.Transparent,
                    ),
                ),
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0.35f to Color.Transparent,
                        1f to Color(0xF2000000),
                    ),
                ),
        )
    }
}

@Composable
private fun Title(text: String) {
    Text(
        text,
        fontSize = 34.sp,
        fontWeight = FontWeight.Bold,
        lineHeight = 40.sp,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun Plot(text: String?, maxLines: Int) {
    if (!text.isNullOrBlank()) {
        Text(
            text,
            color = Color(0xE6FFFFFF),
            fontSize = 14.sp,
            lineHeight = 20.sp,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// ============================== FILM ==============================

@Composable
fun MovieDetailScreen(
    movie: Movie,
    detail: Detail<MovieInfo>,
    resumeMs: Long,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onPlay: (extension: String?, fromStart: Boolean) -> Unit,
) {
    val info = detail.data
    val playFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { playFocus.requestFocus() }
    }

    Box(Modifier.fillMaxSize()) {
        Backdrop(info?.backdrop, info?.poster ?: movie.poster)

        Column(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth(0.58f)
                .padding(start = 48.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Title(movie.name)
            MetaLine(
                listOf(
                    info?.year,
                    info?.genre,
                    formatDuration(info?.duration),
                    formatRating(info?.rating ?: movie.rating),
                ),
            )
            when {
                detail.loading -> Text("Chargement des informations…", color = TextSecondary, fontSize = 13.sp)
                info != null -> Plot(info.plot, maxLines = 4)
            }

            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                val ext = info?.extension
                if (resumeMs > 0) {
                    ActionButton(
                        "▶  Reprendre à ${formatClock(resumeMs)}",
                        onClick = { onPlay(ext, false) },
                        modifier = Modifier.focusRequester(playFocus),
                    )
                    ActionButton("Depuis le début", onClick = { onPlay(ext, true) })
                } else {
                    ActionButton(
                        "▶  Lire",
                        onClick = { onPlay(ext, true) },
                        modifier = Modifier.focusRequester(playFocus),
                    )
                }
                ActionButton(if (isFavorite) "★  Favori" else "☆  Favoris", onClick = onToggleFavorite)
            }

            info?.director?.let { Text("Réalisation : $it", color = TextSecondary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            info?.cast?.let { Text("Avec : $it", color = TextSecondary, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis) }
        }
    }
}

// ============================== SÉRIE ==============================

@Composable
fun SeriesDetailScreen(
    series: Series,
    detail: Detail<SeriesInfo>,
    preferredEpisodeId: String?,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    progressOf: (Episode) -> Float,
    onPlay: (Episode) -> Unit,
) {
    val info = detail.data
    val seasons = info?.episodes?.keys?.toList().orEmpty()

    // Saison choisie et dernier épisode lancé : conservés quand on revient du lecteur
    var season by rememberSaveable { mutableIntStateOf(-1) }
    // Depuis « À suivre », on arrive directement sur l'épisode en cours
    var lastEpisodeId by rememberSaveable { mutableStateOf(preferredEpisodeId) }
    val currentSeason = if (season in seasons) {
        season
    } else {
        seasons.firstOrNull { s -> info?.episodes?.get(s)?.any { it.id == lastEpisodeId } == true } ?: seasons.firstOrNull()
    }
    val episodes = currentSeason?.let { info?.episodes?.get(it) }.orEmpty()

    val episodeFocus = remember { FocusRequester() }
    val listState = rememberLazyListState()
    val focusIndex = episodes.indexOfFirst { it.id == lastEpisodeId }.takeIf { it >= 0 } ?: 0

    // Le focus va sur le dernier épisode lancé, sinon sur le premier, dès que la liste est chargée
    LaunchedEffect(episodes.isNotEmpty()) {
        if (episodes.isNotEmpty()) {
            if (listState.layoutInfo.visibleItemsInfo.none { it.index == focusIndex }) {
                listState.scrollToItem(focusIndex)
            }
            withFrameNanos { }
            runCatching { episodeFocus.requestFocus() }
        }
    }

    Box(Modifier.fillMaxSize()) {
        Backdrop(info?.backdrop ?: series.backdrop, series.cover)

        Column(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(
                Modifier.fillMaxWidth(0.6f).padding(start = 48.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Title(series.name)
                MetaLine(
                    listOf(
                        info?.year ?: series.year,
                        info?.genre ?: series.genre,
                        seasons.size.takeIf { it > 0 }?.let { if (it > 1) "$it saisons" else "1 saison" },
                        formatRating(info?.rating ?: series.rating),
                    ),
                )
                Plot(info?.plot ?: series.plot, maxLines = 3)
                ActionButton(if (isFavorite) "★  Favori" else "☆  Favoris", onClick = onToggleFavorite)
            }

            when {
                detail.loading -> Text("Chargement des épisodes…", color = TextSecondary, modifier = Modifier.padding(start = 48.dp, top = 16.dp))
                detail.error != null -> Text(detail.error, color = ErrorRed, modifier = Modifier.padding(start = 48.dp, top = 16.dp))
                seasons.isEmpty() -> Text("Aucun épisode disponible", color = TextSecondary, modifier = Modifier.padding(start = 48.dp, top = 16.dp))
                else -> {
                    if (seasons.size > 1) {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 48.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            itemsIndexed(seasons) { _, s ->
                                ChipButton(
                                    label = "Saison $s",
                                    selected = s == currentSeason,
                                    onClick = { season = s },
                                )
                            }
                        }
                    } else {
                        Spacer(Modifier.height(8.dp))
                    }

                    LazyRow(
                        state = listState,
                        contentPadding = PaddingValues(horizontal = 48.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(20.dp),
                    ) {
                        itemsIndexed(episodes, key = { _, e -> e.id }) { index, episode ->
                            MediaCard(
                                title = "${episode.number}. ${episode.title}",
                                image = episode.image ?: series.backdrop ?: info?.backdrop,
                                aspectRatio = 16f / 9f,
                                subtitle = formatDuration(episode.duration),
                                progress = progressOf(episode),
                                focusRequester = if (index == focusIndex) episodeFocus else null,
                                modifier = Modifier.width(210.dp),
                                onClick = {
                                    lastEpisodeId = episode.id
                                    onPlay(episode)
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 4 523 000 ms → « 1:15:23 », 312 000 ms → « 5:12 ». */
fun formatClock(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
