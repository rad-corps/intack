@file:OptIn(ExperimentalMaterial3Api::class)

package app.twodo.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Build
import android.widget.Toast
import android.text.format.DateUtils
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material.icons.filled.Close
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.twodo.R
import app.twodo.BuildConfig
import app.twodo.TwoDoApp
import app.twodo.data.ALL_CALENDARS
import app.twodo.data.CrashLog
import app.twodo.data.HOME
import app.twodo.data.OTHER_LISTS
import app.twodo.model.BLANK_LIST
import app.twodo.model.calendarRef
import app.twodo.model.sharedLook
import app.twodo.net.SyncLog
import app.twodo.model.Invite
import app.twodo.model.Item
import app.twodo.model.LIST_TEMPLATES
import app.twodo.model.ListTemplate
import app.twodo.model.afterHeadingMove
import app.twodo.model.STATUS_DELETED
import app.twodo.model.ShareLink
import app.twodo.model.SpaceKind
import app.twodo.model.TodoList
import app.twodo.model.headings
import app.twodo.model.isJoining
import app.twodo.model.sectionItems
import app.twodo.model.sectionOf
import app.twodo.sync.ListEvent
import app.twodo.sync.Notifications
import app.twodo.sync.SyncStatus
import app.twodo.model.describe
import app.twodo.model.indexAmongAll
import app.twodo.model.offerArchive
import app.twodo.model.pendingStatusRequest
import com.google.zxing.BarcodeFormat
import com.journeyapps.barcodescanner.BarcodeEncoder
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@Composable
fun TwoDoRoot(
    app: TwoDoApp,
    pendingInvite: Invite?,
    onInviteHandled: () -> Unit,
    openRequest: String? = null,
    onOpenHandled: () -> Unit = {},
    onScreenDark: (Boolean) -> Unit = {},
) {
    val appThemeId by app.appTheme.collectAsStateWithLifecycle()
    val appTheme = themeById(appThemeId)
    val lists by app.repo.lists.collectAsStateWithLifecycle()
    val status by app.sync.status.collectAsStateWithLifecycle()
    // The group on screen (or OTHER_LISTS), and a list or diary opened on top of it.
    var view by rememberSaveable { mutableStateOf(app.identity.lastView) }
    var openSpaceId by rememberSaveable { mutableStateOf<String?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val groupStates = rememberSaveableStateHolder()
    var newGroup by remember { mutableStateOf(false) }
    var joining by remember { mutableStateOf(false) }
    var settings by remember { mutableStateOf(false) }
    var notifications by rememberSaveable { mutableStateOf(false) }
    // Someone who opens an invite link straight away skips the welcome screen, so ask their name then.
    var nameAsked by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    // Crashes since last time, offered for emailing only if the user opted in.
    var crashes by remember { mutableStateOf(if (app.identity.offerCrashReports) CrashLog.unseen(context, app.identity) else emptyList()) }

    fun show(viewId: String) {
        view = viewId
        openSpaceId = null
        app.identity.lastView = viewId
    }

    /** Shows [listId]: a group's home, a list on top of its group, or a list outside any group. */
    fun open(listId: String) {
        val list = app.repo.lists.value[listId] ?: return
        when {
            list.kind == SpaceKind.GROUP -> show(list.id)
            list.groupId != null -> {
                show(list.groupId)
                // The calendar is the group's first tab; lists open on top.
                if (list.kind == SpaceKind.LIST) openSpaceId = list.id
            }
            else -> {
                show(OTHER_LISTS)
                openSpaceId = list.id
            }
        }
    }

    LaunchedEffect(Unit) { app.repo.conflicts.collect { snackbar.showSnackbar(it.describe()) } }
    // Joins and leaves get a pull-down notification too; this is the in-app version.
    LaunchedEffect(Unit) {
        app.sync.events.collect { event ->
            when (event) {
                is ListEvent.Joined -> "${event.who} joined “${event.listName}”"
                is ListEvent.Left -> "${event.who} left “${event.listName}”"
                is ListEvent.JoinedList -> "Joined “${event.listName}” with ${event.who}"
                is ListEvent.Changed -> null
            }?.let { snackbar.showSnackbar(it) }
        }
    }
    LaunchedEffect(openRequest) {
        if (openRequest != null) {
            open(openRequest)
            onOpenHandled()
        }
    }
    LaunchedEffect(pendingInvite) {
        if (pendingInvite != null) {
            open(app.repo.joinList(pendingInvite).id)
            onInviteHandled()
        }
    }

    val groups = lists.values.filter { it.kind == SpaceKind.GROUP }.sortedBy { it.name.lowercase() }
    val others = lists.values.filter { it.kind != SpaceKind.GROUP && it.groupId == null }.sortedBy { it.name.lowercase() }
    val calendars = groups.mapNotNull { g -> g.calendarRef?.spaceId?.let { lists[it] }?.let { it to g } } +
        others.filter { it.kind == SpaceKind.DIARY }.map { it to null }
    val navigation = GroupNavigation(
        groups = groups,
        hasOtherLists = others.isNotEmpty(),
        hasAllCalendars = calendars.size > 1,
        onSwitch = ::show,
        onAllCalendars = { show(ALL_CALENDARS) },
        onOtherLists = { show(OTHER_LISTS) },
        onNewGroup = { newGroup = true },
        onJoin = { joining = true },
        onSettings = { settings = true },
        onHome = { show(HOME) },
    )
    val space = openSpaceId?.let { lists[it] }
    val home = view == HOME
    val allCalendars = view == ALL_CALENDARS && calendars.size > 1
    val group = view?.let { lists[it] }?.takeIf { it.kind == SpaceKind.GROUP }
        ?: if (home || (view == OTHER_LISTS && others.isNotEmpty()) || allCalendars) null else groups.firstOrNull()
    // App-wide screens (Notifications) wear the app's theme, not the look of the group they were opened from.
    val look = rememberLook(app, if (notifications) null else space ?: group, appTheme)
    LaunchedEffect(look.theme.dark) { onScreenDark(look.theme.dark) }

    val myColor by app.myColor.collectAsStateWithLifecycle()
    val colors by app.sync.colors.collectAsStateWithLifecycle()
    val people = remember(myColor, colors) { People(app.identity.deviceId, myColor, colors) }
    // Back from a group (or the other lists, or all calendars) goes home; back from home leaves the app.
    // Before the screens, so their own back handling (e.g. closing history) comes first.
    BackHandler(enabled = lists.isNotEmpty() && !home && space == null && !notifications) { show(HOME) }

    CompositionLocalProvider(LocalPeople provides people) {
    LookSurface(look) {
        when {
            notifications -> NotificationSettingsScreen(app, onBack = { notifications = false })
            lists.isEmpty() -> WelcomeScreen(
                app,
                onStart = { name -> scope.launch { show(app.repo.createGroup(name).id) } },
                onJoin = { joining = true },
            )
            space != null -> {
                BackHandler { openSpaceId = null }
                val spaceStatus = status[space.id] ?: SyncStatus()
                if (space.kind == SpaceKind.DIARY) {
                    DiaryScreen(app, space, spaceStatus, snackbar, onBack = { openSpaceId = null })
                } else {
                    ListScreen(app, space, spaceStatus, snackbar, onBack = { openSpaceId = null })
                }
            }
            home -> HomeScreen(app, lists, others, calendarSources(calendars), status, snackbar, navigation, onOpen = ::open)
            // Kept per group while a list is open on top, so coming back finds the same tab.
            group != null -> groupStates.SaveableStateProvider(group.id) {
                GroupScreen(app, group, lists, status, snackbar, navigation, onOpenSpace = { openSpaceId = it })
            }
            allCalendars -> AllCalendarsScreen(app, calendars, navigation)
            else -> ListsScreen(app, others, status, snackbar, appTheme, navigation, onOpen = { openSpaceId = it })
        }
    }
    }

    if (lists.isNotEmpty() && !app.identity.hasName && !nameAsked) {
        TextPromptDialog(
            title = "What's your first name?",
            label = "Your name",
            confirm = "Save",
            supporting = "So the others know who added what. You can change it in Settings.",
            onDismiss = { nameAsked = true },
        ) { name ->
            app.setName(name)
            nameAsked = true
        }
    }
    if (crashes.isNotEmpty()) {
        val brand = stringResource(R.string.brand_name)
        fun done() {
            CrashLog.markSeen(context, app.identity)
            crashes = emptyList()
        }
        AlertDialog(
            onDismissRequest = ::done,
            title = { Text("$brand closed unexpectedly") },
            text = {
                Text(
                    "Email a report to the developer so it can be fixed? It opens in your email app so you can see what's sent: " +
                        "the app and phone version, and where in the app it went wrong — nothing from your lists or calendar.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (!CrashLog.email(context, crashes)) Toast.makeText(context, "No email app found", Toast.LENGTH_LONG).show()
                    done()
                }) { Text("Email report") }
            },
            dismissButton = { TextButton(onClick = ::done) { Text("Not now") } },
        )
    }
    StatusRequestDialog(app, lists)
    if (newGroup) {
        NewGroupDialog(others, onDismiss = { newGroup = false }) { name, calendarId, listIds ->
            newGroup = false
            scope.launch { show(app.repo.createGroup(name, calendarId, listIds).id) }
        }
    }
    if (joining) {
        JoinDialog(onDismiss = { joining = false }) { invite ->
            joining = false
            scope.launch { open(app.repo.joinList(invite).id) }
        }
    }
    if (settings) {
        SettingsDialog(app, onDismiss = { settings = false }, onNotifications = {
            settings = false
            notifications = true
        })
    }
}

@Composable
private fun ListsScreen(
    app: TwoDoApp,
    lists: List<TodoList>,
    status: Map<String, SyncStatus>,
    snackbar: SnackbarHostState,
    appTheme: AppTheme,
    navigation: GroupNavigation,
    onOpen: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var creating by remember { mutableStateOf(false) }
    var switching by remember { mutableStateOf(false) }
    var showArchived by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Box {
                        Row(
                            Modifier.clip(RoundedCornerShape(10.dp)).clickable { switching = true }.padding(horizontal = 4.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                if (navigation.groups.isEmpty()) stringResource(R.string.brand_name) else "Other lists",
                                style = MaterialTheme.typography.titleLarge,
                            )
                            Icon(Icons.Default.ArrowDropDown, "Switch group")
                        }
                        GroupSwitcher(switching, null, navigation, onDismiss = { switching = false })
                    }
                },
                colors = flatBar(),
                actions = {
                    IconButton(onClick = navigation.onSettings) { Icon(Icons.Default.Settings, "Settings") }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { creating = true },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                elevation = FloatingActionButtonDefaults.elevation(0.dp, 0.dp, 0.dp, 0.dp),
                icon = { Icon(Icons.Default.Add, null) },
                text = { Text("New") },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        if (lists.isEmpty()) {
            Column(
                Modifier.fillMaxSize().padding(padding).padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("No lists yet", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Create a list or a diary, or join one by scanning the QR code on another phone.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
        LazyColumn(contentPadding = padding) {
            if (navigation.groups.isEmpty()) item { GroupsIntro(onStart = navigation.onNewGroup) }
            val (archived, active) = lists.partition { it.archived }
            items(active + if (showArchived) archived else emptyList(), key = { it.id }) { list ->
                if (list === archived.firstOrNull()) ArchivedHeader(archived.size, expanded = true) { showArchived = false }
                Row(
                    Modifier.fillMaxWidth().clickable { onOpen(list.id) }.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SpaceBadge(list, appTheme)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(list.name, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            if (list.archived) list.summary() else "${list.summary()} · ${status[list.id].summary()}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (!list.archived) SyncDot(status[list.id])
                }
                HorizontalDivider(Modifier.padding(start = 70.dp, end = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
            }
            if (archived.isNotEmpty() && !showArchived) item { ArchivedHeader(archived.size, expanded = false) { showArchived = true } }
        }
    }

    if (creating) {
        NewSpaceDialog(onDismiss = { creating = false }) { name, kind, template ->
            creating = false
            scope.launch { onOpen(app.repo.createList(name, kind, template).id) }
        }
    }
}

/** Every calendar this phone has — each group's, and diaries outside groups — in one schedule. */
@Composable
private fun AllCalendarsScreen(app: TwoDoApp, calendars: List<Pair<TodoList, TodoList?>>, navigation: GroupNavigation) {
    var switching by remember { mutableStateOf(false) }
    val sources = calendarSources(calendars)
    Scaffold(
        topBar = {
            TopAppBar(
                colors = flatBar(),
                title = {
                    Box {
                        Row(
                            Modifier.clip(RoundedCornerShape(10.dp)).clickable { switching = true }.padding(horizontal = 4.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("All calendars", style = MaterialTheme.typography.titleLarge)
                            Icon(Icons.Default.ArrowDropDown, "Switch group")
                        }
                        GroupSwitcher(switching, ALL_CALENDARS, navigation, onDismiss = { switching = false })
                    }
                },
            )
        },
    ) { padding -> ScheduleView(app, sources, Modifier.padding(padding)) }
}

/**
 * Name the new group; someone who already has a diary and lists can make them the group's calendar and
 * lists rather than starting over.
 */
@Composable
private fun NewGroupDialog(others: List<TodoList>, onDismiss: () -> Unit, onCreate: (String, String?, List<String>) -> Unit) {
    val diaries = others.filter { it.kind == SpaceKind.DIARY }
    val existingLists = others.filter { it.kind == SpaceKind.LIST }
    var name by remember { mutableStateOf("Family") }
    var calendarId by remember { mutableStateOf(diaries.firstOrNull()?.id) }
    var listIds by remember { mutableStateOf(emptySet<String>()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Start a new group") },
        text = {
            Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState())) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Group name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                if (diaries.isEmpty() && existingLists.isEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text("It comes with a calendar and a shopping list. Invite people once it's made.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (diaries.isNotEmpty()) {
                    Spacer(Modifier.height(16.dp))
                    SectionTitle("Calendar")
                    (diaries.map { it.id to "Use “${it.name}”" } + (null to "Start a new one")).forEach { (id, label) ->
                        ChoiceRow(label, calendarId == id, radio = true) { calendarId = id }
                    }
                }
                if (existingLists.isNotEmpty()) {
                    Spacer(Modifier.height(16.dp))
                    SectionTitle("Bring these lists")
                    existingLists.forEach { list ->
                        ChoiceRow(list.name, list.id in listIds, radio = false) {
                            listIds = if (list.id in listIds) listIds - list.id else listIds + list.id
                        }
                    }
                }
                if (diaries.isNotEmpty() || existingLists.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Everyone you invite gets them. People you already share them with keep them too.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = { onCreate(name, calendarId, listIds.toList()) }, enabled = name.isNotBlank()) { Text("Start") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun ChoiceRow(label: String, selected: Boolean, radio: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick).padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (radio) RadioButton(selected = selected, onClick = onClick) else Checkbox(checked = selected, onCheckedChange = { onClick() })
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

/** For people from before groups: what groups are, and a button to start one. */
@Composable
private fun GroupsIntro(onStart: () -> Unit) {
    Card(
        Modifier.fillMaxWidth().padding(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("New: groups", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
            Spacer(Modifier.height(4.dp))
            Text(
                "One calendar and your lists, shared with the same people. Invite them once and they get everything.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Spacer(Modifier.height(12.dp))
            Button(onClick = onStart) { Text("Start a group") }
        }
    }
}

@Composable
internal fun ListScreen(app: TwoDoApp, list: TodoList, status: SyncStatus, snackbar: SnackbarHostState, onBack: () -> Unit) {
    var showHistory by rememberSaveable(list.id) { mutableStateOf(false) }
    val names by app.sync.names.collectAsStateWithLifecycle()
    if (showHistory) return HistoryScreen(list, app.identity.deviceId, names, onBack = { showHistory = false })
    Scaffold(
        topBar = { SpaceTopBar(app, list, status, onBack, onHistory = { showHistory = true }) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        ListBody(app, list, status, onBack, Modifier.padding(padding).consumeWindowInsets(padding))
    }
}

/** The list itself: add bar and tickable, draggable items. */
@Composable
internal fun ListBody(app: TwoDoApp, list: TodoList, status: SyncStatus, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    var newText by rememberSaveable(list.id) { mutableStateOf("") }
    val names by app.sync.names.collectAsStateWithLifecycle()
    val highlighted = rememberRemoteHighlights(app, list.id)

    // Set when this phone adds an item, so the list scrolls to it once it appears.
    var scrollToNew by remember { mutableStateOf(false) }
    // The item (or heading) open for changing.
    var editing by remember { mutableStateOf<Item?>(null) }

    fun add() {
        val text = newText
        newText = ""
        scrollToNew = true
        scope.launch { app.repo.addItem(list.id, text) }
    }

    Column(modifier.fillMaxSize()) {
        JoiningBanner(list, status)
        ArchiveBanner(app, list, onBack)
        // Local copy so dragging is smooth; committed to the repository when the drag ends.
        val shown = if (list.hideHeadings) list.visibleItems.filter { !it.heading } else list.visibleItems
        // The drag handles keep the callbacks they're first given, so everything those read must be
        // current: one lasting state for the order on screen, and the latest list.
        val current by rememberUpdatedState(list)
        var ordered by remember(list.id) { mutableStateOf(shown) }
        // The item being dragged: the heading of the section it would land in lights up.
        var dragging by remember { mutableStateOf<String?>(null) }
        // A heading being dragged carries its items: they're drawn under it, inside the dragged block.
        var carried by remember { mutableStateOf<Pair<String, List<Item>>?>(null) }
        // Follow changes to the list, except mid-drag, when the order on screen is the drag's.
        LaunchedEffect(shown) { if (dragging == null && carried == null) ordered = shown }
        val target = dragging?.let { id -> sectionOf(ordered, id)?.id }
        val listState = rememberLazyListState()
        val reorderState = rememberReorderableLazyListState(listState) { from, to ->
            ordered = ordered.toMutableList().apply { add(to.index, removeAt(from.index)) }
        }
        LaunchedEffect(ordered.size) {
            // New items go above the headings, if there are any: scroll to the newest.
            val newest = ordered.withIndex().filter { !it.value.heading }.maxByOrNull { it.value.createdAt }?.index
            if (scrollToNew && newest != null) listState.animateScrollToItem(newest)
            scrollToNew = false
        }
        LazyColumn(Modifier.weight(1f), state = listState, contentPadding = PaddingValues(bottom = 8.dp)) {
            items(ordered, key = { it.id }) { item ->
                ReorderableItem(reorderState, key = item.id) {
                    val rowStyle = LocalListStyle.current
                    val background by animateColorAsState(
                        when {
                            item.id == target -> MaterialTheme.colorScheme.tertiaryContainer
                            item.id in highlighted -> MaterialTheme.colorScheme.primaryContainer
                            else -> Color.Transparent
                        },
                        animationSpec = tween(if (dragging != null) 150 else 600),
                        label = "highlight",
                    )
                    val dragHandle = if (item.heading) {
                        // A heading takes its items with it: they come out of the list and ride along under it.
                        Modifier.draggableHandle(
                            onDragStarted = {
                                val items = sectionItems(ordered, item.id).drop(1)
                                carried = item.id to items
                                ordered = ordered.filter { it !in items }
                            },
                            onDragStopped = {
                                val index = ordered.indexOfFirst { it.id == item.id }
                                // Show the result straight away (the same as the saved move will give), whether
                                // or not anything actually moved.
                                ordered = afterHeadingMove(current.visibleItems, item.id, index)
                                carried = null
                                scope.launch { app.repo.moveSection(current.id, item.id, index) }
                            },
                        )
                    } else {
                        Modifier.draggableHandle(
                            onDragStarted = { dragging = item.id },
                            onDragStopped = {
                                dragging = null
                                val index = ordered.indexOfFirst { it.id == item.id }
                                val drop = if (current.hideHeadings) indexAmongAll(current.visibleItems, ordered, item.id, index) else index
                                scope.launch { app.repo.moveItem(current.id, item.id, drop) }
                            },
                        )
                    }
                    if (item.heading) {
                        val riding = carried?.takeIf { it.first == item.id }?.second
                        Column(
                            if (riding == null) Modifier
                            else Modifier.shadow(6.dp, RoundedCornerShape(12.dp)).clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                        ) {
                            // A section heading: bold, no checkbox; tap to rename or delete.
                            Row(
                                Modifier.fillMaxWidth().background(if (riding == null) background else Color.Transparent).clickable { editing = item }
                                    .padding(start = 20.dp, end = 4.dp, top = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    item.text,
                                    Modifier.weight(1f).padding(vertical = 8.dp),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.secondary,
                                )
                                Icon(painterResource(R.drawable.ic_drag_handle), "Move heading", tint = MaterialTheme.colorScheme.outline, modifier = dragHandle.padding(12.dp))
                            }
                            riding?.forEach { CarriedItem(it) }
                        }
                        return@ReorderableItem
                    }
                    Row(
                        Modifier.fillMaxWidth().listRow(rowStyle, background, MaterialTheme.colorScheme.surfaceContainerHigh).padding(start = 8.dp, end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // The checkbox ticks; the words open the item to change or delete it.
                        Checkbox(
                            checked = item.checked,
                            onCheckedChange = { scope.launch { app.repo.setChecked(list.id, item.id, it) } },
                            colors = CheckboxDefaults.colors(
                                // Ticked in the colour of whoever ticked it.
                                checkedColor = personColor(item.version.by),
                                uncheckedColor = MaterialTheme.colorScheme.outline,
                            ),
                        )
                        Column(Modifier.weight(1f).clip(RoundedCornerShape(8.dp)).clickable { editing = item }.padding(vertical = rowStyle.spacing, horizontal = 4.dp)) {
                            Text(
                                item.text,
                                style = MaterialTheme.typography.bodyLarge,
                                textDecoration = if (item.checked) TextDecoration.LineThrough else null,
                                color = if (item.checked) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface,
                            )
                            // The start of its note; tap the item to read or change all of it.
                            item.note?.let { note ->
                                Text(
                                    note,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            if (item.checked) {
                                Text(
                                    tickedBy(item, app.identity.deviceId, names),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = personColor(item.version.by),
                                )
                            }
                        }
                        Icon(painterResource(R.drawable.ic_drag_handle), "Reorder", tint = MaterialTheme.colorScheme.outline, modifier = dragHandle.padding(12.dp))
                    }
                }
            }
        }
        // At the bottom, within thumb reach, and lifted above the keyboard while typing.
        Row(Modifier.fillMaxWidth().imePadding().padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = newText,
                onValueChange = { newText = it },
                placeholder = { Text("Add an item") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
                keyboardOptions = KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { add() }),
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = ::add, enabled = newText.isNotBlank()) { Icon(Icons.Default.Add, "Add") }
        }
    }

    editing?.let { open ->
        // The latest version, in case someone changed it meanwhile.
        val item = list.items[open.id]?.takeIf { !it.deleted } ?: return@let
        val history = remember(list.audit, item.id) { list.audit.values.filter { it.itemId == item.id }.sortedByDescending { it.ts } }
        ListItemSheet(
            item = item,
            headings = list.headings,
            section = sectionOf(list.visibleItems, item.id),
            onDismiss = { editing = null },
            onSave = { text, headingId, note ->
                editing = null
                val before = sectionOf(list.visibleItems, item.id)?.id
                app.save {
                    app.repo.editText(list.id, item.id, text)
                    if (!item.heading) app.repo.setNote(list.id, item.id, note)
                    if (!item.heading && headingId != before) app.repo.moveToSection(list.id, item.id, headingId)
                }
            },
            onDelete = {
                editing = null
                app.save { app.repo.deleteItem(list.id, item.id) }
            },
            history = history,
            historyRow = { AuditRow(it, app.identity.deviceId, names, showDate = true, compact = true) },
        )
    }

}

/** Title with sync status, Share, and a menu with History and Remove — shared by lists and diaries. */
@Composable
internal fun SpaceTopBar(app: TwoDoApp, list: TodoList, status: SyncStatus, onBack: () -> Unit, onHistory: () -> Unit) {
    var sharing by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var confirmRemove by remember { mutableStateOf(false) }
    var pickingTheme by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var addingHeading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val inGroup = list.groupId != null
    TopAppBar(
        title = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(list.kind.icon, list.kind.label(inGroup), tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(list.name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleLarge)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (!list.archived) {
                        SyncDot(status)
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(
                        if (list.archived) "Archived · not syncing" else status.summary(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        colors = flatBar(),
        navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
        },
        actions = {
            // In a group, people are invited to the whole group and the look is the group's.
            if (!inGroup) IconButton(onClick = { sharing = true }) { Icon(Icons.Default.Share, "Share") }
            IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "More") }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                if (list.kind == SpaceKind.LIST) {
                    DropdownMenuItem(text = { Text("Add a heading") }, onClick = { menu = false; addingHeading = true })
                    if (list.headings.isNotEmpty()) {
                        DropdownMenuItem(
                            text = { Text(if (list.hideHeadings) "Show headings" else "Hide headings") },
                            onClick = { menu = false; app.save { app.repo.setHideHeadings(list.id, !list.hideHeadings) } },
                        )
                    }
                }
                DropdownMenuItem(text = { Text("History") }, onClick = { menu = false; onHistory() })
                if (list.kind == SpaceKind.LIST && !list.archived) {
                    DropdownMenuItem(text = { Text("Archive") }, onClick = { menu = false; onBack(); app.save { app.repo.archive(list.id) } })
                }
                // Only here, not on the archived banner, so following the prompts can't archive and unarchive in a loop.
                if (list.archived) {
                    DropdownMenuItem(text = { Text("Bring back") }, onClick = { menu = false; app.save { app.repo.unarchive(list.id) } })
                }
                if (inGroup) {
                    DropdownMenuItem(text = { Text("Rename") }, onClick = { menu = false; renaming = true })
                    if (list.kind == SpaceKind.LIST) {
                        DropdownMenuItem(text = { Text("Delete list") }, onClick = { menu = false; confirmRemove = true })
                    }
                } else {
                    DropdownMenuItem(text = { Text("Look") }, onClick = { menu = false; pickingTheme = true })
                    DropdownMenuItem(
                        text = { Text(if (list.kind == SpaceKind.LIST) "Delete" else "Remove from this phone") },
                        onClick = { menu = false; confirmRemove = true },
                    )
                }
            }
        },
    )
    if (sharing) ShareDialog(app, list, onDismiss = { sharing = false })
    if (pickingTheme) ListLookEditorDialog(app, list, onDismiss = { pickingTheme = false })
    if (addingHeading) {
        TextPromptDialog(
            title = "Add a heading",
            label = "Heading",
            confirm = "Add",
            supporting = "For example: Fruit & veg, Dairy, Bakery. It goes at the bottom; drag it, or move items into it.",
            onDismiss = { addingHeading = false },
        ) { name ->
            addingHeading = false
            app.save { app.repo.addItem(list.id, name, heading = true) }
        }
    }
    if (renaming) {
        TextPromptDialog(title = "Rename", label = "Name", confirm = "Save", initial = list.name, onDismiss = { renaming = false }) { name ->
            renaming = false
            app.save { app.repo.rename(list.id, name) }
        }
    }
    if (confirmRemove && inGroup) {
        AlertDialog(
            onDismissRequest = { confirmRemove = false },
            title = { Text("Delete “${list.name}”?") },
            text = { Text(DELETE_EXPLAINED.format("everyone in the group")) },
            confirmButton = {
                TextButton(onClick = {
                    confirmRemove = false
                    onBack()
                    app.save { app.repo.deleteFromGroup(list.id) }
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                Row {
                    if (!list.archived) {
                        TextButton(onClick = { confirmRemove = false; onBack(); app.save { app.repo.archive(list.id) } }) { Text("Archive instead") }
                    }
                    TextButton(onClick = { confirmRemove = false }) { Text("Cancel") }
                }
            },
        )
    } else if (confirmRemove && list.kind == SpaceKind.LIST) {
        AlertDialog(
            onDismissRequest = { confirmRemove = false },
            title = { Text("Delete “${list.name}”?") },
            text = { Text(DELETE_EXPLAINED.format("everyone you share it with")) },
            confirmButton = {
                TextButton(onClick = {
                    confirmRemove = false
                    onBack()
                    app.sync.leave(list.id, deleting = true)
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                Row {
                    if (!list.archived) {
                        TextButton(onClick = { confirmRemove = false; onBack(); app.save { app.repo.archive(list.id) } }) { Text("Archive instead") }
                    }
                    TextButton(onClick = { confirmRemove = false }) { Text("Cancel") }
                }
            },
        )
    } else if (confirmRemove) {
        AlertDialog(
            onDismissRequest = { confirmRemove = false },
            title = { Text("Remove “${list.name}”?") },
            text = { Text("It's removed from this phone only. Other devices keep their copy, and you can rejoin with the QR code.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmRemove = false
                    onBack()
                    app.sync.leave(list.id)
                }) { Text("Remove") }
            },
            dismissButton = { TextButton(onClick = { confirmRemove = false }) { Text("Cancel") } },
        )
    }
}

/**
 * Item ids changed by others in the last moment, for a brief highlight. Also clears the list's
 * "changes" notification, since the user is now looking at it.
 */
@Composable
internal fun rememberRemoteHighlights(app: TwoDoApp, listId: String): Map<String, Long> {
    val context = LocalContext.current
    val highlighted = remember(listId) { mutableStateMapOf<String, Long>() }
    LaunchedEffect(listId) {
        Notifications.clearChanges(context, listId)
        app.sync.events.collect { event ->
            if (event !is ListEvent.Changed || event.listId != listId) return@collect
            val stamp = System.currentTimeMillis()
            event.itemIds.forEach { highlighted[it] = stamp }
            launch {
                delay(HIGHLIGHT_MS)
                event.itemIds.forEach { if (highlighted[it] == stamp) highlighted.remove(it) }
            }
        }
    }
    return highlighted
}

@Composable
internal fun ShareDialog(app: TwoDoApp, list: TodoList, onDismiss: () -> Unit) {
    val context = LocalContext.current
    // Look for the newcomer every few seconds while the code is on screen.
    DisposableEffect(list.id) {
        app.sync.setSharing(list.id, true)
        onDispose { app.sync.setSharing(list.id, false) }
    }
    // Whoever connects to this list for the first time while the dialog is open.
    val membersBefore = remember(list.id) { list.members.keys }
    val newcomers = list.members.filterKeys { it !in membersBefore }.values
    val link = remember(list.id) { ShareLink.build(list) }
    val isGroup = list.kind == SpaceKind.GROUP
    val brand = stringResource(R.string.brand_name)
    val qr = remember(link) { BarcodeEncoder().encodeBitmap(link, BarcodeFormat.QR_CODE, 720, 720).asImageBitmap() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isGroup) "Invite to ${list.name}" else "Share “${list.name}”") },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Image(qr, "QR code for this ${if (isGroup) "group" else "list"}", Modifier.size(260.dp))
                Spacer(Modifier.height(12.dp))
                Text(
                    if (isGroup) {
                        "On their phone: install $brand, tap “Join with an invite” and scan this code — or send them the link. " +
                            "They'll get the calendar and all the lists. Only invite people you trust: they can see and change everything."
                    } else {
                        "Scan this with $brand on the other phone (Join list). Anyone with this code can see and edit the list."
                    },
                )
                Spacer(Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (newcomers.isEmpty()) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "Waiting for someone to scan or open the link…",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        Text(
                            "✓ ${newcomers.joinToString(", ")} joined",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
        dismissButton = {
            TextButton(onClick = {
                val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, ShareLink.message(list, context.getString(R.string.brand_name)))
                context.startActivity(Intent.createChooser(send, "Send link"))
            }) { Text("Send link") }
        },
    )
}

@Composable
private fun JoinDialog(onDismiss: () -> Unit, onJoin: (Invite) -> Unit) {
    val appName = stringResource(R.string.brand_name)
    var link by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val scanner = rememberLauncherForActivityResult(ScanContract()) { result ->
        val contents = result.contents ?: return@rememberLauncherForActivityResult
        ShareLink.parse(contents)?.let(onJoin) ?: run { error = "That QR code isn't a $appName list." }
    }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            QrImage.decode(context, uri)?.let(ShareLink::parse)?.let(onJoin)
                ?: run { error = "Couldn't find a $appName QR code in that image." }
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Join with an invite") },
        text = {
            Column {
                OutlinedButton(
                    onClick = {
                        scanner.launch(
                            ScanOptions()
                                .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                                .setPrompt("Scan the invite's QR code")
                                .setBeepEnabled(false)
                                .setOrientationLocked(false),
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Scan QR code") }
                OutlinedButton(
                    onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Choose screenshot") }
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = link,
                    onValueChange = { link = it; error = null },
                    label = { Text("…or paste a link") },
                    isError = error != null,
                    supportingText = error?.let { { Text(it) } },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                ShareLink.parse(link)?.let(onJoin) ?: run { error = "That isn't a $appName link." }
            }, enabled = link.isNotBlank()) { Text("Join") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun SettingsDialog(app: TwoDoApp, onDismiss: () -> Unit, onNotifications: () -> Unit) {
    var name by remember { mutableStateOf(if (app.identity.hasName) app.identity.deviceName else "") }
    var background by remember { mutableStateOf(app.identity.backgroundSync) }
    val themeId by app.appTheme.collectAsStateWithLifecycle()
    val appLook by app.appLook.collectAsStateWithLifecycle()
    var pickingTheme by remember { mutableStateOf(false) }
    // Saved straight away by the editor, like the text size.
    if (pickingTheme) return AppLookEditorDialog(app, onDismiss = { pickingTheme = false })
    var textScale by remember { mutableStateOf(app.identity.textScale) }
    var offerCrashReports by remember { mutableStateOf(app.identity.offerCrashReports) }
    val startColor = app.identity.myColor ?: defaultColor(app.identity.deviceId)
    var myColor by remember { mutableStateOf(startColor) }
    val context = LocalContext.current
    var crashReports by remember { mutableStateOf(CrashLog.reports(context)) }
    var showLog by remember { mutableStateOf(false) }
    var direct by remember { mutableStateOf(app.identity.directConnections) }
    var diagnostics by remember { mutableStateOf(app.identity.diagnostics) }
    if (showLog) return ConnectionLogDialog(onDismiss = { showLog = false })
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Settings") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Your name") },
                    supportingText = { Text("Shown to others next to items you tick") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Text("Your colour")
                Text(
                    "Everyone sees what you tick and add in it",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(PERSON_COLORS) { option ->
                        Box(
                            Modifier.size(36.dp).clip(CircleShape).background(Color(option))
                                .border(if (option == myColor) 3.dp else 0.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                                .clickable { myColor = option },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (option == myColor) Icon(Icons.Default.Check, "Your colour", tint = Color.White, modifier = Modifier.size(18.dp))
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { pickingTheme = true }.padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Look")
                        Text(
                            "${themeById(themeId).name}${appLook.photoId?.let { " with a background" }.orEmpty()} · groups can choose their own",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    val colors = themeById(themeId).colors
                    Box(
                        Modifier.size(28.dp).clip(CircleShape).background(colors.background)
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) { Box(Modifier.size(12.dp).clip(CircleShape).background(colors.primary)) }
                }
                Spacer(Modifier.height(16.dp))
                Text("Text size")
                Spacer(Modifier.height(6.dp))
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    TEXT_SIZES.forEachIndexed { index, (label, scale) ->
                        SegmentedButton(
                            selected = textScale == scale,
                            onClick = { textScale = scale; app.setTextScale(scale) },
                            shape = SegmentedButtonDefaults.itemShape(index, TEXT_SIZES.size),
                        ) { Text(label) }
                    }
                }
                Spacer(Modifier.height(16.dp))
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { onNotifications() }.padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Notifications")
                        Text(
                            "Daily schedule, reminders, and what others change",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.outline)
                }
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth().toggleable(value = background, onValueChange = { background = it }), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Sync in background")
                        Text(
                            "Stay reachable while the app is closed. Shows a permanent notification and uses some battery.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = background, onCheckedChange = null)
                }
                if (BuildConfig.DEBUG) {
                    Spacer(Modifier.height(16.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Direct connections (debug)", Modifier.weight(1f))
                        Switch(checked = direct, onCheckedChange = { direct = it })
                    }
                    TextButton(onClick = { error("Test crash from Settings (debug)") }, contentPadding = PaddingValues(0.dp)) {
                        Text("Crash now (debug)")
                    }
                }
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth().toggleable(value = offerCrashReports, onValueChange = { offerCrashReports = it }), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Offer to email crash reports")
                        Text(
                            "If the app closes unexpectedly, ask to email a report to the developer. You see it before it's sent.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = offerCrashReports, onCheckedChange = null)
                }
                if (crashReports.isNotEmpty()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = {
                            if (!CrashLog.email(context, crashReports)) Toast.makeText(context, "No email app found", Toast.LENGTH_LONG).show()
                            CrashLog.markSeen(context, app.identity)
                        }, contentPadding = PaddingValues(0.dp)) { Text("Email crash reports (${crashReports.size})") }
                        Spacer(Modifier.width(16.dp))
                        TextButton(onClick = {
                            CrashLog.clear(context)
                            crashReports = emptyList()
                        }) { Text("Delete them") }
                    }
                }
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth().toggleable(value = diagnostics, onValueChange = { diagnostics = it }), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Diagnostics")
                        Text(
                            "Shows where the app spends battery, on top of every screen: processor use, network and sync activity.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = diagnostics, onCheckedChange = null)
                }
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = { showLog = true }, contentPadding = PaddingValues(0.dp)) { Text("Connection log") }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (name.isNotBlank()) app.setName(name)
                // Only once actually changed: until then, the colour stays the one derived from this device.
                if (myColor != startColor) app.setColor(myColor)
                if (offerCrashReports && !app.identity.offerCrashReports) {
                    // Turning it on shouldn't immediately pop up old crashes; they're in Settings.
                    CrashLog.markSeen(context, app.identity)
                }
                app.identity.offerCrashReports = offerCrashReports
                if (direct != app.identity.directConnections) {
                    app.identity.directConnections = direct
                    app.sync.refresh()
                }
                if (background != app.identity.backgroundSync) app.setBackgroundSync(background)
                if (diagnostics != app.identity.diagnostics) app.setDiagnostics(diagnostics)
                onDismiss()
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Recent connection events with timings, to see why connecting is slow; Copy to send them on. */
@Composable
private fun ConnectionLogDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    var lines by remember { mutableStateOf(SyncLog.snapshot()) }
    // Keep it live while open.
    LaunchedEffect(Unit) {
        while (true) {
            delay(1_000)
            lines = SyncLog.snapshot()
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Connection log") },
        text = {
            if (lines.isEmpty()) {
                Text("Nothing yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                val listState = rememberLazyListState()
                LaunchedEffect(lines.size) { listState.scrollToItem(lines.size - 1) }
                LazyColumn(Modifier.heightIn(max = 420.dp), state = listState) {
                    items(lines) {
                        Text(it, style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        dismissButton = {
            TextButton(onClick = {
                val header = "${context.getString(R.string.brand_name)} ${BuildConfig.VERSION_NAME} on ${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE}"
                val clipboard = context.getSystemService(ClipboardManager::class.java)
                clipboard.setPrimaryClip(ClipData.newPlainText("Connection log", (listOf(header) + lines).joinToString("\n")))
                Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
            }) { Text("Copy") }
        },
    )
}

/** "Groceries: 3 to do" / "Family diary: 2 today". */
/** Progress while a newly joined list or diary is found and fetched. */
@Composable
internal fun JoiningBanner(list: TodoList, status: SyncStatus) {
    // The phone that shares also does a first sync with a newcomer; that side gets the share dialog's status instead.
    if (!list.isJoining && !(status.receiving && !list.createdHere)) return
    val what = when (list.kind) {
        SpaceKind.DIARY -> if (list.groupId != null) "calendar" else "diary"
        SpaceKind.GROUP -> "group"
        SpaceKind.LIST -> "list"
    }
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        LinearProgressIndicator(Modifier.fillMaxWidth(), trackColor = MaterialTheme.colorScheme.surfaceVariant)
        Spacer(Modifier.height(8.dp))
        Text(
            when {
                status.receiving || status.connecting || status.peerNames.isNotEmpty() || status.relayPeerNames.isNotEmpty() -> "Getting the $what…"
                !status.online -> "Waiting for an internet connection…"
                status.trackersOnline == 0 && status.relaysOnline == 0 -> "Connecting…"
                else -> "Looking for the other phone… ${stringResource(R.string.brand_name)} needs to be open there, or have Sync in background on."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun TodoList.summary(): String = if (isJoining) "Joining…" else if (archived) "Archived" else when (kind) {
    SpaceKind.DIARY -> {
        val today = java.time.LocalDate.now().toString()
        val count = items.values.count { !it.deleted && it.date == today }
        if (count == 0) "Diary · nothing today" else "Diary · $count today"
    }
    SpaceKind.LIST -> "${visibleItems.count { !it.checked && !it.heading }} to do"
    SpaceKind.GROUP -> "Group"
}

/** Name plus a List / Diary choice. */
@Composable
private fun NewSpaceDialog(onDismiss: () -> Unit, onCreate: (String, SpaceKind, ListTemplate) -> Unit) {
    var name by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf(SpaceKind.LIST) }
    var template by remember { mutableStateOf(BLANK_LIST) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (kind == SpaceKind.DIARY) "New diary" else "New list") },
        text = {
            Column {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    SpaceKind.entries.forEachIndexed { index, option ->
                        SegmentedButton(
                            selected = kind == option,
                            onClick = { kind = option },
                            shape = SegmentedButtonDefaults.itemShape(index, SpaceKind.entries.size),
                            icon = { Icon(option.icon, null, Modifier.size(18.dp)) },
                        ) { Text(option.label) }
                    }
                }
                if (kind == SpaceKind.LIST) {
                    Spacer(Modifier.height(12.dp))
                    TemplateChoice(template) { picked ->
                        // Suggest its name, unless the user has typed one of their own.
                        if (name.isBlank() || name == template.name) name = picked.name
                        template = picked
                    }
                }
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, singleLine = true)
            }
        },
        confirmButton = { TextButton(onClick = { onCreate(name, kind, if (kind == SpaceKind.LIST) template else BLANK_LIST) }, enabled = name.isNotBlank()) { Text("Create") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
internal fun TextPromptDialog(
    title: String,
    label: String,
    confirm: String,
    supporting: String? = null,
    initial: String = "",
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text(label) },
                supportingText = supporting?.let { { Text(it) } },
                singleLine = true,
            )
        },
        confirmButton = { TextButton(onClick = { onConfirm(text) }, enabled = text.isNotBlank()) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** e.g. "Adam · 5 min. ago" — who ticked the item and when. */
private fun tickedBy(item: Item, myDeviceId: String, names: Map<String, String>): String {
    val who = if (item.version.by == myDeviceId) "You" else names[item.version.by] ?: item.editor
    val now = System.currentTimeMillis()
    val time = if (now - item.version.ts < DateUtils.MINUTE_IN_MILLIS) {
        "just now"
    } else {
        DateUtils.getRelativeTimeSpanString(item.version.ts, now, DateUtils.MINUTE_IN_MILLIS, DateUtils.FORMAT_ABBREV_RELATIVE)
    }
    return "$who · $time"
}

@Composable
internal fun flatBar() = TopAppBarDefaults.topAppBarColors(
    containerColor = MaterialTheme.colorScheme.background,
    scrolledContainerColor = MaterialTheme.colorScheme.background,
)

/** Small status dot: accent when connected to another device, grey otherwise. */
@Composable
internal fun SyncDot(status: SyncStatus?) {
    val connected = !status?.peerNames.isNullOrEmpty() || !status?.relayPeerNames.isNullOrEmpty()
    Box(
        Modifier.size(8.dp).clip(CircleShape)
            .background(if (connected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline),
    )
}

internal const val HIGHLIGHT_MS = 2_500L

/** Text size choices: label and scale on top of the phone's own font size. */
private val TEXT_SIZES = listOf("Normal" to 1f, "Large" to 1.15f, "Larger" to 1.3f)

/** Plain-words sync state: who we're in sync with, or why not. How it's connected doesn't matter here. */
internal fun SyncStatus?.summary(): String {
    if (this == null) return "Connecting…"
    val people = online()
    return when {
        people.isNotEmpty() -> "In sync with ${joinNames(people)}"
        connecting -> "Connecting…"
        !online -> "Offline · changes will send later"
        trackersOnline == 0 && relaysOnline == 0 -> "Connecting…"
        else -> "Waiting for the others to come online"
    }
}

/** Everyone in sync with this phone right now, directly or through the relays. */
internal fun SyncStatus.online(): List<String> = (peerNames + relayPeerNames).distinct()

/** "Sarah", "Sarah and Tom", "Sarah, Tom and 2 others". */
internal fun joinNames(names: List<String>): String = when (names.size) {
    0 -> ""
    1 -> names[0]
    2 -> "${names[0]} and ${names[1]}"
    3 -> "${names[0]}, ${names[1]} and ${names[2]}"
    else -> "${names[0]}, ${names[1]} and ${names.size - 2} others"
}

private const val DELETE_EXPLAINED =
    "It's deleted from this phone, and %s is asked whether to delete theirs too. " +
        "To just get it out of the way, archive it instead: it stops syncing, which saves battery, and you can bring it back any time."

/** "Archived (3)": opens or closes the archived lists below it. */
@Composable
internal fun ArchivedHeader(count: Int, expanded: Boolean, onToggle: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.Inventory2, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text("Archived ($count)", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Icon(if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, if (expanded) "Hide archived" else "Show archived", tint = MaterialTheme.colorScheme.outline)
    }
}

/**
 * At the top of a list: if it's archived, where to bring it back from (the menu, deliberately not a
 * button here); if everything in it is ticked, an offer to archive it, since every list that's open
 * keeps connections going. Archiving closes the list, like the menu's Archive.
 */
@Composable
private fun ArchiveBanner(app: TwoDoApp, list: TodoList, onBack: () -> Unit) {
    if (!list.archived && !list.offerArchive) return
    Card(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = if (list.archived) 12.dp else 4.dp)) {
            Text(
                if (list.archived) "Archived on this phone, so it isn't syncing. To see the others' changes and send yours, bring it back from the ⋮ menu."
                else "All done! Archive this list? It stops syncing, which saves battery, and you can bring it back any time.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            if (!list.archived) Row(Modifier.align(Alignment.End)) {
                TextButton(onClick = { app.save { app.repo.declineArchiveOffer(list.id) } }) { Text("Not now") }
                TextButton(onClick = { onBack(); app.save { app.repo.archive(list.id) } }) { Text("Archive") }
            }
        }
    }
}

/**
 * Someone else archived or deleted a list this phone has: ask whether to do the same here. Keeping
 * it is fine, but it keeps syncing in the background, which uses battery.
 */
@Composable
private fun StatusRequestDialog(app: TwoDoApp, lists: Map<String, TodoList>) {
    // Dismissed without an answer: asked again next time the app opens.
    var later by remember { mutableStateOf(setOf<String>()) }
    val (list, request) = lists.values.firstNotNullOfOrNull { list ->
        list.pendingStatusRequest(app.identity.deviceId)?.takeIf { list.id !in later }?.let { list to it }
    } ?: return
    val names by app.sync.names.collectAsStateWithLifecycle()
    val who = names[request.version.by] ?: request.editor
    val deleted = request.text == STATUS_DELETED
    fun done() { later = later + list.id }
    AlertDialog(
        onDismissRequest = ::done,
        title = { Text("$who ${if (deleted) "deleted" else "archived"} “${list.name}”") },
        text = {
            Text(
                "Do the same on this phone? Archived lists stop syncing, which saves battery, and you can bring them back any time. " +
                    "If you keep it, it keeps syncing in the background, which uses some battery.",
            )
        },
        confirmButton = {
            Row {
                TextButton(onClick = { done(); app.save { app.repo.answerStatus(list.id, archive = false) } }) { Text("Keep") }
                TextButton(onClick = {
                    done()
                    if (list.groupId != null) app.save { app.repo.deleteFromGroup(list.id) } else app.sync.leave(list.id)
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = { done(); app.save { app.repo.answerStatus(list.id, archive = true) } }) { Text("Archive") }
            }
        },
    )
}

/** "Start from": a blank list, or one with headings ready (Groceries: Fresh, Bakery, Fridge…). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TemplateChoice(selected: ListTemplate, onSelect: (ListTemplate) -> Unit) {
    Text("Start from", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        LIST_TEMPLATES.forEach { template ->
            FilterChip(selected = template == selected, onClick = { onSelect(template) }, label = { Text(template.label) })
        }
    }
    if (selected.headings.isNotEmpty()) {
        Text(
            "Headings: ${selected.headings.joinToString(", ")}. Rename or remove them by tapping, or hide them from the ⋮ menu.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** An item travelling under its heading while the heading is dragged: how it looks in the list, but still. */
@Composable
private fun CarriedItem(item: Item) {
    Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = item.checked, onCheckedChange = null, modifier = Modifier.padding(12.dp))
        Text(
            item.text,
            Modifier.weight(1f).padding(vertical = 12.dp, horizontal = 4.dp),
            style = MaterialTheme.typography.bodyLarge,
            textDecoration = if (item.checked) TextDecoration.LineThrough else null,
            color = if (item.checked) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface,
        )
    }
}
