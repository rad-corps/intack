package app.intack.net

import android.os.SystemClock
import android.util.Log
import app.intack.data.Diagnostics
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import org.webrtc.PeerConnectionFactory

/**
 * Public WebTorrent trackers used as a meeting point. Several, so any one going down doesn't matter.
 * Checked October 2026 that each relays offers for arbitrary rooms (btorrent.xyz and files.fm had died).
 */
val TRACKERS = listOf(
    "wss://tracker.webtorrent.dev",
    "wss://tracker.openwebtorrent.com",
    "wss://tracker.novage.com.ua/announce",
)

interface SwarmEvents {
    suspend fun onPeerOpen(swarm: ListSwarm, peer: Peer)
    suspend fun onPeerMessage(swarm: ListSwarm, peer: Peer, text: String)
    suspend fun onChanged(swarm: ListSwarm)
    suspend fun onPeerClosed(swarm: ListSwarm, peer: Peer)
}

/**
 * Finds and connects to the other devices sharing one list. Each device joins the list's room on every
 * tracker; a device that's looking for others announces WebRTC offers there, and the trackers pass
 * them to devices already in the room, which answer straight away through the tracker. Offers are
 * prepared ahead of time so an announcement goes out as soon as a tracker connects. If two devices
 * answer each other's offers at the same moment, both keep the connection offered by the device with
 * the lower tracker peer id.
 *
 * Every offer is a WebRTC connection, and making one costs CPU and network, so offers are only sent
 * while looking: on starting, when the network or app comes back, and with a growing gap while nobody
 * answers. Once connected, a device just stays in the room; anyone arriving later makes the offer.
 * All methods must be called in [scope], which must be single-threaded.
 */
class ListSwarm(
    val listId: String,
    val topic: String,
    private val myPeerId: String,
    private val factory: PeerConnectionFactory,
    private val pool: TrackerPool,
    private val scope: CoroutineScope,
    private val events: SwarmEvents,
    /** Offers per announcement: one for each other device that may be there to answer. */
    private val offerCount: () -> Int = { OFFERS_PER_ANNOUNCE },
) : PeerEvents {
    private class PendingOffer(val id: String, val peer: Peer, val sdp: String)

    private val trackers get() = pool.clients
    private val pending = mutableMapOf<String, PendingOffer>()
    /** Offers prepared in advance, ready to announce. */
    private val ready = ArrayDeque<PendingOffer>()
    private var preparing: Job? = null
    private val peers = mutableMapOf<String, Peer>()
    private var announceJob: Job? = null
    private var stopped = false
    /** How long to wait before looking again while nobody is connected; grows while nobody answers. */
    private var searchInterval = SEARCH_INTERVAL_MS
    private var lastRetry = 0L
    private val startedAt = SystemClock.elapsedRealtime()

    /** Timing log for diagnosing how long finding and syncing with peers takes (`adb logcat -s IntackSync`). */
    fun trace(message: String) = SyncLog.add("[${topic.take(4)}] +${SystemClock.elapsedRealtime() - startedAt}ms $message")

    val openPeers: List<Peer> get() = peers.values.filter { it.isOpen }
    val trackersOnline: Int get() = pool.online

    /**
     * Someone is being invited right now (the share dialog is open): announce every few seconds so
     * their phone finds this one straight away.
     */
    var eager = false
        set(value) {
            if (field == value) return
            field = value
            trace(if (value) "sharing: announcing often" else "sharing ended")
            searchInterval = SEARCH_INTERVAL_MS
            startAnnouncing(now = value)
        }

    fun start() {
        trace("started")
        prepareOffers()
        pool.register(this)
        startAnnouncing(now = true)
    }

    private fun startAnnouncing(now: Boolean) {
        announceJob?.cancel()
        announceJob = scope.launch {
            if (now) announce()
            // Look often at first, then back off while nobody answers (the other phone may simply be off);
            // in between, and once someone is connected, just stay in the room so others' offers reach us.
            var offersDue = SystemClock.elapsedRealtime() + nextOfferDelay()
            while (isActive) {
                delay((offersDue - SystemClock.elapsedRealtime()).coerceIn(0, ROOM_INTERVAL_MS))
                if (SystemClock.elapsedRealtime() >= offersDue) {
                    // Someone may have connected since this round was planned.
                    if (eager || openPeers.isEmpty()) announce() else stayInRoom()
                    offersDue = SystemClock.elapsedRealtime() + nextOfferDelay()
                } else {
                    stayInRoom()
                }
            }
        }
    }

    fun stop() {
        stopped = true
        announceJob?.cancel()
        pool.unregister(this)
        // Leave the tracker's room for this list (the shared connections stay open for other lists).
        val leave = buildJsonObject {
            put("action", "announce")
            put("info_hash", topic)
            put("peer_id", myPeerId)
            put("event", "stopped")
            put("numwant", 0)
            putJsonArray("offers") {}
        }
        trackers.forEach { it.send(leave) }
        preparing?.cancel()
        (pending.values + ready).forEach { it.peer.close() }
        pending.clear()
        ready.clear()
        peers.values.forEach { it.close() }
        peers.clear()
    }

    private fun nextOfferDelay(): Long = when {
        eager -> EAGER_INTERVAL_MS
        // Connected: no more offers. If the connection drops, looking starts again (see onClosed).
        openPeers.isNotEmpty() -> Long.MAX_VALUE / 2
        else -> searchInterval.also { searchInterval = minOf(it * 2, MAX_SEARCH_INTERVAL_MS) }
    }

    /** Announces without offers: keeps this device in the tracker's room (it forgets quiet peers). */
    private fun stayInRoom() {
        if (stopped) return
        val message = buildJsonObject {
            put("action", "announce")
            put("info_hash", topic)
            put("peer_id", myPeerId)
            put("numwant", 0)
            putJsonArray("offers") {}
        }
        trackers.filter { it.isOpen }.forEach { it.send(message) }
    }

    private fun nextDelayIsShort() = eager || (openPeers.isEmpty() && searchInterval <= OFFER_TTL_MS / 2)

    /** The network changed or the app came back: look for peers again now, and often. */
    fun kick() {
        searchInterval = SEARCH_INTERVAL_MS
        // Just started: it's already announcing.
        if (announceJob == null || SystemClock.elapsedRealtime() - startedAt < 3_000) return
        startAnnouncing(now = true)
    }

    /** Re-announce after a failed attempt, but at most every [RETRY_INTERVAL_MS]. */
    private suspend fun retrySoon() {
        if (openPeers.isNotEmpty() || stopped) return
        val now = SystemClock.elapsedRealtime()
        if (now - lastRetry < RETRY_INTERVAL_MS) return
        lastRetry = now
        announce()
    }

    fun drop(peer: Peer) {
        peer.close()
        peer.remotePeerId?.let { if (peers[it] === peer) peers.remove(it) }
        scope.launch {
            events.onPeerClosed(this@ListSwarm, peer)
            events.onChanged(this@ListSwarm)
        }
    }

    suspend fun onTrackerOpen(tracker: TrackerClient) {
        trace("tracker open ${tracker.url}")
        events.onChanged(this)
        // The other trackers usually open moments apart: send the same offers rather than making more.
        val cutoff = System.currentTimeMillis() - OFFER_TTL_MS / 2
        val fresh = pending.values.filter { it.peer.createdAt > cutoff }
        if (fresh.isEmpty()) announce(listOf(tracker)) else sendOffers(listOf(tracker), fresh)
    }

    /** Keeps enough offers ready for the next announcement, creating any missing ones in parallel. */
    private fun prepareOffers() {
        if (preparing?.isActive == true || stopped) return
        preparing = scope.launch {
            // While inviting, someone new is in the room besides the usual members.
            val wanted = if (eager) OFFERS_PER_ANNOUNCE else offerCount().coerceIn(1, OFFERS_PER_ANNOUNCE)
            val missing = wanted - ready.size
            (1..missing).map { async { createOffer() } }.awaitAll().filterNotNull().forEach { ready += it }
        }
    }

    private suspend fun createOffer(): PendingOffer? {
        val peer = Peer(factory, scope, this)
        return runCatching { PendingOffer(randomId(), peer, peer.createOffer()) }
            .onFailure { e -> Log.w(TAG, "Offer failed", e); peer.close() }
            .getOrNull()
    }

    /** Takes the prepared offers (making them now if none are ready yet). */
    private suspend fun takeOffers(): List<PendingOffer> {
        val cutoff = System.currentTimeMillis() - OFFER_TTL_MS / 2
        ready.filter { it.peer.createdAt < cutoff }.forEach { ready.remove(it); it.peer.close() }
        if (ready.isEmpty()) preparing?.join()
        if (ready.isEmpty()) prepareOffers().also { preparing?.join() }
        return ready.toList().also { ready.clear() }
    }

    /** Sends offers to [to]; offers nobody answered by the next round are discarded. */
    private suspend fun announce(to: List<TrackerClient> = trackers) {
        if (to.none { it.isOpen } || stopped) return
        expireOffers()
        val offersStarted = SystemClock.elapsedRealtime()
        val offers = takeOffers()
        // Have the next ones ready only if they'll be used before going stale.
        if (nextDelayIsShort()) prepareOffers()
        val online = to.filter { it.isOpen }
        if (stopped || online.isEmpty()) return offers.forEach { it.peer.close() }
        offers.forEach { pending[it.id] = it }
        sendOffers(online, offers)
        trace("announced ${offers.size} offers to ${online.size} tracker(s); waited ${SystemClock.elapsedRealtime() - offersStarted}ms for offers")
    }

    private fun sendOffers(to: List<TrackerClient>, offers: List<PendingOffer>) {
        val message = buildJsonObject {
            put("action", "announce")
            put("info_hash", topic)
            put("peer_id", myPeerId)
            put("numwant", offers.size)
            putJsonArray("offers") {
                offers.forEach { o ->
                    addJsonObject {
                        put("offer_id", o.id)
                        putJsonObject("offer") { put("type", "offer"); put("sdp", o.sdp) }
                    }
                }
            }
        }
        to.forEach { it.send(message) }
        Diagnostics.count("Direct: offers announced", offers.size.toLong())
    }

    private fun expireOffers() {
        val cutoff = System.currentTimeMillis() - OFFER_TTL_MS
        pending.values.filter { it.peer.createdAt < cutoff }.forEach { pending.remove(it.id); it.peer.close() }
        // Connections that never opened.
        peers.values.filter { !it.isOpen && it.createdAt < cutoff }.forEach { drop(it) }
    }

    suspend fun onTrackerMessage(tracker: TrackerClient, message: JsonObject) {
        if (message.string("info_hash") != topic) return
        val from = message.string("peer_id") ?: return
        val offerId = message.string("offer_id") ?: return
        if (from == myPeerId || stopped) return
        message["offer"]?.jsonObject?.string("sdp")?.let { handleOffer(tracker, from, offerId, it) }
        message["answer"]?.jsonObject?.string("sdp")?.let { handleAnswer(from, offerId, it) }
    }

    private suspend fun handleOffer(tracker: TrackerClient, from: String, offerId: String, sdp: String) {
        if (peers.containsKey(from)) return
        trace("offer from ${from.take(4)}")
        Diagnostics.count("Direct: offers answered")
        val peer = Peer(factory, scope, this).also { it.remotePeerId = from }
        peers[from] = peer
        val answerStarted = SystemClock.elapsedRealtime()
        val answer = runCatching { peer.acceptOffer(sdp) }.getOrElse { e ->
            Log.w(TAG, "Answer failed", e)
            drop(peer)
            return
        }
        tracker.send(buildJsonObject {
            put("action", "announce")
            put("info_hash", topic)
            put("peer_id", myPeerId)
            put("to_peer_id", from)
            put("offer_id", offerId)
            putJsonObject("answer") { put("type", "answer"); put("sdp", answer) }
        })
        trace("answered ${from.take(4)}; answer took ${SystemClock.elapsedRealtime() - answerStarted}ms")
        retryIfNotConnected(peer)
    }

    private suspend fun handleAnswer(from: String, offerId: String, sdp: String) {
        val offer = pending.remove(offerId) ?: return
        trace("answer from ${from.take(4)}")
        peers[from]?.let { existing ->
            // We also answered one of their offers. Both sides keep the lower peer id's offer.
            if (myPeerId > from) return offer.peer.close()
            peers.remove(from)
            existing.close()
        }
        offer.peer.remotePeerId = from
        peers[from] = offer.peer
        runCatching { offer.peer.acceptAnswer(sdp) }.onFailure { e ->
            Log.w(TAG, "Accepting answer failed", e)
            drop(offer.peer)
        }
        retryIfNotConnected(offer.peer)
    }

    /**
     * A direct connection opens within a few hundred ms when it's going to work, while WebRTC takes
     * ~15 s to give up. If [peer] isn't open soon, drop it and try again with fresh ports, which often
     * gets through a NAT that the first attempt didn't.
     */
    private fun retryIfNotConnected(peer: Peer) {
        scope.launch {
            delay(CONNECT_TIMEOUT_MS)
            val id = peer.remotePeerId ?: return@launch
            if (stopped || peer.isOpen || peers[id] !== peer) return@launch
            trace("connection to ${id.take(4)} didn't open in ${CONNECT_TIMEOUT_MS}ms; retrying")
            drop(peer)
            retrySoon()
        }
    }

    override suspend fun onOpen(peer: Peer) {
        if (peer.remotePeerId?.let { peers[it] } !== peer) return peer.close()
        trace("connected to ${peer.remotePeerId?.take(4)}")
        events.onPeerOpen(this, peer)
        events.onChanged(this)
    }

    override suspend fun onMessage(peer: Peer, text: String) = events.onPeerMessage(this, peer, text)

    override suspend fun onClosed(peer: Peer) {
        val id = peer.remotePeerId ?: return
        if (peers[id] !== peer) return
        val wasOpen = peer.isOpen
        drop(peer)
        when {
            // A connection attempt that failed: try again soon rather than at the next round.
            !wasOpen -> retrySoon()
            // Lost the last connection: start looking again.
            openPeers.isEmpty() && !stopped -> {
                searchInterval = SEARCH_INTERVAL_MS
                startAnnouncing(now = true)
            }
        }
    }

    companion object {
        private const val TAG = "Intack"
        /** How often to remind the trackers this device is still in the room. */
        private const val ROOM_INTERVAL_MS = 120_000L
        private const val SEARCH_INTERVAL_MS = 10_000L
        private const val EAGER_INTERVAL_MS = 3_000L
        private const val MAX_SEARCH_INTERVAL_MS = 600_000L
        private const val RETRY_INTERVAL_MS = 10_000L
        private const val CONNECT_TIMEOUT_MS = 5_000L
        private const val OFFER_TTL_MS = 60_000L
        private const val OFFERS_PER_ANNOUNCE = 3
        private const val ID_CHARS = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ"

        /** 20 characters, the length trackers expect for peer and offer ids. */
        fun randomId(): String = (1..20).map { ID_CHARS.random() }.joinToString("")
    }
}

private fun JsonObject.string(key: String): String? =
    runCatching { this[key]?.jsonPrimitive?.content }.getOrNull()
