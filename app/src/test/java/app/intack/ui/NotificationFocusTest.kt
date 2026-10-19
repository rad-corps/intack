package app.intack.ui

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NotificationFocusTest {
    @Test
    fun aRequestIsHandedOutOnceToItsOwnList() = runBlocking {
        NotificationFocus.request("groceries", listOf("milk", "eggs"))
        assertNull(withTimeoutOrNull(100) { NotificationFocus.requests("dev").first() })
        assertEquals(listOf("milk", "eggs"), NotificationFocus.requests("groceries").first())
        assertNull(withTimeoutOrNull(100) { NotificationFocus.requests("groceries").first() })
    }

    @Test
    fun noItemsMeansNothingToPickOut() = runBlocking {
        NotificationFocus.request("calendar", emptyList())
        assertNull(withTimeoutOrNull(100) { NotificationFocus.requests("calendar").first() })
    }
}
