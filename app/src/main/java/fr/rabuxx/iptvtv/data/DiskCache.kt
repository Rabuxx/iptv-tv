package fr.rabuxx.iptvtv.data

import android.content.Context
import java.io.File
import java.security.MessageDigest

/**
 * Cache disque très simple des réponses de l'API : une réponse = un fichier.
 * Permet d'afficher les catalogues instantanément au lancement, puis de les rafraîchir en arrière-plan.
 */
class DiskCache(context: Context) {

    private val dir = File(context.cacheDir, "xtream").apply { mkdirs() }

    fun read(key: String): String? =
        runCatching { file(key).takeIf { it.exists() }?.readText() }.getOrNull()

    /** Contenu en cache s'il a moins de [maxAgeMs], sinon null. */
    fun readFresh(key: String, maxAgeMs: Long): String? = runCatching {
        file(key).takeIf { it.exists() && System.currentTimeMillis() - it.lastModified() < maxAgeMs }?.readText()
    }.getOrNull()

    fun write(key: String, body: String) {
        runCatching {
            val target = file(key)
            val tmp = File(dir, target.name + ".tmp")
            tmp.writeText(body)
            tmp.renameTo(target)
        }
    }

    fun clear() {
        dir.listFiles()?.forEach { it.delete() }
    }

    private fun file(key: String) = File(dir, sha1(key) + ".json")

    private fun sha1(s: String): String =
        MessageDigest.getInstance("SHA-1").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }
}
