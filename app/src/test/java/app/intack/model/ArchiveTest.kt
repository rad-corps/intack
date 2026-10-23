package app.intack.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ArchiveTest {
    private val list = TodoList("list", "Groceries", ListKeys.newSecret(), addedAt = 50)
    private val milk = Item("milk", "Milk", createdAt = 1, version = Version(100, "A"), editor = "Phone A")

    private fun TodoList.with(vararg items: Item) = copy(items = this.items + items.associateBy { it.id })

    @Test
    fun othersArchivingIsAskedAbout() {
        val archived = list.with(milk).statusItem(STATUS_ARCHIVED, "B", "Sarah", 200)
        val request = list.with(milk, archived).pendingStatusRequest("A")
        assertNotNull(request)
        assertEquals("Sarah", request!!.editor)
    }

    @Test
    fun ownArchivingIsNotAskedAbout() {
        val archived = list.statusItem(STATUS_ARCHIVED, "A", "Me", 200)
        assertNull(list.with(archived).pendingStatusRequest("A"))
    }

    @Test
    fun answeredRequestIsNotAskedAgainButANewerOneIs() {
        val first = list.statusItem(STATUS_DELETED, "B", "Sarah", 200)
        val answered = list.with(first).copy(statusAnswered = first.version)
        assertNull(answered.pendingStatusRequest("A"))
        val again = answered.statusItem(STATUS_ARCHIVED, "C", "Tom", 300)
        assertNotNull(answered.with(again).pendingStatusRequest("A"))
    }

    @Test
    fun requestFromBeforeThisPhoneHadTheListIsIgnored() {
        val old = list.statusItem(STATUS_ARCHIVED, "B", "Sarah", 40)
        assertNull(list.with(old).pendingStatusRequest("A"))
    }

    @Test
    fun archivedListIsNotAskedAbout() {
        val archived = list.statusItem(STATUS_ARCHIVED, "B", "Sarah", 200)
        assertNull(list.with(archived).copy(archived = true).pendingStatusRequest("A"))
    }

    @Test
    fun statusItemIsHiddenFromOlderVersions() {
        val archived = list.statusItem(STATUS_ARCHIVED, "B", "Sarah", 200)
        assertTrue(archived.deleted)
        assertTrue(list.with(milk, archived).visibleItems.none { it.id == LIST_STATUS_ITEM })
    }

    @Test
    fun statusItemDescendsFromThePreviousOne() {
        val first = list.statusItem(STATUS_ARCHIVED, "B", "Sarah", 200)
        val second = list.with(first).statusItem(STATUS_DELETED, "A", "Me", 150)
        assertTrue(second.version > first.version)
        assertTrue(first.version in second.history)
    }

    @Test
    fun archivingIsOfferedWhenEverythingIsTicked() {
        val ticked = milk.edited("A", "Phone A", 300) { copy(checked = true) }
        val heading = Item("h", "Dairy", createdAt = 0, version = Version(90, "A"), editor = "Phone A", heading = true)
        assertTrue(list.with(ticked, heading).offerArchive)
        assertFalse(list.with(milk).offerArchive)
        assertFalse(list.offerArchive) // nothing in it
    }

    @Test
    fun notNowHoldsUntilSomethingChanges() {
        val ticked = milk.edited("A", "Phone A", 300) { copy(checked = true) }
        val declined = list.with(ticked).copy(archiveOfferDeclinedAt = 400)
        assertFalse(declined.offerArchive)
        val bread = Item("bread", "Bread", createdAt = 500, version = Version(500, "B"), editor = "Phone B", checked = true)
        assertTrue(declined.with(bread).offerArchive)
    }

    @Test
    fun stopAskingHoldsEvenAfterChanges() {
        val ticked = milk.edited("A", "Phone A", 300) { copy(checked = true) }
        val stopped = list.with(ticked).copy(archiveOfferStopped = true)
        assertFalse(stopped.offerArchive)
        val bread = Item("bread", "Bread", createdAt = 500, version = Version(500, "B"), editor = "Phone B", checked = true)
        assertFalse(stopped.with(bread).offerArchive)
    }

    @Test
    fun statusChangeIsDescribed() {
        val archived = list.statusItem(STATUS_ARCHIVED, "B", "Sarah", 200)
        assertEquals("archived the list", describeChange(null, archived))
        assertEquals("deleted the list", describeChange(archived, list.with(archived).statusItem(STATUS_DELETED, "B", "Sarah", 300)))
    }
}
