package app.twodo.model

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatTest {
    private fun entry(id: String, kind: SpaceKind, chat: Boolean = false, createdAt: Long = 1) = Item(
        id, id, createdAt = createdAt, version = Version(createdAt, "A"), editor = "Adam",
        spaceId = id, spaceSecret = ListKeys.newSecret(), spaceKind = kind, chat = chat,
    )

    private val shopping = entry("shopping", SpaceKind.LIST)
    private val chatEntry = entry("chat", SpaceKind.LIST, chat = true, createdAt = 2)
    private val group = TodoList("g", "Family", ListKeys.newSecret(), kind = SpaceKind.GROUP, items = listOf(shopping, chatEntry).associateBy { it.id })

    private fun message(id: String, at: Long, by: String) = Item(id, "hi $id", createdAt = at, version = Version(at, by), editor = by)

    @Test
    fun theMessagesAreNotAList() {
        assertEquals(listOf("shopping"), group.listRefs.map { it.id })
        assertEquals(listOf("chat"), group.chatRefs.map { it.id })
        assertEquals(SpaceKind.CHAT, chatEntry.toInvite()?.kind)
    }

    @Test
    fun theGroupAnnouncesMessagesAsAListSoOlderVersionsCanReadIt() {
        val json = Json.encodeToString(Item.serializer(), chatEntry)
        assertTrue(json, "\"spaceKind\":\"list\"" in json)
        // What an older version sees: the fields it knows, which decode fine.
        val older = Json { ignoreUnknownKeys = true }.decodeFromString(Item.serializer(), json.replace("\"chat\":true", "\"unknownField\":true"))
        assertEquals(SpaceKind.LIST, older.effectiveSpaceKind)
    }

    @Test
    fun joiningMakesAChatAndALocalListCopyBecomesOne() {
        assertEquals(SpaceKind.CHAT, planGroup(group, emptyMap()).join.single { it.listId == "chat" }.kind)
        val joinedAsList = TodoList("chat", "chat", chatEntry.spaceSecret!!, groupId = "g")
        val update = planGroup(group, mapOf("chat" to joinedAsList)).update.single()
        assertEquals(SpaceKind.CHAT, update.kind)
    }

    @Test
    fun messagesFromBothChatsShowInOrderAndUnreadCountsOthersOnly() {
        val second = entry("chat2", SpaceKind.LIST, chat = true, createdAt = 3)
        val g = group.copy(items = group.items + (second.id to second))
        val lists = mapOf(
            "chat" to TodoList("chat", "Messages", ListKeys.newSecret(), kind = SpaceKind.CHAT, readUpTo = 10,
                items = listOf(message("a", 10, "B"), message("c", 30, "B")).associateBy { it.id }),
            "chat2" to TodoList("chat2", "Messages", ListKeys.newSecret(), kind = SpaceKind.CHAT,
                items = listOf(message("b", 20, "A"), message("d", 40, "B")).associateBy { it.id }),
        )
        assertEquals(listOf("a", "b", "c", "d"), g.messages(lists).map { it.id })
        // "c" (after readUpTo) and "d" (nothing read in chat2); "a" was read and "b" is mine.
        assertEquals(2, g.unreadMessages(lists, me = "A"))
    }
}
