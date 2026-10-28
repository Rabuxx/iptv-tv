package fr.rabuxx.iptvtv.data

import java.text.Collator
import java.util.Locale

enum class SortMode(val label: String) {
    Recent("Récents"),
    Rating("Note"),
    Alpha("A → Z"),
    ;

    fun next(): SortMode = entries[(ordinal + 1) % entries.size]
}

/** Réglages d'affichage d'une grille de films ou de séries. */
data class ListFilter(
    val sort: SortMode = SortMode.Recent,
    /** Note minimale sur 10 (0 = toutes). */
    val minRating: Int = 0,
) {
    val ratingLabel: String get() = if (minRating == 0) "Toutes" else "≥ $minRating"

    companion object {
        val RATING_STEPS = listOf(0, 6, 7, 8)
    }
}

object ListPresenter {

    /** Préfixes de fournisseur à ignorer pour le tri alphabétique : « FR | », « VOSTFR - », « [4K] »… */
    private val PREFIX = Regex("^\\s*(\\[[^]]*]|\\|?[A-Z0-9]{2,8}\\|?)\\s*[|:\\-–]+\\s*")

    private val collator: Collator = Collator.getInstance(Locale.FRANCE).apply { strength = Collator.PRIMARY }

    fun sortKey(name: String): String = name.replace(PREFIX, "").trim().ifEmpty { name }

    fun <T> apply(
        filter: ListFilter,
        items: List<T>,
        date: (T) -> Long,
        rating: (T) -> String?,
        name: (T) -> String,
    ): List<T> {
        fun score(item: T): Double = rating(item)?.replace(',', '.')?.toDoubleOrNull() ?: 0.0

        val filtered = if (filter.minRating > 0) items.filter { score(it) >= filter.minRating } else items
        return when (filter.sort) {
            // Les éléments sans date gardent l'ordre du fournisseur, en fin de liste
            SortMode.Recent -> filtered.sortedByDescending(date)
            SortMode.Rating -> filtered.sortedWith(
                compareByDescending<T> { score(it) }.thenComparator { a, b -> collator.compare(sortKey(name(a)), sortKey(name(b))) },
            )
            SortMode.Alpha -> filtered.sortedWith { a, b -> collator.compare(sortKey(name(a)), sortKey(name(b))) }
        }
    }
}
