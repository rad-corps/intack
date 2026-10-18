package app.intack.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ImagesTest {
    private val empty = TodoList("list", "Groceries", ListKeys.newSecret())
    private val milk = Item("milk", "Milk", createdAt = 1, version = Version(100, "A"), editor = "Phone A")

    private fun TodoList.with(vararg items: Item) = copy(items = items.associateBy { it.id })
    private fun part(imageId: String, index: Int, text: String) =
        Item(imagePartId(imageId, index), text, deleted = true, createdAt = 1, version = Version(100, "A"), editor = "Phone A")

    @Test
    fun photoIsPutTogetherOnceEveryPieceHasArrived() {
        val pictured = milk.withImage("abc", 2, "A", 200)
        val partial = empty.with(pictured, part("abc", 1, "World"))
        assertNull(partial.imageBase64(pictured))
        val whole = partial.copy(items = partial.items + part("abc", 0, "Hello").let { it.id to it })
        assertEquals("HelloWorld", whole.imageBase64(pictured))
        assertEquals(2, whole.imageParts("abc").size)
    }

    @Test
    fun piecesStayHiddenAndOutOfTheNews() {
        val piece = part("abc", 0, "Hello")
        assertTrue(piece.isPhotoPart)
        assertTrue(empty.with(milk, piece).visibleItems == listOf(milk))
        assertNull(describeChange(null, piece))
    }

    @Test
    fun photoAndConcurrentTickAreBothKept() {
        val picturedOnA = milk.withImage("abc", 3, "A", 200)
        val tickedOnB = milk.edited("B", "Phone B", 300) { copy(checked = true) }
        for (side in listOf(empty.with(picturedOnA).merge(listOf(tickedOnB)), empty.with(tickedOnB).merge(listOf(picturedOnA)))) {
            val item = side.list.items.getValue("milk")
            assertTrue(item.checked)
            assertEquals("abc", item.image)
            assertEquals(3, item.imageParts)
            assertTrue(side.conflicts.isEmpty())
        }
    }

    @Test
    fun photoSurvivesAnEditFromAVersionWithoutPhotos() {
        val pictured = milk.withImage("abc", 3, "A", 200)
        val tickedByOldVersion = pictured.copy(image = null, imageParts = 0, imageVersion = null).edited("B", "Phone B", 300) { copy(checked = true) }
        assertEquals("abc", empty.with(pictured).merge(listOf(tickedByOldVersion)).list.items.getValue("milk").image)
    }

    @Test
    fun removingThePhotoWins() {
        val pictured = milk.withImage("abc", 3, "A", 200)
        val removed = pictured.withImage(null, 0, "B", 300)
        val item = empty.with(pictured).merge(listOf(removed)).list.items.getValue("milk")
        assertNull(item.image)
        assertEquals(0, item.imageParts)
    }

    @Test
    fun photoChangesAreDescribedAndCreditedToWhoeverMadeThem() {
        val pictured = milk.withImage("abc", 3, "B", 300)
        assertEquals("added a photo to Milk", describeChange(milk, pictured))
        assertEquals("changed the photo on Milk", describeChange(pictured, pictured.withImage("def", 2, "B", 400)))
        assertEquals("removed the photo from Milk", describeChange(pictured, pictured.withImage(null, 0, "B", 400)))
        val entry = auditEntryFor(milk, pictured, byName = "Sam")!!
        assertEquals("milk/image/300/B", entry.id)
        assertEquals("Sam", entry.byName)
        assertEquals(Version(300, "B"), changeVersion(milk, pictured))
    }
}
