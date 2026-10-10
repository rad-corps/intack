package app.twodo.data

import android.net.TrafficStats
import android.os.Process
import android.os.SystemClock
import android.system.Os
import android.system.OsConstants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Where the app spends battery, for Settings › Diagnostics: CPU time by thread (from the kernel's
 * per-thread counters in /proc), on screen vs. in the background, network use, and counts of sync
 * activity. Counting and the screen/background split are always on (they cost next to nothing);
 * reading the per-thread counters happens only while diagnostics are switched on, once a minute in
 * the background and every couple of seconds while the overlay is on screen.
 */
object Diagnostics {
    private val counters = ConcurrentHashMap<String, AtomicLong>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var sampler: Job? = null

    private val _enabled = MutableStateFlow(false)
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    /** Counts one (or [n]) of something the app did, e.g. "Trackers: messages in". */
    fun count(name: String, n: Long = 1) {
        counters.getOrPut(name) { AtomicLong() }.addAndGet(n)
    }

    // ---- On screen vs. background (always tracked) -------------------------------------------------------

    private val startedAt = Process.getStartElapsedRealtime()
    private var visible = false
    private var stateSince = startedAt
    private var stateCpuSince = 0L
    private var screenMs = 0L
    private var backgroundMs = 0L
    private var screenCpuMs = 0L
    private var backgroundCpuMs = 0L
    private val rxAtStart = uidRx()
    private val txAtStart = uidTx()

    /** The app came on screen or went off it. */
    @Synchronized
    fun setVisible(nowVisible: Boolean) {
        if (nowVisible == visible) return
        closeState()
        visible = nowVisible
    }

    @Synchronized
    private fun closeState() {
        val now = SystemClock.elapsedRealtime()
        val cpu = Process.getElapsedCpuTime()
        if (visible) {
            screenMs += now - stateSince
            screenCpuMs += cpu - stateCpuSince
        } else {
            backgroundMs += now - stateSince
            backgroundCpuMs += cpu - stateCpuSince
        }
        stateSince = now
        stateCpuSince = cpu
    }

    // ---- Per-thread CPU (while enabled) ------------------------------------------------------------------

    private val msPerTick = 1000.0 / Os.sysconf(OsConstants._SC_CLK_TCK).coerceAtLeast(1)
    /** Last reading of each live thread: tid -> (name, ticks). */
    private var lastByTid = mapOf<Int, Pair<String, Long>>()
    /** CPU ticks seen per thread name since the first reading (threads with the same name are added up). */
    private val ticksByName = HashMap<String, Long>()
    /** Ticks per thread name in the latest interval, for "now". */
    private var recentByName = mapOf<String, Long>()
    private var lastSampleAt = 0L
    private var lastSampleCpu = 0L
    private var recentCpuPercent = 0.0
    private var recentIntervalMs = 0L
    private var lastRx = rxAtStart
    private var lastTx = txAtStart
    private var recentRxPerS = 0.0
    private var recentTxPerS = 0.0
    private var firstSampleAt = 0L
    /** CPU % of each whole minute while enabled, oldest first (last hour). */
    private val minutes = ArrayDeque<Double>()
    private var minuteStartedAt = 0L
    private var minuteStartCpu = 0L

    fun setEnabled(on: Boolean) {
        _enabled.value = on
        sampler?.cancel()
        sampler = null
        if (!on) return
        sampler = scope.launch {
            sample()
            while (isActive) {
                // A plain delay: it doesn't wake a sleeping phone, it just catches up when it wakes.
                delay(BACKGROUND_SAMPLE_MS)
                sample()
            }
        }
    }

    /** Reads every thread's CPU counters. At most once a second; cheap (a few hundred microseconds). */
    @Synchronized
    fun sample() {
        val now = SystemClock.elapsedRealtime()
        if (now - lastSampleAt < 1_000) return
        val cpu = Process.getElapsedCpuTime()
        val current = HashMap<Int, Pair<String, Long>>()
        File("/proc/self/task").listFiles()?.forEach { dir ->
            val tid = dir.name.toIntOrNull() ?: return@forEach
            readThread(File(dir, "stat"))?.let { current[tid] = it }
        }
        val recent = HashMap<String, Long>()
        for ((tid, reading) in current) {
            val (name, ticks) = reading
            val before = lastByTid[tid]?.takeIf { it.first == name }?.second ?: 0L
            val delta = (ticks - before).coerceAtLeast(0)
            // The first reading is only the starting point.
            if (delta > 0 && lastSampleAt > 0) {
                ticksByName[name] = (ticksByName[name] ?: 0) + delta
                recent[name] = (recent[name] ?: 0) + delta
            }
        }
        val rx = uidRx()
        val tx = uidTx()
        if (lastSampleAt > 0) {
            recentIntervalMs = now - lastSampleAt
            recentCpuPercent = 100.0 * (cpu - lastSampleCpu) / recentIntervalMs
            recentByName = recent
            recentRxPerS = (rx - lastRx) * 1000.0 / recentIntervalMs
            recentTxPerS = (tx - lastTx) * 1000.0 / recentIntervalMs
        } else {
            firstSampleAt = now
            minuteStartedAt = now
            minuteStartCpu = cpu
        }
        if (now - minuteStartedAt >= 60_000) {
            minutes.addLast(100.0 * (cpu - minuteStartCpu) / (now - minuteStartedAt))
            while (minutes.size > 60) minutes.removeFirst()
            minuteStartedAt = now
            minuteStartCpu = cpu
        }
        lastByTid = current
        lastSampleAt = now
        lastSampleCpu = cpu
        lastRx = rx
        lastTx = tx
    }

    /** "tid (name) S ppid … utime stime …" — the name can contain spaces and brackets. */
    private fun readThread(stat: File): Pair<String, Long>? = runCatching {
        val text = stat.readText()
        val name = text.substring(text.indexOf('(') + 1, text.lastIndexOf(')'))
        val fields = text.substring(text.lastIndexOf(')') + 2).split(' ')
        // fields[0] is the state (field 3), so utime (field 14) and stime (field 15) are at 11 and 12.
        name to fields[11].toLong() + fields[12].toLong()
    }.getOrNull()

    // ---- Report ------------------------------------------------------------------------------------------

    /** Plain text, shown in the overlay and copied to send on. */
    @Synchronized
    fun report(header: String): String {
        closeState()
        val now = SystemClock.elapsedRealtime()
        val upMs = now - startedAt
        val cpuMs = Process.getElapsedCpuTime()
        val out = StringBuilder()
        out.appendLine("$header · ${LocalTime.now().format(TIME)}")
        out.appendLine("App running ${duration(upMs)}: on screen ${duration(screenMs)}, background ${duration(backgroundMs)}")
        out.appendLine("CPU % is of one core; 100% = one core flat out.")
        out.appendLine()
        out.appendLine("CPU now ${pct(recentCpuPercent)} (last ${recentIntervalMs / 1000} s)")
        out.appendLine("CPU average ${pct(100.0 * cpuMs / upMs.coerceAtLeast(1))} = ${seconds(cpuMs)} since the app started")
        out.appendLine("  on screen  ${pct(100.0 * screenCpuMs / screenMs.coerceAtLeast(1))} (${seconds(screenCpuMs)})")
        out.appendLine("  background ${pct(100.0 * backgroundCpuMs / backgroundMs.coerceAtLeast(1))} (${seconds(backgroundCpuMs)})")
        if (minutes.isNotEmpty()) {
            val max = minutes.max().coerceAtLeast(1.0)
            out.appendLine("Per minute, last ${minutes.size} (top = ${pct(max)}):")
            out.appendLine("  " + minutes.joinToString("") { BARS[((it / max) * (BARS.length - 1)).toInt().coerceIn(0, BARS.length - 1)].toString() })
        }

        if (lastSampleAt > 0) {
            val sinceMs = now - firstSampleAt
            val counted = ticksByName.values.sum() * msPerTick
            out.appendLine()
            out.appendLine("CPU by area (since diagnostics started, ${duration(sinceMs)} ago):")
            val byArea = HashMap<String, Double>()
            ticksByName.forEach { (name, ticks) -> byArea.merge(area(name), ticks * msPerTick, Double::plus) }
            val recentByArea = HashMap<String, Double>()
            recentByName.forEach { (name, ticks) -> recentByArea.merge(area(name), ticks * msPerTick, Double::plus) }
            val total = counted.coerceAtLeast(1.0)
            byArea.entries.sortedByDescending { it.value }.forEach { (area, ms) ->
                val nowPct = 100.0 * (recentByArea[area] ?: 0.0) / recentIntervalMs.coerceAtLeast(1)
                out.appendLine("  ${area.padEnd(18)} ${seconds(ms.toLong()).padStart(8)} ${share(ms / total)}  now ${pct(nowPct)}")
            }
            out.appendLine()
            out.appendLine("Busiest threads (CPU since diagnostics started · now):")
            ticksByName.entries.sortedByDescending { it.value }.take(12).forEach { (name, ticks) ->
                val nowPct = 100.0 * (recentByName[name] ?: 0) * msPerTick / recentIntervalMs.coerceAtLeast(1)
                out.appendLine("  ${name.take(18).padEnd(18)} ${seconds((ticks * msPerTick).toLong()).padStart(8)}  ${pct(nowPct)}")
            }
        }

        out.appendLine()
        val rx = uidRx() - rxAtStart
        val tx = uidTx() - txAtStart
        out.appendLine("Network since the app started: in ${size(rx)}, out ${size(tx)}")
        out.appendLine("  now in ${size(recentRxPerS.toLong())}/s, out ${size(recentTxPerS.toLong())}/s")

        out.appendLine()
        val hours = upMs / 3_600_000.0
        out.appendLine("Activity since the app started (total · per hour):")
        counters.entries.sortedBy { it.key }.forEach { (name, value) ->
            val n = value.get()
            val shown = if (name.endsWith(" data")) size(n) else n.toString()
            val rate = if (name.endsWith(" data")) size((n / hours).toLong()) else "%.0f".format(n / hours)
            out.appendLine("  ${name.padEnd(34)} ${shown.padStart(8)} · $rate")
        }
        return out.toString().trimEnd()
    }

    /** A short line for the collapsed overlay. */
    @Synchronized
    fun summary(): String {
        val upMs = SystemClock.elapsedRealtime() - startedAt
        return "CPU ${pct(recentCpuPercent)} · avg ${pct(100.0 * Process.getElapsedCpuTime() / upMs.coerceAtLeast(1))}"
    }

    /** Groups threads by what they're for, from their (15-character) names. */
    private fun area(name: String): String = when {
        name.startsWith("network_thread") || name.startsWith("worker_thread") || name.startsWith("signaling_thre") ||
            name.startsWith("rtc") || name.startsWith("webrtc") || name.startsWith("TwoDo-dispose") -> "Direct (WebRTC)"
        name.startsWith("OkHttp") -> "Trackers & relays"
        name.startsWith("TwoDo-sync") -> "Sync"
        name.startsWith("DefaultDispatch") || name.startsWith("androidx.work") || name.startsWith("WM.task") -> "App work"
        name == "app.twodo" || name.startsWith("app.twodo") || name == "RenderThread" || name.startsWith("hwui") -> "Screen"
        name.startsWith("HeapTaskDaemon") || name.startsWith("Finalizer") || name.startsWith("ReferenceQueue") ||
            name.startsWith("Jit thread") || name.startsWith("Profile Saver") || name.startsWith("Signal Catcher") ||
            name.startsWith("binder") || name.startsWith("HwBinder") -> "Android upkeep"
        else -> "Other"
    }

    private fun uidRx() = TrafficStats.getUidRxBytes(Process.myUid()).coerceAtLeast(0)
    private fun uidTx() = TrafficStats.getUidTxBytes(Process.myUid()).coerceAtLeast(0)

    private fun pct(p: Double) = if (p < 10) "%.1f%%".format(p) else "%.0f%%".format(p)
    private fun share(f: Double) = "%3.0f%%".format(100 * f)
    private fun seconds(ms: Long) = if (ms < 100_000) "%.1f s".format(ms / 1000.0) else "%.0f s".format(ms / 1000.0)
    private fun size(bytes: Long) = when {
        bytes < 10_000 -> "$bytes B"
        bytes < 10_000_000 -> "${bytes / 1000} KB"
        else -> "${bytes / 1_000_000} MB"
    }
    private fun duration(ms: Long): String {
        val minutes = ms / 60_000
        return if (minutes < 60) "${minutes}m" else "${minutes / 60}h ${minutes % 60}m"
    }

    private const val BACKGROUND_SAMPLE_MS = 60_000L
    private const val BARS = "▁▂▃▄▅▆▇█"
    private val TIME = DateTimeFormatter.ofPattern("HH:mm:ss")
}
