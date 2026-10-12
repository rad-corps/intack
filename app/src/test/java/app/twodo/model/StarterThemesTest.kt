package app.twodo.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StarterThemesTest {
    private val themes = setOf("twodo-dark", "twodo-light")

    @Test
    fun everyStarterIsValidThemeCode() {
        STARTER_THEMES.forEach { starter ->
            val result = parseThemeCode(starter.code, Look(), themes)
            assertEquals("${starter.name}: ${result.problems}", emptyList<String>(), result.problems)
            assertTrue("${starter.name} sets its own page colour", result.look.style.background != null)
            assertTrue("${starter.name} shows four colours on its tile", starter.swatch.size == 4)
        }
    }

    @Test
    fun startersHaveDifferentNamesAndLooks() {
        assertEquals(STARTER_THEMES.size, STARTER_THEMES.map { it.name }.toSet().size)
        assertEquals(STARTER_THEMES.size, STARTER_THEMES.map { parseThemeCode(it.code, Look(), themes).look }.toSet().size)
    }

    @Test
    fun aStarterReplacesWhateverStyleWasThere() {
        val busy = Look(style = ListStyle(items = ITEMS_CARDS, shadow = 12, font = FONT_HANDWRITTEN))
        val sunshine = parseThemeCode(STARTER_THEMES.first { it.name == "Constructivist" }.code, busy, themes).look
        assertEquals(ITEMS_LINES, sunshine.style.items)
        assertEquals(null, sunshine.style.shadow)
        assertEquals(null, sunshine.style.font)
    }
}
