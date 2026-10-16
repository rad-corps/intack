package app.intack.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream

class TombstoneTest {
    /** Tiny protobuf writer for building test tombstones. */
    private class Msg {
        private val out = ByteArrayOutputStream()
        private fun varint(v: Long) {
            var x = v
            while (true) {
                if (x and 0x7fL.inv() == 0L) { out.write(x.toInt()); return }
                out.write(((x and 0x7f) or 0x80).toInt()); x = x ushr 7
            }
        }
        fun num(field: Int, v: Long) = apply { varint((field shl 3).toLong()); varint(v) }
        fun bytes(field: Int, b: ByteArray) = apply { varint(((field shl 3) or 2).toLong()); varint(b.size.toLong()); out.write(b) }
        fun str(field: Int, s: String) = bytes(field, s.toByteArray())
        fun msg(field: Int, m: Msg) = bytes(field, m.build())
        fun build(): ByteArray = out.toByteArray()
    }

    private fun frame(relPc: Long, file: String, fn: String = "", off: Long = 0) =
        Msg().num(1, relPc).str(4, fn).num(5, off).str(6, file).str(8, "abc123")

    @Test
    fun readsSignalFaultAndCrashingThread() {
        val other = Msg().num(1, 100).str(2, "main").msg(4, frame(0x10, "/system/lib64/libc.so", "__epoll_pwait", 10))
        val crashing = Msg().num(1, 200).str(2, "rtc_event_log")
            .msg(4, frame(0x1234, "/data/app/x/base.apk!libjingle_peerconnection_so.so"))
            .msg(4, frame(0x5678, "/apex/com.android.runtime/lib64/bionic/libc.so", "__pthread_start", 64))
        val tombstone = Msg()
            .num(6, 200)
            .msg(10, Msg().num(1, 11).str(2, "SIGSEGV").num(3, 1).str(4, "SEGV_MAPERR").num(8, 1).num(9, 0xdeadbeefL))
            .msg(15, Msg().str(1, "null pointer dereference"))
            .msg(16, Msg().num(1, 100).msg(2, other))
            .msg(16, Msg().num(1, 200).msg(2, crashing))
            .build()

        val crash = Tombstone.parse(tombstone)!!
        assertEquals("SIGSEGV", crash.signal)
        assertEquals("SEGV_MAPERR", crash.code)
        assertEquals(0xdeadbeefL, crash.faultAddress)
        assertEquals("rtc_event_log", crash.thread?.name)
        assertEquals(2, crash.thread?.frames?.size)
        val text = crash.describe()
        assertTrue(text, "fault address 0xdeadbeef" in text)
        assertTrue(text, "Cause: null pointer dereference" in text)
        assertTrue(text, "#00 pc 00001234  base.apk!libjingle_peerconnection_so.so [abc123]" in text)
        assertTrue(text, "#01 pc 00005678  libc.so (__pthread_start+64)" in text)
    }

    @Test
    fun garbageIsNotATombstone() {
        assertNull(Tombstone.parse("not a tombstone at all".toByteArray()))
    }
}
