package com.durpalla.booking.calling

/**
 * Wire statuses match driver contract exactly:
 * `ringing | accepted | declined | ended | missed`
 * (see docs/calling-contract.md / Durpalla/durpalla-driver).
 */
enum class CallStatus {
    IDLE,
    RINGING,
    ACCEPTED,
    DECLINED,
    ENDED,
    MISSED,
    ;

    /** Firestore / API string. */
    val wire: String get() = name.lowercase()

    val isTerminal get() = this == ENDED || this == DECLINED || this == MISSED
    val isActive get() = this == RINGING || this == ACCEPTED

    companion object {
        fun fromWire(raw: String?): CallStatus = when (raw?.lowercase()) {
            "ringing" -> RINGING
            "accepted" -> ACCEPTED
            "declined" -> DECLINED
            "ended" -> ENDED
            "missed" -> MISSED
            else -> IDLE
        }
    }
}

enum class CallRole(val wire: String) {
    CUSTOMER("customer"),
    DRIVER("driver"),
}

data class CallSession(
    val callId: String,
    val tripId: Int,
    val peerName: String,
    val isOutgoing: Boolean,
    val status: CallStatus = CallStatus.RINGING,
    val callerId: String? = null,
    val calleeId: String? = null,
    val answeredAtMs: Long? = null,
    val endedAtMs: Long? = null,
    val endedBy: String? = null,
    val reason: String? = null,
    val muted: Boolean = false,
    val speakerOn: Boolean = false,
)

data class CallLogEntry(
    val callId: String,
    val tripId: Int,
    val peerName: String,
    val status: CallStatus,
    val startedAtMs: Long,
    val durationSec: Long = 0,
    val isOutgoing: Boolean,
)

/** One entry from GET /calls/ice-servers → `[{urls, username, credential}]` (+ ttl). */
data class IceServerEntry(
    val urls: List<String>,
    val username: String? = null,
    val credential: String? = null,
)

data class IceServersResponse(
    val servers: List<IceServerEntry>,
    val ttlSeconds: Long? = null,
)

data class SdpPayload(val type: String, val sdp: String)

/** Subcollection doc: `{candidate, sdpMid, sdpMLineIndex}`. */
data class IceCandidatePayload(
    val candidate: String,
    val sdpMid: String?,
    val sdpMLineIndex: Int,
)
