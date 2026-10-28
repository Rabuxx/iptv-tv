package fr.rabuxx.iptvtv.data

data class Credentials(
    val server: String,
    val username: String,
    val password: String,
) {
    /** URL du serveur normalisée : schéma ajouté si absent, sans slash final. */
    val baseUrl: String
        get() {
            val s = server.trim().trimEnd('/')
            return if (s.startsWith("http://") || s.startsWith("https://")) s else "http://$s"
        }
}

data class AccountInfo(
    val status: String,
    /** Date d'expiration en secondes Unix, null si illimité ou non renseigné. */
    val expiresAt: Long?,
    val maxConnections: String?,
)

data class Category(
    val id: String,
    val name: String,
)

data class Channel(
    val streamId: String,
    val number: String,
    val name: String,
    val icon: String?,
    val categoryId: String?,
    /** Nombre de jours de replay disponibles (0 = pas de replay). */
    val archiveDays: Int = 0,
)

data class Movie(
    val streamId: String,
    val name: String,
    val poster: String?,
    val rating: String?,
    val extension: String,
    val categoryId: String?,
    /** Date d'ajout chez le fournisseur (secondes Unix), 0 si inconnue. */
    val added: Long = 0,
)

data class MovieInfo(
    val plot: String?,
    val genre: String?,
    val year: String?,
    val duration: String?,
    val director: String?,
    val cast: String?,
    val rating: String?,
    val backdrop: String?,
    val poster: String?,
    val extension: String?,
)

data class Series(
    val seriesId: String,
    val name: String,
    val cover: String?,
    val plot: String?,
    val genre: String?,
    val year: String?,
    val rating: String?,
    val backdrop: String?,
    val categoryId: String?,
    /** Dernière mise à jour chez le fournisseur (secondes Unix), 0 si inconnue. */
    val lastModified: Long = 0,
)

data class Episode(
    val id: String,
    val season: Int,
    val number: Int,
    val title: String,
    val extension: String,
    val plot: String?,
    val duration: String?,
    val image: String?,
)

data class SeriesInfo(
    val plot: String?,
    val genre: String?,
    val year: String?,
    val rating: String?,
    val backdrop: String?,
    val cast: String?,
    /** Saisons triées → épisodes triés. */
    val episodes: Map<Int, List<Episode>>,
)

/** Programme passé d'une chaîne avec replay. */
data class EpgProgram(
    val title: String,
    val description: String?,
    /** Heure de début telle que fournie par le serveur (« 2026-10-27 21:00:00 », heure du serveur). */
    val startRaw: String,
    val startTs: Long,
    val stopTs: Long,
    val hasArchive: Boolean,
)

/** Catalogue complet (toutes catégories), utilisé pour la recherche, le replay et l'accueil. */
data class Library(
    val live: List<Channel> = emptyList(),
    val movies: List<Movie> = emptyList(),
    val series: List<Series> = emptyList(),
)

data class Favorites(
    val channels: List<Channel> = emptyList(),
    val movies: List<Movie> = emptyList(),
    val series: List<Series> = emptyList(),
) {
    fun has(channel: Channel) = channels.any { it.streamId == channel.streamId }
    fun has(movie: Movie) = movies.any { it.streamId == movie.streamId }
    fun has(series: Series) = this.series.any { it.seriesId == series.seriesId }
}

/** Entrée de la rangée « À suivre » : un film entamé, ou une série dont on regarde un épisode. */
data class HistoryItem(
    val movie: Movie? = null,
    val series: Series? = null,
    /** Épisode en cours pour une série. */
    val episodeId: String? = null,
    /** « S1 · É3 » pour une série. */
    val label: String? = null,
    /** Clé de position de lecture à afficher en barre de progression. */
    val progressKey: String,
    val updatedAt: Long,
) {
    val id: String get() = movie?.let { "m:${it.streamId}" } ?: "s:${series?.seriesId}"
}

enum class Section(val label: String) {
    Home("Accueil"),
    Live("Direct"),
    Movies("Films"),
    Series("Séries"),
    Search("Rechercher"),
    Settings("Réglages"),
}

/** Catégories virtuelles ajoutées en tête des pastilles. */
const val FAVORITES_CATEGORY = "__favorites__"
const val REPLAY_CATEGORY = "__replay__"
/** Films / séries : derniers ajouts FR et VOSTFR de tout le catalogue. */
const val LATEST_CATEGORY = "__latest__"
