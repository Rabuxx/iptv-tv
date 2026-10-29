package fr.rabuxx.iptvtv.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream

class V07Test {

    // ---------- Versions et canal bêta ----------

    @Test
    fun `une version finale passe devant ses pre-versions`() {
        assertTrue(Versions.isNewer("0.7.0", "0.7.0-beta.3"))
        assertTrue(Versions.isNewer("0.7.0-beta.2", "0.7.0-beta.1"))
        assertTrue(Versions.isNewer("0.7.0-beta.1", "0.6.0"))
        assertTrue(Versions.isNewer("0.7.0-beta.10", "0.7.0-beta.9"))
        assertFalse(Versions.isNewer("0.7.0-beta.1", "0.7.0"))
        assertFalse(Versions.isNewer("0.7.0", "0.7.0"))
        assertTrue(Versions.isPrerelease("v0.7.0-beta.1"))
        assertFalse(Versions.isPrerelease("0.7.0"))
    }

    @Test
    fun `le code de version Android reste croissant`() {
        val ordered = listOf("0.6.0", "0.7.0-beta.1", "0.7.0-beta.2", "0.7.0", "0.7.1-beta.1", "0.7.1", "1.0.0")
        val codes = ordered.map { Versions.versionCode(it) }
        assertEquals(codes.sorted(), codes)
        assertEquals(codes.size, codes.toSet().size)
        assertEquals(70099, Versions.versionCode("0.7.0"))
        // Toujours au-dessus de l'ancien schéma (0.6.0 = 600) : la mise à jour s'installe
        assertTrue(Versions.versionCode("0.7.0-beta.1") > 600)
    }

    // ---------- Rapprochement des titres TMDB ----------

    @Test
    fun `cle de titre sans prefixe, annee, accents ni ponctuation`() {
        assertEquals("dune deuxieme partie", TitleMatcher.key("FR | Dune : Deuxième Partie (2024) [4K]"))
        assertEquals("dune deuxieme partie", TitleMatcher.key("Dune: Deuxième partie"))
        assertEquals("amelie poulain", TitleMatcher.key("|FR| Amélie Poulain - 2001"))
        assertEquals("matrix", TitleMatcher.key("The Matrix (1999) MULTI"))
        assertEquals(2024, TitleMatcher.year("Dune 2 (2024)"))
        assertNull(TitleMatcher.year("Blade Runner 2049"))
        assertEquals("blade runner 2049", TitleMatcher.key("Blade Runner 2049"))
        assertEquals("1917", TitleMatcher.key("1917"))
        assertEquals("dune", TitleMatcher.key("Dune [4K] - 2021"))
    }

    @Test
    fun `titre lisible pour la recherche TMDB`() {
        assertEquals("Dune", TitleMatcher.cleanTitle("FR | Dune (2021) [4K]"))
        assertEquals("Amélie Poulain", TitleMatcher.cleanTitle("|FR| Amélie Poulain - 2001 MULTI"))
        assertEquals("Blade Runner 2049", TitleMatcher.cleanTitle("Blade Runner 2049"))
    }

    @Test
    fun `l'index retrouve le bon film, en privilegiant l'annee`() {
        val catalogue = listOf("FR | Dune (1984)", "FR | Dune (2021)", "VOSTFR - Oppenheimer", "Le Fabuleux Destin d'Amélie Poulain")
        val index = TitleMatcher.Index(catalogue) { it }
        assertEquals("FR | Dune (2021)", index.find("Dune", 2021))
        assertEquals("FR | Dune (1984)", index.find("Dune", 1984))
        assertEquals("VOSTFR - Oppenheimer", index.find("Oppenheimer", 2023))
        assertEquals("Le Fabuleux Destin d'Amélie Poulain", index.find("Le Fabuleux Destin d'Amélie Poulain", 2001))
        assertNull(index.find("Interstellar", 2014))
    }

    // ---------- Rapports de plantage ----------

    @Test
    fun `les identifiants ne partent jamais dans un rapport`() {
        val text = "Unable to connect to http://srv.example.com:8080/live/baptiste/s3cr3t/1234.ts " +
            "and http://srv.example.com:8080/player_api.php?username=baptiste&password=s3cr3t&action=x"
        val clean = Scrubber.scrub(text)!!
        assertFalse(clean.contains("baptiste"))
        assertFalse(clean.contains("s3cr3t"))
        assertFalse(clean.contains("srv.example.com"))
        assertTrue(clean.contains("/live/***/***/1234.ts"))
        assertTrue(clean.contains("password=***"))
    }

    // ---------- Statistiques ----------

    private val tf1 = WatchItem("l:1", WatchType.Live, "TF1", null, null)
    private val dune = WatchItem("m:9", WatchType.Movie, "Dune", null, "Science-fiction, Aventure")
    private val lupin = WatchItem("s:4", WatchType.Series, "Lupin", null, "Policier")

    @Test
    fun `le bilan additionne par type, element et genre`() {
        var log = WatchLog()
        log = WatchStatsCalc.record(log, tf1, "2026-10-27", 1800)
        log = WatchStatsCalc.record(log, tf1, "2026-10-28", 600)
        log = WatchStatsCalc.record(log, dune, "2026-10-28", 7200)
        log = WatchStatsCalc.record(log, lupin, "2026-10-29", 2700)
        val s = WatchStatsCalc.summarize(log, listOf("2026-10-27", "2026-10-28", "2026-10-29"))
        assertEquals(1800L + 600 + 7200 + 2700, s.totalSeconds)
        assertEquals(2400L, s.byType[WatchType.Live])
        assertEquals("TF1", s.topChannels.first().value.name)
        assertEquals(7200L, s.topGenres.first { it.value == "Science-fiction" }.seconds)
        assertEquals(3, s.activeDays)
        assertEquals(3, s.longestStreak)
    }

    @Test
    fun `une periode ne compte que ses jours`() {
        var log = WatchLog()
        log = WatchStatsCalc.record(log, tf1, "2026-10-01", 3600)
        log = WatchStatsCalc.record(log, tf1, "2026-10-29", 60)
        val s = WatchStatsCalc.summarize(log, listOf("2026-10-28", "2026-10-29"))
        assertEquals(60L, s.totalSeconds)
        assertEquals(0, s.activeDays) // moins de 5 minutes : pas un jour actif
        assertEquals("1 h 05", WatchStatsCalc.formatHours(3900))
        assertEquals("45 min", WatchStatsCalc.formatHours(2700))
    }

    // ---------- Saisie depuis le téléphone ----------

    @Test
    fun `lecture d'une requete POST de formulaire`() {
        val body = "server=http%3A%2F%2Fexemple.com%3A8080&username=bap&password=a%26b+c%C3%A9"
        val raw = "POST /k3y HTTP/1.1\r\nHost: tv\r\nContent-Type: application/x-www-form-urlencoded\r\n" +
            "Content-Length: ${body.toByteArray().size}\r\n\r\n$body"
        val req = MiniHttp.read(ByteArrayInputStream(raw.toByteArray()))!!
        assertEquals("POST", req.method)
        assertEquals("/k3y", req.path)
        val form = MiniHttp.parseForm(req.body)
        assertEquals("http://exemple.com:8080", form["server"])
        assertEquals("a&b cé", form["password"])
    }

    @Test
    fun `une requete trop grosse est refusee`() {
        val raw = "POST / HTTP/1.1\r\nContent-Length: 999999\r\n\r\n"
        assertNull(MiniHttp.read(ByteArrayInputStream(raw.toByteArray())))
        assertEquals("&lt;b&gt;&quot;", MiniHttp.escape("<b>\""))
    }
}
