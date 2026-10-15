package app.twodo.sync

import android.os.SystemClock
import app.twodo.BuildConfig
import app.twodo.data.Diagnostics
import app.twodo.net.SyncLog
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException

/**
 * Asks the wake server (server/wake) to push a content-free "something changed" to everyone in a
 * space, so their phones can sleep instead of staying connected. Does nothing in builds without a
 * wake server configured. Use from the sync thread only.
 */
class Wake(private val http: OkHttpClient) {
    private val lastSent = mutableMapOf<String, Long>()

    /** [urgent]: a message, worth waking a sleeping phone for at once. */
    fun send(topic: String, urgent: Boolean) {
        if (BuildConfig.WAKE_URL.isEmpty()) return
        // A burst of edits (ticking off a shop) needs one wake: the phone fetches everything once awake.
        val now = SystemClock.elapsedRealtime()
        if (!urgent && now - (lastSent[topic] ?: Long.MIN_VALUE / 2) < MIN_GAP_MS) return
        lastSent[topic] = now
        Diagnostics.count("Wake: sent")
        val body = """{"topic":"$topic","urgent":$urgent}""".toRequestBody(JSON)
        http.newCall(Request.Builder().url(BuildConfig.WAKE_URL).post(body).build()).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) = SyncLog.add("wake failed: ${e.message}")
            override fun onResponse(call: Call, response: Response) = response.use {
                if (it.code != 202 && it.code != 429) SyncLog.add("wake refused: ${it.code}")
            }
        })
    }

    private companion object {
        const val MIN_GAP_MS = 10_000L
        val JSON = "application/json".toMediaType()
    }
}
