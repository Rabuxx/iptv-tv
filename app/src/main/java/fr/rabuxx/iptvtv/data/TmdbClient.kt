package fr.rabuxx.iptvtv.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

enum class TmdbKind(val path: String) { Movie("movie"), Tv("tv") }

data class CastMember(val name: String, val role: String?, val photo: String?)

/** Titre recommandé par TMDB. */
data class TmdbRef(val id: Long, val title: String, val originalTitle: String?, val year: Int?, val poster: String?)

data class TmdbDetails(
    val id: Long,
    val kind: TmdbKind,
    val title: String,
    val overview: String?,
    val backdrop: String?,
    val poster: String?,
    val rating: Double?,
    val genres: List<String>,
    /** Clé YouTube de la bande-annonce (VF de préférence), ou null. */
    val trailerKey: String?,
    val cast: List<CastMember>,
    val recommendations: List<TmdbRef>,
)

/**
 * Client TMDB (themoviedb.org) : bandes-annonces, casting, images, recommandations.
 * Accepte une clé API v3 (32 caractères) ou un jeton de lecture v4 (« eyJ… »).
 * Les réponses sont gardées en cache disque une semaine.
 */
class TmdbClient(private val apiKey: String, private val cache: DiskCache?) {

    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private val bearer = apiKey.startsWith("eyJ")

    /** Détails complets à partir d'un identifiant TMDB. */
    suspend fun details(kind: TmdbKind, id: Long): TmdbDetails? = withContext(Dispatchers.IO) {
        val json = getJson(
            "${kind.path}/$id",
            mapOf(
                "append_to_response" to "videos,credits,recommendations",
                "include_video_language" to "fr,en,null",
            ),
        ) ?: return@withContext null
        parseDetails(kind, json)
    }

    /** Recherche par titre (et année si connue) quand le serveur IPTV ne fournit pas d'identifiant TMDB. */
    suspend fun search(kind: TmdbKind, title: String, year: Int?): Long? = withContext(Dispatchers.IO) {
        val params = buildMap {
            put("query", title)
            if (year != null) put(if (kind == TmdbKind.Movie) "year" else "first_air_date_year", year.toString())
        }
        val results = getJson("search/${kind.path}", params)?.optJSONArray("results") ?: return@withContext null
        // Sans résultat avec l'année, on retente sans (année du fournisseur parfois décalée)
        val first = results.optJSONObject(0)
        when {
            first != null -> first.optLong("id").takeIf { it > 0 }
            year != null -> search(kind, title, null)
            else -> null
        }
    }

    private fun parseDetails(kind: TmdbKind, o: JSONObject): TmdbDetails {
        val videos = o.optJSONObject("videos")?.optJSONArray("results").objects()
            .filter { it.optString("site") == "YouTube" && it.optString("type") in setOf("Trailer", "Teaser") }
        val trailer = videos.sortedWith(
            compareBy<JSONObject>(
                { if (it.optString("iso_639_1") == "fr") 0 else 1 },
                { if (it.optString("type") == "Trailer") 0 else 1 },
                { if (it.optBoolean("official")) 0 else 1 },
            ),
        ).firstOrNull()?.optString("key")

        val cast = o.optJSONObject("credits")?.optJSONArray("cast").objects().take(15).map {
            CastMember(
                name = it.optString("name"),
                role = it.optString("character").takeIf { r -> r.isNotBlank() },
                photo = image(it.optString("profile_path"), "w185"),
            )
        }

        val recommendations = o.optJSONObject("recommendations")?.optJSONArray("results").objects().map {
            TmdbRef(
                id = it.optLong("id"),
                title = it.optString(if (kind == TmdbKind.Movie) "title" else "name"),
                originalTitle = it.optString(if (kind == TmdbKind.Movie) "original_title" else "original_name").takeIf { t -> t.isNotBlank() },
                year = it.optString(if (kind == TmdbKind.Movie) "release_date" else "first_air_date").take(4).toIntOrNull(),
                poster = image(it.optString("poster_path"), "w342"),
            )
        }

        return TmdbDetails(
            id = o.optLong("id"),
            kind = kind,
            title = o.optString(if (kind == TmdbKind.Movie) "title" else "name"),
            overview = o.optString("overview").takeIf { it.isNotBlank() },
            backdrop = image(o.optString("backdrop_path"), "w1280"),
            poster = image(o.optString("poster_path"), "w500"),
            rating = o.optDouble("vote_average").takeIf { !it.isNaN() && it > 0 },
            genres = o.optJSONArray("genres").objects().map { it.optString("name") },
            trailerKey = trailer,
            cast = cast,
            recommendations = recommendations,
        )
    }

    private fun image(path: String?, size: String): String? =
        path?.takeIf { it.startsWith("/") }?.let { "https://image.tmdb.org/t/p/$size$it" }

    private fun getJson(path: String, params: Map<String, String>): JSONObject? {
        val url: HttpUrl = "https://api.themoviedb.org/3/$path".toHttpUrl().newBuilder().apply {
            addQueryParameter("language", "fr-FR")
            params.forEach { (k, v) -> addQueryParameter(k, v) }
        }.build()
        // La clé ne fait pas partie de la clé de cache
        val cacheKey = "tmdb:$url"
        cache?.readFresh(cacheKey, CACHE_MS)?.let { return runCatching { JSONObject(it) }.getOrNull() }

        val requestUrl = if (bearer) url else url.newBuilder().addQueryParameter("api_key", apiKey).build()
        val request = Request.Builder().url(requestUrl).apply {
            if (bearer) header("Authorization", "Bearer $apiKey")
            header("Accept", "application/json")
        }.build()
        val body = http.newCall(request).execute().use { resp ->
            when {
                resp.code == 401 -> throw IOException("Clé TMDB refusée")
                resp.code == 404 -> return null
                !resp.isSuccessful -> throw IOException("TMDB a répondu HTTP ${resp.code}")
                else -> resp.body?.string() ?: return null
            }
        }
        cache?.write(cacheKey, body)
        return runCatching { JSONObject(body) }.getOrNull()
    }

    private fun JSONArray?.objects(): List<JSONObject> =
        if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }

    private companion object {
        const val CACHE_MS = 7 * 24 * 3600_000L
    }
}
