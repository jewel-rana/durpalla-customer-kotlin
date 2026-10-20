package com.durpalla.booking.calling

import com.durpalla.booking.core.i
import com.durpalla.booking.core.o
import com.durpalla.booking.core.s
import com.durpalla.booking.data.net.ApiClient

/** GET /calls/{callId}/agora-token → { appId, channel, token, uid, expiresAt }. */
object AgoraTokenClient {
    suspend fun fetch(api: ApiClient, callId: String): AgoraCredentials {
        val r = api.get("calls/$callId/agora-token")
        CallApiErrors.requireCallSuccess(r)
        val o = r.o("data") ?: r
        val appId = o.s("appId") ?: r.s("appId")
            ?: throw CallApiErrors.toException(r)
        val channel = o.s("channel") ?: r.s("channel")
            ?: throw CallApiErrors.toException(r)
        val token = o.s("token") ?: r.s("token")
            ?: throw CallApiErrors.toException(r)
        val uid = o.i("uid") ?: r.i("uid") ?: 0
        val expiresRaw = o.i("expiresAt")?.toLong()
            ?: o.s("expiresAt")?.toLongOrNull()
            ?: r.i("expiresAt")?.toLong()
        val expiresAtMs = expiresRaw?.let { v ->
            // Seconds vs millis heuristic.
            if (v < 10_000_000_000L) v * 1000L else v
        }
        return AgoraCredentials(appId, channel, token, uid, expiresAtMs)
    }
}
