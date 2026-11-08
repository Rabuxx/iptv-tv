package fr.rabuxx.iptvtv.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DownloadLogicTest {

    private fun item(id: String, state: DownloadState) =
        DownloadItem(id, "Titre", null, null, "http://x/1.mp4", "mp4", state = state)

    @Test
    fun unSeulTelechargementALaFois() {
        assertNull(nextDownload(listOf(item("a", DownloadState.Running), item("b", DownloadState.Queued))))
    }

    @Test
    fun lePremierEnAttentePart() {
        val items = listOf(item("a", DownloadState.Done), item("b", DownloadState.Queued), item("c", DownloadState.Queued))
        assertEquals("b", nextDownload(items)?.id)
    }

    @Test
    fun unEchecNeBloquePasLaFile() {
        assertEquals("b", nextDownload(listOf(item("a", DownloadState.Failed), item("b", DownloadState.Queued)))?.id)
    }

    @Test
    fun nomDeFichierSansCaractereRisque() {
        assertEquals("m_12.mp4", item("m:12", DownloadState.Queued).fileName)
    }

    @Test
    fun tailleLisible() {
        assertEquals("734 Mo", formatBytes(734_000_000))
        assertEquals("1,4 Go", formatBytes(1_400_000_000))
    }

    @Test
    fun vitesseEtTempsRestant() {
        val item = item("a", DownloadState.Running).copy(bytes = 1_000_000_000, totalBytes = 2_200_000_000, speed = 2_000_000)
        assertEquals("1,0 Go sur 2,2 Go · 2 Mo/s · reste 10 min", downloadDetail(item))
    }

    @Test
    fun coupureDuServeurAffichee() {
        val item = item("a", DownloadState.Running).copy(
            bytes = 40_000_000,
            totalBytes = 500_000_000,
            pause = pauseFromReason(1),
            interruptions = 2,
        )
        assertEquals("40 Mo sur 500 Mo · En pause : connexion coupée, nouvel essai bientôt · 2 coupure(s)", downloadDetail(item))
    }

    @Test
    fun echecAvecCodeHttp() {
        assertEquals("Échec : le serveur a répondu HTTP 403", downloadDetail(item("a", DownloadState.Failed).copy(failureCode = 403)))
    }
}
