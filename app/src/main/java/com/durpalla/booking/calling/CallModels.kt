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

/**
 * Server-selected audio transport.
 * Server launch default is `agora` (server always sends `provider`).
 * Client omit / unknown → `webrtc` (matches driver app).
 */
enum class CallProvider(val wire: String) {
    WEBRTC("webrtc"),
    AGORA("agora"),
    ;

    companion object {
        fun fromWire(raw: String?): CallProvider = when (raw?.lowercase()?.trim()) {
            "agora" -> AGORA
            else -> WEBRTC
        }
    }
}

/**
 * Agora block from create/accept: `{ appId, channel, token, uid, expiresAt }`.
 * `uid` = customer id (drivers use 2000000000+id). App ID never hard-coded.
 * Renewal uses `POST /calls/{id}/token` → `{ data: { token, expiresAt } }` only.
 */
data class AgoraCredentials(
    val appId: String,
    val channel: String,
    val token: String,
    val uid: Int,
    /** Derived from wire `expiresAt` (epoch seconds) × 1000. */
    val expiresAtMs: Long? = null,
) {
    companion object {
        fun parse(o: kotlinx.serialization.json.JsonObject): AgoraCredentials? {
            val appId = with(com.durpalla.booking.core) { o.s("appId") } ?: return null
            val token = with(com.durpalla.booking.core) { o.s("token") } ?: return null
            val channel = with(com.durpalla.booking.core) { o.s("channel") }.orEmpty()
            val uid = with(com.durpalla.booking.core) { o.i("uid") } ?: 0
            val expiresAtSec = with(com.durpalla.booking.core) {
                o.i("expiresAt")?.toLong() ?: o.s("expiresAt")?.toLongOrNull()
            }
            return AgoraCredentials(appId, channel, token, uid, expiresAtSec?.times(1000L))
        }

        /** Prefer server `channel`; else `call_{callId}`. */
        fun resolveChannel(creds: AgoraCredentials, callId: String): String =
            creds.channel.ifBlank { "call_$callId" }
    }
}

data class CallSession(
    val callId: String,
    val tripId: Int,
    val peerName: String,
    val isOutgoing: Boolean,
    val status: CallStatus = CallStatus.RINGING,
    val provider: CallProvider = CallProvider.WEBRTC,
    val callerId: String? = null,
    val calleeId: String? = null,
    val answeredAtMs: Long? = null,
    val endedAtMs: Long? = null,
    val endedBy: String? = null,
    val reason: String? = null,
    val muted: Boolean = false,
    val speakerOn: Boolean = false,
    /** True when adaptive bitrate / Agora network quality reports a weak link. */
    val weakConnection: Boolean = false,
    /** Localized user-facing note (e.g. renew failure); shown on the call UI. */
    val userMessage: String? = null,
)

/**
 * Trip payload `callLog[]` entry (oldest first), plus local session appends.
 * Wire fields: callId, callerRole, status, provider, createdAt, answeredAt, endedAt, durationSec, endedBy, reason.
 */
data class CallLogEntry(
    val callId: String,
    val tripId: Int,
    val peerName: String = "",
    val callerRole: String = "",
    val status: CallStatus,
    val provider: CallProvider = CallProvider.WEBRTC,
    val createdAtMs: Long = 0L,
    val answeredAtMs: Long? = null,
    val endedAtMs: Long? = null,
    val durationSec: Long = 0,
    val endedBy: String? = null,
    val reason: String? = null,
    val isOutgoing: Boolean = false,
) {
    val startedAtMs get() = createdAtMs

    companion object {
        fun fromTripJson(m: kotlinx.serialization.json.JsonObject, tripId: Int): CallLogEntry? {
            val callId = with(com.durpalla.booking.core) { m.s("callId") } ?: return null
            val role = with(com.durpalla.booking.core) { m.str("callerRole") }
            return CallLogEntry(
                callId = callId,
                tripId = tripId,
                callerRole = role,
                status = CallStatus.fromWire(with(com.durpalla.booking.core) { m.s("status") }),
                provider = CallProvider.fromWire(with(com.durpalla.booking.core) { m.s("provider") }),
                createdAtMs = with(com.durpalla.booking.core) {
                    m.d("createdAt")?.toLong() ?: m.i("createdAt")?.toLong() ?: 0L
                },
                answeredAtMs = with(com.durpalla.booking.core) {
                    m.d("answeredAt")?.toLong() ?: m.i("answeredAt")?.toLong()
                },
                endedAtMs = with(com.durpalla.booking.core) {
                    m.d("endedAt")?.toLong() ?: m.i("endedAt")?.toLong()
                },
                durationSec = with(com.durpalla.booking.core) { m.i("durationSec")?.toLong() ?: 0L },
                endedBy = with(com.durpalla.booking.core) { m.s("endedBy") },
                reason = with(com.durpalla.booking.core) { m.s("reason") },
                isOutgoing = role == CallRole.CUSTOMER.wire,
            )
        }
    }
}

/** One ICE server entry (`urls` string|array + optional username/credential). */
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
