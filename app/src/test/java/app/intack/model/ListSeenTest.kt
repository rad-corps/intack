package app.intack.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ListSeenTest {
    private val milk = Item("milk", "Milk", createdAt = 1, version = Version(100, "me"), editor = "Me")
    private val list = TodoList("list", "Groceries", ListKeys.newSecret(), items = mapOf("milk" to milk))

    @Test
    fun changesByOthersAreNewUntilSeen() {
        val ticked = list.copy(items = mapOf("milk" to milk.edited("sam", "Sam", 200) { copy(checked = true) }))
        assertTrue(ticked.hasUnseenChanges("me"))
        assertFalse(ticked.copy(readUpTo = 200).hasUnseenChanges("me"))
    }

    @Test
    fun ownChangesAndArchivedListsAreNeverNew() {
        assertFalse(list.hasUnseenChanges("me"))
        val ticked = list.copy(items = mapOf("milk" to milk.edited("sam", "Sam", 200) { copy(checked = true) }))
        assertFalse(ticked.copy(archived = true).hasUnseenChanges("me"))
    }

    @Test
    fun sameNameIgnoresCaseAndSpaces() {
        assertTrue(sameListName("Groceries", " groceries "))
        assertFalse(sameListName("Groceries", "Groceries 2"))
    }

    @Test
    fun freeNameCountsUp() {
        assertEquals("Hardware", freeListName("Hardware", listOf("Groceries")))
        assertEquals("groceries 2", freeListName("groceries", listOf("Groceries")))
        assertEquals("Groceries 3", freeListName("Groceries", listOf("Groceries", "Groceries 2")))
    }
}
