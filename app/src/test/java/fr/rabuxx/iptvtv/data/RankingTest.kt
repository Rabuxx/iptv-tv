package fr.rabuxx.iptvtv.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RankingTest {

    @Test
    fun `detecte le francais dans les ecritures habituelles des fournisseurs`() {
        assertTrue(Ranking.isFrench("FR| SPORTS"))
        assertTrue(Ranking.isFrench("|FR| BEIN SPORT"))
        assertTrue(Ranking.isFrench("FRANCE : CINÉMA"))
        assertTrue(Ranking.isFrench("VF - Films 4K"))
        assertFalse(Ranking.isFrench("AFRICA"))
        assertFalse(Ranking.isFrench("UK | MOVIES"))
    }

    @Test
    fun `VOSTFR n'est pas compte comme du francais double`() {
        assertTrue(Ranking.isVostfr("VOSTFR - SÉRIES"))
        assertFalse(Ranking.isFrench("VOSTFR - SÉRIES"))
    }

    @Test
    fun `detecte le sport`() {
        assertTrue(Ranking.isSport("FR| SPORTS"))
        assertTrue(Ranking.isSport("|FR| BEIN SPORT 1"))
        assertTrue(Ranking.isSport("DAZN"))
        assertFalse(Ranking.isSport("FR| GENERALISTES"))
    }

    @Test
    fun `direct - sport FR puis FR puis autres sports puis le reste`() {
        val sorted = Ranking.sortCategories(
            Section.Live,
            listOf(
                Category("1", "UK SPORT"),
                Category("2", "FR GENERALISTES"),
                Category("3", "ALLEMAGNE"),
                Category("4", "FR SPORTS"),
            ),
        )
        assertEquals(listOf("4", "2", "1", "3"), sorted.map { it.id })
    }

    @Test
    fun `films - FR puis VOSTFR puis le reste, ordre du fournisseur conserve`() {
        val sorted = Ranking.sortCategories(
            Section.Movies,
            listOf(
                Category("1", "EN MOVIES"),
                Category("2", "VOSTFR FILMS"),
                Category("3", "FR ACTION"),
                Category("4", "FR COMEDIE"),
            ),
        )
        assertEquals(listOf("3", "4", "2", "1"), sorted.map { it.id })
    }

    @Test
    fun `dernieres sorties limitees aux categories FR et VOSTFR`() {
        val categories = listOf(Category("fr", "FR FILMS"), Category("vo", "VOSTFR"), Category("en", "EN MOVIES"))
        val movies = listOf(
            movie("a", "fr", added = 100),
            movie("b", "en", added = 300),
            movie("c", "vo", added = 200),
            movie("d", "fr", added = 0), // sans date : ignoré
        )
        val latest = Ranking.latest(movies, categories, frenchOnly = true, limit = 10, date = { it.added }, categoryOf = { it.categoryId })
        assertEquals(listOf("c", "a"), latest.map { it.streamId })
    }

    @Test
    fun `sans categorie FR, toutes les sorties sont gardees`() {
        val categories = listOf(Category("en", "EN MOVIES"))
        val movies = listOf(movie("a", "en", added = 1), movie("b", "en", added = 2))
        val latest = Ranking.latest(movies, categories, frenchOnly = true, limit = 10, date = { it.added }, categoryOf = { it.categoryId })
        assertEquals(listOf("b", "a"), latest.map { it.streamId })
    }

    private fun movie(id: String, category: String, added: Long) =
        Movie(streamId = id, name = id, poster = null, rating = null, extension = "mp4", categoryId = category, added = added)
}
