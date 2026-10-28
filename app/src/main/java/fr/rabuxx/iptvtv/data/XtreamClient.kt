package fr.rabuxx.iptvtv.data

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

const val USER_AGENT = "RabuxTV/0.6 (Android TV)"

/**
 * Client pour l'API Xtream Codes (player_api.php).
 *
 * Direct  : get_live_categories, get_live_streams         → {server}/live/{u}/{p}/{id}.ts|m3u8
 * Replay  : get_simple_data_table&stream_id=              → {server}/timeshift/{u}/{p}/{minutes}/{YYYY-MM-DD:HH-MM}/{id}.ts
 * Films   : get_vod_categories, get_vod_streams, get_vod_info        → {server}/movie/{u}/{p}/{id}.{ext}
 * Séries  : get_series_categories, get_series, get_series_info      → {server}/series/{u}/{p}/{episodeId}.{ext}
 *
 * Les méthodes de liste acceptent `cached = true` : elles renvoient alors la dernière réponse
 * enregistrée sur disque (ou null), sans appel réseau. En mode réseau, la réponse est mise en cache.
 */
class XtreamClient(private val creds: Credentials, private val cache: DiskCache?) {

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    // ---------- Compte ----------

    suspend fun authenticate(): AccountInfo = withContext(Dispatchers.IO) {
        val body = get(apiUrl())
        val json = try {
            JSONObject(body)
        } catch (e: Exception) {
            throw IOException("Réponse inattendue : ce serveur ne semble pas compatible Xtream Codes")
        }
        val user = json.optJSONObject("user_info")
            ?: throw IOException("Identifiants refusés par le serveur")
        if (user.optInt("auth", 0) != 1) throw IOException("Identifiant ou mot de passe incorrect")
        val status = user.optString("status", "?")
        if (!status.equals("Active", ignoreCase = true)) {
            throw IOException("Abonnement non actif (statut : $status)")
        }
        AccountInfo(
            status = status,
            expiresAt = user.str("exp_date")?.toLongOrNull(),
            maxConnections = user.str("max_connections"),
        )
    }

    // ---------- Catégories ----------

    suspend fun liveCategories(cached: Boolean = false) = categories("get_live_categories", cached)
    suspend fun movieCategories(cached: Boolean = false) = categories("get_vod_categories", cached)
    suspend fun seriesCategories(cached: Boolean = false) = categories("get_series_categories", cached)

    private suspend fun categories(action: String, cached: Boolean): List<Category>? =
        list(apiUrl(action), cached) { o ->
            Category(id = o.optString("category_id"), name = o.optString("category_name").trim())
        }

    // ---------- Direct ----------

    /** categoryId = null → toutes les chaînes. */
    suspend fun liveStreams(categoryId: String?, cached: Boolean = false): List<Channel>? =
        list(apiUrl("get_live_streams", categoryParam(categoryId)), cached) { o ->
            Channel(
                streamId = o.optString("stream_id"),
                number = o.optString("num"),
                name = o.optString("name").trim(),
                icon = o.url("stream_icon"),
                categoryId = o.str("category_id"),
                archiveDays = if (o.optString("tv_archive") == "1") {
                    o.optString("tv_archive_duration").toIntOrNull()?.coerceAtLeast(1) ?: 1
                } else {
                    0
                },
            )
        }

    fun liveUrl(channel: Channel, extension: String = "ts") =
        "${creds.baseUrl}/live/${enc(creds.username)}/${enc(creds.password)}/${channel.streamId}.$extension"

    // ---------- Replay ----------

    /** Programmes passés disponibles en replay, du plus récent au plus ancien. */
    suspend fun replayPrograms(channel: Channel): List<EpgProgram> = withContext(Dispatchers.IO) {
        val body = get(apiUrl("get_simple_data_table", mapOf("stream_id" to channel.streamId)))
        val json = runCatching { JSONObject(body) }.getOrNull() ?: return@withContext emptyList()
        val now = System.currentTimeMillis() / 1000
        parsePrograms(json)
            .filter { it.hasArchive && it.stopTs <= now }
            .sortedByDescending { it.startTs }
    }

    // ---------- Guide TV ----------

    /** Programme en cours et suivants d'une chaîne (get_short_epg), triés dans l'ordre chronologique. */
    suspend fun shortEpg(channel: Channel, limit: Int = 4): List<EpgProgram> = withContext(Dispatchers.IO) {
        val body = get(apiUrl("get_short_epg", mapOf("stream_id" to channel.streamId, "limit" to limit.toString())))
        val json = runCatching { JSONObject(body) }.getOrNull() ?: return@withContext emptyList()
        val now = System.currentTimeMillis() / 1000
        parsePrograms(json).filter { it.stopTs > now }.sortedBy { it.startTs }
    }

    private fun parsePrograms(json: JSONObject): List<EpgProgram> {
        val listings = json.optJSONArray("epg_listings") ?: return emptyList()
        return listings.objects().mapNotNull { o ->
            val start = o.optString("start_timestamp").toLongOrNull() ?: return@mapNotNull null
            val stop = o.optString("stop_timestamp").toLongOrNull() ?: return@mapNotNull null
            EpgProgram(
                title = decode(o.str("title")) ?: "Programme",
                description = decode(o.str("description")),
                startRaw = o.str("start") ?: "",
                startTs = start,
                stopTs = stop,
                hasArchive = o.optString("has_archive") == "1",
            )
        }
    }

    fun replayUrl(channel: Channel, program: EpgProgram): String {
        val minutes = ((program.stopTs - program.startTs) / 60).coerceAtLeast(1)
        // « 2026-10-27 21:00:00 » → « 2026-10-27:21-00 »
        val date = program.startRaw.take(10)
        val time = program.startRaw.drop(11).take(5).replace(':', '-')
        return "${creds.baseUrl}/timeshift/${enc(creds.username)}/${enc(creds.password)}/$minutes/$date:$time/${channel.streamId}.ts"
    }

    // ---------- Films ----------

    /** categoryId = null → tous les films. */
    suspend fun movies(categoryId: String?, cached: Boolean = false): List<Movie>? =
        list(apiUrl("get_vod_streams", categoryParam(categoryId)), cached) { o ->
            Movie(
                streamId = o.optString("stream_id"),
                name = o.optString("name").trim(),
                poster = o.url("stream_icon"),
                rating = o.rating(),
                extension = o.str("container_extension") ?: "mp4",
                categoryId = o.str("category_id"),
                added = o.optString("added").toLongOrNull() ?: 0,
            )
        }

    suspend fun movieInfo(movie: Movie): MovieInfo = withContext(Dispatchers.IO) {
        val json = JSONObject(get(apiUrl("get_vod_info", mapOf("vod_id" to movie.streamId))))
        val info = json.optJSONObject("info") ?: JSONObject()
        val data = json.optJSONObject("movie_data")
        MovieInfo(
            plot = info.str("plot") ?: info.str("description"),
            genre = info.str("genre"),
            year = (info.str("releasedate") ?: info.str("release_date") ?: info.str("year"))?.take(4),
            duration = info.str("duration"),
            director = info.str("director"),
            cast = info.str("cast") ?: info.str("actors"),
            rating = info.rating(),
            backdrop = info.firstBackdrop(),
            poster = info.url("movie_image") ?: info.url("cover_big"),
            extension = data?.str("container_extension"),
        )
    }

    fun movieUrl(movie: Movie, extension: String = movie.extension) =
        "${creds.baseUrl}/movie/${enc(creds.username)}/${enc(creds.password)}/${movie.streamId}.$extension"

    // ---------- Séries ----------

    /** categoryId = null → toutes les séries. */
    suspend fun series(categoryId: String?, cached: Boolean = false): List<Series>? =
        list(apiUrl("get_series", categoryParam(categoryId)), cached) { o ->
            Series(
                seriesId = o.optString("series_id"),
                name = o.optString("name").trim(),
                cover = o.url("cover"),
                plot = o.str("plot"),
                genre = o.str("genre"),
                year = (o.str("releaseDate") ?: o.str("release_date") ?: o.str("year"))?.take(4),
                rating = o.rating(),
                backdrop = o.firstBackdrop(),
                categoryId = o.str("category_id"),
                lastModified = o.optString("last_modified").toLongOrNull() ?: 0,
            )
        }

    suspend fun seriesInfo(series: Series): SeriesInfo = withContext(Dispatchers.IO) {
        val json = JSONObject(get(apiUrl("get_series_info", mapOf("series_id" to series.seriesId))))
        val info = json.optJSONObject("info") ?: JSONObject()

        val episodes = mutableListOf<Episode>()
        fun parse(o: JSONObject, fallbackSeason: Int) {
            val id = o.str("id") ?: return
            val epInfo = o.optJSONObject("info")
            episodes += Episode(
                id = id,
                season = o.optString("season").toIntOrNull() ?: fallbackSeason,
                number = o.optString("episode_num").toIntOrNull() ?: 0,
                title = o.str("title") ?: "Épisode",
                extension = o.str("container_extension") ?: "mp4",
                plot = epInfo?.str("plot"),
                duration = epInfo?.str("duration"),
                image = epInfo?.url("movie_image"),
            )
        }
        // Selon les serveurs, "episodes" est un objet {"1": [...]} ou un tableau de tableaux
        when (val raw = json.opt("episodes")) {
            is JSONObject -> raw.keys().forEach { key ->
                raw.optJSONArray(key)?.objects()?.forEach { parse(it, key.toIntOrNull() ?: 1) }
            }
            is JSONArray -> for (i in 0 until raw.length()) {
                when (val item = raw.opt(i)) {
                    is JSONArray -> item.objects().forEach { parse(it, i + 1) }
                    is JSONObject -> parse(item, 1)
                }
            }
        }

        SeriesInfo(
            plot = info.str("plot") ?: series.plot,
            genre = info.str("genre") ?: series.genre,
            year = (info.str("releaseDate") ?: info.str("release_date"))?.take(4) ?: series.year,
            rating = info.rating() ?: series.rating,
            backdrop = info.firstBackdrop() ?: series.backdrop,
            cast = info.str("cast"),
            episodes = episodes
                .groupBy { it.season }
                .toSortedMap()
                .mapValues { (_, list) -> list.sortedBy { it.number } },
        )
    }

    fun episodeUrl(episode: Episode) =
        "${creds.baseUrl}/series/${enc(creds.username)}/${enc(creds.password)}/${episode.id}.${episode.extension}"

    // ---------- HTTP / JSON / cache ----------

    private fun categoryParam(categoryId: String?) =
        if (categoryId != null) mapOf("category_id" to categoryId) else emptyMap()

    private fun apiUrl(action: String? = null, extra: Map<String, String> = emptyMap()): HttpUrl {
        val base = "${creds.baseUrl}/player_api.php".toHttpUrlOrNull()
            ?: throw IOException("Adresse du serveur invalide : ${creds.server}")
        return base.newBuilder().apply {
            addQueryParameter("username", creds.username)
            addQueryParameter("password", creds.password)
            if (action != null) addQueryParameter("action", action)
            extra.forEach { (k, v) -> addQueryParameter(k, v) }
        }.build()
    }

    /** Appel réseau bloquant : à n'appeler que depuis Dispatchers.IO. */
    private fun get(url: HttpUrl): String {
        val request = Request.Builder().url(url).header("User-Agent", USER_AGENT).build()
        return http.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("Le serveur a répondu HTTP ${resp.code}")
            resp.body?.string() ?: throw IOException("Réponse vide du serveur")
        }
    }

    /**
     * Récupère et analyse une liste JSON, depuis le cache (`cached = true`) ou le réseau.
     * Certains serveurs renvoient {} ou une chaîne vide au lieu de [] quand une liste est vide.
     */
    private suspend fun <T> list(url: HttpUrl, cached: Boolean, parse: (JSONObject) -> T): List<T>? =
        withContext(Dispatchers.IO) {
            val key = url.toString()
            val raw: String = if (cached) {
                cache?.read(key) ?: return@withContext null
            } else {
                get(url).also { cache?.write(key, it) }
            }
            val body = raw.trim()
            val arr = if (body.startsWith("[")) runCatching { JSONArray(body) }.getOrNull() ?: JSONArray() else JSONArray()
            arr.objects().map(parse)
        }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8").replace("+", "%20")

    private fun decode(b64: String?): String? =
        b64?.let { runCatching { String(Base64.decode(it, Base64.DEFAULT)).trim() }.getOrNull() }
            ?.takeIf { it.isNotEmpty() }
}

private fun JSONArray.objects(): List<JSONObject> =
    (0 until length()).mapNotNull { optJSONObject(it) }

private fun JSONObject.str(key: String): String? =
    optString(key, "").trim().takeIf { it.isNotEmpty() && it != "null" }

private fun JSONObject.url(key: String): String? =
    str(key)?.takeIf { it.startsWith("http") }

private fun JSONObject.rating(): String? =
    str("rating")?.takeIf { (it.toDoubleOrNull() ?: 0.0) > 0.0 }

private fun JSONObject.firstBackdrop(): String? =
    when (val v = opt("backdrop_path")) {
        is JSONArray -> (0 until v.length()).map { v.optString(it) }.firstOrNull { it.startsWith("http") }
        is String -> v.takeIf { it.startsWith("http") }
        else -> null
    }
