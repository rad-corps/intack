package app.intack.ui

import androidx.compose.ui.graphics.Color
import app.intack.model.Item
import app.intack.model.ListKeys
import app.intack.model.SpaceKind
import app.intack.model.TodoList
import app.intack.model.Version
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class UpcomingTest {
    private val today = LocalDate.of(2026, 10, 12)

    private fun calendar(vararg entries: Pair<Long, String>): ScheduleSource {
        val items = entries.mapIndexed { i, (offset, text) ->
            Item("e$i", text, createdAt = i.toLong(), version = Version(1, "A"), editor = "A", date = today.plusDays(offset).toString())
        }
        return ScheduleSource(TodoList("cal", "Calendar", ListKeys.newSecret(), items = items.associateBy { it.id }, kind = SpaceKind.DIARY), "Family", Color.Red)
    }

    @Test
    fun aBusyTodayIsAllThatShows() {
        val source = calendar(0L to "a", 0L to "b", 0L to "c", 0L to "d", 0L to "e", 0L to "f", 1L to "tomorrow")
        val days = upcoming(listOf(source), today)
        assertEquals(listOf(today), days.map { it.first })
        assertEquals(6, days.single().second.size)
    }

    @Test
    fun aQuietTodayIsFilledFromTheNextDays() {
        val source = calendar(0L to "swim", 2L to "dentist", 3L to "a", 3L to "b", 5L to "c", 9L to "too far")
        val days = upcoming(listOf(source), today)
        // Today, then days until there are five: day 5 brings it to five, so day 9 is left out.
        assertEquals(listOf(0L, 2L, 3L, 5L), days.map { it.first.toEpochDay() - today.toEpochDay() })
    }

    @Test
    fun pastEntriesAndDaysBeyondTwoWeeksAreLeftOut() {
        val source = calendar(-1L to "yesterday", 15L to "much later")
        assertEquals(emptyList<Any>(), upcoming(listOf(source), today))
    }
}
