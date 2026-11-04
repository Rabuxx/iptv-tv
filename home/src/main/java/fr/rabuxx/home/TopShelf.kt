package fr.rabuxx.home

import android.content.Context
import android.net.Uri

data class ShelfItem(val title: String, val subtitle: String?, val image: String, val uri: String)

/** Lit les contenus à reprendre dans Rabux TV. Une liste vide n'est jamais une erreur : l'accueil s'en passe. */
object TopShelf {
    private val URI: Uri = Uri.parse("content://fr.rabuxx.iptvtv.topshelf/items")

    fun load(context: Context): List<ShelfItem> = runCatching {
        context.contentResolver.query(URI, null, null, null, null)?.use { c ->
            buildList {
                while (c.moveToNext()) {
                    add(ShelfItem(c.getString(0), c.getString(1), c.getString(2), c.getString(3)))
                }
            }
        }
    }.getOrNull().orEmpty()
}
