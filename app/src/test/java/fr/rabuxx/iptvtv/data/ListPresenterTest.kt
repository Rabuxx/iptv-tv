package fr.rabuxx.iptvtv.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ListPresenterTest {

    private data class Item(val name: String, val rating: String? = null, val date: Long = 0)

    private fun present(filter: ListFilter, items: List<Item>) =
        ListPresenter.apply(filter, items, date = { it.date }, rating = { it.rating }, name = { it.name }).map { it.name }

    @Test
    fun `les prefixes de fournisseur sont ignores pour le tri`() {
        assertEquals("Avatar", ListPresenter.sortKey("FR | Avatar"))
        assertEquals("Avatar", ListPresenter.sortKey("|FR| Avatar"))
        assertEquals("Dune", ListPresenter.sortKey("[4K] Dune"))
        assertEquals("Dune", ListPresenter.sortKey("FR | 4K | Dune"))
        assertEquals("Lupin", ListPresenter.sortKey("VOSTFR - Lupin"))
    }

    @Test
    fun `un vrai titre n'est pas ampute`() {
        assertEquals("En attendant Bojangles", ListPresenter.sortKey("En attendant Bojangles"))
        assertEquals("FRANCE", ListPresenter.sortKey("FRANCE"))
        assertEquals("Élysée", ListPresenter.sortKey("Élysée"))
    }

    @Test
    fun `tri alphabetique sans tenir compte des prefixes ni des accents`() {
        val items = listOf(Item("FR | Zorro"), Item("Avatar"), Item("[4K] Batman"), Item("Élan"), Item("Eagle"))
        assertEquals(
            listOf("Avatar", "[4K] Batman", "Eagle", "Élan", "FR | Zorro"),
            present(ListFilter(sort = SortMode.Alpha), items),
        )
    }

    @Test
    fun `tri par note, les films sans note en dernier`() {
        val items = listOf(Item("B", "7.5"), Item("C", null), Item("A", "8"), Item("D", "7,5"))
        assertEquals(listOf("A", "B", "D", "C"), present(ListFilter(sort = SortMode.Rating), items))
    }

    @Test
    fun `filtre par note minimale`() {
        val items = listOf(Item("A", "8"), Item("B", "6.9"), Item("C", null), Item("D", "7"))
        assertEquals(listOf("A", "D"), present(ListFilter(sort = SortMode.Rating, minRating = 7), items))
    }

    @Test
    fun `tri par date, les plus recents d'abord`() {
        val items = listOf(Item("old", date = 1), Item("new", date = 3), Item("mid", date = 2))
        assertEquals(listOf("new", "mid", "old"), present(ListFilter(sort = SortMode.Recent), items))
    }

    @Test
    fun `les modes de tri defilent en boucle`() {
        assertEquals(SortMode.Rating, SortMode.Recent.next())
        assertEquals(SortMode.Alpha, SortMode.Rating.next())
        assertEquals(SortMode.Recent, SortMode.Alpha.next())
    }
}
