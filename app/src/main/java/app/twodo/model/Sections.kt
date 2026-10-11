package app.twodo.model

/** A list's section headings, in order. */
val TodoList.headings: List<Item> get() = visibleItems.filter { it.heading }

/** Things to tick: everything but headings. */
val TodoList.tickable: List<Item> get() = visibleItems.filter { !it.heading }

/**
 * Where a new item goes in [ordered] (a list's items in order): at the bottom, or, in a list with
 * headings, at the end of the unsorted part above the first heading, ready to be moved into a section.
 */
fun newItemPosition(ordered: List<Item>): Double {
    val first = ordered.indexOfFirst { it.heading }
    if (first < 0) return (ordered.maxOfOrNull { it.position } ?: 0.0) + 1
    val after = ordered[first].position
    val before = ordered.getOrNull(first - 1)?.position ?: return after - 1
    return (before + after) / 2
}

/**
 * The index [moveItem]-style moves expect among all of [ordered] except [itemId], for an item dropped
 * at [index] in [shown] (the same items with the headings left out).
 */
fun indexAmongAll(ordered: List<Item>, shown: List<Item>, itemId: String, index: Int): Int {
    val others = ordered.filter { it.id != itemId }
    val rest = shown.filter { it.id != itemId }
    // Just after the item it now follows, or else just before the one it now leads.
    rest.getOrNull(index - 1)?.let { prev -> return others.indexOfFirst { it.id == prev.id } + 1 }
    rest.getOrNull(index)?.let { next -> return others.indexOfFirst { it.id == next.id } }
    return others.size
}

/** [headingId]'s section in [ordered]: the heading, then the items under it up to the next heading. */
fun sectionItems(ordered: List<Item>, headingId: String): List<Item> {
    val start = ordered.indexOfFirst { it.id == headingId }
    if (start < 0) return emptyList()
    val end = ordered.subList(start + 1, ordered.size).indexOfFirst { it.heading }.let { if (it < 0) ordered.size else start + 1 + it }
    return ordered.subList(start, end)
}

/**
 * New positions for moving [headingId]'s whole section to [dropIndex] among [ordered] without that
 * section (the list as it looks while the heading is dragged on its own). A drop inside another
 * section, or among the items above every heading, goes on to just before the next heading (or the
 * end), so the section never splits another or takes in items that weren't in it.
 */
fun sectionMove(ordered: List<Item>, headingId: String, dropIndex: Int): Map<String, Double> {
    val section = sectionItems(ordered, headingId)
    if (section.isEmpty()) return emptyMap()
    val ids = section.map { it.id }.toSet()
    val rest = ordered.filter { it.id !in ids }
    var index = dropIndex.coerceIn(0, rest.size)
    while (index < rest.size && !rest[index].heading) index++
    val before = rest.getOrNull(index - 1)?.position
    val after = rest.getOrNull(index)?.position
    val n = section.size
    return section.mapIndexed { k, item ->
        item.id to when {
            before != null && after != null -> before + (after - before) * (k + 1) / (n + 1)
            before != null -> before + k + 1
            after != null -> after - (n - k)
            else -> item.position
        }
    }.toMap()
}

/** The heading [itemId] sits under in [ordered] (a list's items in order), or null if it's above them all. */
fun sectionOf(ordered: List<Item>, itemId: String): Item? {
    val index = ordered.indexOfFirst { it.id == itemId }
    if (index < 0) return null
    return ordered.subList(0, index).lastOrNull { it.heading }
}

/**
 * Where [itemId] goes, as an index among the other items, to join [headingId]'s section — at its end,
 * just before the next heading — or, with null, to sit above every heading.
 */
fun indexForSection(ordered: List<Item>, itemId: String, headingId: String?): Int {
    val others = ordered.filter { it.id != itemId }
    if (headingId == null) return others.indexOfFirst { it.heading }.let { if (it < 0) others.size else it }
    val start = others.indexOfFirst { it.id == headingId }
    if (start < 0) return others.size
    val next = others.subList(start + 1, others.size).indexOfFirst { it.heading }
    return if (next < 0) others.size else start + 1 + next
}
