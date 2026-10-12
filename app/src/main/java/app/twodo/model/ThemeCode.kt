package app.twodo.model

import kotlinx.serialization.Serializable

/**
 * How a list is laid out and decorated, on top of its colour theme. Part of a [Look], so a group's
 * style reaches everyone in it. Null means the default for each.
 */
@Serializable
data class ListStyle(
    /** Space above and below each item's text, in dp (default 12). */
    val spacing: Int? = null,
    /** [ITEMS_PLAIN], [ITEMS_LINES], [ITEMS_CARDS] or [ITEMS_OUTLINED]. */
    val items: String? = null,
    /** Corner radius of cards and outlines, in dp. */
    val corners: Int? = null,
    val borderWidth: Int? = null,
    /** [BORDER_SOLID], [BORDER_DASHED] or [BORDER_DOTTED]. */
    val borderStyle: String? = null,
    /** ARGB. */
    val borderColor: Long? = null,
    /** Shadow depth of cards, in dp. */
    val shadow: Int? = null,
    /** Background of each item (ARGB). */
    val itemColor: Long? = null,
    /** Headings (ARGB). */
    val secondary: Long? = null,
    /** Highlights, such as the section a dragged item is joining (ARGB). */
    val tertiary: Long? = null,
    /** The page behind everything (ARGB). */
    val background: Long? = null,
    /** Text (ARGB); picked to suit [background] if that's set and this isn't. */
    val text: Long? = null,
    /** [FONT_SERIF], [FONT_MONO] or [FONT_HANDWRITTEN]; null for the usual font. */
    val font: String? = null,
)

const val ITEMS_PLAIN = "plain"
const val ITEMS_LINES = "lines"
const val ITEMS_CARDS = "cards"
const val ITEMS_OUTLINED = "outlined"
const val BORDER_SOLID = "solid"
const val BORDER_DASHED = "dashed"
const val BORDER_DOTTED = "dotted"
const val FONT_SERIF = "serif"
const val FONT_MONO = "mono"
const val FONT_HANDWRITTEN = "handwritten"

/*
 * Theme code: a look written out like CSS, for people who want to type or paste one in and share it
 * ("background: #1b1b2f; items: cards; border: 2px dashed hotpink;"). It only ever sets the options
 * above — nothing in it runs — so a pasted theme is always safe.
 */

/** [look] as theme code, one property per line; only what differs from the defaults. */
fun themeCode(look: Look): String = buildList {
    look.themeId?.let { add("theme: $it") }
    look.accent?.let { add("accent: ${colorCode(it)}") }
    val s = look.style
    s.secondary?.let { add("secondary: ${colorCode(it)}") }
    s.tertiary?.let { add("tertiary: ${colorCode(it)}") }
    s.background?.let { add("background: ${colorCode(it)}") }
    s.text?.let { add("text: ${colorCode(it)}") }
    s.itemColor?.let { add("item-background: ${colorCode(it)}") }
    s.items?.let { add("items: $it") }
    s.spacing?.let { add("spacing: ${it}px") }
    s.corners?.let { add("corners: ${it}px") }
    if (s.borderWidth != null || s.borderStyle != null || s.borderColor != null) {
        add("border: " + listOfNotNull(s.borderWidth?.let { "${it}px" }, s.borderStyle, s.borderColor?.let(::colorCode)).joinToString(" "))
    }
    s.shadow?.let { add("shadow: ${it}px") }
    s.font?.let { add("font: $it") }
    look.photoId?.takeIf { it.startsWith(BUILTIN_PREFIX) }?.let { add("background-image: ${it.removePrefix(BUILTIN_PREFIX)}") }
    if (look.photoId != null) add("background-strength: ${(look.photoStrength * 100).toInt()}%")
}.joinToString("\n") { "$it;" }

/** The result of reading theme code: the look it describes, and anything that couldn't be understood. */
data class ThemeCodeResult(val look: Look, val problems: List<String>)

/**
 * Reads theme code onto [base] (keeping its background photo): every property given replaces the
 * same option; ones not mentioned go back to their defaults. Unknown properties and values are skipped
 * and listed in [ThemeCodeResult.problems].
 */
fun parseThemeCode(code: String, base: Look, knownThemes: Set<String>): ThemeCodeResult {
    val problems = mutableListOf<String>()
    var look = Look(photoId = base.photoId, photoParts = base.photoParts, photoStrength = base.photoStrength)
    var style = ListStyle()
    val stripped = code.replace(Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL), " ")
    for (raw in stripped.split(';', '\n')) {
        val line = raw.trim().ifEmpty { continue }
        val colon = line.indexOf(':')
        if (colon < 0) { problems += "“$line” needs a colon, like “items: cards”"; continue }
        val key = line.substring(0, colon).trim().lowercase()
        val value = line.substring(colon + 1).trim().removeSuffix("!important").trim()
        fun color(): Long? = parseColor(value).also { if (it == null) problems += "“$value” isn't a colour I know ($key)" }
        fun px(max: Int): Int? = value.lowercase().removeSuffix("px").removeSuffix("dp").trim().toIntOrNull()?.coerceIn(0, max)
            .also { if (it == null) problems += "“$value” isn't a size ($key)" }
        fun oneOf(vararg options: String): String? = value.lowercase().takeIf { it in options }
            .also { if (it == null) problems += "$key can be ${options.joinToString(", ")}" }
        when (key) {
            "theme" -> if (value in knownThemes) look = look.copy(themeId = value) else problems += "No theme called “$value”"
            "accent", "primary", "highlight" -> look = look.copy(accent = color())
            "secondary", "heading", "headings" -> style = style.copy(secondary = color())
            "tertiary" -> style = style.copy(tertiary = color())
            "background", "background-color" -> style = style.copy(background = color())
            "text", "color" -> style = style.copy(text = color())
            "item-background", "item-color", "items-background" -> style = style.copy(itemColor = color())
            "items", "item-style" -> style = style.copy(items = oneOf(ITEMS_PLAIN, ITEMS_LINES, ITEMS_CARDS, ITEMS_OUTLINED))
            "spacing", "padding" -> style = style.copy(spacing = px(40))
            "corners", "border-radius", "radius" -> style = style.copy(corners = px(40))
            "shadow", "box-shadow" -> style = style.copy(shadow = if (value.lowercase() == "none") 0 else px(24))
            "font", "font-family" -> style = style.copy(font = when (value.lowercase().trim('"', '\'')) {
                "default", "normal", "sans-serif", "sans" -> null
                "serif" -> FONT_SERIF
                "mono", "monospace" -> FONT_MONO
                "handwritten", "cursive", "comic sans", "comic sans ms" -> FONT_HANDWRITTEN
                else -> null.also { problems += "font can be default, serif, mono or handwritten" }
            })
            "border" -> {
                if (value.lowercase() == "none") { style = style.copy(borderWidth = 0); continue }
                for (part in value.split(Regex("\\s+"))) {
                    val p = part.lowercase()
                    when {
                        p in setOf(BORDER_SOLID, BORDER_DASHED, BORDER_DOTTED) -> style = style.copy(borderStyle = p)
                        p.removeSuffix("px").removeSuffix("dp").toIntOrNull() != null ->
                            style = style.copy(borderWidth = p.removeSuffix("px").removeSuffix("dp").toInt().coerceIn(0, 8))
                        parseColor(part) != null -> style = style.copy(borderColor = parseColor(part))
                        else -> problems += "“$part” in border isn't a width, style or colour"
                    }
                }
            }
            "background-image" -> look = when (val v = value.lowercase().trim('"', '\'')) {
                "none" -> look.copy(photoId = null, photoParts = 0)
                else -> look.copy(photoId = BUILTIN_PREFIX + v.removePrefix(BUILTIN_PREFIX), photoParts = 0)
            }
            "background-strength", "photo-strength" ->
                value.removeSuffix("%").trim().toIntOrNull()?.let { look = look.copy(photoStrength = (it / 100f).coerceIn(0.1f, 1f)) }
                    ?: run { problems += "background-strength is a percentage, like 45%" }
            else -> problems += "I don't know “$key”"
        }
    }
    return ThemeCodeResult(look.copy(style = style), problems)
}

/** A look's photo id for a background that comes with the app. */
const val BUILTIN_PREFIX = "builtin:"

/** "#rrggbb", or "#aarrggbb" when not opaque. */
fun colorCode(argb: Long): String {
    val v = argb and 0xFFFFFFFF
    return if (v ushr 24 == 0xFFL) "#%06x".format(v and 0xFFFFFF) else "#%08x".format(v)
}

/** #rgb, #rrggbb, #aarrggbb, rgb(r, g, b) or a colour name; null if it's none of those. */
fun parseColor(text: String): Long? {
    val t = text.trim().lowercase()
    NAMED_COLORS[t]?.let { return it }
    Regex("""rgb\(\s*(\d+)\s*,\s*(\d+)\s*,\s*(\d+)\s*\)""").matchEntire(t)?.let { m ->
        val (r, g, b) = m.destructured
        return 0xFF000000 or (r.toLong().coerceIn(0, 255) shl 16) or (g.toLong().coerceIn(0, 255) shl 8) or b.toLong().coerceIn(0, 255)
    }
    val hex = t.removePrefix("#").takeIf { t.startsWith("#") && it.all { c -> c in "0123456789abcdef" } } ?: return null
    return when (hex.length) {
        3 -> 0xFF000000 or hex.map { "$it$it" }.joinToString("").toLong(16)
        6 -> 0xFF000000 or hex.toLong(16)
        8 -> hex.toLong(16)
        else -> null
    }
}

private val NAMED_COLORS = mapOf(
    "black" to 0xFF000000, "white" to 0xFFFFFFFF, "red" to 0xFFFF0000, "green" to 0xFF008000, "blue" to 0xFF0000FF,
    "yellow" to 0xFFFFFF00, "orange" to 0xFFFFA500, "purple" to 0xFF800080, "pink" to 0xFFFFC0CB, "hotpink" to 0xFFFF69B4,
    "deeppink" to 0xFFFF1493, "magenta" to 0xFFFF00FF, "fuchsia" to 0xFFFF00FF, "cyan" to 0xFF00FFFF, "aqua" to 0xFF00FFFF,
    "lime" to 0xFF00FF00, "teal" to 0xFF008080, "navy" to 0xFF000080, "maroon" to 0xFF800000, "olive" to 0xFF808000,
    "gold" to 0xFFFFD700, "silver" to 0xFFC0C0C0, "gray" to 0xFF808080, "grey" to 0xFF808080, "brown" to 0xFFA52A2A,
    "coral" to 0xFFFF7F50, "salmon" to 0xFFFA8072, "tomato" to 0xFFFF6347, "crimson" to 0xFFDC143C, "indigo" to 0xFF4B0082,
    "violet" to 0xFFEE82EE, "lavender" to 0xFFE6E6FA, "plum" to 0xFFDDA0DD, "turquoise" to 0xFF40E0D0, "skyblue" to 0xFF87CEEB,
    "beige" to 0xFFF5F5DC, "ivory" to 0xFFFFFFF0, "mintcream" to 0xFFF5FFFA, "khaki" to 0xFFF0E68C, "chocolate" to 0xFFD2691E,
)
