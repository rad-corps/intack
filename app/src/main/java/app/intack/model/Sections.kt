package app.intack.model

/*
 * Sections in a list. Every item records which heading it's under ([Item.section], the heading's id;
 * null for items above every heading). The order on screen follows from that: the unsorted items,
 * then each heading in turn with its own items. So a heading's items always stay with it — moving a
 * heading is one change to the heading, whatever anyone else does to its items at the same time.
 * Positions only order things among their own kind: headings among headings, items within a section.
 */

private val byPosition = compareBy<Item>({ it.position }, { it.id })

/**
 * [items] in the order they're shown: unsorted items, then each heading followed by its items. Items
 * whose heading is gone count as unsorted.
 */
fun sectionedOrder(items: Collection<Item>): List<Item> {
    val headings = items.filter { it.heading }.sortedWith(byPosition)
    val headingIds = headings.map { it.id }.toSet()
    val bySection = items.filter { !it.heading }.groupBy { it.section?.takeIf { s -> s in headingIds } }
    return bySection[null].orEmpty().sortedWith(byPosition) +
        headings.flatMap { h -> listOf(h) + bySection[h.id].orEmpty().sortedWith(byPosition) }
}

/**
 * Gives every item the section it appears in when ordered by position alone — how lists were kept
 * before sections were recorded. The same on every phone, since positions are synced.
 */
fun sectionsFromPositions(items: Map<String, Item>): Map<String, Item> {
    var current: String? = null
    return items.values.sortedWith(byPosition).associate { item ->
        if (item.heading) current = item.id
        item.id to if (item.heading) item else item.copy(section = current)
    }
}

/** A list's section headings, in order. */
val TodoList.headings: List<Item> get() = visibleItems.filter { it.heading }

/** Things to tick: everything but headings. */
val TodoList.tickable: List<Item> get() = visibleItems.filter { !it.heading }

/** Where a new item goes: at the end of the unsorted items (above every heading), ready to be moved into a section. */
fun newItemPosition(ordered: List<Item>): Double =
    (ordered.filter { !it.heading && sectionOf(ordered, it.id) == null }.maxOfOrNull { it.position } ?: 0.0) + 1

/** Where a new heading goes: after the last one. */
fun newHeadingPosition(ordered: List<Item>): Double = (ordered.filter { it.heading }.maxOfOrNull { it.position } ?: 0.0) + 1

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

/** The heading [itemId] sits under in [ordered] (a list's items in order), or null if it's above them all. */
fun sectionOf(ordered: List<Item>, itemId: String): Item? {
    val index = ordered.indexOfFirst { it.id == itemId }
    if (index < 0) return null
    return ordered.subList(0, index).lastOrNull { it.heading }
}

/** [ordered] as it will be once [headingId] is dropped at [dropIndex] (see [headingMove]). */
fun afterHeadingMove(ordered: List<Item>, headingId: String, dropIndex: Int): List<Item> {
    val pos = headingMove(ordered, headingId, dropIndex) ?: return ordered
    return sectionedOrder(ordered.map { if (it.id == headingId) it.copy(pos = pos) else it })
}

/** Halfway between two positions, or one step past whichever there is. */
private fun between(before: Double?, after: Double?, fallback: Double): Double = when {
    before != null && after != null -> (before + after) / 2
    before != null -> before + 1
    after != null -> after - 1
    else -> fallback
}

/**
 * Where an item dropped at [toIndex] among the other items of [ordered] ends up: the section it now
 * sits in (the heading above it) and a position among that section's items.
 */
fun itemMove(ordered: List<Item>, itemId: String, toIndex: Int): Pair<String?, Double> {
    val item = ordered.first { it.id == itemId }
    val others = ordered.filter { it.id != itemId }
    val index = toIndex.coerceIn(0, others.size)
    val section = others.subList(0, index).lastOrNull { it.heading }?.id
    val before = others.getOrNull(index - 1)?.takeIf { !it.heading }?.position
    val after = others.getOrNull(index)?.takeIf { !it.heading }?.position
    return section to between(before, after, item.position)
}

/** An item moved to the end of [headingId]'s section (null: the unsorted items above every heading). */
fun itemToSection(ordered: List<Item>, itemId: String, headingId: String?): Pair<String?, Double> {
    val inSection = ordered.filter { !it.heading && it.id != itemId && sectionOf(ordered, it.id)?.id == headingId }
    return headingId to (inSection.maxOfOrNull { it.position } ?: 0.0) + 1
}

/**
 * The new position of [headingId] dropped at [dropIndex] among [ordered] without its section (the
 * list as it looks while the heading is dragged). Sections can't split one another, so a drop inside
 * another section goes to whichever end of it is nearer — on a tie, the end it was being dragged
 * towards — and a drop among the unsorted items goes below them. Its items come along because they're
 * its items.
 */
fun headingMove(ordered: List<Item>, headingId: String, dropIndex: Int): Double? {
    val heading = ordered.firstOrNull { it.id == headingId } ?: return null
    val ids = sectionItems(ordered, headingId).map { it.id }.toSet()
    val rest = ordered.filter { it.id !in ids }
    var index = dropIndex.coerceIn(0, rest.size)
    if (index < rest.size && !rest[index].heading) {
        val above = (index - 1 downTo 0).firstOrNull { rest[it].heading }
        val below = (index until rest.size).firstOrNull { rest[it].heading } ?: rest.size
        val wasAt = ordered.takeWhile { it.id != headingId }.count { it.id !in ids }
        val movingDown = index > wasAt
        index = when {
            above == null -> below
            index - above < below - index -> above
            index - above > below - index -> below
            movingDown -> below
            else -> above
        }
    }
    val before = rest.subList(0, index).lastOrNull { it.heading }?.position
    val after = rest.getOrNull(index)?.position
    return between(before, after, heading.position)
}
