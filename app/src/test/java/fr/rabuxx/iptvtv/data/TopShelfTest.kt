package fr.rabuxx.iptvtv.data

import org.junit.Assert.assertEquals
import org.junit.Test

class TopShelfTest {

    private fun series(id: String, backdrop: String?, cover: String?) = Series(
        seriesId = id, name = "Série $id", cover = cover, plot = null, genre = null,
        year = null, rating = null, backdrop = backdrop, categoryId = null,
    )

    private fun item(series: Series, at: Long) =
        HistoryItem(series = series, label = "S1 · É2", progressKey = "k", updatedAt = at)

    @Test
    fun lePlusRecentPasseEnPremier() {
        val rows = topShelfRows(listOf(item(series("1", "a", null), 1), item(series("2", "b", null), 2)))
        assertEquals(listOf("Série 2", "Série 1"), rows.map { it.title })
    }

    @Test
    fun lImageLargeEstPrefereeAuPoster() {
        assertEquals("large", topShelfRows(listOf(item(series("1", "large", "poster"), 1))).single().image)
    }

    @Test
    fun unContenuSansImageEstIgnore() {
        assertEquals(emptyList<TopShelfRow>(), topShelfRows(listOf(item(series("1", null, null), 1))))
    }

    @Test
    fun leLienOuvreLEntreeDeLHistorique() {
        assertEquals("rabuxtv://history/s%3A1", topShelfRows(listOf(item(series("1", "a", null), 1))).single().uri)
    }
}
