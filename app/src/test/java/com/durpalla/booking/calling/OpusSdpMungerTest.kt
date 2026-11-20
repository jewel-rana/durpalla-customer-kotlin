package com.durpalla.booking.calling

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OpusSdpMungerTest {
    @Test
    fun munge_keepsOpusOnly_andSetsFmtp() {
        val sdp = """
            v=0
            o=- 0 0 IN IP4 127.0.0.1
            s=-
            t=0 0
            m=audio 9 UDP/TLS/RTP/SAVPF 111 0 8
            c=IN IP4 0.0.0.0
            a=rtpmap:111 opus/48000/2
            a=fmtp:111 minptime=10;useinbandfec=1
            a=rtpmap:0 PCMU/8000
            a=rtpmap:8 PCMA/8000
            a=sendrecv
        """.trimIndent().replace("\n", "\r\n")

        val out = OpusSdpMunger.munge(sdp)
        assertTrue(out.contains("m=audio 9 UDP/TLS/RTP/SAVPF 111"))
        assertFalse(out.contains("PCMU"))
        assertFalse(out.contains("PCMA"))
        assertTrue(out.contains("a=fmtp:111 ${OpusSdpMunger.OPUS_FMTP}"))
        assertTrue(out.contains("a=ptime:20"))
        assertTrue(out.contains("stereo=0"))
        assertTrue(out.contains("maxaveragebitrate=24000"))
    }
}
