package app.intack.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeCodeTest {
    private val themes = setOf("twodo-dark", "nord")

    @Test
    fun aLookSurvivesBeingWrittenOutAndReadBack() {
        val look = Look(
            themeId = "nord", accent = 0xFFFF69B4, photoId = "builtin:sea", photoStrength = 0.6f,
            style = ListStyle(
                spacing = 20, items = ITEMS_CARDS, corners = 16, borderWidth = 2, borderStyle = BORDER_DASHED,
                borderColor = 0xFF00FFFF, shadow = 8, itemColor = 0xFF1B1B2F, secondary = 0xFF8C52FF,
                tertiary = 0x8000E5FF, background = 0xFF101418, text = 0xFFFFFFFF, font = FONT_MONO,
            ),
        )
        val result = parseThemeCode(themeCode(look), Look(), themes)
        assertEquals(emptyList<String>(), result.problems)
        assertEquals(look, result.look)
    }

    @Test
    fun readsCssLikeCode() {
        val code = """
            /* my page */
            background: #1b1b2f; color: white;
            items: cards; border-radius: 20px;
            border: 3px dotted hotpink;
            box-shadow: none;
            font-family: "Comic Sans MS";
            headings: rgb(255, 128, 0);
        """
        val look = parseThemeCode(code, Look(), themes).look
        assertEquals(0xFF1B1B2F, look.style.background)
        assertEquals(0xFFFFFFFF, look.style.text)
        assertEquals(ITEMS_CARDS, look.style.items)
        assertEquals(20, look.style.corners)
        assertEquals(3, look.style.borderWidth)
        assertEquals(BORDER_DOTTED, look.style.borderStyle)
        assertEquals(0xFFFF69B4, look.style.borderColor)
        assertEquals(0, look.style.shadow)
        assertEquals(FONT_HANDWRITTEN, look.style.font)
        assertEquals(0xFFFF8000, look.style.secondary)
    }

    @Test
    fun skipsWhatItDoesntUnderstandAndSaysSo() {
        val result = parseThemeCode("items: wobbly; sparkles: yes; background: #12; theme: nope; spacing: 8px", Look(), themes)
        assertEquals(8, result.look.style.spacing)
        assertNull(result.look.style.items)
        assertNull(result.look.style.background)
        assertEquals(4, result.problems.size)
    }

    @Test
    fun keepsTheBackgroundUnlessTheCodeChangesIt() {
        val base = Look(photoId = "own123", photoParts = 2)
        assertEquals("own123", parseThemeCode("items: lines", base, themes).look.photoId)
        assertNull(parseThemeCode("background-image: none", base, themes).look.photoId)
        assertEquals("builtin:forest", parseThemeCode("background-image: forest", base, themes).look.photoId)
    }

    @Test
    fun coloursInAllTheUsualForms() {
        assertEquals(0xFFFF0000, parseColor("#f00"))
        assertEquals(0xFF123456, parseColor("#123456"))
        assertEquals(0x80123456, parseColor("#80123456"))
        assertEquals(0xFF0A141E, parseColor("rgb(10, 20, 30)"))
        assertEquals(0xFF40E0D0, parseColor("Turquoise"))
        assertNull(parseColor("bluish"))
        assertEquals("#123456", colorCode(0xFF123456))
        assertTrue(colorCode(0x80123456).length == 9)
    }
}
