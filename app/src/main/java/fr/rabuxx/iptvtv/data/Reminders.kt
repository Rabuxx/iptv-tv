package fr.rabuxx.iptvtv.data

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import fr.rabuxx.iptvtv.R
import org.json.JSONArray
import org.json.JSONObject

/** Rappel « préviens-moi quand ça commence » sur un programme à venir. */
data class Reminder(
    val channel: Channel,
    val title: String,
    val startTs: Long,
    val stopTs: Long,
) {
    val id: String get() = "${channel.streamId}:$startTs"
}

/**
 * Rappels de programmes. Quand l'app est ouverte, une fenêtre propose de basculer sur la chaîne ;
 * sinon une notification est envoyée (selon le modèle de TV, les notifications sont plus ou moins visibles).
 */
class Reminders(private val context: Context) {

    private val prefs = context.getSharedPreferences("reminders", Context.MODE_PRIVATE)

    fun all(): List<Reminder> = runCatching {
        val arr = JSONArray(prefs.getString("list", "[]"))
        (0 until arr.length()).mapNotNull { i -> arr.optJSONObject(i)?.toReminder() }
    }.getOrDefault(emptyList())
        // Les rappels de programmes terminés disparaissent
        .filter { it.stopTs > System.currentTimeMillis() / 1000 }

    fun add(reminder: Reminder): List<Reminder> {
        val list = (all().filterNot { it.id == reminder.id } + reminder).sortedBy { it.startTs }
        save(list)
        schedule(reminder)
        return list
    }

    fun remove(id: String): List<Reminder> {
        all().firstOrNull { it.id == id }?.let { cancel(it) }
        val list = all().filterNot { it.id == id }
        save(list)
        return list
    }

    fun clear() {
        all().forEach { cancel(it) }
        save(emptyList())
    }

    private fun save(list: List<Reminder>) {
        val arr = JSONArray()
        list.forEach { arr.put(it.toJson()) }
        prefs.edit().putString("list", arr.toString()).apply()
    }

    // ---------- Alarme système (app fermée) ----------

    private fun pending(reminder: Reminder): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java)
            .setAction(ACTION)
            .putExtra(EXTRA_ID, reminder.id)
            .putExtra(EXTRA_TITLE, reminder.title)
            .putExtra(EXTRA_CHANNEL, reminder.channel.name)
            .putExtra(EXTRA_STREAM, reminder.channel.streamId)
        return PendingIntent.getBroadcast(
            context,
            reminder.id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun schedule(reminder: Reminder) {
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        val at = reminder.startTs * 1000 - LEAD_MS
        if (at <= System.currentTimeMillis()) return
        // Fenêtre de 2 minutes : pas besoin de l'autorisation « alarmes exactes »
        runCatching { alarms.setWindow(AlarmManager.RTC_WAKEUP, at - WINDOW_MS, WINDOW_MS, pending(reminder)) }
    }

    private fun cancel(reminder: Reminder) {
        context.getSystemService(AlarmManager::class.java)?.cancel(pending(reminder))
    }

    private fun Reminder.toJson() = JSONObject()
        .put("streamId", channel.streamId).put("number", channel.number).put("name", channel.name)
        .put("icon", channel.icon ?: "").put("categoryId", channel.categoryId ?: "").put("archiveDays", channel.archiveDays)
        .put("title", title).put("start", startTs).put("stop", stopTs)

    private fun JSONObject.toReminder(): Reminder? {
        val id = optString("streamId").takeIf { it.isNotBlank() } ?: return null
        return Reminder(
            channel = Channel(
                streamId = id,
                number = optString("number"),
                name = optString("name"),
                icon = optString("icon").takeIf { it.isNotBlank() },
                categoryId = optString("categoryId").takeIf { it.isNotBlank() },
                archiveDays = optInt("archiveDays"),
            ),
            title = optString("title"),
            startTs = optLong("start"),
            stopTs = optLong("stop"),
        )
    }

    companion object {
        const val ACTION = "fr.rabuxx.iptvtv.REMINDER"
        const val EXTRA_ID = "id"
        const val EXTRA_TITLE = "title"
        const val EXTRA_CHANNEL = "channel"
        const val EXTRA_STREAM = "stream"
        const val CHANNEL_ID = "reminders"

        /** Prévenir 1 minute avant le début. */
        const val LEAD_MS = 60_000L
        private const val WINDOW_MS = 120_000L

        fun ensureChannel(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val manager = context.getSystemService(NotificationManager::class.java) ?: return
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Rappels de programmes", NotificationManager.IMPORTANCE_HIGH),
            )
        }
    }
}

/** Reçoit l'alarme d'un rappel et affiche la notification ; un clic ouvre la chaîne (rabuxtv://live/<id>). */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Reminders.ACTION) return
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        Reminders.ensureChannel(context)
        val stream = intent.getStringExtra(Reminders.EXTRA_STREAM) ?: return
        val open = Intent(Intent.ACTION_VIEW, Uri.parse("rabuxtv://live/$stream"))
            .setPackage(context.packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val content = PendingIntent.getActivity(
            context,
            stream.hashCode(),
            open,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, Reminders.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle(intent.getStringExtra(Reminders.EXTRA_TITLE) ?: "Ton programme commence")
            .setContentText("Commence dans 1 minute sur ${intent.getStringExtra(Reminders.EXTRA_CHANNEL).orEmpty()}")
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(content)
            .setAutoCancel(true)
            .build()
        runCatching {
            context.getSystemService(NotificationManager::class.java)
                ?.notify(intent.getStringExtra(Reminders.EXTRA_ID).hashCode(), notification)
        }
    }
}
