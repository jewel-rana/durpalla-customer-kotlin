package com.durpalla.booking.calling

import com.durpalla.booking.core.i
import com.durpalla.booking.core.o
import com.durpalla.booking.core.s
import com.durpalla.booking.data.net.ApiClient

/**
 * `GET /calls/{callId}/agora-token` → `{ "data": { appId, channel, token, uid, expiresAt } }`.
 * `expiresAt` is epoch **seconds**. `uid` is the customer id (drivers use 2000000000+id).
 * Channel is normally `call_{callId}`; blank → that fallback.
 */
object AgoraTokenClient {
    suspend fun fetch(api: ApiClient, callId: String): AgoraCredentials {
        val r = api.get("calls/$callId/agora-token")
        CallApiErrors.requireCallSuccess(r)
        val o = r.o("data") ?: r
        val appId = o.s("appId") ?: throw CallApiErrors.toException(r)
        val channel = o.s("channel").orEmpty()
        val token = o.s("token") ?: throw CallApiErrors.toException(r)
        val uid = o.i("uid") ?: 0
        val expiresAtSec = o.i("expiresAt")?.toLong()
            ?: o.s("expiresAt")?.toLongOrNull()
        val expiresAtMs = expiresAtSec?.times(1000L)
        return AgoraCredentials(appId, channel, token, uid, expiresAtMs)
    }

    /** Prefer server `channel`; else `call_{callId}`. */
    fun resolveChannel(creds: AgoraCredentials, callId: String): String =
        creds.channel.ifBlank { "call_$callId" }
}
