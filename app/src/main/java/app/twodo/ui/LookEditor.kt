package app.twodo.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.CheckBoxOutlineBlank
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.twodo.TwoDoApp
import app.twodo.model.Look
import app.twodo.model.TodoList
import app.twodo.model.hasSharedLook
import app.twodo.model.PhotoChoice
import app.twodo.R
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.runtime.produceState
import app.twodo.model.sharedLook
import android.graphics.BitmapFactory
import kotlinx.coroutines.launch

/** Edits a group's look: everyone in it sees the same. */
@Composable
internal fun GroupLookEditorDialog(app: TwoDoApp, group: TodoList, onDismiss: () -> Unit) {
    val appThemeId by app.appTheme.collectAsStateWithLifecycle()
    val current = rememberLook(app, group, themeById(appThemeId))
    LookEditorDialog(
        app = app,
        title = "Look of ${group.name}",
        intro = "Everyone in ${group.name} sees the same look.",
        previewTitle = group.name,
        saved = if (group.hasSharedLook) group.sharedLook else Look(),
        savedPhoto = current.photo.takeIf { group.hasSharedLook },
        followLabel = "Each phone's own",
        saveLabel = "Save for everyone",
        onDismiss = onDismiss,
    ) { look, photo -> app.save { app.repo.setGroupLook(group.id, look, photo) } }
}

/** Edits this phone's look for a list or diary outside any group. */
@Composable
internal fun ListLookEditorDialog(app: TwoDoApp, list: TodoList, onDismiss: () -> Unit) {
    val appThemeId by app.appTheme.collectAsStateWithLifecycle()
    val current = rememberLook(app, list, themeById(appThemeId))
    val own = list.look
    LookEditorDialog(
        app = app,
        title = "Look of ${list.name}",
        intro = "Only on this phone.",
        previewTitle = list.name,
        saved = own ?: Look(themeId = list.themeId),
        savedPhoto = current.photo.takeIf { own != null },
        followLabel = "The app's",
        saveLabel = "Save",
        onDismiss = onDismiss,
    ) { look, photo -> app.save { app.repo.setListLook(list.id, look, photo) } }
}

/** Edits the app's own look: its colours, and a background for screens outside groups. */
@Composable
internal fun AppLookEditorDialog(app: TwoDoApp, onDismiss: () -> Unit) {
    val appThemeId by app.appTheme.collectAsStateWithLifecycle()
    val appLook by app.appLook.collectAsStateWithLifecycle()
    val current = rememberLook(app, null, themeById(appThemeId))
    LookEditorDialog(
        app = app,
        title = "App look",
        intro = "Groups can choose their own.",
        previewTitle = stringResource(R.string.brand_name),
        saved = appLook.copy(themeId = appLook.themeId ?: appThemeId),
        savedPhoto = current.photo,
        followLabel = null,
        saveLabel = "Save",
        onDismiss = onDismiss,
    ) { look, photo ->
        // Plain colours are the app theme; only "from the photo" is kept in the look itself.
        val themeId = look.themeId?.takeIf { it != PHOTO_THEME } ?: appThemeId
        app.save { app.setAppLook(themeId, look.copy(themeId = look.themeId?.takeIf { it == PHOTO_THEME }), photo) }
    }
}

/**
 * Edits a look — background, colours and highlight — with a live preview. [followLabel] names the
 * "no colours of its own" choice, if there is one; [onSave] gets the look and the background change.
 */
@Composable
private fun LookEditorDialog(
    app: TwoDoApp,
    title: String,
    intro: String,
    previewTitle: String,
    saved: Look,
    savedPhoto: ImageBitmap?,
    followLabel: String?,
    saveLabel: String,
    onDismiss: () -> Unit,
    onSave: (Look, PhotoChoice) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val appThemeId by app.appTheme.collectAsStateWithLifecycle()
    val appTheme = themeById(appThemeId)

    var themeId by remember { mutableStateOf(saved.themeId) }
    var accent by remember { mutableStateOf(saved.accent) }
    var strength by remember { mutableFloatStateOf(saved.photoStrength) }
    var choice by remember { mutableStateOf<PhotoChoice>(PhotoChoice.Keep) }
    // The chosen background, once loaded (a built-in one loads from the app, a picked one is decoded).
    var chosenImage by remember { mutableStateOf<ImageBitmap?>(null) }
    var loading by remember { mutableStateOf(false) }

    val photo = if (choice == PhotoChoice.Keep) savedPhoto else chosenImage
    val selectedId = when (val c = choice) {
        PhotoChoice.Keep -> saved.photoId?.let { if (isBuiltin(it)) it else OWN_PHOTO }
        PhotoChoice.None -> null
        is PhotoChoice.Builtin -> c.photoId
        is PhotoChoice.Own -> OWN_PHOTO
    }
    val photoTheme = remember(photo) { photo?.let { photoTheme(it) } }
    val photoAccents = remember(photo) { photo?.let { photoAccents(it) }.orEmpty() }
    val candidate = Look(themeId = themeId.takeIf { it != PHOTO_THEME || photo != null }, accent = accent, photoStrength = strength)
    val preview = resolve(candidate, appTheme, photo, photoTheme)

    /** A new background usually wants its own colours. */
    fun choose(next: PhotoChoice, image: ImageBitmap?) {
        choice = next
        chosenImage = image
        if (image != null) {
            themeId = PHOTO_THEME
            accent = null
        } else if (themeId == PHOTO_THEME) {
            themeId = followLabel?.let { null } ?: appThemeId
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        loading = true
        scope.launch {
            val bytes = preparePhoto(context, uri)
            loading = false
            if (bytes != null) choose(PhotoChoice.Own(bytes), BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap())
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(intro, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                LookPreview(preview, previewTitle)
                Spacer(Modifier.height(16.dp))

                Label("Background")
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item { BackgroundTile("None", null, selectedId == null) { choose(PhotoChoice.None, null) } }
                    item {
                        BackgroundTile(if (loading) "Loading…" else "Your photo", if (selectedId == OWN_PHOTO) photo else null, selectedId == OWN_PHOTO, icon = Icons.Outlined.Image) {
                            picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        }
                    }
                }
                BACKGROUNDS.forEach { category ->
                    Text(category.name, Modifier.padding(top = 10.dp, bottom = 4.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(category.backgrounds, key = { it.id }) { background ->
                            val thumbnail by produceState<ImageBitmap?>(null, background.id) { value = loadBackground(context, background.id, sample = 8) }
                            BackgroundTile(background.name, thumbnail, selectedId == background.photoId) {
                                scope.launch { choose(PhotoChoice.Builtin(background.photoId), loadBackground(context, background.id)) }
                            }
                        }
                    }
                }
                if (photo != null) {
                    Spacer(Modifier.height(10.dp))
                    Text("How much it shows", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Slider(value = strength, onValueChange = { strength = it }, valueRange = 0.1f..1f)
                }
                Spacer(Modifier.height(8.dp))

                Label("Colours")
                val options = buildList<Pair<String?, AppTheme>> {
                    if (followLabel != null) add(null to appTheme)
                    if (photoTheme != null) add(PHOTO_THEME to photoTheme)
                    THEMES.forEach { add(it.id to it) }
                }
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(options, key = { it.first ?: "own" }) { (id, theme) ->
                        Box(Modifier.width(88.dp)) {
                            ThemeSwatch(
                                theme = theme,
                                label = when (id) {
                                    null -> followLabel.orEmpty()
                                    PHOTO_THEME -> "From the background"
                                    else -> theme.name
                                },
                                selected = id == candidate.themeId,
                                icon = null,
                                onClick = { themeId = id },
                            )
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))

                Label("Highlight colour")
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    // "Auto": the colours' own accent.
                    val auto = resolve(candidate.copy(accent = null), appTheme, photo, photoTheme).theme.colors.primary
                    item { AccentDot(null, auto, accent == null) { accent = null } }
                    items((photoAccents + ACCENTS).distinct()) { argb -> AccentDot(argb, Color(argb), accent == argb) { accent = argb } }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onDismiss()
                onSave(candidate, choice)
            }, enabled = !loading) { Text(saveLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private const val OWN_PHOTO = "own"

/** One choice of background: a portrait thumbnail with its name under it. */
@Composable
private fun BackgroundTile(name: String, image: ImageBitmap?, selected: Boolean, icon: ImageVector? = null, onClick: () -> Unit) {
    val shape = RoundedCornerShape(10.dp)
    Column(Modifier.width(64.dp).clip(RoundedCornerShape(10.dp)).clickable(onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(64.dp, 100.dp).clip(shape).background(MaterialTheme.colorScheme.surfaceVariant)
                .border(if (selected) 3.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, shape),
            contentAlignment = Alignment.Center,
        ) {
            if (image != null) Image(image, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            else if (icon != null) Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            if (selected) {
                Box(Modifier.size(24.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Check, "Selected", tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(16.dp))
                }
            }
        }
        Text(name, style = MaterialTheme.typography.labelSmall, maxLines = 1, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp))
    }
}

@Composable
private fun Label(text: String) {
    Text(text, Modifier.padding(bottom = 8.dp), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
}

/** A small mock-up of the group's home in the candidate look. */
@Composable
private fun LookPreview(look: ResolvedLook, title: String) {
    val colors = look.theme.colors
    val shape = RoundedCornerShape(16.dp)
    Box(Modifier.fillMaxWidth().height(150.dp).clip(shape).border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape).background(colors.background)) {
        look.photo?.let {
            Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            Box(Modifier.fillMaxSize().background(colors.background.copy(alpha = veil(look.strength))))
        }
        Column(Modifier.padding(14.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = colors.onBackground)
            Spacer(Modifier.height(10.dp))
            PreviewRow("Milk", true, colors.primary, colors.outline)
            PreviewRow("Bread", false, colors.outline, colors.onBackground)
            Spacer(Modifier.height(6.dp))
            Box(Modifier.clip(RoundedCornerShape(10.dp)).background(colors.primaryContainer).padding(horizontal = 12.dp, vertical = 6.dp)) {
                Text("Swimming 3:30 PM", style = MaterialTheme.typography.labelLarge, color = colors.onPrimaryContainer)
            }
        }
    }
}

@Composable
private fun PreviewRow(text: String, checked: Boolean, tint: Color, textColor: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
        Icon(if (checked) Icons.Outlined.CheckBox else Icons.Outlined.CheckBoxOutlineBlank, null, tint = tint, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = textColor)
    }
}

@Composable
private fun AccentDot(argb: Long?, color: Color, selected: Boolean, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(40.dp).clip(CircleShape).background(color)
                .border(if (selected) 3.dp else 1.dp, if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outlineVariant, CircleShape)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) Icon(Icons.Default.Check, "Selected", tint = Color.White, modifier = Modifier.size(18.dp))
        }
        Text(
            if (argb == null) "Auto" else "",
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
