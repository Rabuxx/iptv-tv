package fr.rabuxx.iptvtv.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FormatTest {

    @Test
    fun `duree dans les formats renvoyes par les fournisseurs`() {
        assertEquals("1 h 45", formatDuration("01:45:00"))
        assertEquals("1 h 45", formatDuration("105 min"))
        assertEquals("1 h 45", formatDuration("6300")) // secondes
        assertEquals("45 min", formatDuration("45"))
        assertEquals("2 h 05", formatDuration("02:05:30"))
        assertNull(formatDuration(null))
        assertNull(formatDuration("00:00:00"))
    }

    @Test
    fun `note au format francais`() {
        assertEquals("★ 7,4", formatRating("7.4"))
        assertEquals("★ 8,0", formatRating("8"))
        assertNull(formatRating("0"))
        assertNull(formatRating(null))
    }

    @Test
    fun `horloge du lecteur`() {
        assertEquals("1:15:23", formatClock(4_523_000))
        assertEquals("5:12", formatClock(312_000))
        assertEquals("0:00", formatClock(-5))
    }
}
