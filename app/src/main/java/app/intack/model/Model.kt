package app.intack.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.LocalDate

/** Identifies one edit of an item: when it was made and by which device. Ordered by time, then device. */
@Serializable
data class Version(val ts: Long, val by: String) : Comparable<Version> {
    override fun compareTo(other: Version) = compareValuesBy(this, other, { it.ts }, { it.by })
}

/** A to-do entry. Every edit replaces the whole item with a new [version]. */
@Serializable
data class Item(
    val id: String,
    val text: String,
    val checked: Boolean = false,
    /** Deletes are tombstones so they sync like any other edit. */
    val deleted: Boolean = false,
    val createdAt: Long,
    val version: Version,
    /** Display name of the device that made [version]. */
    val editor: String,
    /** Versions this one descends from, newest first, capped at [HISTORY_LIMIT]. */
    val history: List<Version> = emptyList(),
    /**
     * Manual sort position. Synced as its own last-writer-wins value (stamped by [posVersion]) so that
     * moving an item never conflicts with someone ticking it. Orders items within their section, and
     * headings among headings (see Sections.kt).
     */
    val pos: Double? = null,
    val posVersion: Version? = null,
    /** The heading this item is under (its id); null above every heading. Moves with [pos]. */
    val section: String? = null,
    /** Diary entries: the day (ISO yyyy-MM-dd) and optional time (HH:mm). Null for list items. */
    val date: String? = null,
    val time: String? = null,
    /**
     * Group entries: the space this entry stands for (its id, secret and kind). Joining the group joins
     * every space it lists. Null for list items and diary entries.
     */
    val spaceId: String? = null,
    val spaceSecret: String? = null,
    val spaceKind: SpaceKind? = null,
    /**
     * Group entries: the space is the group's messages (see [SpaceKind.CHAT]). Its [spaceKind] says LIST,
     * so versions without messages show it as a list instead of failing on it.
     */
    val chat: Boolean = false,
    /**
     * A section heading in a list ("Dairy"), not something to tick. Items below it, up to the next
     * heading, are in its section. Older versions ignore this and show it as an item.
     */
    val heading: Boolean = false,
    /**
     * More detail about a list item, as free text. Synced as its own last-writer-wins value (stamped by
     * [noteVersion]), like [pos], so writing a note never conflicts with someone ticking the item, and a
     * version that doesn't know notes can't wipe one out by passing the item on without it.
     */
    val note: String? = null,
    val noteVersion: Version? = null,
    /**
     * A photo of the item: its id and how many pieces it travels in (see Images.kt). Synced as its own
     * last-writer-wins value (stamped by [imageVersion]), like [note].
     */
    val image: String? = null,
    val imageParts: Int = 0,
    val imageVersion: Version? = null,
) {
    val position: Double get() = pos ?: createdAt.toDouble()
    val localDate: LocalDate? get() = date?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
}

/** One recorded change: who did what, when. The log is shared and only ever grows. */
@Serializable
data class AuditEntry(
    /** Derived from the item version, so the same edit always has the same id. */
    val id: String,
    val itemId: String,
    val ts: Long,
    /** Device id of whoever made the change. */
    val by: String,
    /** Their name at the time. */
    val byName: String,
    /** e.g. "ticked Milk", "added Dentist on Fri 9 Oct at 15:00". */
    val description: String,
)

@Serializable
enum class SpaceKind {
    @SerialName("list") LIST,
    @SerialName("diary") DIARY,
    /** A set of people sharing a calendar and lists; its items are the spaces in it. */
    @SerialName("group") GROUP,
    /**
     * A group's messages: each item is one message. Only ever this phone's own record of a space; a
     * group announces it as a LIST with [Item.chat] set, because versions without messages would fail
     * on a kind they don't know.
     */
    @SerialName("chat") CHAT,
}

@Serializable
data class TodoList(
    val id: String,
    val name: String,
    /** Shared secret from the QR code; derives the room id and the encryption key. */
    val secret: String,
    val items: Map<String, Item> = emptyMap(),
    /** Local only: concurrent versions that lost a conflict, so they're never re-applied. */
    val superseded: Map<String, Set<Version>> = emptyMap(),
    /** Local only: other devices seen on this list (device id -> name). */
    val members: Map<String, String> = emptyMap(),
    /** Local only: this device created the list (rather than joining it). */
    val createdHere: Boolean = false,
    val kind: SpaceKind = SpaceKind.LIST,
    /** Local only: this phone's colour theme for the list (null: follow the app's theme). */
    val themeId: String? = null,
    /** Local only: this device has had a complete copy of the list from someone (or created it). */
    val fullSynced: Boolean = false,
    /** Local only: newest relay event seen (unix seconds), to fetch only what's new next time. */
    val relaySince: Long = 0,
    /** Every recorded change, by [AuditEntry.id]. Synced. */
    val audit: Map<String, AuditEntry> = emptyMap(),
    /** Local only: the group this space belongs to, if any. */
    val groupId: String? = null,
    /** Local only: archived on this phone — kept, but not synced (see Archive.kt). */
    val archived: Boolean = false,
    /** Local only: the latest archive/delete request from someone else that this phone has answered. */
    val statusAnswered: Version? = null,
    /** Local only: when this phone got the list (0 for lists from before this was recorded). */
    val addedAt: Long = 0,
    /** Local only, for a group this phone joined: when, until "… joined the group" is on its calendar. */
    val joinNoteDue: Long? = null,
    /** Local only: joined here, and the others haven't yet been left a hello on the relays saying so. */
    val joinHelloDue: Boolean = false,
    /** Local only: when the user last said "not now" to archiving it with everything ticked. */
    val archiveOfferDeclinedAt: Long = 0,
    /** Local only: this phone's look for a list outside any group (null: [themeId] and the app's background). */
    val look: Look? = null,
    /** Local only: show the items without their headings on this phone. */
    val hideHeadings: Boolean = false,
    /** Local only: items record their section; false for lists saved before they did (see [sectionsFromPositions]). */
    val sectioned: Boolean = false,
    /**
     * Local only. For messages: the newest message this phone has shown (its createdAt), for the unread count.
     * For lists: the newest change shown (its version time), so lists with changes not yet seen stand out.
     */
    val readUpTo: Long = 0,
) {
    /** Items in the order they're shown: unsorted ones, then each heading with its items. */
    val visibleItems: List<Item>
        get() = sectionedOrder(items.values.filter { !it.deleted })
}

/**
 * How a group looks, shared by everyone in it (stored as JSON in the group's [GROUP_LOOK_ITEM]).
 * The photo travels as [photoParts] base64 pieces in items `photo:<photoId>:<n>`.
 */
@Serializable
data class Look(
    /** Colour theme; null follows each phone's app theme. "photo" takes the colours from the photo. */
    val themeId: String? = null,
    val photoId: String? = null,
    val photoParts: Int = 0,
    /** How strongly the photo shows through, 0 (barely) to 1 (fully). */
    val photoStrength: Float = 0.45f,
    /** Accent colour (ARGB) overriding the theme's; null keeps the theme's. */
    val accent: Long? = null,
    /** Spacing, item style, borders, shadows, fonts and extra colours (see ThemeCode.kt). */
    val style: ListStyle = ListStyle(),
)

/** A change of background in a look editor. */
sealed interface PhotoChoice {
    /** Leave the background as it is. */
    data object Keep : PhotoChoice
    data object None : PhotoChoice
    /** A background that comes with the app (`builtin:<id>`). */
    data class Builtin(val photoId: String) : PhotoChoice
    /** A photo of the user's own, as a backdrop-sized JPEG. */
    class Own(val jpeg: ByteArray) : PhotoChoice
}

/** Just joined and nothing received yet. */
val TodoList.isJoining: Boolean get() = !createdHere && members.isEmpty() && items.isEmpty()

/** Two devices edited the same item concurrently; [winner] (the later edit) was kept. */
data class Conflict(
    val listId: String,
    val listName: String,
    val winner: Item,
    val loser: Item,
    /** True when this device's edit was the one overridden. */
    val localLost: Boolean,
)
