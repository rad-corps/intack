package app.intack.model

import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/**
 * When the list was made, as far as this phone can tell: when it was added to its group ([entry]),
 * else its oldest item or recorded change, else when this phone got it. Null if nothing says.
 */
fun TodoList.createdTime(entry: Item? = null): Long? =
    entry?.createdAt?.takeIf { it > 0 }
        ?: (items.values.map { it.createdAt } + audit.values.map { it.ts }).filter { it > 0 }.minOrNull()
        ?: addedAt.takeIf { it > 0 }

/** The newest change anyone made to the list's items (adding, ticking, editing, removing). */
val TodoList.lastEdited: Long?
    get() = items.values.filter { !it.isPhotoPart }.map { it.version.ts }.filter { it > 0 }.maxOrNull()

private val clock = DateTimeFormatter.ofPattern("HH:mm")

/** e.g. "just now", "5 min ago", "today at 14:05", "yesterday", "Fri 9 Oct". */
fun formatWhen(millis: Long, now: Long = System.currentTimeMillis(), zone: ZoneId = ZoneId.systemDefault()): String {
    val ago = now - millis
    if (ago in 0 until 60_000) return "just now"
    if (ago in 0 until 3_600_000) return "${ago / 60_000} min ago"
    val at: ZonedDateTime = Instant.ofEpochMilli(millis).atZone(zone)
    val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    return when (at.toLocalDate()) {
        today -> "today at ${at.format(clock)}"
        today.minusDays(1) -> "yesterday"
        else -> formatDay(at.toLocalDate(), today)
    }
}

/** "Created Fri 9 Oct · edited 5 min ago", or whichever half is known; null if neither is. */
fun TodoList.timesLine(entry: Item? = null, now: Long = System.currentTimeMillis(), zone: ZoneId = ZoneId.systemDefault()): String? {
    val created = createdTime(entry)
    // Edited moments after it was made says nothing new.
    val edited = lastEdited?.takeIf { created == null || it - created > 60_000 }
    return listOfNotNull(
        created?.let { "Created ${formatWhen(it, now, zone)}" },
        edited?.let { (if (created == null) "Edited " else "edited ") + formatWhen(it, now, zone) },
    ).joinToString(" · ").ifEmpty { null }
}
