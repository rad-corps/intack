@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package app.intack.ui

import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import app.intack.model.AuditEntry
import app.intack.model.Item
import app.intack.model.PhotoChoice
import kotlinx.coroutines.launch

/**
 * Change a list item: its words, its note, its photo, which section it's in, or delete it. For a heading: rename or delete
 * (deleting a heading leaves its items where they are).
 */
@Composable
internal fun ListItemSheet(
    item: Item,
    /** The list's headings, for moving the item into a section. */
    headings: List<Item>,
    /** The heading the item is under now, if any. */
    section: Item?,
    onDismiss: () -> Unit,
    onSave: (text: String, headingId: String?, note: String, photo: PhotoChoice) -> Unit,
    onDelete: () -> Unit,
    history: List<AuditEntry> = emptyList(),
    historyRow: @Composable (AuditEntry) -> Unit = {},
    /** The item's photo, if it has one and it has arrived. */
    image: ImageBitmap? = null,
) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var text by remember { mutableStateOf(item.text) }
    var headingId by remember { mutableStateOf(section?.id) }
    var note by remember { mutableStateOf(item.note ?: "") }
    var showHistory by remember { mutableStateOf(false) }
    var photo by remember { mutableStateOf<PhotoChoice>(PhotoChoice.Keep) }
    var preview by remember { mutableStateOf<ImageBitmap?>(null) }
    var loading by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        loading = true
        scope.launch {
            val bytes = preparePhoto(context, uri)
            loading = false
            if (bytes != null) {
                photo = PhotoChoice.Own(bytes)
                preview = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
            }
        }
    }
    val shown = when (photo) {
        PhotoChoice.None -> null
        is PhotoChoice.Own -> preview
        else -> image
    }
    val hasPhoto = photo is PhotoChoice.Own || (photo == PhotoChoice.Keep && item.image != null)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet) {
        Column(Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, bottom = 24.dp)) {
            Text(if (item.heading) "Edit heading" else "Edit item", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text(if (item.heading) "Heading" else "Item") },
                textStyle = MaterialTheme.typography.titleMedium,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                shape = RoundedCornerShape(12.dp),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            if (!item.heading) {
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Note") },
                    placeholder = { Text("More detail, for everyone") },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    shape = RoundedCornerShape(12.dp),
                    minLines = 2,
                    maxLines = 8,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (!item.heading) {
                Spacer(Modifier.height(12.dp))
                if (hasPhoto) {
                    Box(
                        Modifier.fillMaxWidth().heightIn(max = 240.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (shown != null) Image(shown, "Photo", Modifier.fillMaxWidth(), contentScale = ContentScale.FillWidth)
                        else Text("The photo is on its way", Modifier.padding(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                        enabled = !loading,
                    ) { Text(if (loading) "Getting the photo…" else if (hasPhoto) "Change photo" else "Add a photo") }
                    if (hasPhoto) TextButton(onClick = { photo = PhotoChoice.None; preview = null }) { Text("Remove photo") }
                }
            }
            if (!item.heading && headings.isNotEmpty()) {
                Text(
                    "Section",
                    Modifier.padding(top = 20.dp, bottom = 8.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = headingId == null, onClick = { headingId = null }, label = { Text("None") })
                    headings.forEach { h ->
                        FilterChip(selected = headingId == h.id, onClick = { headingId = h.id }, label = { Text(h.text) })
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = { if (text.isNotBlank()) onSave(text, headingId, note, photo) },
                enabled = text.isNotBlank(),
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            ) { Text("Save", style = MaterialTheme.typography.titleMedium) }
            TextButton(onClick = onDelete, modifier = Modifier.fillMaxWidth()) {
                Text(if (item.heading) "Delete heading (keeps its items)" else "Delete", color = MaterialTheme.colorScheme.error)
            }
            if (history.isNotEmpty()) {
                TextButton(onClick = { showHistory = !showHistory }, modifier = Modifier.fillMaxWidth()) {
                    Text(if (showHistory) "Hide history" else "History (${history.size})")
                }
                if (showHistory) history.forEach { historyRow(it) }
            }
        }
    }
}
