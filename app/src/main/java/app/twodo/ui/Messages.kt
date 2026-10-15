package app.twodo.ui

import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import app.twodo.TwoDoApp
import app.twodo.model.Item
import app.twodo.model.TodoList
import app.twodo.model.chatRefs
import app.twodo.model.formatDay
import app.twodo.model.messages
import app.twodo.sync.Notifications
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * The group's messages: a conversation for everyone in it, newest at the bottom, with a box to write
 * in. The first message sent starts it.
 */
@Composable
internal fun MessagesTab(app: TwoDoApp, group: TodoList, lists: Map<String, TodoList>, names: Map<String, String>, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val me = app.identity.deviceId
    val messages = group.messages(lists)
    val chatIds = group.chatRefs.mapNotNull { it.spaceId }.filter { it in lists }
    var text by rememberSaveable(group.id) { mutableStateOf("") }

    // On screen, so they're read: clear the badge and any notification for them.
    LaunchedEffect(chatIds, messages.lastOrNull()?.id) {
        chatIds.forEach {
            app.repo.markRead(it)
            Notifications.clearChanges(context, it)
        }
    }

    Column(modifier.fillMaxWidth()) {
        if (messages.isEmpty()) {
            Column(Modifier.weight(1f)) { EmptyState("No messages yet.\nEveryone in ${group.name} will see what you write here.") }
        } else {
            // Newest at the bottom, and the list starts there.
            val newestFirst = messages.asReversed()
            LazyColumn(
                Modifier.weight(1f).fillMaxWidth(),
                reverseLayout = true,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.Bottom),
            ) {
                itemsIndexed(newestFirst, key = { _, m -> m.id }) { i, message ->
                    val older = newestFirst.getOrNull(i + 1)
                    val day = message.localDay()
                    // Who wrote it, above the first of a run of messages from the same person.
                    val showName = message.version.by != me && (older == null || older.version.by != message.version.by || older.localDay() != day)
                    Column {
                        if (older == null || older.localDay() != day) DayLabel(day)
                        MessageBubble(message, mine = message.version.by == me, showName = showName, name = names[message.version.by] ?: message.editor)
                    }
                }
            }
        }
        // At the bottom, within thumb reach, and lifted above the keyboard while typing.
        Row(Modifier.fillMaxWidth().imePadding().padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Text("Message ${group.name}") },
                maxLines = 5,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.weight(1f),
            )
            IconButton(
                onClick = {
                    val sending = text
                    text = ""
                    scope.launch { app.repo.sendMessage(group.id, sending) }
                },
                enabled = text.isNotBlank(),
            ) { Icon(Icons.AutoMirrored.Filled.Send, "Send") }
        }
    }
}

private fun Item.localDay(): LocalDate = Instant.ofEpochMilli(createdAt).atZone(ZoneId.systemDefault()).toLocalDate()

/** "Today", "Yesterday", or the day, between messages from different days. */
@Composable
private fun DayLabel(day: LocalDate) {
    val today = LocalDate.now()
    // On a small tile of its own, so it reads over a background photo.
    Box(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp), contentAlignment = Alignment.Center) {
        Text(
            when (day) {
                today -> "Today"
                today.minusDays(1) -> "Yesterday"
                else -> formatDay(day, today)
            },
            Modifier.background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(8.dp)).padding(horizontal = 10.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** One message: yours on the right, others' on the left with their name (in their colour) inside. */
@Composable
private fun MessageBubble(message: Item, mine: Boolean, showName: Boolean, name: String) {
    val context = LocalContext.current
    val time = remember(message.createdAt) { DateFormat.getTimeFormat(context).format(message.createdAt) }
    Column(Modifier.fillMaxWidth().padding(top = if (showName) 6.dp else 0.dp), horizontalAlignment = if (mine) Alignment.End else Alignment.Start) {
        Column(
            Modifier.widthIn(max = 300.dp)
                .background(
                    if (mine) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                    RoundedCornerShape(16.dp),
                )
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            if (showName) Text(name, style = MaterialTheme.typography.labelLarge, color = personColor(message.version.by))
            Text(
                message.text,
                style = MaterialTheme.typography.bodyLarge,
                color = if (mine) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
            )
            Text(
                time,
                Modifier.align(Alignment.End),
                style = MaterialTheme.typography.labelSmall,
                color = if (mine) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
