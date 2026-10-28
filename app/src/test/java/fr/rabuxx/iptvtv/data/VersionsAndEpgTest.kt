package fr.rabuxx.iptvtv.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VersionsAndEpgTest {

    @Test
    fun `comparaison de versions numerique, segment par segment`() {
        assertTrue(Versions.isNewer("0.10.0", "0.9.9"))
        assertTrue(Versions.isNewer("v0.6.1", "0.6.0"))
        assertTrue(Versions.isNewer("1.0.0", "0.99.99"))
        assertFalse(Versions.isNewer("0.6.0", "0.6.0"))
        assertFalse(Versions.isNewer("0.6", "0.6.0"))
        assertFalse(Versions.isNewer("0.5.9", "0.6.0"))
    }

    private fun program(title: String, start: Long, stop: Long) =
        EpgProgram(title = title, description = null, startRaw = "", startTs = start, stopTs = stop, hasArchive = false)

    @Test
    fun `programme en cours et suivant`() {
        val list = listOf(program("Journal", 100, 200), program("Film", 200, 400), program("Docu", 400, 500))
        assertEquals("Film", list.nowPlaying(nowSec = 250)?.title)
        assertEquals("Docu", list.upNext(nowSec = 250)?.title)
        assertNull(list.nowPlaying(nowSec = 600))
    }

    @Test
    fun `avancement d'un programme borne entre 0 et 1`() {
        val p = program("Film", 200, 400)
        assertEquals(0.5f, p.progress(nowSec = 300), 0.0001f)
        assertEquals(0f, p.progress(nowSec = 100), 0.0001f)
        assertEquals(1f, p.progress(nowSec = 900), 0.0001f)
    }

    @Test
    fun `cle de categorie distincte par section`() {
        assertEquals("Live:12", categoryKey(Section.Live, "12"))
        assertFalse(categoryKey(Section.Live, "12") == categoryKey(Section.Movies, "12"))
    }
}
