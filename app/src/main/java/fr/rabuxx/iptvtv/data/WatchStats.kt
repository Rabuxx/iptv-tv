package fr.rabuxx.iptvtv.data

/** Type de contenu regardé. */
enum class WatchType { Live, Movie, Series }

/** Ce qui a été regardé (chaîne, film ou série). */
data class WatchItem(
    val key: String,
    val type: WatchType,
    val name: String,
    val image: String?,
    val genre: String?,
)

/**
 * Journal de visionnage : secondes regardées par jour (« 2026-10-29 ») et par élément.
 * Logique pure, sans Android : testable en JVM. Le stockage est fait par WatchStatsStore.
 */
data class WatchLog(
    val items: Map<String, WatchItem> = emptyMap(),
    val days: Map<String, Map<String, Long>> = emptyMap(),
)

data class Ranked<T>(val value: T, val seconds: Long)

data class StatsSummary(
    val totalSeconds: Long,
    val byType: Map<WatchType, Long>,
    val topChannels: List<Ranked<WatchItem>>,
    val topMovies: List<Ranked<WatchItem>>,
    val topSeries: List<Ranked<WatchItem>>,
    val topGenres: List<Ranked<String>>,
    /** Secondes par jour de la période, dans l'ordre. */
    val perDay: List<Ranked<String>>,
    val activeDays: Int,
    /** Plus longue série de jours consécutifs avec au moins 5 minutes regardées. */
    val longestStreak: Int,
)

object WatchStatsCalc {

    /** Jours conservés dans le journal. */
    const val KEEP_DAYS = 400

    private const val ACTIVE_DAY_SECONDS = 5 * 60L

    fun record(log: WatchLog, item: WatchItem, day: String, seconds: Long): WatchLog {
        if (seconds <= 0) return log
        val dayMap = log.days[day].orEmpty()
        val updatedDay = dayMap + (item.key to (dayMap[item.key] ?: 0L) + seconds)
        var days = log.days + (day to updatedDay)
        if (days.size > KEEP_DAYS) days = days.toSortedMap().entries.toList().takeLast(KEEP_DAYS).associate { it.key to it.value }
        return WatchLog(items = log.items + (item.key to item), days = days)
    }

    /** Bilan sur les jours donnés (dans l'ordre chronologique). */
    fun summarize(log: WatchLog, period: List<String>, top: Int = 5): StatsSummary {
        val perItem = HashMap<String, Long>()
        val perDay = period.map { day ->
            val map = log.days[day].orEmpty()
            map.forEach { (k, s) -> perItem[k] = (perItem[k] ?: 0L) + s }
            Ranked(day, map.values.sum())
        }

        fun topOf(type: WatchType) = perItem.mapNotNull { (k, s) -> log.items[k]?.takeIf { it.type == type }?.let { Ranked(it, s) } }
            .sortedByDescending { it.seconds }
            .take(top)

        val byType = WatchType.entries.associateWith { type ->
            perItem.entries.sumOf { (k, s) -> if (log.items[k]?.type == type) s else 0L }
        }

        // Un film « Action, Aventure » compte pour chacun de ses genres
        val genres = HashMap<String, Long>()
        perItem.forEach { (k, s) ->
            log.items[k]?.genre
                ?.split(',', '/', '&', ';')
                ?.map { it.trim().replaceFirstChar { c -> c.uppercase() } }
                ?.filter { it.length > 1 }
                ?.distinct()
                ?.forEach { g -> genres[g] = (genres[g] ?: 0L) + s }
        }

        var streak = 0
        var best = 0
        perDay.forEach { d ->
            if (d.seconds >= ACTIVE_DAY_SECONDS) {
                streak++
                best = maxOf(best, streak)
            } else {
                streak = 0
            }
        }

        return StatsSummary(
            totalSeconds = perDay.sumOf { it.seconds },
            byType = byType,
            topChannels = topOf(WatchType.Live),
            topMovies = topOf(WatchType.Movie),
            topSeries = topOf(WatchType.Series),
            topGenres = genres.map { Ranked(it.key, it.value) }.sortedByDescending { it.seconds }.take(top),
            perDay = perDay,
            activeDays = perDay.count { it.seconds >= ACTIVE_DAY_SECONDS },
            longestStreak = best,
        )
    }

    /** « 12 h 05 », « 45 min », « 0 min ». */
    fun formatHours(seconds: Long): String {
        val minutes = seconds / 60
        return if (minutes >= 60) "${minutes / 60} h ${"%02d".format(minutes % 60)}" else "$minutes min"
    }
}
