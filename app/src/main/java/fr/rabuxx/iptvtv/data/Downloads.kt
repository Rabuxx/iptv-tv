package fr.rabuxx.iptvtv.data

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Téléchargements hors ligne, confiés au gestionnaire du système : ils continuent écran éteint
 * ou app fermée, avec leur notification, et uniquement en Wi-Fi.
 * Les fichiers restent dans le dossier privé de l'app : ils disparaissent à sa désinstallation.
 */
class Downloads(context: Context) {

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("downloads", Context.MODE_PRIVATE)
    private val manager = appContext.getSystemService(DownloadManager::class.java)
    private val folder: File? get() = appContext.getExternalFilesDir(Environment.DIRECTORY_MOVIES)

    fun add(item: DownloadItem): List<DownloadItem> = synchronized(LOCK) {
        val items = read().filterNot { it.id == item.id } + item.copy(state = DownloadState.Queued)
        write(items)
        refreshLocked()
    }

    fun retry(id: String): List<DownloadItem> = synchronized(LOCK) {
        write(read().map { if (it.id == id) it.copy(state = DownloadState.Queued, systemId = -1, bytes = 0) else it })
        refreshLocked()
    }

    fun remove(id: String): List<DownloadItem> = synchronized(LOCK) {
        read().firstOrNull { it.id == id }?.let { item ->
            if (item.systemId >= 0) runCatching { manager.remove(item.systemId) }
            file(item)?.delete()
        }
        write(read().filterNot { it.id == id })
        refreshLocked()
    }

    /** Met à jour l'avancement et lance le suivant dans la file. */
    fun refresh(): List<DownloadItem> = synchronized(LOCK) { refreshLocked() }

    fun fileUri(item: DownloadItem): String? = file(item)?.takeIf { it.exists() }?.let { Uri.fromFile(it).toString() }

    fun freeBytes(): Long = folder?.usableSpace ?: 0

    private fun file(item: DownloadItem): File? = folder?.let { File(it, item.fileName) }

    private fun refreshLocked(): List<DownloadItem> {
        var items = read().map(::withSystemStatus)
        nextDownload(items)?.let { next ->
            val started = start(next)
            items = items.map { if (it.id == next.id) started else it }
        }
        write(items)
        return items
    }

    private fun withSystemStatus(item: DownloadItem): DownloadItem {
        if (item.state != DownloadState.Running) return item
        return runCatching {
            manager.query(DownloadManager.Query().setFilterById(item.systemId)).use { c ->
                if (!c.moveToFirst()) return@use item.copy(state = DownloadState.Failed)
                val status = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                item.copy(
                    bytes = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)),
                    totalBytes = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)),
                    state = when (status) {
                        DownloadManager.STATUS_SUCCESSFUL -> DownloadState.Done
                        DownloadManager.STATUS_FAILED -> DownloadState.Failed
                        else -> DownloadState.Running
                    },
                )
            }
        }.getOrDefault(item.copy(state = DownloadState.Failed))
    }

    private fun start(item: DownloadItem): DownloadItem = runCatching {
        file(item)?.delete()
        val request = DownloadManager.Request(Uri.parse(item.url))
            .setTitle(item.title)
            .setDescription(item.subtitle)
            .addRequestHeader("User-Agent", USER_AGENT)
            .setAllowedOverMetered(false)
            .setAllowedOverRoaming(false)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(appContext, Environment.DIRECTORY_MOVIES, item.fileName)
        item.copy(systemId = manager.enqueue(request), state = DownloadState.Running)
    }.getOrDefault(item.copy(state = DownloadState.Failed))

    private fun read(): List<DownloadItem> {
        val raw = prefs.getString("items", null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).map { i ->
                val o = array.getJSONObject(i)
                DownloadItem(
                    id = o.getString("id"),
                    title = o.getString("title"),
                    subtitle = o.optString("subtitle").ifEmpty { null },
                    image = o.optString("image").ifEmpty { null },
                    url = o.getString("url"),
                    extension = o.getString("extension"),
                    systemId = o.optLong("systemId", -1),
                    state = DownloadState.valueOf(o.getString("state")),
                    bytes = o.optLong("bytes"),
                    totalBytes = o.optLong("totalBytes"),
                )
            }
        }.getOrDefault(emptyList())
    }

    private fun write(items: List<DownloadItem>) {
        val array = JSONArray()
        items.forEach {
            array.put(
                JSONObject()
                    .put("id", it.id)
                    .put("title", it.title)
                    .put("subtitle", it.subtitle.orEmpty())
                    .put("image", it.image.orEmpty())
                    .put("url", it.url)
                    .put("extension", it.extension)
                    .put("systemId", it.systemId)
                    .put("state", it.state.name)
                    .put("bytes", it.bytes)
                    .put("totalBytes", it.totalBytes),
            )
        }
        prefs.edit().putString("items", array.toString()).apply()
    }

    private companion object {
        val LOCK = Any()
    }
}

/** Fin d'un téléchargement, même app fermée : le suivant de la file démarre. */
class DownloadReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Downloads(context).refresh()
    }
}
