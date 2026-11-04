package fr.rabuxx.iptvtv.cast

import org.junit.Assert.assertEquals
import org.junit.Test

class CastLogicTest {

    @Test
    fun typeDeFichierSelonLExtension() {
        assertEquals("video/mp4", castContentType("MP4"))
        assertEquals("video/x-matroska", castContentType("mkv"))
        assertEquals("video/mp4", castContentType("inconnu"))
    }
}
