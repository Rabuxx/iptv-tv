package fr.rabuxx.home

import org.junit.Assert.assertEquals
import org.junit.Test

class WeatherTest {

    @Test
    fun `chaque famille de codes a son symbole`() {
        assertEquals("☀", weatherIcon(0))
        assertEquals("🌧", weatherIcon(61))
        assertEquals("🌧", weatherIcon(81))
        assertEquals("❄", weatherIcon(73))
        assertEquals("⛈", weatherIcon(95))
    }
}
