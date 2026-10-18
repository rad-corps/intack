package app.intack.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.intack.IntackApp
import app.intack.model.Item
import app.intack.model.TodoList
import app.intack.model.imageParts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * [item]'s photo, once all its pieces have arrived (null until then, or if it has none). With [sample] > 1,
 * a smaller copy for thumbnails.
 */
@Composable
fun rememberItemImage(app: IntackApp, list: TodoList, item: Item, sample: Int = 1): ImageBitmap? {
    // How many pieces are here, so the photo is put together as soon as the last one arrives.
    val arrived = remember(list.items, item.image) { item.image?.let { id -> list.imageParts(id).count { it.text.isNotEmpty() } } ?: 0 }
    val image by produceState<ImageBitmap?>(null, item.image, arrived >= item.imageParts, sample) {
        value = app.repo.itemImage(list, item)?.let { file ->
            withContext(Dispatchers.IO) {
                runCatching { BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })?.asImageBitmap() }.getOrNull()
            }
        }
    }
    return image
}

/** A list row's small photo; tap to see it whole. Shows a placeholder while the photo is on its way. */
@Composable
fun ItemThumbnail(app: IntackApp, list: TodoList, item: Item) {
    val thumb = rememberItemImage(app, list, item, sample = 4)
    var open by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(8.dp)
    Box(
        Modifier.padding(start = 4.dp).size(44.dp).clip(shape).background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(enabled = thumb != null, onClickLabel = "Show photo") { open = true },
        contentAlignment = Alignment.Center,
    ) {
        if (thumb != null) Image(thumb, "Photo", Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else Icon(Icons.Outlined.Image, "Photo on its way", tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(20.dp))
    }
    if (open) PhotoViewer(app, list, item) { open = false }
}

/** The item's photo filling the screen; tap anywhere to close it. */
@Composable
fun PhotoViewer(app: IntackApp, list: TodoList, item: Item, onDismiss: () -> Unit) {
    val image = rememberItemImage(app, list, item)
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black).clickable(onClickLabel = "Close", onClick = onDismiss), contentAlignment = Alignment.Center) {
            if (image != null) Image(image, item.text, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
        }
    }
}
