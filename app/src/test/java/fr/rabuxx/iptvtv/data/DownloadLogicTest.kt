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
}
