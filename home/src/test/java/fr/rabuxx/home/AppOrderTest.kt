package fr.rabuxx.home

import org.junit.Assert.assertEquals
import org.junit.Test

class AppOrderTest {

    private data class App(val pkg: String, val label: String)

    private fun order(vararg apps: App) = orderApps(apps.toList(), { it.pkg }, { it.label }).map { it.label }

    @Test
    fun `Rabux TV passe devant les autres apps épinglées`() {
        val result = order(App("com.netflix.ninja", "Netflix"), App("fr.rabuxx.iptvtv", "Rabux TV"))
        assertEquals(listOf("Rabux TV", "Netflix"), result)
    }

    @Test
    fun `les apps épinglées restent devant les autres`() {
        val result = order(App("a.a", "Arte"), App("com.netflix.ninja", "Netflix"))
        assertEquals(listOf("Netflix", "Arte"), result)
    }

    @Test
    fun `le tri alphabétique ignore les accents et la casse`() {
        val result = order(App("z", "Zattoo"), App("e", "Éducation"), App("b", "bein"), App("f", "France TV"))
        assertEquals(listOf("bein", "Éducation", "France TV", "Zattoo"), result)
    }
}
