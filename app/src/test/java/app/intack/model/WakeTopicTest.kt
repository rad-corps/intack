package app.intack.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WakeTopicTest {
    @Test
    fun theWakeTopicIsWhatTheServerAcceptsAndTheSameOnEveryPhone() {
        val secret = ListKeys.newSecret()
        val topic = ListKeys(secret).wakeTopic
        // Must match TOPIC in server/wake/index.mjs.
        assertTrue(topic, Regex("^w[0-9a-f]{32}$").matches(topic))
        assertEquals(topic, ListKeys(secret).wakeTopic)
        assertNotEquals(topic, ListKeys(ListKeys.newSecret()).wakeTopic)
    }
}
