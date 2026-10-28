package fr.rabuxx.iptvtv

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import fr.rabuxx.iptvtv.data.AccountInfo
import fr.rabuxx.iptvtv.data.Category
import fr.rabuxx.iptvtv.data.Channel
import fr.rabuxx.iptvtv.data.Credentials
import fr.rabuxx.iptvtv.data.CredentialsStore
import fr.rabuxx.iptvtv.data.DiskCache
import fr.rabuxx.iptvtv.data.EpgProgram
import fr.rabuxx.iptvtv.data.Episode
import fr.rabuxx.iptvtv.data.FAVORITES_CATEGORY
import fr.rabuxx.iptvtv.data.Favorites
import fr.rabuxx.iptvtv.data.HistoryItem
import fr.rabuxx.iptvtv.data.LATEST_CATEGORY
import fr.rabuxx.iptvtv.data.Library
import fr.rabuxx.iptvtv.data.LibraryStore
import fr.rabuxx.iptvtv.data.Movie
import fr.rabuxx.iptvtv.data.MovieInfo
import fr.rabuxx.iptvtv.data.REPLAY_CATEGORY
import fr.rabuxx.iptvtv.data.Ranking
import fr.rabuxx.iptvtv.data.Section
import fr.rabuxx.iptvtv.data.Series
import fr.rabuxx.iptvtv.data.SeriesInfo
import fr.rabuxx.iptvtv.data.UpdateInfo
import fr.rabuxx.iptvtv.data.Updater
import fr.rabuxx.iptvtv.data.XtreamClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.Normalizer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Écrans de l'app, empilés : Retour dépile. */
sealed interface Route {
    data object Login : Route
    data object Main : Route
    data class MovieDetail(val movie: Movie) : Route
    data class SeriesDetail(val series: Series, val preferredEpisodeId: String? = null) : Route
    data class Replay(val channel: Channel) : Route

    /** Lecteur direct : on zappe dans [channels] (grille, favoris, récents ou résultats de recherche). */
    data class LivePlayer(val channels: List<Channel>, val index: Int, val fromGrid: Boolean) : Route

    data class VodPlayer(
        val title: String,
        val subtitle: String?,
        val url: String,
        /** Clé de reprise de lecture (« m:123 » film, « e:456 » épisode, « r:… » replay). */
        val resumeKey: String,
        val fromStart: Boolean = false,
        /** Entrée à mettre à jour dans « À suivre » (null pour le replay). */
        val history: HistoryItem? = null,
        /** Renseignés pour un épisode : permettent d'enchaîner l'épisode suivant. */
        val series: Series? = null,
        val episode: Episode? = null,
    ) : Route
}

/** Clé stable d'un écran, pour conserver son état (défilement, saison choisie…) quand on y revient. */
val Route.key: String
    get() = when (this) {
        Route.Login -> "login"
        Route.Main -> "main"
        is Route.MovieDetail -> "movie:${movie.streamId}"
        is Route.SeriesDetail -> "series:${series.seriesId}"
        is Route.Replay -> "replay:${channel.streamId}"
        is Route.LivePlayer -> "live-player"
        is Route.VodPlayer -> "vod-player:$resumeKey"
    }

/** Catalogue d'une section : catégories, catégorie sélectionnée, éléments affichés. */
data class Catalog<T>(
    val categories: List<Category> = emptyList(),
    val selectedId: String? = null,
    val items: List<T> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
    val loaded: Boolean = false,
    /** Dernier élément ouvert, pour y replacer le focus au retour. */
    val focusIndex: Int? = null,
)

data class Detail<T>(
    val data: T? = null,
    val loading: Boolean = false,
    val error: String? = null,
)

data class SearchState(
    val query: String = "",
    val channels: List<Channel> = emptyList(),
    val movies: List<Movie> = emptyList(),
    val series: List<Series> = emptyList(),
) {
    val isEmpty get() = channels.isEmpty() && movies.isEmpty() && series.isEmpty()
}

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val info: UpdateInfo) : UpdateState
    data class Downloading(val info: UpdateInfo, val progress: Float) : UpdateState
    /** La TV doit autoriser l'app à installer des applications. [opened] : l'écran système a pu être ouvert. */
    data class NeedsPermission(val info: UpdateInfo, val opened: Boolean) : UpdateState
    data class Error(val message: String) : UpdateState
}

data class UiState(
    val stack: List<Route> = listOf(Route.Login),
    val section: Section = Section.Home,
    val savedCredentials: Credentials? = null,
    val loggingIn: Boolean = false,
    val loginError: String? = null,
    val account: AccountInfo? = null,
    val live: Catalog<Channel> = Catalog(),
    val movies: Catalog<Movie> = Catalog(),
    val series: Catalog<Series> = Catalog(),
    val library: Library? = null,
    val libraryLoading: Boolean = false,
    val recentMovies: List<Movie> = emptyList(),
    val recentSeries: List<Series> = emptyList(),
    val favorites: Favorites = Favorites(),
    val history: List<HistoryItem> = emptyList(),
    val recentChannels: List<Channel> = emptyList(),
    val search: SearchState = SearchState(),
    val movieInfo: Detail<MovieInfo> = Detail(),
    val seriesInfo: Detail<SeriesInfo> = Detail(),
    val seriesInfoFor: String? = null,
    val replay: Detail<List<EpgProgram>> = Detail(),
    val update: UpdateState = UpdateState.Idle,
    /** Catégories FR / VOSTFR en tête, dernières sorties par défaut. */
    val preferFrench: Boolean = true,
    /** Section dont le contenu doit reprendre le focus (retour depuis une fiche ou un lecteur). */
    val restoreFocus: Section? = null,
) {
    val route: Route get() = stack.last()
    val hasReplay: Boolean get() = library?.live?.any { it.archiveDays > 0 } == true
}

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val store = CredentialsStore(app)
    private val libraryStore = LibraryStore(app)
    private val diskCache = DiskCache(app)
    private val updater = Updater(app)
    private var client: XtreamClient? = null

    private var detailJob: Job? = null
    private var libraryJob: Job? = null
    private var searchJob: Job? = null
    private var recentJob: Job? = null
    private var updateJob: Job? = null
    private var searchIndex: SearchIndex? = null
    private var downloadedApk: File? = null

    /** Ignore une fois les catégories mémorisées par l'ancienne version, pour appliquer le nouveau tri par défaut. */
    private val resetSavedCategories = store.consumeDefaultsReset()

    private val _state = MutableStateFlow(
        UiState(
            savedCredentials = store.load(),
            favorites = libraryStore.favorites(),
            history = libraryStore.history(),
            recentChannels = libraryStore.recentChannels(),
            preferFrench = store.preferFrench,
        ),
    )
    val state: StateFlow<UiState> = _state.asStateFlow()

    val appVersion: String get() = updater.currentVersion

    // Les trois catalogues partagent la même logique de chargement (cache puis réseau)
    private val liveHandle = CatalogHandle(
        section = Section.Live,
        read = { it.live },
        write = { s, c -> s.copy(live = c) },
        loadCategories = { c, cached -> c.liveCategories(cached) },
        loadItems = { c, id, cached -> c.liveStreams(id, cached) },
        sortItems = { it },
        defaultCategory = null,
        virtual = { id ->
            when (id) {
                FAVORITES_CATEGORY -> _state.value.favorites.channels
                REPLAY_CATEGORY -> _state.value.library?.live?.filter { it.archiveDays > 0 }.orEmpty()
                else -> null
            }
        },
    )
    private val moviesHandle = CatalogHandle(
        section = Section.Movies,
        read = { it.movies },
        write = { s, c -> s.copy(movies = c) },
        loadCategories = { c, cached -> c.movieCategories(cached) },
        loadItems = { c, id, cached -> c.movies(id, cached) },
        // Les plus récents d'abord dans chaque catégorie
        sortItems = { list -> if (_state.value.preferFrench) list.sortedByDescending { it.added } else list },
        defaultCategory = LATEST_CATEGORY,
        virtual = { id ->
            when (id) {
                FAVORITES_CATEGORY -> _state.value.favorites.movies
                LATEST_CATEGORY -> latestMovies(150)
                else -> null
            }
        },
    )
    private val seriesHandle = CatalogHandle(
        section = Section.Series,
        read = { it.series },
        write = { s, c -> s.copy(series = c) },
        loadCategories = { c, cached -> c.seriesCategories(cached) },
        loadItems = { c, id, cached -> c.series(id, cached) },
        sortItems = { list -> if (_state.value.preferFrench) list.sortedByDescending { it.lastModified } else list },
        defaultCategory = LATEST_CATEGORY,
        virtual = { id ->
            when (id) {
                FAVORITES_CATEGORY -> _state.value.favorites.series
                LATEST_CATEGORY -> latestSeries(150)
                else -> null
            }
        },
    )
    private val handles: List<CatalogHandle<*>> get() = listOf(liveHandle, moviesHandle, seriesHandle)

    init {
        store.load()?.let { login(it) }
    }

    // ---------- Connexion ----------

    fun login(creds: Credentials) {
        if (creds.server.isBlank() || creds.username.isBlank() || creds.password.isBlank()) {
            _state.update { it.copy(loginError = "Renseigne le serveur, l'identifiant et le mot de passe") }
            return
        }
        _state.update { it.copy(loggingIn = true, loginError = null, savedCredentials = creds) }
        viewModelScope.launch {
            try {
                val c = XtreamClient(creds, diskCache)
                val account = c.authenticate()
                client = c
                store.save(creds)
                _state.update {
                    it.copy(loggingIn = false, account = account, stack = listOf(Route.Main), section = Section.Home)
                }
                ensureLibrary()
                // Catégories chargées dès le départ : le tri FR sert aussi à l'accueil
                handles.forEach { it.ensureLoaded() }
                checkForUpdate(silent = true)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(loggingIn = false, loginError = e.message ?: "Erreur de connexion") }
            }
        }
    }

    fun logout() {
        handles.forEach { it.cancel() }
        listOf(detailJob, libraryJob, searchJob, recentJob).forEach { it?.cancel() }
        store.clear()
        libraryStore.clear()
        diskCache.clear()
        client = null
        searchIndex = null
        _state.value = UiState()
    }

    // ---------- Navigation ----------

    fun selectSection(section: Section) {
        if (_state.value.section == section) return
        _state.update { it.copy(section = section) }
        when (section) {
            Section.Home, Section.Search -> ensureLibrary()
            Section.Settings -> Unit
            else -> handle(section)?.ensureLoaded()
        }
    }

    fun selectCategory(section: Section, id: String) {
        handle(section)?.select(id)
    }

    fun retry(section: Section) {
        if (section == Section.Home || section == Section.Search) ensureLibrary(force = true) else handle(section)?.retry()
    }

    /** @return false si on est déjà à la racine (Retour quitte alors l'app). */
    fun back(): Boolean {
        val stack = _state.value.stack
        if (stack.size <= 1) return false
        val newStack = stack.dropLast(1)
        _state.update {
            it.copy(
                stack = newStack,
                restoreFocus = if (newStack.last() == Route.Main) it.section else it.restoreFocus,
            )
        }
        return true
    }

    fun consumeRestoreFocus() = _state.update { it.copy(restoreFocus = null) }

    private fun push(route: Route) = _state.update { it.copy(stack = it.stack + route) }

    private fun replaceTop(route: Route) = _state.update { it.copy(stack = it.stack.dropLast(1) + route) }

    // ---------- Direct ----------

    fun playChannels(channels: List<Channel>, index: Int, fromGrid: Boolean = false) {
        if (channels.isEmpty()) return
        if (fromGrid) liveHandle.rememberFocus(index)
        push(Route.LivePlayer(channels, index, fromGrid))
    }

    fun onZapped(route: Route.LivePlayer, index: Int) {
        if (route.fromGrid) liveHandle.rememberFocus(index)
        val channel = route.channels.getOrNull(index) ?: return
        // Une chaîne n'entre dans « récentes » que si on la regarde au moins quelques secondes
        recentJob?.cancel()
        recentJob = viewModelScope.launch {
            delay(5_000)
            val list = (listOf(channel) + _state.value.recentChannels.filterNot { it.streamId == channel.streamId }).take(15)
            libraryStore.saveRecentChannels(list)
            _state.update { it.copy(recentChannels = list) }
        }
    }

    fun liveUrl(channel: Channel, extension: String) = client?.liveUrl(channel, extension) ?: ""

    // ---------- Replay ----------

    fun openReplay(channel: Channel, gridIndex: Int? = null) {
        val c = client ?: return
        gridIndex?.let { liveHandle.rememberFocus(it) }
        push(Route.Replay(channel))
        detailJob?.cancel()
        _state.update { it.copy(replay = Detail(loading = true)) }
        detailJob = viewModelScope.launch {
            try {
                val programs = c.replayPrograms(channel)
                _state.update { it.copy(replay = Detail(data = programs)) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(replay = Detail(error = e.message ?: "Impossible de charger le replay")) }
            }
        }
    }

    fun playReplay(channel: Channel, program: EpgProgram) {
        val c = client ?: return
        val whenText = SimpleDateFormat("EEEE d MMMM · HH:mm", Locale.FRANCE).format(Date(program.startTs * 1000))
        push(
            Route.VodPlayer(
                title = program.title,
                subtitle = "${channel.name} · $whenText",
                url = c.replayUrl(channel, program),
                resumeKey = "r:${channel.streamId}:${program.startTs}",
                fromStart = true,
            ),
        )
    }

    // ---------- Films ----------

    fun openMovie(movie: Movie, gridIndex: Int? = null) {
        val c = client ?: return
        gridIndex?.let { moviesHandle.rememberFocus(it) }
        push(Route.MovieDetail(movie))
        detailJob?.cancel()
        _state.update { it.copy(movieInfo = Detail(loading = true)) }
        detailJob = viewModelScope.launch {
            try {
                val info = c.movieInfo(movie)
                _state.update { it.copy(movieInfo = Detail(data = info)) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Pas bloquant : on peut quand même lancer le film
                _state.update { it.copy(movieInfo = Detail(error = e.message)) }
            }
        }
    }

    fun playMovie(movie: Movie, extension: String?, fromStart: Boolean) {
        val c = client ?: return
        val ext = extension ?: movie.extension
        // On garde l'extension réelle (fournie par get_vod_info) pour relancer depuis « À suivre »
        val playable = movie.copy(extension = ext)
        push(
            Route.VodPlayer(
                title = movie.name,
                subtitle = null,
                url = c.movieUrl(playable, ext),
                resumeKey = movieKey(movie),
                fromStart = fromStart,
                history = HistoryItem(movie = playable, progressKey = movieKey(movie), updatedAt = now()),
            ),
        )
    }

    fun movieKey(movie: Movie) = "m:${movie.streamId}"

    // ---------- Séries ----------

    fun openSeries(series: Series, gridIndex: Int? = null, preferredEpisodeId: String? = null) {
        val c = client ?: return
        gridIndex?.let { seriesHandle.rememberFocus(it) }
        push(Route.SeriesDetail(series, preferredEpisodeId))
        detailJob?.cancel()
        _state.update { it.copy(seriesInfo = Detail(loading = true), seriesInfoFor = series.seriesId) }
        detailJob = viewModelScope.launch {
            try {
                val info = c.seriesInfo(series)
                _state.update { it.copy(seriesInfo = Detail(data = info)) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(seriesInfo = Detail(error = e.message ?: "Impossible de charger la série")) }
            }
        }
    }

    fun playEpisode(series: Series, episode: Episode, replace: Boolean = false) {
        val route = episodeRoute(series, episode) ?: return
        if (replace) replaceTop(route) else push(route)
    }

    private fun episodeRoute(series: Series, episode: Episode): Route.VodPlayer? {
        val c = client ?: return null
        return Route.VodPlayer(
            title = series.name,
            subtitle = "Saison ${episode.season} · Épisode ${episode.number} · ${episode.title}",
            url = c.episodeUrl(episode),
            resumeKey = episodeKey(episode),
            history = HistoryItem(
                series = series,
                episodeId = episode.id,
                label = "S${episode.season} · É${episode.number}",
                progressKey = episodeKey(episode),
                updatedAt = now(),
            ),
            series = series,
            episode = episode,
        )
    }

    fun episodeKey(episode: Episode) = "e:${episode.id}"

    /** Épisode suivant (saison suivante comprise), si les épisodes de cette série sont chargés. */
    fun nextEpisode(series: Series?, current: Episode?): Episode? {
        if (series == null || current == null) return null
        val s = _state.value
        if (s.seriesInfoFor != series.seriesId) return null
        val all = s.seriesInfo.data?.episodes?.values?.flatten() ?: return null
        val i = all.indexOfFirst { it.id == current.id }
        return if (i >= 0) all.getOrNull(i + 1) else null
    }

    fun playNext(route: Route.VodPlayer) {
        val series = route.series
        val next = nextEpisode(series, route.episode)
        if (series == null || next == null) {
            back()
        } else {
            playEpisode(series, next, replace = true)
        }
    }

    // ---------- Favoris ----------

    fun toggleFavorite(channel: Channel) = updateFavorites { f ->
        if (f.has(channel)) f.copy(channels = f.channels.filterNot { it.streamId == channel.streamId })
        else f.copy(channels = listOf(channel) + f.channels)
    }

    fun toggleFavorite(movie: Movie) = updateFavorites { f ->
        if (f.has(movie)) f.copy(movies = f.movies.filterNot { it.streamId == movie.streamId })
        else f.copy(movies = listOf(movie) + f.movies)
    }

    fun toggleFavorite(series: Series) = updateFavorites { f ->
        if (f.has(series)) f.copy(series = f.series.filterNot { it.seriesId == series.seriesId })
        else f.copy(series = listOf(series) + f.series)
    }

    private fun updateFavorites(change: (Favorites) -> Favorites) {
        val updated = change(_state.value.favorites)
        libraryStore.saveFavorites(updated)
        _state.update { it.copy(favorites = updated) }
        handles.forEach { it.refreshVirtual() }
    }

    // ---------- Reprise de lecture et « À suivre » ----------

    fun resumePosition(key: String) = store.position(key)

    fun progress(key: String) = store.progress(key)

    fun savePosition(route: Route.VodPlayer, positionMs: Long, durationMs: Long) {
        store.savePosition(route.resumeKey, positionMs, durationMs)
        val entry = route.history ?: return
        val stillInProgress = store.position(route.resumeKey) > 0
        val others = _state.value.history.filterNot { it.id == entry.id }
        val updated = when {
            // Film terminé (ou à peine commencé) : il sort de « À suivre »
            entry.movie != null && !stillInProgress -> others
            // Épisode : la série reste dans « À suivre » pour enchaîner la suite
            else -> listOf(entry.copy(updatedAt = now())) + others
        }.take(20)
        libraryStore.saveHistory(updated)
        _state.update { it.copy(history = updated) }
    }

    fun removeFromHistory(item: HistoryItem) {
        val updated = _state.value.history.filterNot { it.id == item.id }
        libraryStore.saveHistory(updated)
        _state.update { it.copy(history = updated) }
    }

    // ---------- Catalogue complet : accueil, recherche, replay ----------

    fun ensureLibrary(force: Boolean = false) {
        if (libraryJob?.isActive == true) return
        if (!force && _state.value.library != null) return
        val c = client ?: return
        _state.update { it.copy(libraryLoading = true) }
        libraryJob = viewModelScope.launch {
            try {
                // 1) Affichage immédiat depuis le cache disque
                val cached = Library(
                    live = runCatching { c.liveStreams(null, cached = true) }.getOrNull().orEmpty(),
                    movies = runCatching { c.movies(null, cached = true) }.getOrNull().orEmpty(),
                    series = runCatching { c.series(null, cached = true) }.getOrNull().orEmpty(),
                )
                if (cached.live.isNotEmpty() || cached.movies.isNotEmpty() || cached.series.isNotEmpty()) {
                    setLibrary(cached)
                }
                // 2) Rafraîchissement réseau, type par type (un échec n'empêche pas les autres)
                var current = _state.value.library ?: Library()
                runCatching { c.liveStreams(null) }.getOrNull()?.let { current = current.copy(live = it); setLibrary(current) }
                runCatching { c.movies(null) }.getOrNull()?.let { current = current.copy(movies = it); setLibrary(current) }
                runCatching { c.series(null) }.getOrNull()?.let { current = current.copy(series = it); setLibrary(current) }
                if (_state.value.library == null) setLibrary(Library())
            } finally {
                _state.update { it.copy(libraryLoading = false) }
            }
        }
    }

    private suspend fun setLibrary(library: Library) {
        val index = withContext(Dispatchers.Default) { SearchIndex(library) }
        searchIndex = index
        _state.update { it.copy(library = library) }
        recomputeRecents()
        handles.forEach { it.refreshVirtual() }
        val q = _state.value.search.query
        if (q.isNotBlank()) search(q)
    }

    // ---------- Tri FR / dernières sorties ----------

    private fun latestMovies(limit: Int): List<Movie> {
        val s = _state.value
        val library = s.library ?: return emptyList()
        return Ranking.latest(library.movies, s.movies.categories, s.preferFrench, limit, { it.added }, { it.categoryId })
    }

    private fun latestSeries(limit: Int): List<Series> {
        val s = _state.value
        val library = s.library ?: return emptyList()
        return Ranking.latest(library.series, s.series.categories, s.preferFrench, limit, { it.lastModified }, { it.categoryId })
    }

    /** Rangées « Dernières sorties » de l'accueil. */
    private suspend fun recomputeRecents() {
        val (movies, series) = withContext(Dispatchers.Default) { latestMovies(24) to latestSeries(24) }
        _state.update { it.copy(recentMovies = movies, recentSeries = series) }
    }

    fun setPreferFrench(enabled: Boolean) {
        store.preferFrench = enabled
        _state.update { it.copy(preferFrench = enabled) }
        handles.forEach { it.applyRanking() }
        viewModelScope.launch { recomputeRecents() }
    }

    // ---------- Recherche ----------

    fun search(query: String) {
        _state.update { it.copy(search = it.search.copy(query = query)) }
        searchJob?.cancel()
        if (query.isBlank()) {
            _state.update { it.copy(search = SearchState()) }
            return
        }
        val index = searchIndex ?: run { ensureLibrary(); return }
        searchJob = viewModelScope.launch {
            delay(250) // on attend la fin de la frappe
            val result = withContext(Dispatchers.Default) { index.search(query) }
            _state.update { if (it.search.query == query) it.copy(search = result) else it }
        }
    }

    private class SearchIndex(library: Library) {
        private val live = library.live.map { normalize(it.name) to it }
        private val movies = library.movies.map { normalize(it.name) to it }
        private val series = library.series.map { normalize(it.name) to it }

        fun search(query: String): SearchState {
            val terms = normalize(query).split(' ').filter { it.isNotBlank() }
            if (terms.isEmpty()) return SearchState(query = query)
            fun matches(name: String) = terms.all { name.contains(it) }
            fun <T> pick(list: List<Pair<String, T>>) =
                list.asSequence()
                    .filter { matches(it.first) }
                    // Les titres qui commencent par la recherche passent en premier
                    .sortedBy { if (it.first.startsWith(terms.first())) 0 else 1 }
                    .take(40)
                    .map { it.second }
                    .toList()
            return SearchState(query = query, channels = pick(live), movies = pick(movies), series = pick(series))
        }

        companion object {
            private val DIACRITICS = Regex("\\p{Mn}+")
            fun normalize(s: String): String =
                Normalizer.normalize(s, Normalizer.Form.NFD).replace(DIACRITICS, "").lowercase(Locale.ROOT)
        }
    }

    // ---------- Mises à jour ----------

    fun checkForUpdate(silent: Boolean = false) {
        val current = _state.value.update
        if (current is UpdateState.Checking || current is UpdateState.Downloading) return
        if (!silent) _state.update { it.copy(update = UpdateState.Checking) }
        updateJob = viewModelScope.launch {
            val result: UpdateState = try {
                updater.check()?.let { UpdateState.Available(it) } ?: if (silent) UpdateState.Idle else UpdateState.UpToDate
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (silent) UpdateState.Idle else UpdateState.Error(e.message ?: "Vérification impossible")
            }
            _state.update { it.copy(update = result) }
        }
    }

    fun installUpdate(info: UpdateInfo) {
        if (!updater.canInstall()) {
            val opened = updater.openInstallPermission()
            _state.update { it.copy(update = UpdateState.NeedsPermission(info, opened)) }
            return
        }
        downloadedApk?.takeIf { it.exists() && it.name.contains(info.version) }?.let {
            runCatching { updater.install(it) }
            return
        }
        updateJob?.cancel()
        updateJob = viewModelScope.launch {
            try {
                _state.update { it.copy(update = UpdateState.Downloading(info, 0f)) }
                val apk = updater.download(info) { p ->
                    _state.update { it.copy(update = UpdateState.Downloading(info, p)) }
                }
                downloadedApk = apk
                _state.update { it.copy(update = UpdateState.Available(info)) }
                updater.install(apk)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(update = UpdateState.Error(e.message ?: "Mise à jour impossible")) }
            }
        }
    }

    // ---------- Chargement générique des catalogues ----------

    private fun now() = System.currentTimeMillis()

    private fun handle(section: Section): CatalogHandle<*>? = when (section) {
        Section.Live -> liveHandle
        Section.Movies -> moviesHandle
        Section.Series -> seriesHandle
        else -> null
    }

    private inner class CatalogHandle<T>(
        private val section: Section,
        private val read: (UiState) -> Catalog<T>,
        private val write: (UiState, Catalog<T>) -> UiState,
        private val loadCategories: suspend (XtreamClient, Boolean) -> List<Category>?,
        private val loadItems: suspend (XtreamClient, String, Boolean) -> List<T>?,
        /** Tri appliqué aux éléments d'une vraie catégorie (récents d'abord pour films et séries). */
        private val sortItems: (List<T>) -> List<T>,
        /** Catégorie ouverte par défaut (null : la première après tri). */
        private val defaultCategory: String?,
        /** Contenu des catégories virtuelles (favoris, replay, dernières sorties), null pour une vraie catégorie. */
        private val virtual: (String) -> List<T>?,
    ) {
        private var categoriesJob: Job? = null
        private var itemsJob: Job? = null

        /** Catégories dans l'ordre du fournisseur, pour pouvoir re-trier si le réglage change. */
        private var rawCategories: List<Category> = emptyList()

        private fun ranked(categories: List<Category>): List<Category> =
            if (_state.value.preferFrench) Ranking.sortCategories(section, categories) else categories

        private fun setCategories(categories: List<Category>) {
            rawCategories = categories
            mutate { it.copy(categories = ranked(categories), loaded = true) }
            // Les dernières sorties et l'accueil dépendent des catégories FR / VOSTFR
            refreshVirtual()
            viewModelScope.launch { recomputeRecents() }
        }

        fun applyRanking() {
            mutate { it.copy(categories = ranked(rawCategories)) }
            val id = current().selectedId ?: return
            when {
                // Tri désactivé : la pastille « Dernières sorties » disparaît, on revient à la 1re catégorie
                id == LATEST_CATEGORY && !_state.value.preferFrench ->
                    current().categories.firstOrNull()?.let { select(it.id) }
                virtual(id) != null -> refreshVirtual()
                else -> select(id, force = true)
            }
        }

        private fun current() = read(_state.value)

        private fun mutate(f: (Catalog<T>) -> Catalog<T>) =
            _state.update { s -> write(s, f(read(s))) }

        fun cancel() {
            categoriesJob?.cancel()
            itemsJob?.cancel()
        }

        private fun initialCategory(categories: List<Category>): String? {
            val saved = if (resetSavedCategories) null else store.lastCategoryId(section)
            val savedValid = saved != null && (virtual(saved) != null || categories.any { it.id == saved })
            return when {
                savedValid -> saved
                defaultCategory != null && _state.value.preferFrench -> defaultCategory
                else -> current().categories.firstOrNull()?.id
            }
        }

        fun ensureLoaded() {
            if (current().loaded || categoriesJob?.isActive == true) return
            val c = client ?: return
            mutate { it.copy(loading = it.items.isEmpty(), error = null) }
            categoriesJob = viewModelScope.launch {
                val cached = runCatching { loadCategories(c, true) }.getOrNull()
                if (cached != null) {
                    setCategories(cached)
                    initialCategory(cached)?.let { select(it) } ?: mutate { it.copy(loading = false) }
                }
                try {
                    val fresh = loadCategories(c, false).orEmpty()
                    if (fresh != rawCategories || cached == null) setCategories(fresh)
                    if (cached == null) {
                        initialCategory(fresh)?.let { select(it) } ?: mutate { it.copy(loading = false) }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    if (cached == null) {
                        mutate { it.copy(loading = false, error = e.message ?: "Impossible de charger les catégories") }
                    }
                }
            }
        }

        fun select(id: String, force: Boolean = false) {
            val cur = current()
            if (!force && id == cur.selectedId && (cur.items.isNotEmpty() || cur.loading)) return
            itemsJob?.cancel()
            store.saveLastCategoryId(section, id)

            virtual(id)?.let { items ->
                mutate { it.copy(selectedId = id, items = items, loading = false, error = null, focusIndex = null) }
                return
            }

            val c = client ?: return
            mutate { it.copy(selectedId = id, items = emptyList(), loading = true, error = null, focusIndex = null) }
            itemsJob = viewModelScope.launch {
                val cached = runCatching { loadItems(c, id, true) }.getOrNull()?.let(sortItems)
                if (cached != null) {
                    mutate { if (it.selectedId == id) it.copy(items = cached, loading = false) else it }
                }
                try {
                    val fresh = sortItems(loadItems(c, id, false).orEmpty())
                    mutate {
                        when {
                            it.selectedId != id -> it
                            it.items != fresh -> it.copy(items = fresh, loading = false)
                            else -> it.copy(loading = false)
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    if (cached == null) {
                        mutate { it.copy(loading = false, error = e.message ?: "Impossible de charger le contenu") }
                    }
                }
            }
        }

        /** Recalcule une catégorie virtuelle affichée (favori ajouté, catalogue de replay chargé…). */
        fun refreshVirtual() {
            val id = current().selectedId ?: return
            val items = virtual(id) ?: return
            mutate { it.copy(items = items) }
        }

        fun retry() {
            if (!current().loaded) {
                mutate { it.copy(error = null) }
                ensureLoaded()
            } else {
                current().selectedId?.let { select(it, force = true) }
            }
        }

        fun rememberFocus(index: Int) = mutate { it.copy(focusIndex = index) }
    }
}
