package fr.rabuxx.iptvtv.data

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri

/** Une ligne du grand fond de Rabux Home. */
data class TopShelfRow(val title: String, val subtitle: String?, val image: String, val uri: String)

/** Contenus à mettre en avant : les plus récents de l'historique ayant une image, large de préférence. */
fun topShelfRows(history: List<HistoryItem>, limit: Int = 8): List<TopShelfRow> =
    history
        .sortedByDescending { it.updatedAt }
        .mapNotNull { item ->
            val movie = item.movie
            val series = item.series
            val title = movie?.name ?: series?.name ?: return@mapNotNull null
            val image = series?.backdrop ?: series?.cover ?: movie?.poster ?: return@mapNotNull null
            TopShelfRow(title, item.label, image, "rabuxtv://history/${percentEncode(item.id)}")
        }
        .take(limit)

/** Équivalent de Uri.encode, sans dépendre d'Android pour rester testable. */
private fun percentEncode(value: String): String =
    java.net.URLEncoder.encode(value, "UTF-8").replace("+", "%20")

/**
 * Expose à Rabux Home la liste des contenus à reprendre.
 * Réservé par le manifeste aux apps signées avec la même clé que Rabux TV.
 */
class TopShelfProvider : ContentProvider() {

    override fun onCreate() = true

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor {
        val cursor = MatrixCursor(arrayOf("title", "subtitle", "image", "uri"))
        val context = context ?: return cursor
        topShelfRows(LibraryStore(context).history()).forEach {
            cursor.addRow(arrayOf(it.title, it.subtitle, it.image, it.uri))
        }
        return cursor
    }

    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0
}
