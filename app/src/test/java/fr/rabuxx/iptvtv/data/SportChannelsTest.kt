package fr.rabuxx.iptvtv.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SportChannelsTest {

    private fun ch(id: String, name: String, cat: String = "fr") = Channel(id, id, name, null, cat)

    private val categories = mapOf("fr" to "FR| SPORTS", "canal" to "FRANCE | CANAL+", "ar" to "AR| BEIN SPORTS", "uk" to "UK| SPORTS")

    @Test
    fun uneSeuleVersionParChaineEnFhd() {
        val picks = SportChannels.pick(
            listOf(
                ch("1", "FR| CANAL+ SD", "canal"),
                ch("2", "FR| CANAL+ HD", "canal"),
                ch("3", "FR| CANAL+ FHD", "canal"),
                ch("4", "FR| CANAL+ 4K", "canal"),
                ch("5", "FR| CANAL+ FHD (BACKUP)", "canal"),
                ch("6", "FR| CANAL+ CINEMA FHD", "canal"),
                ch("7", "FR| CANAL+ SPORT 360 HD", "canal"),
                ch("8", "FR| CANAL+SPORT FHD", "canal"),
                ch("9", "|FR| beIN SPORT 1 ᶠᴴᴰ"),
                ch("10", "FR: BEIN SPORTS1 HD"),
                ch("11", "FR| BEIN SPORTS MAX 4"),
                ch("12", "RMC SPORT 1 FHD"),
                ch("13", "FR | DAZN 1 HD"),
                ch("14", "AR: BEIN SPORTS 1 HD", "ar"),
                ch("15", "BEIN SPORTS 2", "ar"),
                ch("16", "FR| EUROSPORT 2 HD"),
                ch("17", "FR| L'ÉQUIPE HD"),
                ch("18", "UK| SKY SPORTS F1", "uk"),
                ch("19", "FR| LIGUE1+ FHD"),
            ),
        ) { categories[it] }

        val labels = picks.map { it.label }
        assertEquals(
            listOf(
                "Canal+", "Canal+ Sport 360", "Canal+ Sport", "beIN Sports 1", "beIN Sports Max 4",
                "RMC Sport 1", "DAZN 1", "Ligue 1+", "Eurosport 2", "L'Équipe",
            ),
            labels,
        )
        val canal = picks.first()
        assertEquals("3", canal.channel.streamId) // FHD
        assertEquals(listOf("3", "2", "4", "1", "5"), canal.variants.map { it.streamId })
        assertTrue(canal.generalist)
        // beIN 1 : la version FHD (« ᶠᴴᴰ ») avant la HD, la version arabe écartée
        assertEquals(listOf("9", "10"), picks[3].variants.map { it.streamId })
        assertEquals("beIN Sports", picks[3].group)
    }

    @Test
    fun cleDeChaine() {
        assertEquals("CANAL+", SportChannels.key("FR| Canal + HD"))
        assertEquals("CANAL+", SportChannels.key("CANAL PLUS FHD"))
        assertEquals("RMC SPORT 1", SportChannels.key("RMC SPORT1 HEVC"))
        assertEquals("SPORT EN FRANCE", SportChannels.key("FR| SPORT EN FRANCE"))
    }

    private fun prog(title: String) = EpgProgram(title, null, "", 0, 1, false)

    @Test
    fun canalPlusGeneralisteNeGardeQueLeSport() {
        assertTrue(SportChannels.keep(prog("Ligue 1 McDonald's : PSG / OM"), generalist = true))
        assertTrue(SportChannels.keep(prog("Canal Football Club"), generalist = true))
        assertTrue(SportChannels.keep(prog("Real Madrid vs Barcelone"), generalist = true))
        assertTrue(SportChannels.keep(prog("Formule 1 : Grand Prix du Mexique"), generalist = true))
        assertFalse(SportChannels.keep(prog("Dune : deuxième partie"), generalist = true))
        assertFalse(SportChannels.keep(prog("Le journal des jeux vidéo"), generalist = true))
        // Une chaîne 100 % sport garde tout, sauf le remplissage
        assertTrue(SportChannels.keep(prog("Le Late Football Club"), generalist = false))
        assertTrue(SportChannels.keep(prog("Magazine"), generalist = false))
        assertFalse(SportChannels.keep(prog("Fin des programmes"), generalist = false))
    }
}
