package com.durpalla.booking.calling

/**
 * Customer ↔ driver weak-network audio: Opus-only mono + shared fmtp.
 * Must match Durpalla/durpalla-driver `docs/calling-contract.md`.
 */
object OpusSdpMunger {
    /** Exact fmtp payload params (driver parity). */
    const val OPUS_FMTP =
        "useinbandfec=1;usedtx=1;stereo=0;maxaveragebitrate=24000;maxplaybackrate=16000;ptime=20"

    /**
     * Keep only Opus on the audio m-line, rewrite `a=fmtp:<pt> …` to [OPUS_FMTP],
     * and ensure `a=ptime:20`.
     */
    fun munge(sdp: String): String {
        val nl = if (sdp.contains("\r\n")) "\r\n" else "\n"
        val lines = sdp.replace("\r\n", "\n").split("\n")
        val opusPts = linkedSetOf<String>()
        for (l in lines) {
            if (l.startsWith("a=rtpmap:") && l.contains("opus", ignoreCase = true)) {
                payloadType(l.removePrefix("a=rtpmap:"))?.let { opusPts.add(it) }
            }
        }
        if (opusPts.isEmpty()) return sdp

        val out = ArrayList<String>(lines.size)
        var i = 0
        var inAudio = false
        var wrotePtime = false
        var wroteFmtp = false
        while (i < lines.size) {
            val l = lines[i]
            if (l.startsWith("m=")) {
                inAudio = l.startsWith("m=audio ")
                if (inAudio) {
                    val parts = l.split(" ")
                    out.add((parts.take(3) + opusPts).joinToString(" "))
                    wrotePtime = false
                    wroteFmtp = false
                    i++
                    continue
                }
            }
            if (inAudio) {
                when {
                    l.startsWith("a=rtpmap:") -> {
                        val pt = payloadType(l.removePrefix("a=rtpmap:"))
                        if (pt != null && pt in opusPts) out.add(l)
                    }
                    l.startsWith("a=fmtp:") -> {
                        val pt = payloadType(l.removePrefix("a=fmtp:"))
                        if (pt != null && pt in opusPts) {
                            out.add("a=fmtp:$pt $OPUS_FMTP")
                            wroteFmtp = true
                        }
                    }
                    l.startsWith("a=rtcp-fb:") -> {
                        val pt = payloadType(l.removePrefix("a=rtcp-fb:"))
                        if (pt != null && pt in opusPts) out.add(l)
                    }
                    l.startsWith("a=ptime:") -> {
                        out.add("a=ptime:20")
                        wrotePtime = true
                    }
                    else -> out.add(l)
                }
                val next = lines.getOrNull(i + 1)
                if (next == null || next.startsWith("m=")) {
                    if (!wroteFmtp) {
                        out.add("a=fmtp:${opusPts.first()} $OPUS_FMTP")
                        wroteFmtp = true
                    }
                    if (!wrotePtime) {
                        out.add("a=ptime:20")
                        wrotePtime = true
                    }
                }
                i++
                continue
            }
            out.add(l)
            i++
        }
        val joined = out.joinToString(nl)
        return if (sdp.endsWith("\n") || sdp.endsWith("\r\n")) joined + nl else joined
    }

    private fun payloadType(afterPrefix: String): String? {
        val token = afterPrefix.trim().substringBefore(' ').substringBefore('/')
        return token.takeIf { it.isNotEmpty() && it.all(Char::isDigit) }
    }
}
