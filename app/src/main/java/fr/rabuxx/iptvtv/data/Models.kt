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
    /** Fuseau horaire du serveur (« Europe/Paris »), utilisé pour les adresses de différé. */
    val serverTimeZone: String? = null,
) {
    /** Nombre de connexions simultanées autorisées, ou null si inconnu. */
    val maxStreams: Int? get() = maxConnections?.toIntOrNull()?.takeIf { it > 0 }
}

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
    /** Identifiant TMDB fourni par le serveur, s'il existe. */
    val tmdbId: String? = null,
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
    val tmdbId: String? = null,
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
/** Direct : ouvre la page « Sport ce soir » au lieu d'une catégorie. */
const val SPORT_CATEGORY = "__sport__"
/** Direct : ouvre le multi-écran. */
const val MULTI_CATEGORY = "__multi__"

/** Programme en cours parmi une liste triée, ou null. */
fun List<EpgProgram>.nowPlaying(nowSec: Long = System.currentTimeMillis() / 1000): EpgProgram? =
    firstOrNull { it.startTs <= nowSec && nowSec < it.stopTs }

/** Programme qui suit celui en cours. */
fun List<EpgProgram>.upNext(nowSec: Long = System.currentTimeMillis() / 1000): EpgProgram? =
    firstOrNull { it.startTs > nowSec }

/** Avancement du programme entre 0 et 1. */
fun EpgProgram.progress(nowSec: Long = System.currentTimeMillis() / 1000): Float =
    if (stopTs <= startTs) 0f else ((nowSec - startTs).toFloat() / (stopTs - startTs)).coerceIn(0f, 1f)

/** Programme d'une page « Sport ce soir ». */
data class SportProgram(
    val channel: Channel,
    val program: EpgProgram,
    /** Nom propre de la chaîne (« beIN Sports 1 ») au lieu de « FR| BEIN SPORTS 1 FHD ». */
    val label: String = channel.name,
    /** Groupe pour les filtres : « Canal+ », « beIN Sports »… */
    val group: String = "",
)

data class SportTonight(
    val live: List<SportProgram> = emptyList(),
    val upcoming: List<SportProgram> = emptyList(),
    /** Chaînes sport utilisées, pour zapper de l'une à l'autre depuis le lecteur. */
    val channels: List<Channel> = emptyList(),
    /** Groupes présents, dans l'ordre d'affichage des filtres. */
    val groups: List<String> = emptyList(),
)

/** Clé de visibilité d'une catégorie : les identifiants Direct / Films / Séries peuvent se recouper. */
fun categoryKey(section: Section, categoryId: String) = "${section.name}:$categoryId"
