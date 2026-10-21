package app.intack.ui

import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.layout.offset
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.intack.IntackApp
import app.intack.model.Item
import app.intack.model.TodoList
import app.intack.model.formatDay
import app.intack.model.sharedLook
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** A calendar shown in a schedule: entries from [list], labelled [label] in [color] when several are mixed. */
class ScheduleSource(val list: TodoList, val label: String, val color: Color)

internal class ScheduleEntry(val item: Item, val source: ScheduleSource)

/** Each calendar with its group's name and colour (the group's accent, or one derived from its id). */
internal fun calendarSources(calendars: List<Pair<TodoList, TodoList?>>): List<ScheduleSource> = calendars.map { (calendar, group) ->
    val color = group?.sharedLook?.accent ?: ACCENTS[Math.floorMod((group ?: calendar).id.hashCode(), ACCENTS.size)]
    ScheduleSource(calendar, group?.name ?: calendar.name, Color(color))
}

/** One day's entries in order: all-day ones first, then by time. */
private fun List<ScheduleEntry>.inDayOrder() = sortedWith(compareBy({ it.item.time ?: "" }, { it.item.createdAt }, { it.item.id }))

/**
 * What's coming up, for the home screen: all of today, then following days until there are at least
 * [enough] entries, looking at most [maxDays] ahead. Days with nothing on are left out.
 */
internal fun upcoming(sources: List<ScheduleSource>, today: LocalDate, enough: Int = 5, maxDays: Long = 14): List<Pair<LocalDate, List<ScheduleEntry>>> {
    val byDay = sources.flatMap { source -> source.list.items.values.filter { !it.deleted && it.date != null }.map { ScheduleEntry(it, source) } }
        .groupBy { it.item.localDate }
    val days = mutableListOf<Pair<LocalDate, List<ScheduleEntry>>>()
    var total = 0
    for (offset in 0..maxDays) {
        if (offset > 0 && total >= enough) break
        val date = today.plusDays(offset)
        val entries = byDay[date] ?: continue
        days += date to entries.inDayOrder()
        total += entries.size
    }
    return days
}

private val scheduleDay = DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.ENGLISH)
private val scheduleDayWithYear = DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", Locale.ENGLISH)

/**
 * Everything coming up, day by day, on one scrolling page — days without entries are skipped. With
 * several [sources] (e.g. all of someone's groups), each entry shows which calendar it's from.
 * Past days are one tap away at the top.
 */
@Composable
internal fun ScheduleView(
    app: IntackApp,
    sources: List<ScheduleSource>,
    modifier: Modifier = Modifier,
    onDay: ((LocalDate) -> Unit)? = null,
    /** Set while nobody else has joined: the empty calendar then suggests inviting the group first. */
    onInvite: (() -> Unit)? = null,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val is24Hour = remember { DateFormat.is24HourFormat(context) }
    val names by app.sync.names.collectAsStateWithLifecycle()
    var showPast by rememberSaveable { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Pair<String, String>?>(null) }
    var adding by remember { mutableStateOf(false) }
    val today = LocalDate.now()
    val mixed = sources.size > 1

    val days = remember(sources.map { it.list.items }, showPast, today) {
        sources.flatMap { source ->
            source.list.items.values.filter { !it.deleted && it.date != null }.map { ScheduleEntry(it, source) }
        }
            .filter { e -> e.item.localDate?.let { showPast || !it.isBefore(today) } == true }
            .groupBy { it.item.localDate!! }
            .toSortedMap()
            .mapValues { (_, entries) -> entries.inDayOrder() }
    }
    val hasPast = remember(sources.map { it.list.items }, today) {
        sources.any { s -> s.list.items.values.any { !it.deleted && it.localDate?.isBefore(today) == true } }
    }

    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                if (hasPast) {
                    TextButton(onClick = { showPast = !showPast }) { Text(if (showPast) "Hide past days" else "Show past days") }
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { adding = true }) {
                    Icon(Icons.Default.Add, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Add")
                }
            }
        }
        days.forEach { (date, entries) ->
            item(key = "day-$date") { DayHeading(date, today, onDay) }
            items(entries, key = { "${it.source.list.id}/${it.item.id}" }) { entry ->
                StyledRow(
                    onClick = { editing = entry.source.list.id to entry.item.id },
                    contentPadding = PaddingValues(start = 20.dp, end = 16.dp, top = 10.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Text(
                        entry.item.time?.let { displayTime(is24Hour, it) } ?: "All day",
                        Modifier.width(72.dp).padding(top = 2.dp),
                        style = MaterialTheme.typography.labelLarge,
                        color = if (entry.item.time != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                    )
                    if (mixed) {
                        Box(Modifier.padding(top = 4.dp, end = 10.dp).width(4.dp).height(36.dp).clip(RoundedCornerShape(2.dp)).background(entry.source.color))
                    }
                    Column(Modifier.weight(1f)) {
                        Text(entry.item.text, style = MaterialTheme.typography.bodyLarge)
                        val who = if (entry.item.version.by == app.identity.deviceId) "You" else names[entry.item.version.by] ?: entry.item.editor
                        Text(
                            if (mixed) "${entry.source.label} · $who" else who,
                            style = MaterialTheme.typography.labelSmall,
                            color = personColor(entry.item.version.by),
                        )
                    }
                }
            }
        }
        // Nothing planned yet, or only the app's own notes (made, joined), which show above it.
        if (days.values.all { day -> day.all { it.item.isAutoNote } }) {
            item { EmptyCalendar(onInvite, onAdd = { adding = true }) }
        }
    }

    editing?.let { (listId, itemId) ->
        val source = sources.firstOrNull { it.list.id == listId } ?: return@let
        val entry = source.list.items[itemId]?.takeIf { !it.deleted } ?: return@let
        EditEntrySheet(
            entry = entry,
            list = source.list,
            myDeviceId = app.identity.deviceId,
            names = names,
            onDismiss = { editing = null },
            onSave = { text, date, time ->
                editing = null
                scope.launch { app.repo.editEntry(listId, itemId, text, date, time) }
            },
            onDelete = {
                editing = null
                scope.launch { app.repo.deleteItem(listId, itemId) }
            },
        )
    }
    if (adding) {
        NewEntrySheet(sources, onDismiss = { adding = false }) { source, text, date, time ->
            adding = false
            scope.launch { app.repo.addEntry(source.list.id, date, text, time) }
        }
    }
}

/**
 * A short look ahead for the home screen (see [upcoming]): each entry with its time and calendar.
 * Tapping one opens its calendar.
 */
@Composable
internal fun UpcomingSchedule(sources: List<ScheduleSource>, onOpen: (String) -> Unit) {
    val context = LocalContext.current
    val is24Hour = remember { DateFormat.is24HourFormat(context) }
    val today = LocalDate.now()
    val days = remember(sources.map { it.list.items }, today) { upcoming(sources, today) }
    val mixed = sources.size > 1
    Column {
        if (days.none { it.first == today }) {
            Text(
                "Nothing on today." + if (days.isEmpty()) " Nothing in the next two weeks either." else "",
                Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        days.forEach { (date, entries) ->
            if (date != today || days.size > 1) DayHeading(date, today, null)
            entries.forEach { entry ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { onOpen(entry.source.list.id) }
                        .padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        entry.item.time?.let { displayTime(is24Hour, it) } ?: "All day",
                        Modifier.width(72.dp),
                        style = MaterialTheme.typography.labelLarge,
                        color = if (entry.item.time != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                    )
                    if (mixed) Box(Modifier.padding(end = 10.dp).width(4.dp).height(28.dp).clip(RoundedCornerShape(2.dp)).background(entry.source.color))
                    Column(Modifier.weight(1f)) {
                        Text(entry.item.text, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (mixed) Text(entry.source.label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

/** "Today · Friday 9 October", "Tomorrow · Saturday 10 October", "Wednesday 14 October". */
@Composable
private fun DayHeading(date: LocalDate, today: LocalDate, onClick: ((LocalDate) -> Unit)?) {
    val formatted = date.format(if (date.year == today.year) scheduleDay else scheduleDayWithYear)
    val relative = when (date) {
        today -> "Today · "
        today.plusDays(1) -> "Tomorrow · "
        today.minusDays(1) -> "Yesterday · "
        else -> ""
    }
    Text(
        relative + formatted,
        Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable { onClick(date) } else Modifier)
            .padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 4.dp),
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = if (date == today) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
    )
}

/** Add an entry on any day — in the entry sheet; with several calendars, it also asks which one. */
@Composable
private fun NewEntrySheet(
    sources: List<ScheduleSource>,
    onDismiss: () -> Unit,
    onAdd: (ScheduleSource, String, LocalDate, String?) -> Unit,
) {
    EntrySheet(
        initial = EntryDraft("", LocalDate.now(), null),
        editing = false,
        onDismiss = onDismiss,
        onSave = { draft, calendar -> onAdd(sources[calendar], draft.text, draft.date, draft.time) },
        calendars = sources.map { it.label to it.color },
    )
}

/** Nothing on the calendar (design canvas "Empty calendar"): a pinned blank note and the next step. */
@Composable
private fun EmptyCalendar(onInvite: (() -> Unit)?, onAdd: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Box(Modifier.size(120.dp).clearAndSetSemantics {}) {
            Box(
                Modifier.padding(14.dp).fillMaxSize().rotate(-4f).shadow(8.dp, RoundedCornerShape(14.dp), clip = false)
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(14.dp)),
            )
            PinDot(Brand.Marigold, 16.dp, Modifier.align(Alignment.TopCenter).offset(y = 8.dp))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Nothing on yet", style = MaterialTheme.typography.headlineSmall.copy(fontFamily = Brand.Serif, fontSize = 24.sp))
            Text(
                if (onInvite != null) "Calendars are better shared. Invite your group members, then add the first entry together."
                else "Add the first calendar entry for everyone to see.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (onInvite != null) {
                BrandButton("Invite group members", onClick = onInvite)
                BrandTextButton("Calendar entry +", onClick = onAdd)
            } else {
                BrandButton("Calendar entry +", onClick = onAdd)
            }
        }
    }
}

/** An entry the app wrote itself: a calendar or group being made, someone joining (ListRepository). */
private val Item.isAutoNote: Boolean get() = id.startsWith("created-") || id.startsWith("joined-")
