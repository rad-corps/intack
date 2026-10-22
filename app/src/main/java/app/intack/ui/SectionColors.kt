package app.intack.ui

import androidx.compose.ui.graphics.Color

/** A colour a section can be: its name (what's synced, see [app.intack.model.Item.color]), what it's called, and how it looks. */
class SectionColor(val key: String, val label: String, val color: Color)

/** Strong enough to read as heading text on a light or a dark page, and to tint the section's pane. */
val SECTION_COLORS = listOf(
    SectionColor("red", "Red", Color(0xFFD64545)),
    SectionColor("orange", "Orange", Color(0xFFE07B2E)),
    SectionColor("yellow", "Yellow", Color(0xFFD4A20F)),
    SectionColor("green", "Green", Color(0xFF3F9A4E)),
    SectionColor("teal", "Teal", Color(0xFF23968A)),
    SectionColor("blue", "Blue", Color(0xFF3C78D8)),
    SectionColor("purple", "Purple", Color(0xFF8A5BC6)),
    SectionColor("pink", "Pink", Color(0xFFD2558F)),
)

/** The colour named [key], or null for plain (including names from newer versions this one doesn't know). */
fun sectionColor(key: String?): Color? = key?.let { k -> SECTION_COLORS.firstOrNull { it.key == k }?.color }

/** How an unnamed section is called where it needs a name, as in the section choice for an item. */
const val UNNAMED_SECTION = "Unnamed"
