package app.intack.model

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val dayFormat = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH)
private val dayWithYearFormat = DateTimeFormatter.ofPattern("EEE d MMM yyyy", Locale.ENGLISH)

/** e.g. "Fri 9 Oct", with the year if it isn't this year. */
fun formatDay(date: LocalDate, today: LocalDate = LocalDate.now()): String =
    date.format(if (date.year == today.year) dayFormat else dayWithYearFormat)

/** " on Fri 9 Oct at 15:00" for diary entries, "" for list items. */
private fun Item.whenText(): String {
    val day = localDate ?: return ""
    return " on ${formatDay(day)}" + (time?.let { " at $it" } ?: "")
}

/**
 * What an edit did to an item, e.g. "ticked Milk" or "added Dentist on Fri 9 Oct at 15:00";
 * null for moves and anything not worth mentioning.
 */
fun describeChange(before: Item?, after: Item): String? = when {
    // Photo pieces and settings in a group aren't news in themselves.
    after.isPhotoPart -> null
    after.id == LIST_STATUS_ITEM -> when (after.text) {
        STATUS_ARCHIVED -> "archived the list"
        STATUS_DELETED -> "deleted the list"
        else -> null
    }
    after.id == GROUP_LOOK_ITEM -> if (after.deleted) null else "changed the group's look"
    after.heading -> when {
        after.deleted -> if (before == null || before.deleted) null else "removed the heading ${before.text}"
        before == null || before.deleted -> "added the heading ${after.text}"
        before.text != after.text -> "renamed the heading ${before.text} to ${after.text}"
        else -> null
    }
    after.deleted -> if (before == null || before.deleted) null else "removed ${before.text}${before.whenText()}"
    before == null || before.deleted -> "added ${after.text}${after.whenText()}"
    before.checked != after.checked -> (if (after.checked) "ticked " else "unticked ") + after.text
    else -> buildList {
        if (before.text != after.text) add("renamed ${before.text} to ${after.text}")
        if (before.date != after.date) after.localDate?.let { add("moved ${after.text} to ${formatDay(it)}") }
        if (before.time != after.time) add(after.time?.let { "set ${after.text} to $it" } ?: "removed the time from ${after.text}")
        if (before.note != after.note) add(
            when {
                after.note == null -> "removed the note from ${after.text}"
                before.note == null -> "added a note to ${after.text}: ${notePreview(after.note)}"
                else -> "changed the note on ${after.text}: ${notePreview(after.note)}"
            },
        )
    }.joinToString(", ").ifEmpty { null }
}

/** A note's first line, cut short, for history and notifications. */
fun notePreview(note: String): String {
    val first = note.lineSequence().first().trim()
    return if (first.length > NOTE_PREVIEW_CHARS || first.length < note.trim().length) first.take(NOTE_PREVIEW_CHARS).trimEnd() + "…" else first
}

private const val NOTE_PREVIEW_CHARS = 60

/** Notes are edited without a new content version, so a change to only the note is by whoever wrote it. */
private fun noteOnly(before: Item?, after: Item) =
    before != null && before.version == after.version && before.noteVersion != after.noteVersion && after.noteVersion != null

/** The version that made this change: the item's, or the note's for a note-only change. */
fun changeVersion(before: Item?, after: Item): Version = if (noteOnly(before, after)) after.noteVersion!! else after.version

/**
 * The audit entry for a local edit, or null if the edit isn't worth recording (e.g. a move).
 * [byName] is who made it, for note-only changes, which don't record an editor.
 */
fun auditEntryFor(before: Item?, after: Item, byName: String = after.editor): AuditEntry? {
    val description = describeChange(before, after) ?: return null
    val version = changeVersion(before, after)
    return AuditEntry(
        id = if (noteOnly(before, after)) "${after.id}/note/${version.ts}/${version.by}" else "${after.id}/${version.ts}/${version.by}",
        itemId = after.id,
        ts = version.ts,
        by = version.by,
        byName = if (noteOnly(before, after)) byName else after.editor,
        description = description,
    )
}

/** Adds audit entries from a peer; the log only grows, so merging is a union. Returns the new ones too. */
fun TodoList.mergeAudit(entries: Collection<AuditEntry>): Pair<TodoList, List<AuditEntry>> {
    val added = entries.filter { it.id !in audit }
    if (added.isEmpty()) return this to emptyList()
    return copy(audit = audit + added.associateBy { it.id }) to added
}

/**
 * e.g. "“Milk”: your change (checked) was replaced by Sam's newer change (deleted)." or, for a diary,
 * "“Dentist”: your newer change (Sat 10 Oct at 15:00) replaced Sam's change (Fri 9 Oct at 15:00)."
 */
fun Conflict.describe(): String {
    val name = winner.text.ifBlank { loser.text }
    return if (localLost) {
        "“$name”: your change (${loser.conflictState(winner)}) was replaced by ${winner.editor}'s newer change (${winner.conflictState(loser)})."
    } else {
        "“$name”: your newer change (${winner.conflictState(loser)}) replaced ${loser.editor}'s change (${loser.conflictState(winner)})."
    }
}

/** How this version differs from [other], in a few words. */
private fun Item.conflictState(other: Item): String = when {
    deleted -> "deleted"
    localDate != null -> buildList {
        if (text != other.text) add(text)
        localDate?.let { add(formatDay(it) + (time?.let { t -> " at $t" } ?: "")) }
    }.joinToString(", ")
    text != other.text -> text
    checked -> "checked"
    else -> "unchecked"
}
