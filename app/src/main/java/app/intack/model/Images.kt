package app.intack.model

private const val IMAGE_PREFIX = "img:"

/**
 * A piece of a list item's photo: [PHOTO_PART_CHARS] base64 characters, in an item of its own so each
 * fits in one relay event. Pieces are stored as deleted, so versions without photos keep them out of sight.
 */
fun imagePartId(imageId: String, index: Int) = "$IMAGE_PREFIX$imageId:$index"

val Item.isImagePart: Boolean get() = id.startsWith(IMAGE_PREFIX)

/** The pieces of the photo with [imageId] in this list. */
fun TodoList.imageParts(imageId: String): List<Item> = items.values.filter { it.id.startsWith("$IMAGE_PREFIX$imageId:") }

/** [item]'s photo as base64, once every piece has arrived; null if it has none or it's incomplete. */
fun TodoList.imageBase64(item: Item): String? {
    val imageId = item.image ?: return null
    if (item.imageParts <= 0) return null
    return (0 until item.imageParts).map { items[imagePartId(imageId, it)]?.text?.takeIf { t -> t.isNotEmpty() } ?: return null }
        .joinToString("")
}

/** Returns this item showing photo [imageId] in [parts] pieces (null removes it); doesn't create a content version. */
fun Item.withImage(imageId: String?, parts: Int, deviceId: String, now: Long): Item =
    copy(image = imageId, imageParts = if (imageId == null) 0 else parts, imageVersion = Version(maxOf(now, (imageVersion?.ts ?: 0) + 1), deviceId))
