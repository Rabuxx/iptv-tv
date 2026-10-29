package fr.rabuxx.iptvtv.data

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Période du bilan de visionnage. */
enum class StatsPeriod(val label: String, val days: Int) {
    Week("7 jours", 7),
    Month("30 jours", 30),
    Year("12 mois", 365),
}

/** Journal de visionnage enregistré dans un fichier JSON privé de l'app. */
class WatchStatsStore(context: Context) {

    private val file = File(context.filesDir, "watch_stats.json")
    private var log: WatchLog = load()
    private var dirty = false

    @Synchronized
    fun record(item: WatchItem, seconds: Long) {
        if (seconds <= 0) return
        log = WatchStatsCalc.record(log, item, today(), seconds)
        dirty = true
    }

    /** Écrit le journal sur le disque (appelé régulièrement et à la fermeture des lecteurs). */
    @Synchronized
    fun flush() {
        if (!dirty) return
        runCatching {
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeText(toJson(log).toString())
            tmp.renameTo(file)
            dirty = false
        }
    }

    @Synchronized
    fun summary(period: StatsPeriod): StatsSummary = WatchStatsCalc.summarize(log, lastDays(period.days))

    @Synchronized
    fun clear() {
        log = WatchLog()
        file.delete()
        dirty = false
    }

    private fun today(): String = DAY.get()!!.format(Date())

    private fun lastDays(count: Int): List<String> {
        val cal = Calendar.getInstance()
        val format = DAY.get()!!
        return (0 until count).map {
            val day = format.format(cal.time)
            cal.add(Calendar.DAY_OF_YEAR, -1)
            day
        }.reversed()
    }

    private fun load(): WatchLog = runCatching {
        if (!file.exists()) return WatchLog()
        val o = JSONObject(file.readText())
        val itemsJson = o.optJSONObject("items") ?: JSONObject()
        val items = itemsJson.keys().asSequence().mapNotNull { k ->
            val i = itemsJson.optJSONObject(k) ?: return@mapNotNull null
            k to WatchItem(
                key = k,
                type = runCatching { WatchType.valueOf(i.optString("type")) }.getOrDefault(WatchType.Live),
                name = i.optString("name"),
                image = i.optString("image").takeIf { it.isNotBlank() },
                genre = i.optString("genre").takeIf { it.isNotBlank() },
            )
        }.toMap()
        val daysJson = o.optJSONObject("days") ?: JSONObject()
        val days = daysJson.keys().asSequence().associateWith { d ->
            val m = daysJson.optJSONObject(d) ?: JSONObject()
            m.keys().asSequence().associateWith { m.optLong(it) }
        }
        WatchLog(items, days)
    }.getOrDefault(WatchLog())

    private fun toJson(log: WatchLog) = JSONObject().apply {
        put("items", JSONObject().apply {
            log.items.forEach { (k, i) ->
                put(k, JSONObject().put("type", i.type.name).put("name", i.name).put("image", i.image ?: "").put("genre", i.genre ?: ""))
            }
        })
        put("days", JSONObject().apply {
            log.days.forEach { (d, m) -> put(d, JSONObject().apply { m.forEach { (k, s) -> put(k, s) } }) }
        })
    }

    private companion object {
        // ThreadLocal.withInitial n'existe qu'à partir d'Android 8 : forme compatible Android 6
        val DAY = object : ThreadLocal<SimpleDateFormat>() {
            override fun initialValue() = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT)
        }
    }
}
