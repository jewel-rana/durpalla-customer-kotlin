package com.durpalla.booking.calling

import com.durpalla.booking.core.i
import com.durpalla.booking.core.o
import com.durpalla.booking.core.s
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject

/**
 * Media bootstrap from `POST /calls` (caller) and `POST /calls/{id}/accept` (callee):
 * `{ data: { callId, provider, agora: {…}|null, iceServers: […]|null, ttl? } }`.
 *
 * Transport is chosen from [provider] (missing → webrtc). [agora] feeds AgoraTransport;
 * [iceServers] feeds WebRtcTransport (null → GET /calls/ice-servers / STUN).
 */
data class CallMediaPayload(
    val callId: String,
    val provider: CallProvider,
    val agora: AgoraCredentials?,
    /** Null means fall back to GET ice-servers / STUN. Non-null is used as-is. */
    val iceServers: IceServersResponse?,
) {
    companion object {
        fun parse(r: JsonObject, fallbackCallId: String? = null): CallMediaPayload {
            val data = r.o("data") ?: r
            val callId = data.s("callId") ?: r.s("callId") ?: fallbackCallId
                ?: throw CallApiErrors.toException(r)
            val provider = CallProvider.fromWire(data.s("provider") ?: r.s("provider"))
            val agoraEl = data["agora"]
            val agora = when {
                agoraEl == null || agoraEl is JsonNull -> null
                agoraEl is JsonObject -> AgoraCredentials.parse(agoraEl)
                else -> null
            }
            val iceEl = data["iceServers"]
            val ttl = data.i("ttl")?.toLong() ?: r.i("ttl")?.toLong()
            val ice = when {
                iceEl == null || iceEl is JsonNull -> null
                iceEl is JsonArray -> IceServersClient.parseArray(iceEl, ttl)
                else -> null
            }
            return CallMediaPayload(callId, provider, agora, ice)
        }
    }
}

/** Optional media handed to [CallTransportFactory] for the current create/accept response. */
data class CallMediaBootstrap(
    val agora: AgoraCredentials? = null,
    val iceServers: IceServersResponse? = null,
) {
    companion object {
        fun from(payload: CallMediaPayload) = CallMediaBootstrap(payload.agora, payload.iceServers)
    }
}
