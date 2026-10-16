package app.intack.data

import android.util.Log
import app.intack.model.AuditEntry
import app.intack.model.BLANK_LIST
import app.intack.model.calendarRef
import app.intack.model.Conflict
import app.intack.model.GROCERIES
import app.intack.model.GROUP_LOOK_ITEM
import app.intack.model.GROUP_NAME_ITEM
import app.intack.model.statusItem
import app.intack.model.STATUS_DELETED
import app.intack.model.STATUS_ARCHIVED
import app.intack.model.LIST_STATUS_ITEM
import app.intack.model.Look
import app.intack.model.PhotoChoice
import app.intack.model.isPhotoPart
import app.intack.model.photoBase64
import app.intack.model.photoPartId
import app.intack.model.sharedLook
import app.intack.model.splitPhoto
import app.intack.model.toJson
import app.intack.model.Invite
import app.intack.model.Item
import app.intack.model.ListKeys
import app.intack.model.ListTemplate
import app.intack.model.SpaceKind
import app.intack.model.TodoList
import app.intack.model.Version
import app.intack.model.auditEntryFor
import app.intack.model.chatRefs
import app.intack.model.edited
import app.intack.model.headingMove
import app.intack.model.itemMove
import app.intack.model.itemToSection
import app.intack.model.merge
import app.intack.model.mergeAudit
import app.intack.model.moved
import app.intack.model.newHeadingPosition
import app.intack.model.newItemPosition
import app.intack.model.planGroup
import app.intack.model.sectionsFromPositions
import app.intack.model.syncedGroupName
import app.intack.model.withNote
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.time.LocalDate
import java.util.Base64
import java.util.UUID

/** A local edit that needs to be sent to peers, with its audit entry if it's worth recording. */
/** [secret] lets the edit still go out if the list was archived or deleted straight after. */
data class LocalEdit(val listId: String, val item: Item, val audit: AuditEntry?, val secret: String)

/** Result of applying a peer's data: items that changed (with their previous state) and new audit entries. */
data class RemoteResult(val changes: List<Pair<Item?, Item>>, val newAudit: List<AuditEntry>)

/** All lists on this device, one JSON file each. The single source of truth for UI and sync. */
class ListRepository(private val dir: File, private val identity: Identity) {
    private val json = Json { ignoreUnknownKeys = true }
    private val mutex = Mutex()
    /** Serialises file writes and deletes. */
    private val fileMutex = Mutex()
    private val writer = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val unsaved = mutableSetOf<String>()
    private var pendingWrite: Job? = null

    private val _lists = MutableStateFlow(load())
    val lists: StateFlow<Map<String, TodoList>> = _lists.asStateFlow()

    private val _localEdits = MutableSharedFlow<LocalEdit>(extraBufferCapacity = 256)
    val localEdits: SharedFlow<LocalEdit> = _localEdits

    private val _photos = MutableStateFlow(0)

    /** Ticks whenever a group photo has been saved, so screens can show it. */
    val photos: StateFlow<Int> = _photos.asStateFlow()

    private val _conflicts = MutableSharedFlow<Conflict>(extraBufferCapacity = 64)
    val conflicts: SharedFlow<Conflict> = _conflicts

    suspend fun createList(name: String, kind: SpaceKind = SpaceKind.LIST, template: ListTemplate = BLANK_LIST): TodoList =
        mutex.withLock { newSpace(name, kind, template = template) }

    private suspend fun newSpace(name: String, kind: SpaceKind, groupId: String? = null, template: ListTemplate = BLANK_LIST, birthText: String? = null): TodoList {
        val fallback = when (kind) {
            SpaceKind.DIARY -> "Calendar"
            SpaceKind.GROUP -> "Family"
            SpaceKind.LIST -> "My list"
            SpaceKind.CHAT -> "Messages"
        }
        val list = TodoList(
            UUID.randomUUID().toString(), name.trim().ifEmpty { fallback }, ListKeys.newSecret(),
            createdHere = true, kind = kind, groupId = groupId, addedAt = System.currentTimeMillis(), sectioned = true,
        ).let { if (kind == SpaceKind.DIARY) it.copy(items = birthEntry(birthText ?: "${it.name} created").let { e -> mapOf(e.id to e) }) else it }
            .let { if (kind == SpaceKind.LIST) it.copy(items = templateHeadings(template).associateBy { h -> h.id }) else it }
        save(list)
        return list
    }

    /** A new list's headings from its template, in order. */
    private fun templateHeadings(template: ListTemplate): List<Item> {
        val now = System.currentTimeMillis()
        return template.headings.mapIndexed { i, text ->
            Item(
                id = UUID.randomUUID().toString(), text = text, createdAt = now, version = Version(now, identity.deviceId),
                editor = identity.deviceName, pos = i + 1.0, posVersion = Version(now, identity.deviceId), heading = true,
            )
        }
    }

    /** A new calendar's first entry: when it was made ("Calendar created", or "Group: Family created"). */
    private fun birthEntry(text: String): Item = eventEntry(UUID.randomUUID().toString(), text, System.currentTimeMillis())

    /** A calendar entry recording something that happened at [at], on that day and at that time. */
    private fun eventEntry(id: String, text: String, at: Long): Item {
        val now = System.currentTimeMillis()
        val then = java.time.Instant.ofEpochMilli(at).atZone(java.time.ZoneId.systemDefault())
        return Item(
            id = id,
            text = text,
            createdAt = now,
            version = Version(now, identity.deviceId),
            editor = identity.deviceName,
            date = then.toLocalDate().toString(),
            time = "%02d:%02d".format(then.hour, then.minute),
        )
    }

    /**
     * Puts "Sam joined the group" on the calendar of each group this phone joined, once the group's
     * calendar is known (its entries merge one by one, so a partial copy is fine; writing it also wakes the
     * others' phones). Only the joining phone writes it, so it appears once; its id is fixed per
     * person, so a second try (or joining again) changes that entry rather than adding another.
     */
    suspend fun addDueJoinNotes() {
        val due = _lists.value.values.filter { it.kind == SpaceKind.GROUP && it.joinNoteDue != null }
        for (group in due) {
            val calendar = group.calendarRef?.spaceId?.let { _lists.value[it] } ?: continue
            val at = group.joinNoteDue ?: continue
            putLocal(calendar.id, eventEntry("joined-${identity.deviceId}", "${identity.deviceName} joined the group", at))
            mutex.withLock { _lists.value[group.id]?.let { save(it.copy(joinNoteDue = null)) } }
        }
    }

    /**
     * Starts a group, ready to invite people to. Its calendar is the diary [calendarId] if given (someone
     * who already has one), otherwise a new one; [listIds] are existing lists to bring in. With none, it
     * starts with a shopping list.
     */
    suspend fun createGroup(name: String, calendarId: String? = null, listIds: List<String> = emptyList()): TodoList = withContext(NonCancellable) { createGroupLocked(name, calendarId, listIds) }

    private suspend fun createGroupLocked(name: String, calendarId: String?, listIds: List<String>): TodoList = mutex.withLock {
        val group = newSpace(name, SpaceKind.GROUP)
        putGroupItem(group.id, nameItem(group.name))
        val existing = (listOfNotNull(calendarId) + listIds).mapNotNull { _lists.value[it] }.filter { it.kind != SpaceKind.GROUP && it.groupId == null }
        if (existing.none { it.kind == SpaceKind.DIARY }) putGroupItem(group.id, spaceItem(newSpace("Calendar", SpaceKind.DIARY, group.id, birthText = "Group: ${group.name} created")))
        existing.forEach { list ->
            save(list.copy(groupId = group.id))
            putGroupItem(group.id, spaceItem(list))
        }
        if (existing.none { it.kind == SpaceKind.LIST }) putGroupItem(group.id, spaceItem(newSpace("Shopping", SpaceKind.LIST, group.id, GROCERIES)))
        _lists.value.getValue(group.id)
    }

    /** Adds a new list to the group, for everyone in it. */
    suspend fun addToGroup(groupId: String, name: String, kind: SpaceKind = SpaceKind.LIST, template: ListTemplate = BLANK_LIST): TodoList? =
        withContext(NonCancellable) { addToGroupLocked(groupId, name, kind, template) }

    private suspend fun addToGroupLocked(groupId: String, name: String, kind: SpaceKind, template: ListTemplate): TodoList? = mutex.withLock {
        if (_lists.value[groupId]?.kind != SpaceKind.GROUP) return null
        val space = newSpace(name, kind, groupId, template)
        putGroupItem(groupId, spaceItem(space))
        space
    }

    /** Moves an existing list or diary into the group; everyone in the group gets it. */
    suspend fun moveIntoGroup(groupId: String, listId: String): Unit = withContext(NonCancellable) { moveIntoGroupLocked(groupId, listId) }

    private suspend fun moveIntoGroupLocked(groupId: String, listId: String): Unit = mutex.withLock {
        if (_lists.value[groupId]?.kind != SpaceKind.GROUP) return
        val list = _lists.value[listId] ?: return
        save(list.copy(groupId = groupId))
        putGroupItem(groupId, spaceItem(list))
    }

    /** Takes a space out of its group for everyone in it, and removes it from this phone. */
    suspend fun deleteFromGroup(listId: String): Unit = withContext(NonCancellable) { deleteFromGroupLocked(listId) }

    private suspend fun deleteFromGroupLocked(listId: String): Unit = mutex.withLock {
        val list = _lists.value[listId] ?: return
        val groupId = list.groupId ?: return
        val ref = _lists.value[groupId]?.items?.values?.firstOrNull { it.spaceId == listId && !it.deleted }
        if (ref != null) {
            val deleted = ref.edited(identity.deviceId, identity.deviceName, System.currentTimeMillis()) { copy(deleted = true) }
            putGroupItem(groupId, deleted)
            // The same request in the list itself, with the same version, so each phone asks once.
            if (list.kind == SpaceKind.LIST) {
                putStatus(list, list.statusItem(STATUS_DELETED, identity.deviceId, identity.deviceName, deleted.version.ts)) { this }
            }
        }
        _lists.value -= listId
        fileMutex.withLock { withContext(Dispatchers.IO) { file(listId).delete() } }
    }

    /** Renames a space; in a group the new name reaches everyone. */
    suspend fun rename(listId: String, name: String): Unit = withContext(NonCancellable) { renameLocked(listId, name) }

    private suspend fun renameLocked(listId: String, name: String): Unit = mutex.withLock {
        val trimmed = name.trim().ifEmpty { return }
        val list = _lists.value[listId] ?: return
        if (list.name == trimmed) return
        save(list.copy(name = trimmed))
        val now = System.currentTimeMillis()
        when {
            list.kind == SpaceKind.GROUP -> {
                val item = list.items[GROUP_NAME_ITEM]
                putGroupItem(listId, item?.edited(identity.deviceId, identity.deviceName, now) { copy(text = trimmed, deleted = false) } ?: nameItem(trimmed))
            }
            list.groupId != null -> {
                val ref = _lists.value[list.groupId]?.items?.values?.firstOrNull { it.spaceId == listId && !it.deleted } ?: return
                putGroupItem(list.groupId, ref.edited(identity.deviceId, identity.deviceName, now) { copy(text = trimmed) })
            }
        }
    }

    /**
     * Sets the group's look for everyone in it, changing its background as [photo] says. A built-in
     * background is just a name; a photo of the user's own is sent to everyone in pieces.
     */
    suspend fun setGroupLook(groupId: String, look: Look, photo: PhotoChoice): Unit = withContext(NonCancellable) { setGroupLookLocked(groupId, look, photo) }

    private suspend fun setGroupLookLocked(groupId: String, look: Look, photo: PhotoChoice): Unit = mutex.withLock {
        val group = _lists.value[groupId]?.takeIf { it.kind == SpaceKind.GROUP } ?: return
        val now = System.currentTimeMillis()
        val old = group.sharedLook
        var next = look.copy(photoId = old.photoId, photoParts = old.photoParts)
        val photoJpeg = (photo as? PhotoChoice.Own)?.jpeg
        if (photo != PhotoChoice.Keep) {
            // Clear the old photo's pieces so they don't linger in everyone's copy.
            group.items.values.filter { it.isPhotoPart && !it.deleted }.forEach { part ->
                putGroupItem(groupId, part.edited(identity.deviceId, identity.deviceName, now) { copy(text = "", deleted = true) })
            }
            next = next.copy(photoId = null, photoParts = 0)
        }
        if (photoJpeg != null) {
            val photoId = UUID.randomUUID().toString().take(8)
            val parts = splitPhoto(Base64.getEncoder().encodeToString(photoJpeg))
            parts.forEachIndexed { i, text ->
                putGroupItem(groupId, Item(photoPartId(photoId, i), text, createdAt = now, version = Version(now, identity.deviceId), editor = identity.deviceName))
            }
            withContext(Dispatchers.IO) { photoFile(photoId).apply { parentFile?.mkdirs() }.writeBytes(photoJpeg) }
            _photos.value++
            next = next.copy(photoId = photoId, photoParts = parts.size)
        }
        if (photo is PhotoChoice.Builtin) next = next.copy(photoId = photo.photoId, photoParts = 0)
        val current = _lists.value.getValue(groupId).items[GROUP_LOOK_ITEM]
        val item = current?.edited(identity.deviceId, identity.deviceName, now) { copy(text = next.toJson(), deleted = false) }
            ?: Item(GROUP_LOOK_ITEM, next.toJson(), createdAt = 0, version = Version(now, identity.deviceId), editor = identity.deviceName)
        putGroupItem(groupId, item)
    }

    /** Sets this phone's look for a list outside any group. */
    suspend fun setListLook(listId: String, look: Look, photo: PhotoChoice): Unit = withContext(NonCancellable) {
        val current = _lists.value[listId] ?: return@withContext
        val photoId = choosePhoto(photo, (current.look ?: Look()).photoId)
        mutex.withLock {
            val list = _lists.value[listId] ?: return@withLock
            save(list.copy(look = look.copy(photoId = photoId, photoParts = 0)))
        }
    }

    /** The background a look on this phone ends up with: kept, none, built in, or a newly saved photo. */
    suspend fun choosePhoto(photo: PhotoChoice, current: String?): String? = when (photo) {
        PhotoChoice.Keep -> current
        PhotoChoice.None -> null
        is PhotoChoice.Builtin -> photo.photoId
        is PhotoChoice.Own -> {
            val photoId = UUID.randomUUID().toString().take(8)
            withContext(Dispatchers.IO) { photoFile(photoId).apply { parentFile?.mkdirs() }.writeBytes(photo.jpeg) }
            _photos.value++
            photoId
        }
    }

    /** Where a group photo is kept on this phone, once it has arrived. */
    fun photoFile(photoId: String): File = File(File(dir.parentFile, "looks"), "$photoId.jpg")

    /** Writes the group's photo to [photoFile] once all its pieces are here (caller holds [mutex]). */
    private suspend fun assemblePhoto(group: TodoList) {
        val look = group.sharedLook
        val photoId = look.photoId ?: return
        val file = photoFile(photoId)
        if (file.exists()) return
        val base64 = group.photoBase64(look) ?: return
        val bytes = runCatching { Base64.getDecoder().decode(base64) }.getOrNull() ?: return
        withContext(Dispatchers.IO) {
            file.parentFile?.mkdirs()
            val tmp = File(file.parentFile, "${file.name}.tmp")
            tmp.writeBytes(bytes)
            tmp.renameTo(file)
        }
        _photos.value++
    }

    private fun nameItem(name: String): Item {
        val now = System.currentTimeMillis()
        return Item(GROUP_NAME_ITEM, name, createdAt = 0, version = Version(now, identity.deviceId), editor = identity.deviceName)
    }

    /** The group entry standing for [space]. */
    private fun spaceItem(space: TodoList): Item {
        val now = System.currentTimeMillis()
        return Item(
            id = space.id, text = space.name, createdAt = now, version = Version(now, identity.deviceId), editor = identity.deviceName,
            spaceId = space.id, spaceSecret = space.secret,
            // Messages go out as a list, marked as the chat, so versions without messages can cope (see SpaceKind.CHAT).
            spaceKind = if (space.kind == SpaceKind.CHAT) SpaceKind.LIST else space.kind, chat = space.kind == SpaceKind.CHAT,
        )
    }

    /**
     * Sends a message to the group's messages, starting them if nobody has yet. Returns false if there's
     * no such group.
     */
    suspend fun sendMessage(groupId: String, text: String): Boolean {
        val trimmed = text.trim().ifEmpty { return true }
        val chatId = withContext(NonCancellable) {
            mutex.withLock {
                val group = _lists.value[groupId]?.takeIf { it.kind == SpaceKind.GROUP } ?: return@withLock null
                group.chatRefs.firstNotNullOfOrNull { it.spaceId?.takeIf { id -> id in _lists.value } }
                    ?: newSpace("Messages", SpaceKind.CHAT, groupId).also { putGroupItem(groupId, spaceItem(it)) }.id
            }
        } ?: return false
        putLocal(chatId) {
            val now = System.currentTimeMillis()
            Item(id = UUID.randomUUID().toString(), text = trimmed, createdAt = now, version = Version(now, identity.deviceId), editor = identity.deviceName)
        }
        markRead(chatId)
        return true
    }

    /** This phone has shown every message in [chatId] so far. */
    suspend fun markRead(chatId: String): Unit = mutex.withLock {
        val chat = _lists.value[chatId] ?: return
        val newest = chat.items.values.maxOfOrNull { it.createdAt } ?: return
        if (newest > chat.readUpTo) save(chat.copy(readUpTo = newest))
    }

    /** Saves a group item made here and sends it to the group (caller holds [mutex]). */
    private suspend fun putGroupItem(groupId: String, item: Item) {
        val group = _lists.value[groupId] ?: return
        save(group.copy(items = group.items + (item.id to item)))
        _localEdits.tryEmit(LocalEdit(groupId, item, null, group.secret))
    }

    /**
     * Brings this phone's spaces in line with the group: joins spaces others added, renames, and removes
     * spaces others deleted (caller holds [mutex]).
     */
    private suspend fun followGroup(groupId: String) {
        val group = _lists.value[groupId] ?: return
        group.syncedGroupName?.let { if (it != group.name) save(group.copy(name = it)) }
        assemblePhoto(group)
        val plan = planGroup(group, _lists.value)
        plan.join.forEach { save(TodoList(it.listId, it.name, it.secret, kind = it.kind, groupId = groupId, addedAt = System.currentTimeMillis(), sectioned = true)) }
        plan.update.forEach { save(it) }
        // Someone deleted a space from the group. Lists stay here, out of the group, and this phone is
        // asked whether to delete its copy too (the deleter's phone sends the same request in the list
        // itself; this covers it not having arrived yet, or an older app that doesn't send it).
        plan.remove.forEach { id ->
            val local = _lists.value[id] ?: return@forEach
            val ref = group.items.values.firstOrNull { it.spaceId == id && it.deleted }
            if (local.kind == SpaceKind.LIST && ref != null) {
                val status = local.items[LIST_STATUS_ITEM]
                val request = if (status != null && status.version >= ref.version) status
                else Item(LIST_STATUS_ITEM, STATUS_DELETED, deleted = true, createdAt = 0, version = ref.version, editor = ref.editor)
                save(local.copy(groupId = null, items = local.items + (request.id to request)))
            } else {
                _lists.value -= id
                fileMutex.withLock { withContext(Dispatchers.IO) { file(id).delete() } }
            }
        }
    }

    /** Adds a shared list from an invite; returns the existing one if already joined. */
    suspend fun joinList(invite: Invite): TodoList = mutex.withLock {
        _lists.value[invite.listId]
            ?: System.currentTimeMillis().let { now ->
                TodoList(
                    invite.listId, invite.name, invite.secret, kind = invite.kind, addedAt = now, sectioned = true,
                    joinNoteDue = now.takeIf { invite.kind == SpaceKind.GROUP },
                ).also { save(it) }
            }
    }

    /** Shows or hides the list's headings on this phone. */
    suspend fun setHideHeadings(listId: String, hide: Boolean): Unit = mutex.withLock {
        val list = _lists.value[listId] ?: return
        save(list.copy(hideHeadings = hide))
    }

    /** Archives the list on this phone (it stops syncing) and asks the others whether to do the same. */
    suspend fun archive(listId: String): Unit = withContext(NonCancellable) {
        mutex.withLock {
            val list = _lists.value[listId] ?: return@withLock
            putStatus(list, list.statusItem(STATUS_ARCHIVED, identity.deviceId, identity.deviceName, System.currentTimeMillis())) { this }
        }
        delay(STOP_DELAY_MS) // let the news go out before the list stops syncing
        mutex.withLock { _lists.value[listId]?.let { save(it.copy(archived = true)) } }
    }

    /** Brings an archived list back: it syncs again and catches up. */
    suspend fun unarchive(listId: String): Unit = mutex.withLock {
        val list = _lists.value[listId] ?: return
        save(list.copy(archived = false))
    }

    /** Tells the others this phone is deleting the list, so they're asked whether to delete theirs. */
    suspend fun markDeleted(listId: String): Unit = mutex.withLock {
        val list = _lists.value[listId]?.takeIf { it.kind == SpaceKind.LIST } ?: return
        putStatus(list, list.statusItem(STATUS_DELETED, identity.deviceId, identity.deviceName, System.currentTimeMillis())) { this }
    }

    /**
     * This phone's answer to someone else archiving or deleting the list: archive it too, or keep it
     * syncing. (Deleting it is [deleteFromGroup] or leaving it.)
     */
    suspend fun answerStatus(listId: String, archive: Boolean): Unit = mutex.withLock {
        val list = _lists.value[listId] ?: return
        save(list.copy(archived = archive || list.archived, statusAnswered = list.items[LIST_STATUS_ITEM]?.version))
    }

    /** "Not now" to archiving a finished list: asked again once something changes. */
    suspend fun declineArchiveOffer(listId: String): Unit = mutex.withLock {
        val list = _lists.value[listId] ?: return
        save(list.copy(archiveOfferDeclinedAt = System.currentTimeMillis()))
    }

    /** Records this phone's status [item] in [list] (applying [also]) and sends it (caller holds [mutex]). */
    private suspend fun putStatus(list: TodoList, item: Item, also: TodoList.() -> TodoList) {
        val entry = auditEntryFor(list.items[item.id], item)
        val audit = if (entry != null) list.audit + (entry.id to entry) else list.audit
        save(list.copy(items = list.items + (item.id to item), audit = audit).also())
        _localEdits.tryEmit(LocalEdit(list.id, item, entry, list.secret))
    }

    /** Removes the list from this device only; peers keep their copies. Leaving a group removes its spaces too. */
    suspend fun removeList(listId: String) = mutex.withLock {
        val inGroup = _lists.value.values.filter { it.groupId == listId }.map { it.id }
        for (id in inGroup + listId) {
            _lists.value -= id
            fileMutex.withLock { withContext(Dispatchers.IO) { file(id).delete() } }
        }
    }

    /** Adds an item (or, with [heading], a section heading) at the bottom of the list. */
    suspend fun addItem(listId: String, text: String, heading: Boolean = false) {
        val trimmed = text.trim().ifEmpty { return }
        putLocal(listId) { list ->
            val now = System.currentTimeMillis()
            Item(
                id = UUID.randomUUID().toString(),
                text = trimmed,
                createdAt = now,
                version = Version(now, identity.deviceId),
                editor = identity.deviceName,
                pos = if (heading) newHeadingPosition(list.visibleItems) else newItemPosition(list.visibleItems),
                posVersion = Version(now, identity.deviceId),
                heading = heading,
            )
        }
    }

    /** Changes an item's (or heading's) text. Does nothing if it's the same. */
    suspend fun editText(listId: String, itemId: String, text: String) {
        val trimmed = text.trim().ifEmpty { return }
        putLocal(listId) { list ->
            val item = list.items[itemId]?.takeIf { it.text != trimmed } ?: return@putLocal null
            item.edited(identity.deviceId, identity.deviceName, System.currentTimeMillis()) { copy(text = trimmed) }
        }
    }

    /** Moves an item to the end of [headingId]'s section, or to the unsorted items above every heading with null. */
    suspend fun moveToSection(listId: String, itemId: String, headingId: String?) = putLocal(listId) { list ->
        val item = list.items[itemId]?.takeIf { !it.heading } ?: return@putLocal null
        val (section, pos) = itemToSection(list.visibleItems, itemId, headingId)
        item.moved(pos, identity.deviceId, System.currentTimeMillis(), section)
    }

    /** Moves an item so it ends up at [toIndex] among the visible items, in the section it lands in. */
    suspend fun moveItem(listId: String, itemId: String, toIndex: Int) = putLocal(listId) { list ->
        val item = list.items[itemId]?.takeIf { !it.heading } ?: return@putLocal null
        val (section, pos) = itemMove(list.visibleItems, itemId, toIndex)
        item.moved(pos, identity.deviceId, System.currentTimeMillis(), section)
    }

    /** Moves a heading, and with it its section (see [headingMove]). */
    suspend fun moveSection(listId: String, headingId: String, dropIndex: Int) = putLocal(listId) { list ->
        val heading = list.items[headingId]?.takeIf { it.heading } ?: return@putLocal null
        val pos = headingMove(list.visibleItems, headingId, dropIndex)?.takeIf { it != heading.position } ?: return@putLocal null
        heading.moved(pos, identity.deviceId, System.currentTimeMillis())
    }

    /** Adds a diary entry on [date], optionally at [time] ("HH:mm"). */
    suspend fun addEntry(listId: String, date: LocalDate, text: String, time: String? = null) {
        val trimmed = text.trim().ifEmpty { return }
        putLocal(listId) {
            val now = System.currentTimeMillis()
            Item(
                id = UUID.randomUUID().toString(),
                text = trimmed,
                createdAt = now,
                version = Version(now, identity.deviceId),
                editor = identity.deviceName,
                date = date.toString(),
                time = time,
            )
        }
    }

    /** Changes a diary entry's text, day and/or time. Does nothing if nothing changed. */
    suspend fun editEntry(listId: String, itemId: String, text: String, date: LocalDate, time: String?) {
        val trimmed = text.trim().ifEmpty { return }
        putLocal(listId) { list ->
            val item = list.items[itemId] ?: return@putLocal null
            if (item.text == trimmed && item.date == date.toString() && item.time == time) return@putLocal null
            item.edited(identity.deviceId, identity.deviceName, System.currentTimeMillis()) {
                copy(text = trimmed, date = date.toString(), time = time)
            }
        }
    }

    suspend fun setChecked(listId: String, itemId: String, checked: Boolean) =
        editItem(listId, itemId) { copy(checked = checked) }

    /** Sets a list item's note; blank removes it. Does nothing if it's the same. */
    suspend fun setNote(listId: String, itemId: String, note: String) = putLocal(listId) { list ->
        list.items[itemId]?.takeIf { (it.note ?: "") != note.trim() }?.withNote(note, identity.deviceId, System.currentTimeMillis())
    }

    suspend fun deleteItem(listId: String, itemId: String) =
        editItem(listId, itemId) { copy(deleted = true) }

    /**
     * Applies items and audit entries from a peer. Returns what changed locally, so it can be
     * forwarded and announced.
     */
    suspend fun applyRemote(listId: String, items: List<Item>, audit: List<AuditEntry> = emptyList()): RemoteResult =
        mutex.withLock {
            val list = _lists.value[listId] ?: return RemoteResult(emptyList(), emptyList())
            val result = list.merge(items)
            val (merged, newAudit) = result.list.mergeAudit(audit)
            // Synced data can be re-fetched, so its writes are batched: a big sync arrives in many chunks.
            if (merged != list) saveSoon(merged)
            if (merged.kind == SpaceKind.GROUP && result.accepted.isNotEmpty()) followGroup(listId)
            // Two people archiving at once isn't a conflict worth mentioning.
            result.conflicts.filter { it.winner.id != LIST_STATUS_ITEM }.forEach { _conflicts.tryEmit(it) }
            RemoteResult(result.accepted.map { list.items[it.id] to it }, newAudit)
        }

    /** Records that this device now has a complete copy of the list. */
    suspend fun markFullSynced(listId: String): Unit = mutex.withLock {
        val list = _lists.value[listId] ?: return
        if (!list.fullSynced) saveSoon(list.copy(fullSynced = true))
    }

    /** Remembers the newest relay event seen, so the next start fetches only what's new. */
    suspend fun setRelaySince(listId: String, createdAt: Long): Unit = mutex.withLock {
        val list = _lists.value[listId] ?: return
        if (createdAt > list.relaySince) saveSoon(list.copy(relaySince = createdAt))
    }

    data class MemberUpdate(val isNew: Boolean, val firstContact: Boolean, val worthAnnouncing: Boolean)

    /** Records that [deviceId] (called [name]) is on the list. */
    suspend fun recordMember(listId: String, deviceId: String, name: String): MemberUpdate = mutex.withLock {
        val list = _lists.value[listId] ?: return MemberUpdate(false, false, false)
        val isNew = deviceId !in list.members
        val firstContact = list.members.isEmpty()
        if (list.members[deviceId] != name) save(list.copy(members = list.members + (deviceId to name)))
        // Someone new is only news on a list we created or have already been sharing.
        MemberUpdate(isNew, firstContact, worthAnnouncing = isNew && (!firstContact || list.createdHere))
    }

    /** Forgets a device that left the list; returns its name. */
    suspend fun removeMember(listId: String, deviceId: String): String? = mutex.withLock {
        val list = _lists.value[listId] ?: return null
        val name = list.members[deviceId] ?: return null
        save(list.copy(members = list.members - deviceId))
        name
    }

    private suspend fun editItem(listId: String, itemId: String, change: Item.() -> Item) = putLocal(listId) { list ->
        list.items[itemId]?.edited(identity.deviceId, identity.deviceName, System.currentTimeMillis(), change)
    }

    private suspend fun putLocal(listId: String, item: Item): Unit = putLocal(listId) { item }

    private suspend fun putLocal(listId: String, makeItem: (TodoList) -> Item?): Unit = mutex.withLock {
        val list = _lists.value[listId] ?: return
        val item = makeItem(list) ?: return
        // Messages are their own record; a history of them would only double what's synced.
        val entry = if (list.kind == SpaceKind.CHAT) null else auditEntryFor(list.items[item.id], item, identity.deviceName)
        val audit = if (entry != null) list.audit + (entry.id to entry) else list.audit
        save(list.copy(items = list.items + (item.id to item), audit = audit))
        _localEdits.tryEmit(LocalEdit(listId, item, entry, list.secret))
    }

    /** Updates the list and writes it to disk now (local edits). */
    private suspend fun save(list: TodoList) {
        _lists.value += list.id to list
        write(list.id)
    }

    /** Updates the list now and writes it once changes stop arriving for a moment. */
    private fun saveSoon(list: TodoList) {
        _lists.value += list.id to list
        synchronized(unsaved) {
            unsaved += list.id
            if (pendingWrite != null) return
            pendingWrite = writer.launch {
                // Keep going until nothing is left: changes can arrive while a write is in progress.
                while (true) {
                    delay(SAVE_DELAY_MS)
                    val ids = synchronized(unsaved) {
                        if (unsaved.isEmpty()) {
                            pendingWrite = null
                            return@launch
                        }
                        unsaved.toList().also { unsaved.clear() }
                    }
                    ids.forEach { write(it) }
                }
            }
        }
    }

    /** Writes the list's current state, unless it has been removed meanwhile. */
    private suspend fun write(listId: String) = fileMutex.withLock {
        val list = _lists.value[listId] ?: return@withLock
        withContext(Dispatchers.IO) {
            val tmp = File(dir, "${list.id}.json.tmp")
            val text = json.encodeToString(list)
            Diagnostics.count("Storage: list writes")
            Diagnostics.count("Storage: written data", text.length.toLong())
            tmp.writeText(text)
            if (!tmp.renameTo(file(list.id))) {
                file(list.id).delete()
                tmp.renameTo(file(list.id))
            }
        }
    }

    private fun file(listId: String) = File(dir, "$listId.json")

    private companion object {
        const val SAVE_DELAY_MS = 300L
        /** Time for a last message to go out before a list stops syncing. */
        const val STOP_DELAY_MS = 1_000L
    }

    private fun load(): Map<String, TodoList> {
        dir.mkdirs()
        return dir.listFiles { f -> f.name.endsWith(".json") }.orEmpty().mapNotNull { f ->
            runCatching { json.decodeFromString<TodoList>(f.readText()) }
                .onFailure { Log.w("Intack", "Skipping unreadable list ${f.name}", it) }
                .getOrNull()
                ?.let { list -> if (list.sectioned) list else recordSections(list, f) }
        }.associateBy { it.id }
    }

    /** Once, for a list saved before items recorded their section: works them out, and saves. */
    private fun recordSections(list: TodoList, file: File): TodoList {
        val updated = list.copy(items = if (list.kind == SpaceKind.LIST) sectionsFromPositions(list.items) else list.items, sectioned = true)
        runCatching { file.writeText(json.encodeToString(updated)) }
        return updated
    }
}
