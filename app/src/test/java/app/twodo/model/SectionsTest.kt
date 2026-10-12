package app.twodo.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SectionsTest {
    private fun item(id: String, pos: Double, section: String? = null) =
        Item(id, id, createdAt = 0, version = Version(1, "A"), editor = "A", pos = pos, section = section)

    private fun heading(id: String, pos: Double) =
        Item(id, id, createdAt = 0, version = Version(1, "A"), editor = "A", pos = pos, heading = true)

    // bread, [Dairy] milk cheese, [Fruit] apples, [Bakery]
    private val items = listOf(
        item("bread", 1.0),
        heading("dairy", 1.0), item("milk", 1.0, "dairy"), item("cheese", 2.0, "dairy"),
        heading("fruit", 2.0), item("apples", 1.0, "fruit"),
        heading("bakery", 3.0),
    )
    private val ordered = sectionedOrder(items)
    private fun ids(list: List<Item>) = list.map { it.id }

    private fun TodoList.with(changed: Item) = copy(items = this.items + (changed.id to changed))
    private val list = TodoList("l", "Groceries", ListKeys.newSecret(), items = items.associateBy { it.id }, sectioned = true)

    @Test
    fun shownAsUnsortedThenEachHeadingWithItsItems() {
        assertEquals(listOf("bread", "dairy", "milk", "cheese", "fruit", "apples", "bakery"), ids(ordered))
        assertNull(sectionOf(ordered, "bread"))
        assertEquals("dairy", sectionOf(ordered, "cheese")?.id)
        assertEquals(listOf("dairy", "milk", "cheese"), ids(sectionItems(ordered, "dairy")))
    }

    @Test
    fun itemsOfADeletedHeadingBecomeUnsorted() {
        val gone = list.with(list.items.getValue("fruit").copy(deleted = true))
        // Both at position 1: the tie goes by id.
        assertEquals(listOf("apples", "bread", "dairy", "milk", "cheese", "bakery"), ids(gone.visibleItems))
    }

    @Test
    fun oldListsGetTheirSectionsFromTheirOrder() {
        // Saved before sections were recorded: only positions.
        val old = listOf(item("bread", 1.0), heading("dairy", 2.0), item("milk", 3.0), heading("fruit", 4.0), item("apples", 5.0))
        val migrated = sectionsFromPositions(old.associateBy { it.id })
        assertNull(migrated.getValue("bread").section)
        assertEquals("dairy", migrated.getValue("milk").section)
        assertEquals("fruit", migrated.getValue("apples").section)
    }

    @Test
    fun droppingAnItemPutsItInTheSectionAboveIt() {
        // Bread dropped after cheese (index 2 among the others: dairy, milk, cheese...).
        val (section, pos) = itemMove(ordered, "bread", 3)
        assertEquals("dairy", section)
        assertEquals(listOf("dairy", "milk", "cheese", "bread", "fruit"), ids(list.with(list.items.getValue("bread").moved(pos, "A", 2, section)).visibleItems).take(5))
        // Apples dropped right under the Bakery heading: into Bakery.
        assertEquals("bakery", itemMove(ordered, "apples", 6).first)
        // Milk dropped at the very top: unsorted.
        assertNull(itemMove(ordered, "milk", 0).first)
    }

    @Test
    fun movingToASectionGoesToItsEnd() {
        val (section, pos) = itemToSection(ordered, "bread", "dairy")
        assertEquals("dairy", section)
        assertEquals(3.0, pos, 0.0)
        assertEquals(1.0, itemToSection(ordered, "milk", "bakery").second, 0.0)
    }

    @Test
    fun newItemsGoAtTheEndOfTheUnsortedOnes() {
        assertEquals(2.0, newItemPosition(ordered), 0.0)
        assertEquals(4.0, newHeadingPosition(ordered), 0.0)
        assertEquals(1.0, newItemPosition(emptyList()), 0.0)
    }

    @Test
    fun movingAHeadingTakesItsItems() {
        // Without Dairy's section: bread, [Fruit] apples, [Bakery]. Dropped before Bakery (index 3).
        val pos = headingMove(ordered, "dairy", 3)!!
        val moved = list.with(list.items.getValue("dairy").moved(pos, "A", 2))
        assertEquals(listOf("bread", "fruit", "apples", "dairy", "milk", "cheese", "bakery"), ids(moved.visibleItems))
    }

    @Test
    fun aHeadingDroppedInsideAnotherSectionGoesToItsEnd() {
        // Bakery dropped between milk and cheese: after Dairy's items, before Fruit.
        val pos = headingMove(ordered, "bakery", 3)!!
        assertEquals(listOf("bread", "dairy", "milk", "cheese", "bakery", "fruit", "apples"), ids(list.with(list.items.getValue("bakery").moved(pos, "A", 2)).visibleItems))
        // Fruit dropped at the very top, above the unsorted bread: before the first heading instead.
        val top = headingMove(ordered, "fruit", 0)!!
        assertEquals(listOf("bread", "fruit", "apples", "dairy", "milk", "cheese", "bakery"), ids(list.with(list.items.getValue("fruit").moved(top, "A", 2)).visibleItems))
    }

    @Test
    fun anItemAddedWhileItsHeadingMovesStaysWithIt() {
        // One phone moves Dairy to the end while another adds yogurt to Dairy, at the same time.
        val dairyMoved = list.items.getValue("dairy").moved(headingMove(ordered, "dairy", 4)!!, "A", 2)
        val yogurt = item("yogurt", 3.0, "dairy")
        val merged = list.merge(listOf(dairyMoved, yogurt)).list
        assertEquals(listOf("bread", "fruit", "apples", "bakery", "dairy", "milk", "cheese", "yogurt"), ids(merged.visibleItems))
    }

    @Test
    fun twoSectionsMovedIntoTheSameGapDontMix() {
        // Dairy and Bakery both dropped between Fruit and nothing, by different phones.
        val dairy = list.items.getValue("dairy").moved(headingMove(ordered, "dairy", 4)!!, "A", 2)
        val bakery = list.items.getValue("bakery").moved(10.0, "B", 2)
        val merged = list.merge(listOf(dairy, bakery)).list
        val order = ids(merged.visibleItems)
        assertEquals(listOf("dairy", "milk", "cheese"), order.subList(order.indexOf("dairy"), order.indexOf("dairy") + 3))
    }

    @Test
    fun dropsWithHeadingsHiddenLandNextToTheSameItems() {
        val shown = ordered.filter { !it.heading } // bread milk cheese apples
        // Apples dragged to the top: before bread among the others.
        assertEquals(0, indexAmongAll(ordered, listOf(shown[3]) + shown.dropLast(1), "apples", 0))
        // Bread dropped after cheese: right after cheese, still in Dairy.
        assertEquals(3, indexAmongAll(ordered, listOf(shown[1], shown[2], shown[0], shown[3]), "bread", 2))
    }
}
