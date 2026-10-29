package fr.rabuxx.iptvtv.ui

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.style.TextAlign
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import fr.rabuxx.iptvtv.TmdbView
import fr.rabuxx.iptvtv.data.CastMember
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
    tmdb: TmdbView?,
    onTrailer: (String) -> Unit,
    onOpenMovie: (Movie) -> Unit,
) {
    val info = detail.data
    val extra = tmdb?.details
    val playFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { playFocus.requestFocus() }
    }

    DetailScaffold(
        backdrop = info?.backdrop ?: extra?.backdrop,
        fallbackPoster = info?.poster ?: movie.poster ?: extra?.poster,
        tmdb = tmdb,
        onOpenMovie = onOpenMovie,
        onOpenSeries = {},
    ) {
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
                    info?.genre ?: extra?.genres?.take(3)?.joinToString(", "),
                    formatDuration(info?.duration),
                    formatRating(info?.rating ?: movie.rating ?: extra?.rating?.toString()),
                ),
            )
            when {
                detail.loading -> Text("Chargement des informations…", color = TextSecondary, fontSize = 13.sp)
                else -> Plot(info?.plot ?: extra?.overview, maxLines = 4)
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
                extra?.trailerKey?.let { key -> ActionButton("Bande-annonce", onClick = { onTrailer(key) }) }
                ActionButton(if (isFavorite) "★  Favori" else "☆  Favoris", onClick = onToggleFavorite)
            }

            info?.director?.let { Text("Réalisation : $it", color = TextSecondary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            if (extra?.cast.isNullOrEmpty()) {
                info?.cast?.let { Text("Avec : $it", color = TextSecondary, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis) }
            }
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
    tmdb: TmdbView?,
    onTrailer: (String) -> Unit,
    onOpenSeries: (Series) -> Unit,
) {
    val info = detail.data
    val extra = tmdb?.details
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

    DetailScaffold(
        backdrop = info?.backdrop ?: series.backdrop ?: extra?.backdrop,
        fallbackPoster = series.cover ?: extra?.poster,
        tmdb = tmdb,
        onOpenMovie = {},
        onOpenSeries = onOpenSeries,
    ) {
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
                        info?.genre ?: series.genre ?: extra?.genres?.take(3)?.joinToString(", "),
                        seasons.size.takeIf { it > 0 }?.let { if (it > 1) "$it saisons" else "1 saison" },
                        formatRating(info?.rating ?: series.rating),
                    ),
                )
                Plot(info?.plot ?: series.plot ?: extra?.overview, maxLines = 3)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ActionButton(if (isFavorite) "★  Favori" else "☆  Favoris", onClick = onToggleFavorite)
                    extra?.trailerKey?.let { key -> ActionButton("Bande-annonce", onClick = { onTrailer(key) }) }
                }
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

// ============================== Fiche : structure commune + TMDB ==============================

/**
 * Fiche plein écran sur l'image de fond ; en descendant, le casting et les recommandations TMDB
 * disponibles chez le fournisseur. Le fond reste fixe pendant le défilement.
 */
@Composable
private fun DetailScaffold(
    backdrop: String?,
    fallbackPoster: String?,
    tmdb: TmdbView?,
    onOpenMovie: (Movie) -> Unit,
    onOpenSeries: (Series) -> Unit,
    hero: @Composable BoxScope.() -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        Backdrop(backdrop, fallbackPoster)
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val screenHeight = maxHeight
            LazyColumn(Modifier.fillMaxSize()) {
                item("hero") { Box(Modifier.fillMaxWidth().height(screenHeight), content = hero) }
                val cast = tmdb?.details?.cast.orEmpty()
                if (cast.isNotEmpty()) {
                    item("cast") {
                        Shelf("Casting") {
                            itemsIndexed(cast, key = { i, c -> "${c.name}-$i" }) { _, member -> CastCard(member) }
                        }
                    }
                }
                val movies = tmdb?.availableMovies.orEmpty()
                val series = tmdb?.availableSeries.orEmpty()
                if (movies.isNotEmpty() || series.isNotEmpty()) {
                    item("recs") {
                        Shelf("Dans la même veine · disponibles chez toi") {
                            itemsIndexed(movies, key = { i, m -> "m${m.streamId}-$i" }) { _, m ->
                                MediaCard(
                                    title = m.name,
                                    image = m.poster,
                                    modifier = Modifier.width(PosterWidth),
                                    onClick = { onOpenMovie(m) },
                                )
                            }
                            itemsIndexed(series, key = { i, sr -> "s${sr.seriesId}-$i" }) { _, sr ->
                                MediaCard(
                                    title = sr.name,
                                    image = sr.cover,
                                    subtitle = sr.year,
                                    modifier = Modifier.width(PosterWidth),
                                    onClick = { onOpenSeries(sr) },
                                )
                            }
                        }
                    }
                }
                if (cast.isNotEmpty() || movies.isNotEmpty() || series.isNotEmpty()) {
                    item("tmdb-credit") {
                        Text(
                            "Informations complémentaires : TMDB",
                            modifier = Modifier.padding(start = 48.dp, top = 8.dp, bottom = 32.dp),
                            color = Color(0x66FFFFFF),
                            fontSize = 10.sp,
                        )
                    }
                }
            }
        }
    }
}

/** Acteur : photo ronde, nom et rôle. Focusable pour pouvoir faire défiler la rangée à la télécommande. */
@Composable
private fun CastCard(member: CastMember) {
    var focused by remember { mutableStateOf(false) }
    Surface(
        onClick = {},
        modifier = Modifier.width(104.dp).onFocusChanged { focused = it.isFocused },
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            contentColor = TextPrimary,
            focusedContainerColor = Color(0x26FFFFFF),
            focusedContentColor = TextPrimary,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
    ) {
        Column(
            Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(
                Modifier.size(76.dp).clip(CircleShape).background(Card),
                contentAlignment = Alignment.Center,
            ) {
                Text(member.name.take(1), fontSize = 24.sp, color = TextSecondary)
                if (member.photo != null) {
                    AsyncImage(
                        model = member.photo,
                        contentDescription = member.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            Text(
                member.name,
                fontSize = 11.sp,
                fontWeight = if (focused) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
            member.role?.let {
                Text(it, fontSize = 10.sp, color = TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
            }
        }
    }
}
