package fr.rabuxx.iptvtv.data

import java.text.Normalizer
import java.util.Locale

/**
 * Choix des chaînes de « Sport ce soir » : les chaînes sport françaises connues (Canal+, beIN, RMC, DAZN…),
 * une seule version par chaîne (FHD de préférence) au lieu des dizaines de variantes du fournisseur.
 * Sans dépendance Android : testable en JVM.
 */
object SportChannels {

    private class Brand(
        val group: String,
        val label: String,
        val pattern: Regex,
        /** Chaîne qui diffuse aussi films et séries : seuls ses programmes de sport sont gardés. */
        val generalist: Boolean = false,
    )

    // Ordre = ordre d'affichage. Les motifs s'appliquent au nom nettoyé (voir key()).
    private val BRANDS = listOf(
        Brand("Canal+", "Canal+", Regex("CANAL\\+"), generalist = true),
        Brand("Canal+", "Canal+ Sport 360", Regex("CANAL\\+ SPORTS? 360")),
        Brand("Canal+", "Canal+ Sport", Regex("CANAL\\+ SPORTS?")),
        Brand("Canal+", "Canal+ Foot", Regex("CANAL\\+ FOOT")),
        Brand("Canal+", "Canal+ Live", Regex("CANAL\\+ LIVE (\\d{1,2})")),
        Brand("beIN Sports", "beIN Sports", Regex("BEIN SPORTS? (\\d)")),
        Brand("beIN Sports", "beIN Sports Max", Regex("BEIN SPORTS? MAX (\\d{1,2})")),
        Brand("RMC Sport", "RMC Sport", Regex("RMC SPORTS? (\\d{1,2})")),
        Brand("RMC Sport", "RMC Sport Live", Regex("RMC SPORTS? LIVE (\\d{1,2})")),
        Brand("DAZN", "DAZN", Regex("DAZN(?: (\\d{1,2}))?")),
        Brand("Ligue 1+", "Ligue 1+", Regex("LIGUE 1\\+(?: (\\d{1,2}))?")),
        Brand("Eurosport", "Eurosport", Regex("EUROSPORT (\\d)")),
        Brand("Autres", "L'Équipe", Regex("L ?EQUIPE(?: TV)?")),
        Brand("Autres", "Infosport+", Regex("INFOSPORT\\+")),
        Brand("Autres", "Sport en France", Regex("SPORT EN FRANCE")),
        Brand("Autres", "Automoto", Regex("AUTOMOTO(?: LA CHAINE)?")),
        Brand("Autres", "Golf+", Regex("GOLF\\+")),
        Brand("Autres", "Equidia", Regex("EQUIDIA")),
        Brand("Autres", "Multisports", Regex("(?:CANAL\\+ )?MULTISPORTS?(?: (\\d{1,2}))?")),
    )

    val GROUPS: List<String> = BRANDS.map { it.group }.distinct()

    private val DIACRITICS = Regex("\\p{Mn}+")
    private val SEPARATORS = Regex("[^A-Z0-9+]+")
    private val GLUED_NUMBER = Regex("([A-Z]{3,})(\\d{1,2})")

    /** Mots ajoutés par les fournisseurs, sans rapport avec l'identité de la chaîne. */
    private val NOISE = setOf(
        "VF", "TNT", "FHD", "HD", "SD", "UHD", "4K", "8K", "HQ", "LQ", "LOW", "HEVC", "H264",
        "H265", "HDR", "FPS", "50FPS", "60FPS", "1080P", "720P", "2160P", "RAW", "BACKUP", "BK", "ALT", "VIP",
        "PREMIUM", "PLUS4K", "SAT", "IPTV",
    )

    /** Mention du pays, retirée seulement en début ou en fin (« Sport en France » garde son nom). */
    private val COUNTRY = setOf("FR", "FRA", "FRANCE")

    /** Pays étrangers : « AR: BEIN SPORTS 1 » n'est pas la chaîne française. */
    private val FOREIGN = setOf(
        "AR", "ARAB", "ARABIC", "MENA", "UK", "GB", "ES", "ESP", "IT", "ITA", "DE", "GER", "PT", "TR", "US", "USA",
        "NL", "PL", "AF", "AFRICA", "AFRIQUE", "CA", "BE", "CH", "MAGHREB",
    )

    private fun tokens(name: String): List<String> =
        Normalizer.normalize(name, Normalizer.Form.NFKD)
            .replace(DIACRITICS, "")
            .uppercase(Locale.ROOT)
            .replace(Regex("\\s+\\+"), "+") // « CANAL + » → « CANAL+ »
            .replace(Regex("\\bCANAL\\s*PLUS\\b"), "CANAL+")
            .replace(Regex("\\bLIGUE\\s*1\\s*\\+"), "LIGUE 1+")
            .replace(Regex("\\+(?=[A-Z0-9])"), "+ ") // « CANAL+SPORT » → « CANAL+ SPORT »
            .split(SEPARATORS)
            .filter { it.isNotBlank() }
            .flatMap { t ->
                // « SPORT1 » → « SPORT 1 » (mais « F1 » reste « F1 »)
                GLUED_NUMBER.matchEntire(t)?.let { listOf(it.groupValues[1], it.groupValues[2]) } ?: listOf(t)
            }

    /** Nom de chaîne nettoyé : « |FR| beIN SPORT 1 ᶠᴴᴰ » → « BEIN SPORT 1 ». */
    fun key(name: String): String {
        val t = tokens(name).filter { it !in NOISE }.dropWhile { it in COUNTRY }.toMutableList()
        while (t.size > 1 && t.last() in COUNTRY && t[t.size - 2] != "EN") t.removeAt(t.size - 1)
        return t.joinToString(" ")
    }

    /** Plus c'est bas, mieux c'est : FHD, puis HD, sans mention, 4K (lourd), SD ; secours en dernier. */
    fun qualityRank(name: String): Int {
        val t = tokens(name).toSet()
        var r = when {
            "FHD" in t || "1080P" in t -> 0
            "HD" in t || "720P" in t -> 1
            "4K" in t || "UHD" in t || "2160P" in t || "8K" in t -> 3
            "SD" in t || "LQ" in t || "LOW" in t -> 4
            else -> 2
        }
        if (t.any { it == "BACKUP" || it == "BK" || it == "ALT" }) r += 10
        return r
    }

    /** Une chaîne retenue, avec ses variantes classées de la meilleure à la moins bonne. */
    data class Pick(
        val label: String,
        val group: String,
        val generalist: Boolean,
        val variants: List<Channel>,
        internal val order: Int,
    ) {
        val channel: Channel get() = variants.first()
    }

    /**
     * Chaînes sport françaises connues parmi tout le Direct, dédoublonnées.
     * [categoryName] donne le nom de la catégorie d'une chaîne (pour reconnaître les catégories françaises).
     */
    fun pick(channels: List<Channel>, categoryName: (String?) -> String?): List<Pick> {
        val found = HashMap<String, MutableList<Channel>>()
        val meta = HashMap<String, Triple<Brand, Int, Int>>() // brand, ordre de la marque, numéro
        for (ch in channels) {
            val all = tokens(ch.name)
            if (all.any { it in FOREIGN }) continue
            val catTokens = categoryName(ch.categoryId)?.let(::tokens).orEmpty()
            if (catTokens.any { it in FOREIGN } && "FR" !in all && "FRANCE" !in all) continue
            val k = key(ch.name)
            val idx = BRANDS.indexOfFirst { it.pattern.matches(k) }
            if (idx < 0) continue
            val brand = BRANDS[idx]
            val num = brand.pattern.matchEntire(k)?.groupValues?.getOrNull(1)?.toIntOrNull()
            val label = if (num != null) "${brand.label} $num" else brand.label
            found.getOrPut(label) { mutableListOf() }.add(ch)
            meta[label] = Triple(brand, idx, num ?: 0)
        }
        return found.map { (label, variants) ->
            val (brand, idx, num) = meta.getValue(label)
            Pick(
                label = label,
                group = brand.group,
                generalist = brand.generalist,
                variants = variants.withIndex()
                    .sortedWith(compareBy({ qualityRank(it.value.name) }, { it.index }))
                    .map { it.value },
                order = idx * 100 + num,
            )
        }.sortedBy { it.order }
    }

    // ---------- Programmes ----------

    private val SPORT_TITLE = Regex(
        "\\b(sport|sports|ligue|champions|europa|conference|coupe|cup|top 14|pro d2|rugby|football|foot|match|" +
            "multiplex|f1|formule|grand prix|gp|motogp|moto|tennis|roland|nba|nfl|nhl|mlb|ufc|mma|boxe|golf|" +
            "cyclisme|tour de france|biathlon|ski|handball|basket|volley|premier league|liga|serie a|bundesliga|" +
            "jeux olympiques|euro|mondial|j\\d{1,2}|journee|avant-match|apres-match)\\b",
        RegexOption.IGNORE_CASE,
    )

    private val FILLER = Regex(
        "^(fin des programmes|programme non communique|pas de programme|no information|no programme|" +
            "aucun programme|programmes? de nuit|a suivre|en attente)",
        RegexOption.IGNORE_CASE,
    )

    private fun plain(s: String) = Normalizer.normalize(s, Normalizer.Form.NFD).replace(DIACRITICS, "").trim()

    /** Programme à afficher ? Les chaînes généralistes ne gardent que le sport. */
    fun keep(program: EpgProgram, generalist: Boolean): Boolean {
        val title = plain(program.title)
        if (title.isEmpty() || FILLER.containsMatchIn(title)) return false
        if (!generalist) return true
        // « PSG / OM », « Real Madrid vs Barcelone » : une affiche de match
        return SPORT_TITLE.containsMatchIn(title) || Regex("\\S \\/ \\S| vs\\.? ", RegexOption.IGNORE_CASE).containsMatchIn(title)
    }
}
