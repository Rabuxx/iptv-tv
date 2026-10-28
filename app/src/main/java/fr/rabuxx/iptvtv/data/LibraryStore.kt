package fr.rabuxx.iptvtv.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Favoris, rangée « À suivre » et chaînes récentes, stockés localement en JSON. */
class LibraryStore(context: Context) {

    private val prefs = context.getSharedPreferences("library", Context.MODE_PRIVATE)

    // ---------- Favoris ----------

    fun favorites() = Favorites(
        channels = readList("fav_live") { it.toChannel() },
        movies = readList("fav_movies") { it.toMovie() },
        series = readList("fav_series") { it.toSeries() },
    )

    fun saveFavorites(f: Favorites) {
        writeList("fav_live", f.channels) { it.toJson() }
        writeList("fav_movies", f.movies) { it.toJson() }
        writeList("fav_series", f.series) { it.toJson() }
    }

    // ---------- À suivre ----------

    fun history(): List<HistoryItem> = readList("history") { o ->
        HistoryItem(
            movie = o.optJSONObject("movie")?.toMovie(),
            series = o.optJSONObject("series")?.toSeries(),
            episodeId = o.s("episodeId"),
            label = o.s("label"),
            progressKey = o.optString("progressKey"),
            updatedAt = o.optLong("updatedAt"),
        )
    }.filter { it.movie != null || it.series != null }

    fun saveHistory(items: List<HistoryItem>) = writeList("history", items) { h ->
        JSONObject()
            .put("movie", h.movie?.toJson())
            .put("series", h.series?.toJson())
            .put("episodeId", h.episodeId)
            .put("label", h.label)
            .put("progressKey", h.progressKey)
            .put("updatedAt", h.updatedAt)
    }

    // ---------- Chaînes récentes ----------

    fun recentChannels(): List<Channel> = readList("recent_live") { it.toChannel() }

    fun saveRecentChannels(list: List<Channel>) = writeList("recent_live", list) { it.toJson() }

    fun clear() = prefs.edit().clear().apply()

    // ---------- JSON ----------

    private fun <T> readList(key: String, parse: (JSONObject) -> T?): List<T> {
        val raw = prefs.getString(key, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { i -> arr.optJSONObject(i)?.let(parse) }
        }.getOrDefault(emptyList())
    }

    private fun <T> writeList(key: String, list: List<T>, toJson: (T) -> JSONObject) {
        val arr = JSONArray()
        list.forEach { arr.put(toJson(it)) }
        prefs.edit().putString(key, arr.toString()).apply()
    }
}

private fun JSONObject.s(key: String): String? =
    if (isNull(key)) null else optString(key).takeIf { it.isNotEmpty() }

private fun Channel.toJson() = JSONObject()
    .put("streamId", streamId).put("number", number).put("name", name)
    .put("icon", icon).put("categoryId", categoryId).put("archiveDays", archiveDays)

private fun JSONObject.toChannel(): Channel? = s("streamId")?.let {
    Channel(it, optString("number"), optString("name"), s("icon"), s("categoryId"), optInt("archiveDays"))
}

private fun Movie.toJson() = JSONObject()
    .put("streamId", streamId).put("name", name).put("poster", poster).put("rating", rating)
    .put("extension", extension).put("categoryId", categoryId).put("added", added)

private fun JSONObject.toMovie(): Movie? = s("streamId")?.let {
    Movie(it, optString("name"), s("poster"), s("rating"), s("extension") ?: "mp4", s("categoryId"), optLong("added"))
}

private fun Series.toJson() = JSONObject()
    .put("seriesId", seriesId).put("name", name).put("cover", cover).put("plot", plot)
    .put("genre", genre).put("year", year).put("rating", rating).put("backdrop", backdrop)
    .put("categoryId", categoryId).put("lastModified", lastModified)

private fun JSONObject.toSeries(): Series? = s("seriesId")?.let {
    Series(
        seriesId = it, name = optString("name"), cover = s("cover"), plot = s("plot"), genre = s("genre"),
        year = s("year"), rating = s("rating"), backdrop = s("backdrop"), categoryId = s("categoryId"),
        lastModified = optLong("lastModified"),
    )
}
