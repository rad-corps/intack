package app.twodo.ui

import android.content.ClipData
import android.content.ClipboardManager
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
import android.widget.Toast
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import app.twodo.model.BORDER_DASHED
import app.twodo.model.BORDER_DOTTED
import app.twodo.model.BORDER_SOLID
import app.twodo.model.BUILTIN_PREFIX
import app.twodo.model.FONT_HANDWRITTEN
import app.twodo.model.FONT_MONO
import app.twodo.model.FONT_SERIF
import app.twodo.model.ITEMS_CARDS
import app.twodo.model.ITEMS_LINES
import app.twodo.model.ITEMS_OUTLINED
import app.twodo.model.ITEMS_PLAIN
import app.twodo.model.ListStyle
import app.twodo.model.parseThemeCode
import app.twodo.model.themeCode
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
    var style by remember { mutableStateOf(saved.style) }
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
    val candidate = Look(themeId = themeId.takeIf { it != PHOTO_THEME || photo != null }, accent = accent, photoStrength = strength, style = style)
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
          // The preview stays put while the options below it scroll.
          Column {
            Text(intro, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
            LookPreview(preview, previewTitle)
            Spacer(Modifier.height(12.dp))
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                Label("Background")
                // One row for everything, so the preview above stays in sight; a labelled bookend starts each group.
                val strip = remember {
                    buildList {
                        fun category(it: BackgroundCategory) {
                            add(StripEntry.Bookend(it.name))
                            it.backgrounds.forEach { b -> add(StripEntry.Builtin(b)) }
                        }
                        add(StripEntry.Bookend("None"))
                        add(StripEntry.NoBackground)
                        // The soft colours first: the most usable.
                        category(BACKGROUNDS.first())
                        add(StripEntry.Bookend("Yours"))
                        add(StripEntry.OwnPhoto)
                        BACKGROUNDS.drop(1).forEach(::category)
                    }
                }
                // Open on the current choice.
                val stripState = rememberLazyListState(
                    initialFirstVisibleItemIndex = strip.indexOfFirst {
                        when (it) {
                            StripEntry.NoBackground -> saved.photoId == null
                            StripEntry.OwnPhoto -> saved.photoId != null && !isBuiltin(saved.photoId)
                            is StripEntry.Builtin -> it.background.photoId == saved.photoId
                            is StripEntry.Bookend -> false
                        }
                    }.let { (it - 1).coerceAtLeast(0) },
                )
                LazyRow(state = stripState, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(strip, key = { it.key }) { entry ->
                        when (entry) {
                            is StripEntry.Bookend -> Bookend(entry.name)
                            StripEntry.NoBackground -> BackgroundTile("None", null, selectedId == null) { choose(PhotoChoice.None, null) }
                            StripEntry.OwnPhoto -> BackgroundTile(
                                if (loading) "Loading…" else "Your photo",
                                if (selectedId == OWN_PHOTO) photo else null,
                                selectedId == OWN_PHOTO,
                                icon = Icons.Outlined.Image,
                            ) { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
                            is StripEntry.Builtin -> {
                                val background = entry.background
                                val thumbnail by produceState<ImageBitmap?>(null, background.id) { value = loadBackground(context, background.id, sample = 8) }
                                BackgroundTile(background.name, thumbnail, selectedId == background.photoId) {
                                    scope.launch { choose(PhotoChoice.Builtin(background.photoId), loadBackground(context, background.id)) }
                                }
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
                Spacer(Modifier.height(16.dp))

                StyleOptions(style, preview.theme.colors) { style = it }
                Spacer(Modifier.height(16.dp))

                // The code can name a built-in background (one of the user's own photos can't be written out).
                val builtin = selectedId?.takeIf { isBuiltin(it) }
                ThemeCodeBox(candidate.copy(photoId = builtin)) { result ->
                    // Background first: choosing one resets the colours to suit it, and the code's colours win.
                    val photoId = result.photoId
                    if (photoId != builtin && !(photoId == null && selectedId == OWN_PHOTO)) {
                        when {
                            photoId == null -> choose(PhotoChoice.None, null)
                            isBuiltin(photoId) -> scope.launch {
                                choose(PhotoChoice.Builtin(photoId), loadBackground(context, photoId.removePrefix(BUILTIN_PREFIX)))
                                themeId = result.themeId; accent = result.accent
                            }
                        }
                    }
                    themeId = result.themeId
                    accent = result.accent
                    strength = result.photoStrength
                    style = result.style
                }
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

/** What's in the row of backgrounds: the choices, and a bookend starting each group. */
private sealed interface StripEntry {
    val key: String

    class Bookend(val name: String) : StripEntry {
        override val key get() = "bookend:$name"
    }

    data object NoBackground : StripEntry {
        override val key get() = "none"
    }

    data object OwnPhoto : StripEntry {
        override val key get() = OWN_PHOTO
    }

    class Builtin(val background: Background) : StripEntry {
        override val key get() = background.photoId
    }
}

/** A thin upright divider with the group's name running up it, as tall as the tiles. */
@Composable
private fun Bookend(name: String) {
    Row(Modifier.height(100.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(1.dp).fillMaxHeight().background(MaterialTheme.colorScheme.outlineVariant))
        Text(
            name,
            Modifier.padding(start = 2.dp).upright(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
        )
    }
}

/** Turns content a quarter-turn anticlockwise, so it reads bottom to top, taking up its rotated space. */
private fun Modifier.upright(): Modifier = layout { measurable, _ ->
    val placeable = measurable.measure(Constraints())
    layout(placeable.height, placeable.width) {
        placeable.place(-(placeable.width - placeable.height) / 2, (placeable.width - placeable.height) / 2)
    }
}.rotate(-90f)

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
    Box(Modifier.fillMaxWidth().height(170.dp).clip(shape).border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape).background(colors.background)) {
        look.photo?.let {
            Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            Box(Modifier.fillMaxSize().background(colors.background.copy(alpha = veil(look.strength))))
        }
        MaterialTheme(colorScheme = colors, typography = typographyIn(look.style.font)) {
            Column(Modifier.padding(vertical = 10.dp)) {
                Text(title, Modifier.padding(horizontal = 14.dp), style = MaterialTheme.typography.titleMedium, color = colors.onBackground)
                Text(
                    "Dairy",
                    Modifier.padding(start = 14.dp, top = 6.dp, bottom = 2.dp),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.secondary,
                )
                PreviewRow("Milk", true, look.style, colors)
                PreviewRow("Cheese", false, look.style, colors)
            }
        }
    }
}

@Composable
private fun PreviewRow(text: String, checked: Boolean, style: ResolvedStyle, colors: androidx.compose.material3.ColorScheme) {
    Row(
        Modifier.fillMaxWidth().listRow(style, Color.Transparent, colors.surfaceContainerHigh).padding(horizontal = 14.dp, vertical = (style.spacing / 3).coerceAtLeast(2.dp)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (checked) Icons.Outlined.CheckBox else Icons.Outlined.CheckBoxOutlineBlank, null,
            tint = if (checked) colors.primary else colors.outline, modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = if (checked) colors.outline else colors.onBackground)
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

/** Page and item colours on offer: light ones, then dark. */
private val SURFACES: List<Long> = listOf(
    0xFFFFFFFF, 0xFFF7F3EA, 0xFFFFF4DC, 0xFFFCE4EC, 0xFFE8F4FD, 0xFFE6F4EA, 0xFFEDE7F6,
    0xFF2A2A2A, 0xFF1B1B2F, 0xFF0D1B2E, 0xFF14211A, 0xFF2D1B33, 0xFF000000,
)

/** Spacing, item style, borders, shadows, extra colours and font. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StyleOptions(style: ListStyle, colors: androidx.compose.material3.ColorScheme, onChange: (ListStyle) -> Unit) {
    Label("Style")
    SubLabel("Spacing")
    val spacings = listOf("Compact" to 6, "Comfortable" to null, "Roomy" to 20)
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        spacings.forEachIndexed { i, (name, value) ->
            SegmentedButton(
                selected = style.spacing == value || (value == null && style.spacing !in setOf(6, 20)),
                onClick = { onChange(style.copy(spacing = value)) },
                shape = SegmentedButtonDefaults.itemShape(i, spacings.size),
            ) { Text(name) }
        }
    }
    SubLabel("Items")
    val items = style.items ?: ITEMS_PLAIN
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(ITEMS_PLAIN to "Plain", ITEMS_LINES to "Lines", ITEMS_CARDS to "Cards", ITEMS_OUTLINED to "Outlined").forEach { (value, name) ->
            FilterChip(selected = items == value, onClick = { onChange(style.copy(items = value.takeIf { it != ITEMS_PLAIN })) }, label = { Text(name) })
        }
    }
    val boxed = items == ITEMS_CARDS || items == ITEMS_OUTLINED
    if (boxed) {
        SubLabel("Corners")
        Slider(value = (style.corners ?: 12).toFloat(), onValueChange = { onChange(style.copy(corners = it.toInt())) }, valueRange = 0f..28f)
        SubLabel("Border")
        val width = style.borderWidth ?: if (items == ITEMS_OUTLINED) 1 else 0
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = width == 0, onClick = { onChange(style.copy(borderWidth = 0)) }, label = { Text("None") })
            listOf(BORDER_SOLID to "Solid", BORDER_DASHED to "Dashed", BORDER_DOTTED to "Dotted").forEach { (value, name) ->
                FilterChip(
                    selected = width > 0 && (style.borderStyle ?: BORDER_SOLID) == value,
                    onClick = { onChange(style.copy(borderStyle = value.takeIf { it != BORDER_SOLID }, borderWidth = width.coerceAtLeast(1))) },
                    label = { Text(name) },
                )
            }
        }
        if (width > 0) {
            Slider(value = width.toFloat(), onValueChange = { onChange(style.copy(borderWidth = it.toInt())) }, valueRange = 1f..6f, steps = 4)
            ColourDots(style.borderColor, colors.outline, ACCENTS + SURFACES) { onChange(style.copy(borderColor = it)) }
        }
    }
    if (items == ITEMS_CARDS) {
        SubLabel("Shadow")
        Slider(value = (style.shadow ?: 2).toFloat(), onValueChange = { onChange(style.copy(shadow = it.toInt())) }, valueRange = 0f..16f)
    }
    SubLabel("Item background")
    ColourDots(style.itemColor, if (items == ITEMS_CARDS) colors.surfaceContainerHigh else Color.Transparent, SURFACES + ACCENTS) { onChange(style.copy(itemColor = it)) }
    SubLabel("Headings (secondary colour)")
    ColourDots(style.secondary, colors.primary, ACCENTS) { onChange(style.copy(secondary = it)) }
    SubLabel("Highlights (tertiary colour)")
    ColourDots(style.tertiary, colors.primary, ACCENTS) { onChange(style.copy(tertiary = it)) }
    SubLabel("Page colour")
    ColourDots(style.background, null, SURFACES) { onChange(style.copy(background = it)) }
    SubLabel("Font")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(null to "Default", FONT_SERIF to "Serif", FONT_MONO to "Mono", FONT_HANDWRITTEN to "Handwritten").forEach { (value, name) ->
            FilterChip(selected = style.font == value, onClick = { onChange(style.copy(font = value)) }, label = { Text(name) })
        }
    }
}

@Composable
private fun SubLabel(text: String) {
    Text(text, Modifier.padding(top = 10.dp, bottom = 4.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** "Auto" (the theme's own, shown as [auto]; none if null) and then each of [options]. */
@Composable
private fun ColourDots(selected: Long?, auto: Color?, options: List<Long>, onPick: (Long?) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        item { AccentDot(null, auto ?: MaterialTheme.colorScheme.surfaceVariant, selected == null) { onPick(null) } }
        items(options.distinct()) { argb -> AccentDot(argb, Color(argb), selected == argb) { onPick(argb) } }
    }
}

/**
 * The look as theme code (see ThemeCode.kt): read it, copy it to share, or edit it and apply — like
 * customising a page on the early web, but it can only ever set the options above.
 */
@Composable
private fun ThemeCodeBox(look: Look, onApply: (Look) -> Unit) {
    val context = LocalContext.current
    var editing by remember { mutableStateOf<String?>(null) }
    var problems by remember { mutableStateOf<List<String>>(emptyList()) }
    // Changing an option with the buttons while the code is open: show the code for that instead,
    // rather than keep text that would undo it on Apply.
    val written = themeCode(look)
    var editedFrom by remember { mutableStateOf(written) }
    if (editing != null && written != editedFrom) {
        editing = written
        editedFrom = written
        problems = emptyList()
    }
    val mono = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
    Label("Theme code")
    Text(
        "For tinkerers: write or paste a theme, CSS-style. Copy yours to share it with someone.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(8.dp))
    val code = editing
    if (code == null) {
        val current = themeCode(look).ifEmpty { "/* All defaults. Try:  items: cards;  border: 2px dashed hotpink; */" }
        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(10.dp)) {
            Text(current, style = mono)
        }
        Row {
            TextButton(onClick = { editing = written; editedFrom = written; problems = emptyList() }) { Text("Edit") }
            TextButton(onClick = {
                context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Theme", themeCode(look)))
                Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
            }) { Text("Copy") }
        }
    } else {
        OutlinedTextField(value = code, onValueChange = { editing = it }, textStyle = mono, minLines = 5, modifier = Modifier.fillMaxWidth())
        problems.forEach { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
        Row {
            TextButton(onClick = {
                val result = parseThemeCode(code, look, THEMES.map { it.id }.toSet() + PHOTO_THEME)
                problems = result.problems
                editedFrom = themeCode(result.look)
                onApply(result.look)
                if (result.problems.isEmpty()) editing = null
            }) { Text("Apply") }
            TextButton(onClick = { editing = null; problems = emptyList() }) { Text("Done") }
        }
    }
}

