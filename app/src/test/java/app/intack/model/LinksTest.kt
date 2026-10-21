package app.intack.model

import org.junit.Assert.assertEquals
import org.junit.Test

class LinksTest {
    private fun urls(text: String) = findLinks(text).map { it.url }
    private fun spans(text: String) = findLinks(text).map { text.substring(it.range) }

    @Test
    fun findsAddressesAmongWords() {
        assertEquals(listOf("https://example.com/a?b=1"), urls("look at https://example.com/a?b=1 later"))
        assertEquals(listOf("http://x.org", "https://y.org"), urls("http://x.org and https://y.org"))
    }

    @Test
    fun aBareWwwOpensOverHttps() {
        assertEquals(listOf("https://www.bunnings.com.au"), urls("try www.bunnings.com.au"))
        assertEquals(listOf("www.bunnings.com.au"), spans("try www.bunnings.com.au"))
    }

    @Test
    fun leavesOffSentencePunctuation() {
        assertEquals(listOf("https://example.com"), spans("Here: https://example.com."))
        assertEquals(listOf("https://example.com/x"), spans("(see https://example.com/x)"))
        assertEquals(listOf("https://en.wikipedia.org/wiki/Foo_(bar)"), spans("https://en.wikipedia.org/wiki/Foo_(bar), ok?"))
        assertEquals(listOf("https://a.com"), spans("is it https://a.com?"))
    }

    @Test
    fun ignoresTextWithNoAddress() {
        assertEquals(emptyList<String>(), urls("no links here, just www. and http://"))
        assertEquals(emptyList<String>(), urls("shopping: milk, bread"))
    }
}
