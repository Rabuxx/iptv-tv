package fr.rabuxx.iptvtv.data

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import androidx.tvprovider.media.tv.TvContractCompat
import androidx.tvprovider.media.tv.WatchNextProgram

/**
 * Rangée « À suivre » de l'écran d'accueil Android TV (API Watch Next).
 * Chaque entrée de l'historique de l'app y est publiée ; un clic rouvre l'app via rabuxtv://history/<id>.
 * Selon le lanceur (Google TV, lanceur constructeur…), la rangée peut ne pas s'afficher : tout échec est ignoré.
 */
class WatchNext(private val context: Context) {

    private val prefs = context.getSharedPreferences("watch_next", Context.MODE_PRIVATE)

    fun sync(history: List<HistoryItem>, progressOf: (String) -> Pair<Long, Long>) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        runCatching {
            val wanted = history.take(MAX_ITEMS)
            val wantedIds = wanted.map { it.id }.toSet()

            // Entrées qui ne sont plus dans l'historique : on les retire de l'accueil
            prefs.all.keys.filter { it !in wantedIds }.forEach { id ->
                val programId = prefs.getLong(id, -1)
                if (programId >= 0) {
                    runCatching {
                        context.contentResolver.delete(TvContractCompat.buildWatchNextProgramUri(programId), null, null)
                    }
                }
                prefs.edit().remove(id).apply()
            }

            wanted.forEach { item ->
                val (position, duration) = progressOf(item.progressKey)
                val values = build(item, position, duration).toContentValues()
                val programId = prefs.getLong(item.id, -1)
                val updated = programId >= 0 && runCatching {
                    context.contentResolver.update(TvContractCompat.buildWatchNextProgramUri(programId), values, null, null)
                }.getOrDefault(0) > 0
                if (!updated) {
                    // Absente ou supprimée par l'utilisateur depuis le lanceur : on la (re)crée
                    val uri: Uri? = context.contentResolver.insert(TvContractCompat.WatchNextPrograms.CONTENT_URI, values)
                    uri?.let { prefs.edit().putLong(item.id, ContentUris.parseId(it)).apply() }
                }
            }
        }
    }

    fun clear() = sync(emptyList()) { 0L to 0L }

    private fun build(item: HistoryItem, positionMs: Long, durationMs: Long): WatchNextProgram {
        val movie = item.movie
        val series = item.series
        val builder = WatchNextProgram.Builder()
        builder
            .setWatchNextType(TvContractCompat.WatchNextPrograms.WATCH_NEXT_TYPE_CONTINUE)
            .setLastEngagementTimeUtcMillis(item.updatedAt)
            .setInternalProviderId(item.id)
            .setIntentUri(Uri.parse("rabuxtv://history/${Uri.encode(item.id)}"))
        if (movie != null) {
            builder.setType(TvContractCompat.PreviewProgramColumns.TYPE_MOVIE).setTitle(movie.name)
            movie.poster?.let { builder.setPosterArtUri(Uri.parse(it)) }
        } else if (series != null) {
            builder.setType(TvContractCompat.PreviewProgramColumns.TYPE_TV_EPISODE)
                .setTitle(series.name)
                .setEpisodeTitle(item.label)
            series.cover?.let { builder.setPosterArtUri(Uri.parse(it)) }
        }
        builder.setPosterArtAspectRatio(TvContractCompat.PreviewProgramColumns.ASPECT_RATIO_2_3)
        if (durationMs > 0) {
            builder.setDurationMillis(durationMs.coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
            builder.setLastPlaybackPositionMillis(positionMs.coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
        }
        return builder.build()
    }

    private companion object {
        const val MAX_ITEMS = 10
    }
}
