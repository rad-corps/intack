package app.intack.ui

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.window.DialogProperties

/** Settings › Text size, on top of the phone's own font size. Provided once at the root. */
val LocalTextScale = staticCompositionLocalOf { 1f }

/**
 * Applies [LocalTextScale]. Dialogs, menus and sheets open in their own window, which starts again from
 * the phone's font size, so the versions below apply it again inside; use them instead of Material's.
 */
@Composable
fun ScaledText(content: @Composable () -> Unit) {
    val density = LocalDensity.current
    CompositionLocalProvider(LocalDensity provides Density(density.density, density.fontScale * LocalTextScale.current), content = content)
}

private fun scaled(content: (@Composable () -> Unit)?): (@Composable () -> Unit)? = content?.let { { ScaledText(it) } }

@Composable
fun AlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: (@Composable () -> Unit)? = null,
    icon: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
    properties: DialogProperties = DialogProperties(),
) = androidx.compose.material3.AlertDialog(
    onDismissRequest = onDismissRequest,
    confirmButton = scaled(confirmButton)!!,
    modifier = modifier,
    dismissButton = scaled(dismissButton),
    icon = scaled(icon),
    title = scaled(title),
    text = scaled(text),
    properties = properties,
)

@Composable
fun DropdownMenu(expanded: Boolean, onDismissRequest: () -> Unit, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) =
    androidx.compose.material3.DropdownMenu(expanded, onDismissRequest, modifier) { ScaledText { content() } }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModalBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(),
    content: @Composable ColumnScope.() -> Unit,
) = androidx.compose.material3.ModalBottomSheet(onDismissRequest, modifier, sheetState) { ScaledText { content() } }

@Composable
fun DatePickerDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    dismissButton: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) = androidx.compose.material3.DatePickerDialog(
    onDismissRequest = onDismissRequest,
    confirmButton = scaled(confirmButton)!!,
    dismissButton = scaled(dismissButton),
    shape = DatePickerDefaults.shape,
) { ScaledText { content() } }
