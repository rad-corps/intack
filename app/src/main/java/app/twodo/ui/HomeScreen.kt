package app.twodo.ui

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.twodo.R
import app.twodo.TwoDoApp
import app.twodo.model.SpaceKind
import app.twodo.model.TodoList
import app.twodo.model.calendarRef
import app.twodo.model.listRefs
import app.twodo.sync.SyncStatus
import java.time.LocalDate

/**
 * The app's home: every group, the lists outside groups, and starting or joining a group. Back from
 * a group comes here; back from here leaves the app.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HomeScreen(
    app: TwoDoApp,
    lists: Map<String, TodoList>,
    others: List<TodoList>,
    status: Map<String, SyncStatus>,
    snackbar: SnackbarHostState,
    navigation: GroupNavigation,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.brand_name), style = MaterialTheme.typography.titleLarge) },
                colors = flatBar(),
                actions = { IconButton(onClick = navigation.onSettings) { Icon(Icons.Outlined.Settings, "Settings") } },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (navigation.groups.isNotEmpty()) item { SectionTitle("Groups") }
            items(navigation.groups, key = { it.id }) { group ->
                GroupCard(app, group, lists, status[group.id]) { navigation.onSwitch(group.id) }
            }
            if (navigation.hasAllCalendars || navigation.hasOtherLists) item { Spacer(Modifier.size(4.dp)) }
            if (navigation.hasAllCalendars) {
                item { HomeRow(Icons.Outlined.CalendarMonth, "All calendars", "Every group's calendar in one place", navigation.onAllCalendars) }
            }
            if (navigation.hasOtherLists) {
                item {
                    val toDo = others.filter { it.kind == SpaceKind.LIST && !it.archived }.sumOf { l -> l.visibleItems.count { !it.checked && !it.heading } }
                    HomeRow(Icons.Outlined.Checklist, "Other lists", "${count(others.size, "list")} not in a group · $toDo to do", navigation.onOtherLists)
                }
            }
            item { Spacer(Modifier.size(8.dp)) }
            item { HomeRow(Icons.Default.Add, "Start a new group", "A calendar and lists, shared with the people you choose", navigation.onNewGroup) }
            item { HomeRow(Icons.Default.PersonAdd, "Join with an invite", "Scan or paste someone's invite", navigation.onJoin) }
            item { HomeRow(Icons.Outlined.Settings, "Settings", "Your name, look, notifications and more", navigation.onSettings) }
        }
    }
}

/** A group: its background (or colours) as a picture, its name, and what's on today. */
@Composable
private fun GroupCard(app: TwoDoApp, group: TodoList, lists: Map<String, TodoList>, status: SyncStatus?, onClick: () -> Unit) {
    val appThemeId by app.appTheme.collectAsStateWithLifecycle()
    val look = rememberLook(app, group, themeById(appThemeId))
    val colors = look.theme.colors
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.9f)),
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(56.dp).clip(RoundedCornerShape(14.dp)).background(colors.primaryContainer), contentAlignment = Alignment.Center) {
                val photo = look.photo
                if (photo != null) Image(photo, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                else Icon(Icons.Outlined.Groups, null, tint = colors.onPrimaryContainer)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(group.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    groupSummary(group, lists),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            SyncDot(status)
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.outline)
        }
    }
}

/** "3 today · 7 to do · with Sarah and Tom". */
private fun groupSummary(group: TodoList, lists: Map<String, TodoList>): String {
    val today = LocalDate.now().toString()
    val calendar = group.calendarRef?.spaceId?.let { lists[it] }
    val todayCount = calendar?.visibleItems?.count { it.date == today } ?: 0
    val toDo = group.listRefs.mapNotNull { ref -> ref.spaceId?.let { lists[it] } }.filter { !it.archived }
        .sumOf { l -> l.visibleItems.count { !it.checked && !it.heading } }
    val people = group.members.values.sortedBy { it.lowercase() }
    return listOfNotNull(
        if (todayCount == 0) "Nothing today" else "$todayCount today",
        "$toDo to do",
        if (people.isEmpty()) "just you so far" else "with ${joinNames(people)}",
    ).joinToString(" · ")
}

@Composable
private fun HomeRow(icon: ImageVector, title: String, detail: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun count(n: Int, noun: String) = if (n == 1) "1 $noun" else "$n ${noun}s"
