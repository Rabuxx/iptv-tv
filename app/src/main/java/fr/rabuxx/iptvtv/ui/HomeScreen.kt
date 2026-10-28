package fr.rabuxx.iptvtv.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import fr.rabuxx.iptvtv.MainViewModel
import fr.rabuxx.iptvtv.UiState
import fr.rabuxx.iptvtv.data.Channel
import fr.rabuxx.iptvtv.data.Movie
import fr.rabuxx.iptvtv.data.Series

/**
 * Mémorise la dernière carte ouverte dans un écran à rangées, pour y replacer le focus au retour.
 * La clé est conservée par l'écran (rememberSaveable) quand on navigue vers une fiche ou un lecteur.
 */
class ShelfFocus(private val requester: FocusRequester, private val current: () -> String?, private val set: (String) -> Unit) {
    fun requesterFor(key: String): FocusRequester? = if (key == current()) requester else null
    fun mark(key: String) = set(key)
}

@Composable
fun rememberShelfFocus(restore: Boolean, onRestored: (Boolean) -> Unit): ShelfFocus {
    var focusKey by rememberSaveable { mutableStateOf<String?>(null) }
    val requester = remember { FocusRequester() }
    LaunchedEffect(restore) {
        if (restore) {
            withFrameNanos { }
            val ok = focusKey != null && runCatching { requester.requestFocus() }.isSuccess
            onRestored(ok)
        }
    }
    return remember(requester) { ShelfFocus(requester, { focusKey }, { focusKey = it }) }
}

/** Onglet Accueil façon Apple TV : « À suivre », favoris, chaînes récentes, nouveautés. */
@Composable
fun HomeContent(state: UiState, vm: MainViewModel, restore: Boolean, onRestored: (Boolean) -> Unit) {
    val focus = rememberShelfFocus(restore, onRestored)
    val fav = state.favorites

    val nothingYet = state.history.isEmpty() && fav.channels.isEmpty() && fav.movies.isEmpty() &&
        fav.series.isEmpty() && state.recentChannels.isEmpty() && state.recentMovies.isEmpty() &&
        state.recentSeries.isEmpty()

    if (nothingYet) {
        CenteredMessage(
            if (state.libraryLoading) {
                "Chargement de ton catalogue…"
            } else {
                "Lance une chaîne, un film ou une série :\ntes contenus en cours et tes favoris apparaîtront ici."
            },
        )
        return
    }

    LazyColumn(
        state = rememberLazyListState(),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 4.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (state.history.isNotEmpty()) item("history") {
            Shelf("À suivre") {
                itemsIndexed(state.history, key = { _, h -> h.id }) { _, h ->
                    val key = "h:${h.id}"
                    MediaCard(
                        title = h.movie?.name ?: h.series?.name.orEmpty(),
                        image = h.movie?.poster ?: h.series?.cover,
                        subtitle = h.label,
                        progress = vm.progress(h.progressKey),
                        modifier = Modifier.width(PosterWidth),
                        focusRequester = focus.requesterFor(key),
                        // Appui long : retirer de « À suivre »
                        onLongClick = { vm.removeFromHistory(h) },
                        onClick = {
                            focus.mark(key)
                            val movie = h.movie
                            val series = h.series
                            when {
                                movie != null -> vm.openMovie(movie)
                                series != null -> vm.openSeries(series, preferredEpisodeId = h.episodeId)
                            }
                        },
                    )
                }
            }
        }

        if (fav.channels.isNotEmpty()) item("fav-live") {
            ChannelShelf("Chaînes favorites", "fl", fav.channels, state, vm, focus)
        }
        if (state.recentChannels.isNotEmpty()) item("recent-live") {
            ChannelShelf("Chaînes récentes", "rl", state.recentChannels, state, vm, focus)
        }
        if (fav.movies.isNotEmpty()) item("fav-movies") {
            MovieShelf("Films favoris", "fm", fav.movies, state, vm, focus)
        }
        if (fav.series.isNotEmpty()) item("fav-series") {
            SeriesShelf("Séries favorites", "fs", fav.series, state, vm, focus)
        }
        if (state.recentMovies.isNotEmpty()) item("new-movies") {
            MovieShelf(if (state.preferFrench) "Dernières sorties · Films FR & VOSTFR" else "Films ajoutés récemment", "nm", state.recentMovies, state, vm, focus)
        }
        if (state.recentSeries.isNotEmpty()) item("new-series") {
            SeriesShelf(if (state.preferFrench) "Dernières sorties · Séries FR & VOSTFR" else "Séries mises à jour", "ns", state.recentSeries, state, vm, focus)
        }
    }
}

// ---------- Rangées réutilisées par l'accueil et la recherche ----------

@Composable
fun ChannelShelf(title: String, prefix: String, channels: List<Channel>, state: UiState, vm: MainViewModel, focus: ShelfFocus) {
    Shelf(title) {
        itemsIndexed(channels, key = { i, c -> "${c.streamId}-$i" }) { index, channel ->
            val key = "$prefix:${channel.streamId}"
            MediaCard(
                title = channel.name,
                image = channel.icon,
                aspectRatio = 16f / 9f,
                contentScale = ContentScale.Fit,
                imagePadding = 12.dp,
                modifier = Modifier.width(WideCardWidth),
                focusRequester = focus.requesterFor(key),
                favorite = state.favorites.has(channel),
                onLongClick = { vm.toggleFavorite(channel) },
                onClick = {
                    focus.mark(key)
                    vm.playChannels(channels, index)
                },
            )
        }
    }
}

@Composable
fun MovieShelf(title: String, prefix: String, movies: List<Movie>, state: UiState, vm: MainViewModel, focus: ShelfFocus) {
    Shelf(title) {
        itemsIndexed(movies, key = { i, m -> "${m.streamId}-$i" }) { _, movie ->
            val key = "$prefix:${movie.streamId}"
            MediaCard(
                title = movie.name,
                image = movie.poster,
                progress = vm.progress(vm.movieKey(movie)),
                modifier = Modifier.width(PosterWidth),
                focusRequester = focus.requesterFor(key),
                favorite = state.favorites.has(movie),
                onLongClick = { vm.toggleFavorite(movie) },
                onClick = {
                    focus.mark(key)
                    vm.openMovie(movie)
                },
            )
        }
    }
}

@Composable
fun SeriesShelf(title: String, prefix: String, series: List<Series>, state: UiState, vm: MainViewModel, focus: ShelfFocus) {
    Shelf(title) {
        itemsIndexed(series, key = { i, s -> "${s.seriesId}-$i" }) { _, item ->
            val key = "$prefix:${item.seriesId}"
            MediaCard(
                title = item.name,
                image = item.cover,
                subtitle = item.year,
                modifier = Modifier.width(PosterWidth),
                focusRequester = focus.requesterFor(key),
                favorite = state.favorites.has(item),
                onLongClick = { vm.toggleFavorite(item) },
                onClick = {
                    focus.mark(key)
                    vm.openSeries(item)
                },
            )
        }
    }
}
