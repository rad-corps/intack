package app.intack.model

/** A web address found in some text: where it sits, and the URL to open (with https:// added to a bare www.). */
data class TextLink(val range: IntRange, val url: String)

private val linkPattern = Regex("""(?i)\b(?:https?://|www\.)[^\s<>"]+""")

/** The web addresses in [text], leaving off punctuation that ends a sentence or closes a bracket around one. */
fun findLinks(text: String): List<TextLink> = linkPattern.findAll(text).mapNotNull { match ->
    var end = match.range.last
    while (end > match.range.first) {
        val c = text[end]
        val keep = when (c) {
            '.', ',', ';', ':', '!', '?', '\'' -> false
            ')' -> text.substring(match.range.first, end).count { it == '(' } > text.substring(match.range.first, end).count { it == ')' }
            else -> true
        }
        if (keep) break
        end--
    }
    val found = text.substring(match.range.first, end + 1)
    val host = found.substringAfter("://").removePrefix("www.").removePrefix("WWW.")
    if (host.isEmpty() || !host.first().isLetterOrDigit()) return@mapNotNull null
    TextLink(match.range.first..end, if (found.startsWith("www.", ignoreCase = true)) "https://$found" else found)
}.toList()
