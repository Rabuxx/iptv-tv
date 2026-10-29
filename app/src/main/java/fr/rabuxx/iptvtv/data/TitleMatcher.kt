package fr.rabuxx.iptvtv.data

import java.text.Normalizer
import java.util.Locale

/**
 * Rapproche un titre TMDB (« Dune : Deuxième partie », 2024) d'un titre de fournisseur
 * (« FR | Dune: Deuxième Partie (2024) [4K] »). Sans dépendance Android : testable en JVM.
 */
object TitleMatcher {

    private val DIACRITICS = Regex("\\p{Mn}+")
    /** Année entre parenthèses ou crochets : « Dune (2021) », « Dune [2021] ». */
    private val YEAR_MARKED = Regex("[(\\[]\\s*((?:19|20)\\d{2})\\s*[)\\]]")

    /** Année en fin de titre : « Dune - 2021 », « Dune 2021 ». */
    private val YEAR_TRAILING = Regex("(?:\\s*[-–|]\\s*|\\s+)((?:19|20)\\d{2})\\s*$")

    private val MAX_YEAR = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR) + 1
    private val BRACKETS = Regex("\\[[^]]*]|\\([^)]*\\)")
    private val TAGS = Regex(
        "\\b(4k|uhd|fhd|hd|sd|multi|vostfr|vost|vf|vff|vfq|truefrench|french|hdr|hevc|x265|1080p|720p|2160p)\\b",
        RegexOption.IGNORE_CASE,
    )
    private val NON_ALNUM = Regex("[^a-z0-9]+")

    /**
     * Année trouvée dans le nom, ou null. Une année en fin de titre n'est retenue que si elle est plausible
     * et qu'il reste un titre : « Blade Runner 2049 » et « 1917 » gardent leur nombre.
     */
    fun year(name: String): Int? = yearMatch(name)?.groupValues?.get(1)?.toIntOrNull()

    private fun yearMatch(name: String): MatchResult? {
        YEAR_MARKED.findAll(name).lastOrNull()?.let { return it }
        val cleaned = name.replace(BRACKETS, " ").trimEnd()
        val m = YEAR_TRAILING.find(cleaned) ?: return null
        val y = m.groupValues[1].toInt()
        val rest = cleaned.substring(0, m.range.first).trim()
        return if (y <= MAX_YEAR && rest.any { it.isLetter() }) m else null
    }

    /** Titre lisible pour une recherche ou un affichage : « FR | Dune (2021) [4K] » → « Dune ». */
    fun cleanTitle(name: String): String {
        var s = ListPresenter.sortKey(name).replace(BRACKETS, " ").replace(TAGS, " ").trimEnd()
        yearMatch(s)?.let { m -> s = s.substring(0, m.range.first) }
        return s
            .replace(Regex("\\s+"), " ")
            .trim()
            .trim('-', '–', '|', ':')
            .trim()
            .ifEmpty { name }
    }

    /** Clé de comparaison : sans préfixe de fournisseur, sans année, sans accents ni ponctuation. */
    fun key(name: String): String {
        // Crochets et parenthèses d'abord (années comprises), puis l'éventuelle année en fin de titre
        var s = ListPresenter.sortKey(name).replace(BRACKETS, " ").replace(TAGS, " ").trimEnd()
        yearMatch(s)?.let { m -> s = s.substring(0, m.range.first) }
        s = Normalizer.normalize(s, Normalizer.Form.NFD).replace(DIACRITICS, "").lowercase(Locale.ROOT)
        s = s.replace("&", " and ").replace(NON_ALNUM, " ").trim()
        // « The », « Le », « La », « Les » en tête ne comptent pas
        s = s.replace(Regex("^(the|le|la|les|l) "), "")
        return s
    }

    /** Index d'un catalogue par clé de titre, pour retrouver vite les recommandations disponibles. */
    class Index<T>(items: List<T>, private val name: (T) -> String) {
        private val byKey: Map<String, List<T>> = items.groupBy { key(name(it)) }

        /** Élément du catalogue correspondant à un titre TMDB, en privilégiant la bonne année. */
        fun find(title: String, year: Int?, originalTitle: String? = null): T? {
            val candidates = byKey[key(title)].orEmpty() + originalTitle?.let { byKey[key(it)] }.orEmpty()
            if (candidates.isEmpty()) return null
            if (year == null) return candidates.first()
            return candidates.firstOrNull { c -> year(name(c))?.let { kotlin.math.abs(it - year) <= 1 } ?: false }
                ?: candidates.firstOrNull { c -> year(name(c)) == null }
        }
    }
}
