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

class ByteRangeTest {

    @Test
    fun plagesDemandeesParLaChromecast() {
        assertEquals(0L to 999L, parseByteRange(null, 1000))
        assertEquals(100L to 999L, parseByteRange("bytes=100-", 1000))
        assertEquals(100L to 199L, parseByteRange("bytes=100-199", 1000))
        assertEquals(500L to 999L, parseByteRange("bytes=-500", 1000))
        assertEquals(100L to 999L, parseByteRange("bytes=100-5000", 1000))
    }

    @Test
    fun plageImpossible() {
        assertEquals(null, parseByteRange("bytes=2000-", 1000))
    }
}
