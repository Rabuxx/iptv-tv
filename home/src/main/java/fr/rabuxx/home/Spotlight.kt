package fr.rabuxx.home

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Un film ou une série mis en avant dans le grand fond de l'accueil. */
data class Spotlight(
    val title: String,
    val overview: String?,
    val backdrop: String,
    val thumbnail: String,
    val logo: String?,
    val isSeries: Boolean,
)

/** Plateformes reconnues : paquet Android TV → (nom, identifiant de fournisseur TMDB en France). */
val PROVIDERS = mapOf(
    "com.netflix.ninja" to ("Netflix" to 8),
    "com.amazon.amazonvideo.livingroom" to ("Prime Video" to 119),
    "com.disney.disneyplus" to ("Disney+" to 337),
    "com.canal.android.canal" to ("Canal+" to 381),
)

/** Alterne séries et films : A1, B1, A2, B2… */
fun <T> interleave(a: List<T>, b: List<T>): List<T> =
    (0 until maxOf(a.size, b.size)).flatMap { i -> listOfNotNull(a.getOrNull(i), b.getOrNull(i)) }

/**
 * Ce qui est populaire en ce moment sur une plateforme, d'après TMDB (titres, résumés et images en français).
 * Sans clé TMDB, ou en cas de souci réseau, la liste est vide : l'accueil garde son fond coloré.
 */
object SpotlightApi {
    private val apiKey = BuildConfig.TMDB_API_KEY
    // Jeton « lecture » TMDB (long) ou ancienne clé API (32 caractères)
    private val bearer = apiKey.length > 40

    fun load(providerId: Int, limit: Int = 10): List<Spotlight> {
        if (apiKey.isBlank()) return emptyList()
        val series = discover("tv", providerId)
        val movies = discover("movie", providerId)
        return interleave(series, movies).take(limit).map { it.copy(logo = logo(it)) }
    }

    private val ids = HashMap<Spotlight, Long>()

    private fun discover(kind: String, providerId: Int): List<Spotlight> = runCatching {
        val json = get(
            "discover/$kind",
            mapOf(
                "language" to "fr-FR",
                "watch_region" to "FR",
                "with_watch_providers" to providerId.toString(),
                "sort_by" to "popularity.desc",
            ),
        ) ?: return emptyList()
        val results = json.optJSONArray("results") ?: return emptyList()
        (0 until results.length()).mapNotNull { i ->
            val o = results.optJSONObject(i) ?: return@mapNotNull null
            val backdrop = o.optString("backdrop_path").takeIf { it.startsWith("/") } ?: return@mapNotNull null
            val title = o.optString(if (kind == "tv") "name" else "title").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            Spotlight(
                title = title,
                overview = o.optString("overview").takeIf { it.isNotBlank() },
                backdrop = "https://image.tmdb.org/t/p/w1280$backdrop",
                thumbnail = "https://image.tmdb.org/t/p/w780$backdrop",
                logo = null,
                isSeries = kind == "tv",
            ).also { synchronized(ids) { ids[it] = o.optLong("id") } }
        }
    }.getOrDefault(emptyList())

    /** Logo du titre (français de préférence), comme sur l'Apple TV. */
    private fun logo(item: Spotlight): String? = runCatching {
        val id = synchronized(ids) { ids[item] } ?: return null
        val json = get(
            "${if (item.isSeries) "tv" else "movie"}/$id/images",
            mapOf("include_image_language" to "fr,en,null"),
        ) ?: return null
        val logos = json.optJSONArray("logos") ?: return null
        val all = (0 until logos.length()).mapNotNull { logos.optJSONObject(it) }
        val best = all.firstOrNull { it.optString("iso_639_1") == "fr" } ?: all.firstOrNull()
        best?.optString("file_path")?.takeIf { it.startsWith("/") }?.let { "https://image.tmdb.org/t/p/w500$it" }
    }.getOrNull()

    private fun get(path: String, params: Map<String, String>): JSONObject? {
        val query = (params + if (bearer) emptyMap() else mapOf("api_key" to apiKey))
            .entries.joinToString("&") { (k, v) -> "$k=${URLEncoder.encode(v, "UTF-8")}" }
        val connection = URL("https://api.themoviedb.org/3/$path?$query").openConnection() as HttpURLConnection
        connection.connectTimeout = 10_000
        connection.readTimeout = 10_000
        connection.setRequestProperty("Accept", "application/json")
        if (bearer) connection.setRequestProperty("Authorization", "Bearer $apiKey")
        return try {
            if (connection.responseCode != 200) null
            else JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
        } finally {
            connection.disconnect()
        }
    }
}
