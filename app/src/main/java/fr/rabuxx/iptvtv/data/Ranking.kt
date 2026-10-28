package fr.rabuxx.iptvtv.data

import java.text.Normalizer
import java.util.Locale

/**
 * Classement des catégories à partir de leur nom, tel que les fournisseurs les écrivent :
 * « FR| SPORTS », « |FR| BEIN SPORT », « FRANCE : CINÉMA », « VOSTFR - SÉRIES », « FR VOD 4K »…
 */
object Ranking {

    private val DIACRITICS = Regex("\\p{Mn}+")
    private val SEPARATORS = Regex("[^A-Z0-9+]+")

    private val FRENCH = setOf("FR", "FRA", "FRANCE", "FRENCH", "FRANCAIS", "FRANCAISE", "VF", "VFF", "VFQ", "VFI", "TRUEFRENCH")
    private val VOSTFR = setOf("VOSTFR", "VOST", "STFR", "VOSTF")
    private val SPORT_WORDS = setOf("BEIN", "EUROSPORT", "DAZN", "FOOT", "FOOTBALL", "LIGUE", "LIGUE1", "UFC", "F1", "NBA", "NFL", "RUGBY", "TENNIS", "MOTO", "GOLF")

    private fun tokens(name: String): List<String> =
        Normalizer.normalize(name, Normalizer.Form.NFD)
            .replace(DIACRITICS, "")
            .uppercase(Locale.ROOT)
            .split(SEPARATORS)
            .filter { it.isNotBlank() }

    fun isVostfr(name: String): Boolean = tokens(name).any { it in VOSTFR }

    fun isFrench(name: String): Boolean = tokens(name).any { it in FRENCH }

    fun isSport(name: String): Boolean = tokens(name).any { t -> t.startsWith("SPORT") || t in SPORT_WORDS }

    /** Plus le score est bas, plus la catégorie remonte. */
    private fun score(section: Section, name: String): Int = when (section) {
        Section.Live -> {
            val fr = isFrench(name)
            val sport = isSport(name)
            when {
                fr && sport -> 0
                fr -> 1
                sport -> 2
                else -> 3
            }
        }
        else -> when {
            isVostfr(name) -> 1 // testé avant FR : « VOSTFR » n'est pas du FR doublé
            isFrench(name) -> 0
            else -> 2
        }
    }

    /** Tri stable : l'ordre du fournisseur est conservé à l'intérieur de chaque groupe. */
    fun sortCategories(section: Section, categories: List<Category>): List<Category> =
        categories.withIndex()
            .sortedWith(compareBy({ score(section, it.value.name) }, { it.index }))
            .map { it.value }

    /** Catégories françaises ou VOSTFR (films et séries). */
    fun frenchCategoryIds(categories: List<Category>): Set<String> =
        categories.filter { isFrench(it.name) || isVostfr(it.name) }.map { it.id }.toSet()

    fun vostfrCategoryIds(categories: List<Category>): Set<String> =
        categories.filter { isVostfr(it.name) }.map { it.id }.toSet()

    /**
     * Les [limit] éléments les plus récents, limités aux catégories FR / VOSTFR quand il y en a.
     * Les éléments sans date d'ajout sont ignorés.
     */
    fun <T> latest(
        items: List<T>,
        categories: List<Category>,
        frenchOnly: Boolean,
        limit: Int,
        date: (T) -> Long,
        categoryOf: (T) -> String?,
    ): List<T> {
        val ids = if (frenchOnly) frenchCategoryIds(categories) else emptySet()
        val pool = if (ids.isNotEmpty()) items.filter { categoryOf(it) in ids } else items
        return pool.filter { date(it) > 0 }.sortedByDescending(date).take(limit)
    }
}
