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
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.twodo.R
import app.twodo.TwoDoApp
import app.twodo.model.TodoList
import app.twodo.model.calendarRef
import app.twodo.model.listRefs
import app.twodo.sync.SyncStatus
import java.time.LocalDate

/**
 * The app's home: what's on today (or soon) across every calendar, every group, and starting or
 * joining a group. Lists are made inside groups; lists outside any group are only a small link at the
 * bottom, for anyone who has some. Back from a group comes here; back from here leaves the app.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HomeScreen(
    app: TwoDoApp,
    lists: Map<String, TodoList>,
    others: List<TodoList>,
    calendars: List<ScheduleSource>,
    status: Map<String, SyncStatus>,
    snackbar: SnackbarHostState,
    navigation: GroupNavigation,
    onOpen: (String) -> Unit,
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
        // Rows run edge to edge in the chosen style (cards bring their own margin); the rest keeps 16dp.
        val margin = Modifier.padding(horizontal = 16.dp)
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(vertical = 16.dp)) {
            item { MovedBanner() }
            if (calendars.isNotEmpty()) {
                item { Box(margin.padding(vertical = 4.dp)) { SectionTitle("Today") } }
                item { Box(margin) { UpcomingSchedule(calendars, onOpen) } }
                item { Spacer(Modifier.size(16.dp)) }
            }
            if (navigation.groups.isNotEmpty()) item { Box(margin.padding(vertical = 4.dp)) { SectionTitle("Groups") } }
            items(navigation.groups, key = { it.id }) { group ->
                GroupCard(app, group, lists, status[group.id]) { navigation.onSwitch(group.id) }
            }
            item { Spacer(Modifier.size(8.dp)) }
            item { HomeRow(Icons.Default.Add, "Start a new group", "A calendar and lists, shared with the people you choose", navigation.onNewGroup) }
            item { HomeRow(Icons.Default.PersonAdd, "Join with an invite", "Scan or paste someone's invite", navigation.onJoin) }
            item { HomeRow(Icons.Outlined.Settings, "Settings", "Your name, look, notifications and more", navigation.onSettings) }
            // Lists outside groups still work (and invites to one still arrive), but out of the way.
            if (others.isNotEmpty()) {
                item {
                    TextButton(onClick = navigation.onOtherLists, modifier = margin.padding(top = 8.dp)) {
                        Text("Lists outside groups (${others.size})", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

/** A group: its background (or colours) as a picture, its name, and what's on today. */
@Composable
private fun GroupCard(app: TwoDoApp, group: TodoList, lists: Map<String, TodoList>, status: SyncStatus?, onClick: () -> Unit) {
    val appThemeId by app.appTheme.collectAsStateWithLifecycle()
    val look = rememberLook(app, group, themeById(appThemeId))
    val colors = look.theme.colors
    StyledRow(onClick = onClick, contentPadding = PaddingValues(12.dp)) {
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

/** TwoDo is now Intack, a separate app: point people at it and say how to bring their groups across. */
@Composable
private fun MovedBanner() {
    val uri = LocalUriHandler.current
    StyledRow(fill = MaterialTheme.colorScheme.primaryContainer, onClick = { uri.openUri("https://github.com/rad-corps/intack/releases/latest") }, contentPadding = PaddingValues(16.dp)) {
        Column(Modifier.weight(1f)) {
            Text("TwoDo is now Intack", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
            Text(
                "This app won't get more updates. Tap here to get Intack (add github.com/rad-corps/intack in Obtainium). " +
                    "Then bring each group across: open it here, tap Invite, then Send link, then Copy. In Intack, tap Join with an invite and paste it. " +
                    "Once your groups show up in Intack you can remove TwoDo.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
    Spacer(Modifier.size(8.dp))
}

@Composable
private fun HomeRow(icon: ImageVector, title: String, detail: String, onClick: () -> Unit) {
    StyledRow(onClick = onClick) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
