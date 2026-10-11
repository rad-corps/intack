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
