package app.twodo.data

/**
 * Reads the parts of Android's native crash record ("tombstone", a protocol buffer — see AOSP
 * system/core/debuggerd/proto/tombstone.proto) that say where a crash happened: the signal and fault
 * address, Android's stated cause, and the crashing thread's call stack.
 */
object Tombstone {
    private const val MAX_FUNCTION_NAME = 100

    data class Frame(val relPc: Long, val file: String, val function: String, val offset: Long, val buildId: String)
    data class Thread(val id: Long, val name: String, val frames: List<Frame>)

    data class Crash(
        val signal: String,
        val code: String,
        val faultAddress: Long?,
        val abortMessage: String,
        val causes: List<String>,
        val thread: Thread?,
    ) {
        /** A plain-text summary for a crash report. */
        fun describe(maxFrames: Int = 32): String = buildString {
            append("Signal $signal ($code)")
            faultAddress?.let { append(", fault address 0x${it.toString(16)}") }
            appendLine()
            causes.forEach { appendLine("Cause: $it") }
            if (abortMessage.isNotBlank()) appendLine("Abort: $abortMessage")
            if (thread == null) return@buildString
            appendLine("Crashing thread: ${thread.name} (${thread.id})")
            thread.frames.take(maxFrames).forEachIndexed { i, f ->
                append("  #%02d pc %08x  %s".format(i, f.relPc, f.file.substringAfterLast('/')))
                // C++ names with their full argument types can run to hundreds of characters.
                if (f.function.isNotEmpty()) append(" (${f.function.take(MAX_FUNCTION_NAME)}+${f.offset})")
                if (f.buildId.isNotEmpty() && "libjingle" in f.file) append(" [${f.buildId}]")
                appendLine()
            }
        }
    }

    /** Decodes a tombstone; null if [bytes] isn't one. */
    fun parse(bytes: ByteArray): Crash? = runCatching {
        var tid = -1L
        var signal = "?"
        var code = "?"
        var fault: Long? = null
        var abort = ""
        val causes = mutableListOf<String>()
        val threads = mutableListOf<Thread>()
        Reader(bytes).forEachField { field, value ->
            when (field) {
                6 -> tid = value.number
                10 -> Reader(value.bytes).forEachField { f, v ->
                    when (f) {
                        2 -> signal = v.text
                        4 -> code = v.text
                        9 -> fault = v.number
                    }
                }
                14 -> abort = value.text
                15 -> Reader(value.bytes).forEachField { f, v -> if (f == 1) causes += v.text }
                16 -> Reader(value.bytes).forEachField { f, v -> if (f == 2) threads += thread(v.bytes) }
            }
        }
        if (signal == "?" && threads.isEmpty()) return null
        Crash(signal, code, fault, abort, causes, threads.firstOrNull { it.id == tid } ?: threads.firstOrNull())
    }.getOrNull()

    private fun thread(bytes: ByteArray): Thread {
        var id = 0L
        var name = ""
        val frames = mutableListOf<Frame>()
        Reader(bytes).forEachField { field, value ->
            when (field) {
                1 -> id = value.number
                2 -> name = value.text
                4 -> frames += frame(value.bytes)
            }
        }
        return Thread(id, name, frames)
    }

    private fun frame(bytes: ByteArray): Frame {
        var relPc = 0L
        var function = ""
        var offset = 0L
        var file = ""
        var buildId = ""
        Reader(bytes).forEachField { field, value ->
            when (field) {
                1 -> relPc = value.number
                4 -> function = value.text
                5 -> offset = value.number
                6 -> file = value.text
                8 -> buildId = value.text
            }
        }
        return Frame(relPc, file, function, offset, buildId)
    }

    /** A field's value: a number (varint/fixed) or bytes (length-delimited). */
    class Value(val number: Long, val bytes: ByteArray) {
        val text: String get() = String(bytes)
    }

    /** Minimal protocol buffer reader: just enough to walk fields. */
    class Reader(private val b: ByteArray) {
        private var pos = 0

        private fun varint(): Long {
            var result = 0L
            var shift = 0
            while (true) {
                val x = b[pos++].toInt() and 0xff
                result = result or ((x and 0x7f).toLong() shl shift)
                if (x and 0x80 == 0) return result
                shift += 7
            }
        }

        private fun fixed(n: Int): Long {
            var result = 0L
            for (i in 0 until n) result = result or ((b[pos + i].toLong() and 0xff) shl (8 * i))
            pos += n
            return result
        }

        fun forEachField(action: (field: Int, value: Value) -> Unit) {
            while (pos < b.size) {
                val key = varint()
                val field = (key ushr 3).toInt()
                val value = when ((key and 7).toInt()) {
                    0 -> Value(varint(), EMPTY)
                    1 -> Value(fixed(8), EMPTY)
                    2 -> {
                        val length = varint().toInt()
                        require(length >= 0 && pos + length <= b.size) { "Bad length" }
                        Value(0, b.copyOfRange(pos, pos + length)).also { pos += length }
                    }
                    5 -> Value(fixed(4), EMPTY)
                    else -> error("Unsupported wire type")
                }
                action(field, value)
            }
        }

        private companion object {
            val EMPTY = ByteArray(0)
        }
    }
}
