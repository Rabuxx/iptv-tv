package fr.rabuxx.iptvtv.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class V08Test {

    @Test
    fun premiereVerificationSansNouveaute() {
        val r = NewEpisodes.update(known = emptyMap(), previousFresh = emptyMap(), current = mapOf("s1" to 10))
        assertEquals(mapOf("s1" to 10), r.known)
        assertTrue(r.fresh.isEmpty())
        assertTrue(r.toNotify.isEmpty())
    }

    @Test
    fun nouveauxEpisodesEtNotificationUneSeuleFois() {
        val first = NewEpisodes.update(mapOf("s1" to 10), emptyMap(), mapOf("s1" to 12))
        assertEquals(mapOf("s1" to 2), first.fresh)
        assertEquals(setOf("s1"), first.toNotify)
        assertEquals(10, first.known["s1"]) // la référence ne bouge pas tant que la fiche n'est pas ouverte

        // Vérification suivante sans changement : pas de nouvelle notification
        val again = NewEpisodes.update(first.known, first.fresh, mapOf("s1" to 12))
        assertEquals(mapOf("s1" to 2), again.fresh)
        assertTrue(again.toNotify.isEmpty())

        // Encore un épisode : nouvelle notification, compteur cumulé
        val more = NewEpisodes.update(again.known, again.fresh, mapOf("s1" to 13))
        assertEquals(mapOf("s1" to 3), more.fresh)
        assertEquals(setOf("s1"), more.toNotify)
    }

    @Test
    fun ouvrirLaFicheEfface() {
        val (known, fresh) = NewEpisodes.acknowledge(mapOf("s1" to 10), mapOf("s1" to 2), "s1", 12)
        assertEquals(12, known["s1"])
        assertTrue(fresh.isEmpty())
    }

    @Test
    fun episodesRetiresParLeFournisseur() {
        val r = NewEpisodes.update(mapOf("s1" to 10), mapOf("s1" to 1), mapOf("s1" to 8))
        assertEquals(8, r.known["s1"])
        assertTrue(r.fresh.isEmpty())
    }

    @Test
    fun reglagesSousTitresEnBoucle() {
        assertEquals(SubtitleSize.Medium, SubtitleSize.Small.next())
        assertEquals(SubtitleSize.Small, SubtitleSize.XLarge.next())
        assertEquals(SubtitleMode.French, enumOrDefault("French", SubtitleMode.Auto))
        assertEquals(SubtitleMode.Auto, enumOrDefault("Inconnu", SubtitleMode.Auto))
        assertEquals("2 nouveaux épisodes", NewEpisodes.label(2))
        assertEquals("Nouvel épisode", NewEpisodes.label(1))
    }
}
