package app.intack.sync

import android.content.Context
import app.intack.IntackApp
import app.intack.data.Diagnostics
import app.intack.model.ListKeys
import app.intack.net.SyncLog
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/**
 * Receives the wake server's content-free pushes (server/wake): something changed in one of this
 * phone's spaces. Connects to the relays for a short window, which fetches and decrypts the change and
 * notifies as usual, then lets the phone sleep again. Firebase runs this on its own background thread
 * and allows it about 20 seconds.
 */
class WakeReceiver : FirebaseMessagingService() {
    override fun onMessageReceived(message: RemoteMessage) {
        if (message.data["wake"] == null) return
        Diagnostics.count("Wake: received")
        SyncLog.add("woken by a push; checking the relays")
        val sync = (application as IntackApp).sync
        sync.acquire()
        try {
            Thread.sleep(WINDOW_MS)
        } finally {
            sync.release()
        }
    }

    /** A new token starts with no topics, so subscribe to them all again. */
    override fun onNewToken(token: String) {
        WakeTopics.forget(this)
        (application as IntackApp).let { WakeTopics.update(it, it.repo.lists.value.values.map { l -> l.secret }) }
    }

    private companion object {
        const val WINDOW_MS = 15_000L
    }
}

/**
 * Keeps this phone subscribed to the wake topic of every space it's in, and to nothing else. Topics are
 * hashes of the spaces' secrets (ListKeys.wakeTopic), so Firebase learns nothing about what they are.
 */
object WakeTopics {
    private const val PREFS = "wake"
    private const val KEY = "topics"

    fun update(context: Context, secrets: Collection<String>) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val wanted = secrets.map { ListKeys(it).wakeTopic }.toSet()
        val have = prefs.getStringSet(KEY, emptySet()).orEmpty()
        if (wanted == have) return
        val messaging = runCatching { FirebaseMessaging.getInstance() }.getOrElse {
            // No Google Play services (or Firebase isn't set up): wakes never arrive, background sync still works.
            SyncLog.add("wake pushes unavailable: ${it.message}")
            return
        }
        for (topic in wanted - have) {
            messaging.subscribeToTopic(topic).addOnSuccessListener { remember(context) { it + topic } }
                .addOnFailureListener { SyncLog.add("wake subscribe failed: ${it.message}") }
        }
        for (topic in have - wanted) {
            messaging.unsubscribeFromTopic(topic).addOnSuccessListener { remember(context) { it - topic } }
        }
    }

    fun forget(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY).apply()
    }

    /** Subscribe callbacks arrive on the main thread, so updates don't race. */
    private fun remember(context: Context, change: (Set<String>) -> Set<String>) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit().putStringSet(KEY, change(prefs.getStringSet(KEY, emptySet()).orEmpty().toSet())).apply()
    }
}
