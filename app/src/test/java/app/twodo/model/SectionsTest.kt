package app.twodo.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SectionsTest {
    private fun item(id: String, heading: Boolean = false) =
        Item(id, id, createdAt = 0, version = Version(1, "A"), editor = "A", heading = heading)

    // bread, [Dairy] milk cheese, [Fruit] apples
    private val ordered = listOf(item("bread"), item("dairy", true), item("milk"), item("cheese"), item("fruit", true), item("apples"))

    @Test
    fun itemsBelongToTheHeadingAboveThem() {
        assertNull(sectionOf(ordered, "bread"))
        assertEquals("dairy", sectionOf(ordered, "cheese")?.id)
        assertEquals("fruit", sectionOf(ordered, "apples")?.id)
    }

    @Test
    fun movingToASectionPutsItAtTheEndOfThatSection() {
        // Bread into Dairy: after cheese, before Fruit (index among the others, without bread).
        assertEquals(3, indexForSection(ordered, "bread", "dairy"))
        // Apples into Dairy: before Fruit.
        assertEquals(4, indexForSection(ordered, "apples", "dairy"))
        // Into the last section: the very end.
        assertEquals(5, indexForSection(ordered, "milk", "fruit"))
        // Out of any section: above the first heading.
        assertEquals(1, indexForSection(ordered, "apples", null))
        assertEquals(0, indexForSection(listOf(item("dairy", true), item("milk")), "milk", null))
    }

    @Test
    fun headingsAreDescribedAsHeadings() {
        val dairy = item("dairy", true).copy(text = "Dairy")
        assertEquals("added the heading Dairy", describeChange(null, dairy))
        assertEquals("renamed the heading Dairy to Milk & eggs", describeChange(dairy, dairy.copy(text = "Milk & eggs")))
    }

    private fun at(id: String, pos: Double, heading: Boolean = false) =
        Item(id, id, createdAt = 0, version = Version(1, "A"), editor = "A", heading = heading, pos = pos)

    @Test
    fun newItemsGoAboveTheFirstHeading() {
        val list = listOf(at("bread", 1.0), at("dairy", 2.0, true), at("milk", 3.0))
        assertEquals(1.5, newItemPosition(list), 0.0)
        // Nothing above the headings yet (a list made from a template).
        assertEquals(1.0, newItemPosition(listOf(at("fresh", 2.0, true), at("bakery", 3.0, true))), 0.0)
        // No headings: at the bottom.
        assertEquals(4.0, newItemPosition(listOf(at("a", 1.0), at("b", 3.0))), 0.0)
        assertEquals(1.0, newItemPosition(emptyList()), 0.0)
    }

    @Test
    fun dropsWithHeadingsHiddenLandNextToTheSameItems() {
        val shown = ordered.filter { !it.heading } // bread milk cheese apples
        // Apples dragged to the top (index 0, before bread): before bread among the others.
        assertEquals(0, indexAmongAll(ordered, listOf(item("apples")) + shown.dropLast(1), "apples", 0))
        // Bread dropped after cheese: right after cheese, still in Dairy.
        assertEquals(3, indexAmongAll(ordered, listOf(item("milk"), item("cheese"), item("bread"), item("apples")), "bread", 2))
        // Milk dropped last: after apples.
        assertEquals(5, indexAmongAll(ordered, listOf(item("bread"), item("cheese"), item("apples"), item("milk")), "milk", 3))
    }
}
