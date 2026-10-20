package com.durpalla.booking.calling

import com.durpalla.booking.core.i
import com.durpalla.booking.core.o
import com.durpalla.booking.core.s
import com.durpalla.booking.data.net.ApiClient

/**
 * Agora token **renewal** only: `POST /calls/{callId}/token` → `{ "data": { token, expiresAt } }`.
 * Initial credentials come from create/accept ([CallMediaPayload]); there is no GET agora-token.
 */
object AgoraTokenClient {
    data class RenewedToken(
        val token: String,
        val expiresAtMs: Long? = null,
    )

    suspend fun renew(api: ApiClient, callId: String): RenewedToken {
        val r = api.postJson("calls/$callId/token", com.durpalla.booking.core.jsonOf())
        CallApiErrors.requireCallSuccess(r)
        val o = r.o("data") ?: r
        val token = o.s("token") ?: throw CallApiErrors.toException(r)
        val expiresAtSec = o.i("expiresAt")?.toLong()
            ?: o.s("expiresAt")?.toLongOrNull()
        return RenewedToken(token, expiresAtSec?.times(1000L))
    }
}
