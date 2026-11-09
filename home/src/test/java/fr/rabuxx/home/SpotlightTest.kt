package fr.rabuxx.home

import org.junit.Assert.assertEquals
import org.junit.Test

class SpotlightTest {

    @Test
    fun `series et films alternent`() {
        assertEquals(listOf("s1", "f1", "s2", "f2", "s3"), interleave(listOf("s1", "s2", "s3"), listOf("f1", "f2")))
    }

    @Test
    fun `libelles meteo`() {
        assertEquals("Ciel dégagé", weatherLabel(0))
        assertEquals("Pluie", weatherLabel(63))
    }
}
