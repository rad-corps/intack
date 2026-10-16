package app.intack.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneOffset

class ListTimesTest {
    private val zone = ZoneOffset.UTC
    private fun at(day: Int, hour: Int, minute: Int = 0) = LocalDateTime.of(2026, 10, day, hour, minute).toInstant(zone).toEpochMilli()
    private val now = at(16, 15)

    private fun item(id: String, created: Long, edited: Long = created) = Item(id, id, createdAt = created, version = Version(edited, "A"), editor = "Adam")

    @Test
    fun recentTimesAreRelativeAndOlderOnesAreDays() {
        assertEquals("just now", formatWhen(now - 20_000, now, zone))
        assertEquals("5 min ago", formatWhen(now - 5 * 60_000, now, zone))
        assertEquals("today at 09:30", formatWhen(at(16, 9, 30), now, zone))
        assertEquals("yesterday", formatWhen(at(15, 23), now, zone))
        assertEquals("Fri 9 Oct", formatWhen(at(9, 12), now, zone))
    }

    @Test
    fun aGroupListIsCreatedWhenItWasAddedAndEditedWhenAnItemLastChanged() {
        val list = TodoList("l", "Groceries", "s", items = mapOf("milk" to item("milk", at(10, 9), edited = at(16, 14, 50))))
        val entry = item("l", at(9, 12))
        assertEquals("Created Fri 9 Oct · edited 10 min ago", list.timesLine(entry, now, zone))
    }

    @Test
    fun withoutAGroupEntryTheOldestItemSaysWhenItWasMade() {
        val list = TodoList("l", "Groceries", "s", items = mapOf("a" to item("a", at(12, 8)), "b" to item("b", at(14, 8))))
        assertEquals("Created Mon 12 Oct · edited Wed 14 Oct", list.timesLine(null, now, zone))
    }

    @Test
    fun anUntouchedListOnlySaysWhenItWasMade() {
        assertEquals("Created yesterday", TodoList("l", "Groceries", "s").timesLine(item("l", at(15, 10)), now, zone))
        assertNull(TodoList("l", "Groceries", "s").timesLine(null, now, zone))
    }
}
