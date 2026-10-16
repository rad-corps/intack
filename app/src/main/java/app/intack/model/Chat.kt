package app.intack.model

/** Every message in this group, oldest first (from each of its message spaces, should there be two). */
fun TodoList.messages(lists: Map<String, TodoList>): List<Item> =
    chatRefs.mapNotNull { ref -> ref.spaceId?.let { lists[it] } }
        .flatMap { chat -> chat.items.values.filter { !it.deleted && it.text.isNotBlank() } }
        .sortedWith(compareBy({ it.createdAt }, { it.id }))

/** Messages in this group from others that this phone ([me]) hasn't shown yet. */
fun TodoList.unreadMessages(lists: Map<String, TodoList>, me: String): Int =
    chatRefs.mapNotNull { ref -> ref.spaceId?.let { lists[it] } }
        .sumOf { chat -> chat.items.values.count { !it.deleted && it.version.by != me && it.createdAt > chat.readUpTo } }
