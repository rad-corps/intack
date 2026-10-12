package app.twodo.ui

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import app.twodo.TwoDoApp
import app.twodo.model.BUILTIN_PREFIX
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Backgrounds that come with the app (assets/backgrounds, credits in CREDITS.md there). A look refers
 * to one by [photoId] `builtin:<id>`, so choosing one for a group sends just that name to everyone.
 */
class Background(val id: String, val name: String) {
    val photoId: String get() = BUILTIN_PREFIX + id
}

class BackgroundCategory(val name: String, val backgrounds: List<Background>)

private fun soft(vararg names: String) = names.map { Background("abstract-${it.lowercase()}", it) }

val BACKGROUNDS = listOf(
    // First: calm behind any list, and they suit both light and dark colours.
    BackgroundCategory(
        "Soft colours",
        soft("Dawn", "Blush", "Peach", "Butter", "Sand", "Sage", "Mint", "Sky", "Mist", "Lilac") +
            soft("Lagoon", "Ocean", "Moss", "Dusk", "Plum", "Ember"),
    ),
    BackgroundCategory(
        "Home",
        listOf(Background("cork", "Corkboard"), Background("notepaper", "Notepaper"), Background("grid-paper", "Grid paper"), Background("linen", "Linen")),
    ),
    BackgroundCategory(
        "Nature",
        listOf(Background("sea", "Sea"), Background("forest", "Forest"), Background("lake", "Lake"), Background("wildflowers", "Wildflowers"), Background("sky", "Sunset sky")),
    ),
    BackgroundCategory(
        "Seasons",
        listOf(Background("spring", "Spring"), Background("summer", "Summer"), Background("autumn", "Autumn"), Background("winter", "Winter")),
    ),
)

fun isBuiltin(photoId: String?): Boolean = photoId?.startsWith(BUILTIN_PREFIX) == true

/** The photo a look refers to: a built-in background, or a photo saved on this phone. */
suspend fun loadLookPhoto(app: TwoDoApp, photoId: String?): ImageBitmap? = when {
    photoId == null -> null
    isBuiltin(photoId) -> loadBackground(app, photoId.removePrefix(BUILTIN_PREFIX))
    else -> app.repo.photoFile(photoId).takeIf { it.exists() }?.let { loadPhoto(it) }
}

/** A built-in background; with [sample] > 1, a smaller copy for thumbnails. Null if this version doesn't have it. */
suspend fun loadBackground(context: Context, id: String, sample: Int = 1): ImageBitmap? = withContext(Dispatchers.IO) {
    runCatching {
        context.assets.open("backgrounds/$id.webp").use { input ->
            BitmapFactory.decodeStream(input, null, BitmapFactory.Options().apply { inSampleSize = sample })?.asImageBitmap()
        }
    }.getOrNull()
}
