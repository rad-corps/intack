package app.intack.model

/**
 * Ready-made looks to start from, written as theme code (see ThemeCode.kt) so anyone can open the
 * code afterwards and see, or change, exactly what they set.
 */
class StarterTheme(val name: String, val code: String) {
    /** The page and highlight colours, for the tile that shows it. */
    val swatch: List<Long> by lazy {
        val look = parseThemeCode(code, Look(), emptySet()).look
        listOfNotNull(look.style.background, look.accent, look.style.secondary, look.style.tertiary)
    }
}

val STARTER_THEMES = listOf(
    // Late-90s desktop media player: near-black panels, LED green and neon, square and tight.
    StarterTheme(
        "Cyberdeck",
        """
        theme: twodo-dark;
        background: #0b0d10;
        text: #c8ffc8;
        accent: #39ff14;
        secondary: #00e5ff;
        tertiary: #ff2bd6;
        item-background: #14181d;
        items: outlined;
        corners: 0px;
        border: 1px solid #2f6f3f;
        spacing: 8px;
        font: mono;
        background-image: none;
        """,
    ),
    // Moss, bark and leaf on soft paper, with the forest showing through.
    StarterTheme(
        "Woodland",
        """
        theme: twodo-light;
        background: #eef1e6;
        text: #263326;
        accent: #4f7942;
        secondary: #7a5230;
        tertiary: #a3b18a;
        item-background: #f8faf2;
        items: cards;
        corners: 18px;
        shadow: 2px;
        font: serif;
        background-image: forest;
        background-strength: 25%;
        """,
    ),
    // Constructivist poster: aged paper, red and black, hard edges and rules.
    StarterTheme(
        "Constructivist",
        """
        theme: twodo-light;
        background: #efe4cc;
        text: #1a1a1a;
        accent: #c8102e;
        secondary: #c8102e;
        tertiary: #e8b833;
        items: lines;
        border: 2px solid #1a1a1a;
        spacing: 10px;
        font: default;
        background-image: none;
        """,
    ),
    // Sunny and cheerful: white cards on warm cream, big friendly corners.
    StarterTheme(
        "Sunshine",
        """
        theme: twodo-light;
        background: #fff8e1;
        text: #222222;
        accent: #ff7a00;
        secondary: #0a84ff;
        tertiary: #ffd400;
        item-background: #ffffff;
        items: cards;
        corners: 22px;
        shadow: 4px;
        spacing: 16px;
        font: default;
        background-image: none;
        """,
    ),
    // Retro platformer: pixel blue sky over brown checkerboard ground, grass-green edges, gold rings.
    StarterTheme(
        "Sky Hills",
        """
        theme: twodo-dark;
        background: #06061a;
        text: #ffffff;
        accent: #3cbf24;
        secondary: #ffe14d;
        tertiary: #ffd21f;
        item-background: #12127a;
        items: cards;
        corners: 10px;
        border: 3px solid #3cbf24;
        shadow: 3px;
        spacing: 14px;
        font: default;
        background-image: pixel-hills;
        background-strength: 100%;
        """,
    ),
    // Disco night: deep purple, hot pink dotted outlines, glowing colours, scrawled text.
    StarterTheme(
        "Party",
        """
        theme: twodo-dark;
        background: #1a0533;
        text: #fff0fb;
        accent: #ff2fd1;
        secondary: #00f0ff;
        tertiary: #ffe600;
        item-background: #2a0a4a;
        items: outlined;
        corners: 24px;
        border: 3px dotted #ff2fd1;
        font: handwritten;
        background-image: abstract-dusk;
        background-strength: 40%;
        """,
    ),
    // Highland cows on a sunny hill: ginger coat on cream, hillside green and heather, soft cards over the photo.
    StarterTheme(
        "Highland Cow",
        """
        theme: twodo-light;
        background: #f6eee2;
        text: #3b2a1f;
        accent: #b4582a;
        secondary: #4e6b34;
        tertiary: #8f6aa3;
        item-background: #fffaf2;
        items: cards;
        corners: 16px;
        shadow: 2px;
        font: serif;
        background-image: highland-cow;
        background-strength: 60%;
        """,
    ),
)
