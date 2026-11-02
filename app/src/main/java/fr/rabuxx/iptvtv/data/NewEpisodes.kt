package fr.rabuxx.iptvtv.data

/**
 * Nouveaux épisodes des séries suivies (« À suivre » et favoris).
 * [known] : nombre d'épisodes vus chez le fournisseur lors de la dernière visite de la fiche.
 * Sans dépendance Android : testable en JVM.
 */
object NewEpisodes {

    data class Result(
        /** Référence mise à jour (séries découvertes, épisodes retirés par le fournisseur). */
        val known: Map<String, Int>,
        /** Série → nombre de nouveaux épisodes depuis la dernière visite. */
        val fresh: Map<String, Int>,
        /** Séries dont le nombre de nouveautés a augmenté depuis la vérification précédente : à notifier. */
        val toNotify: Set<String>,
    )

    fun update(known: Map<String, Int>, previousFresh: Map<String, Int>, current: Map<String, Int>): Result {
        val k = known.toMutableMap()
        val fresh = previousFresh.toMutableMap()
        val notify = mutableSetOf<String>()
        for ((id, count) in current) {
            val ref = k[id]
            when {
                // Première vérification de cette série : elle devient la référence, rien de « nouveau »
                ref == null -> k[id] = count
                count > ref -> {
                    val n = count - ref
                    if (n > (previousFresh[id] ?: 0)) notify += id
                    fresh[id] = n
                }
                else -> {
                    // Épisodes retirés par le fournisseur : la référence suit
                    if (count < ref) k[id] = count
                    fresh.remove(id)
                }
            }
        }
        return Result(k, fresh, notify)
    }

    /** La fiche de la série a été ouverte : ses épisodes ne sont plus « nouveaux ». */
    fun acknowledge(known: Map<String, Int>, fresh: Map<String, Int>, seriesId: String, count: Int): Pair<Map<String, Int>, Map<String, Int>> =
        (known + (seriesId to count)) to (fresh - seriesId)

    fun label(n: Int): String = if (n > 1) "$n nouveaux épisodes" else "Nouvel épisode"
}
