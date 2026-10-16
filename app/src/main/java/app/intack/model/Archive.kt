package app.intack.model

/**
 * Archiving and deleting lists. Every list someone has open keeps connections to the others, which
 * costs battery, so finished lists can be archived: kept on the phone, but not synced until brought back.
 *
 * Archiving and deleting are each phone's own choice, but whoever does it tells the others through one
 * special item, so each of them is asked whether to do the same. The item is stored as deleted, which
 * keeps it out of sight in versions of the app that don't know about it.
 */
const val LIST_STATUS_ITEM = "list-status"

const val STATUS_ARCHIVED = "archived"
const val STATUS_DELETED = "deleted"

/** The status item recording that [deviceName]'s phone archived or deleted the list ([status]). */
fun TodoList.statusItem(status: String, deviceId: String, deviceName: String, now: Long): Item =
    items[LIST_STATUS_ITEM]?.edited(deviceId, deviceName, now) { copy(text = status, deleted = true) }
        ?: Item(LIST_STATUS_ITEM, status, deleted = true, createdAt = 0, version = Version(now, deviceId), editor = deviceName)

/**
 * Someone else archived or deleted this list and this phone hasn't answered yet. Requests from before
 * this phone had the list don't count: whoever kept it then was already asked.
 */
fun TodoList.pendingStatusRequest(myDeviceId: String): Item? {
    if (kind != SpaceKind.LIST || archived) return null
    val item = items[LIST_STATUS_ITEM] ?: return null
    if (item.version.by == myDeviceId || item.text !in setOf(STATUS_ARCHIVED, STATUS_DELETED)) return null
    if (item.version.ts <= addedAt) return null
    return item.takeIf { statusAnswered == null || it.version > statusAnswered }
}

/** A list with things in it, all ticked. */
val TodoList.allDone: Boolean
    get() = kind == SpaceKind.LIST && visibleItems.filter { !it.heading }.let { it.isNotEmpty() && it.all { i -> i.checked } }

/** Everything's ticked and the user hasn't said "not now" since the last change: offer to archive. */
val TodoList.offerArchive: Boolean
    get() = allDone && !archived && (items.values.filter { it.id != LIST_STATUS_ITEM }.maxOfOrNull { it.version.ts } ?: 0) > archiveOfferDeclinedAt
