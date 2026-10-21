package com.durpalla.booking.calling

import com.durpalla.booking.core.i
import com.durpalla.booking.core.o
import com.durpalla.booking.core.s
import com.durpalla.booking.data.net.ApiClient

/**
 * Agora token **renewal** only: `POST /calls/{callId}/token` → `{ "data": { token, expiresAt } }`.
 * Initial credentials come from create/accept ([CallMediaPayload]); there is no GET agora-token.
 *
 * 409 `CALL_PROVIDER_MISMATCH` / `CALL_ALREADY_ENDED` are fatal — stop renewing and end locally.
 */
object AgoraTokenClient {
    data class RenewedToken(
        val token: String,
        val expiresAtMs: Long? = null,
    )

    sealed class RenewResult {
        data class Ok(val token: RenewedToken) : RenewResult()
        /** Stop renewing; end the call locally with [message] (localized). */
        data class Fatal(val code: String, val message: String) : RenewResult()
        data class Failed(val message: String) : RenewResult()
    }

    suspend fun renew(api: ApiClient, callId: String): RenewResult {
        val r = runCatching {
            api.postJson("calls/$callId/token", com.durpalla.booking.core.jsonOf())
        }.getOrElse {
            return RenewResult.Failed(it.message ?: CallApiErrors.messageForCode(null))
        }
        val http = r.i("http_status") ?: 200
        if (http !in 200..299) {
            val code = CallApiErrors.codeOf(r)?.uppercase().orEmpty()
            val message = CallApiErrors.messageForCode(code.ifBlank { null })
            return when (code) {
                "CALL_PROVIDER_MISMATCH", "CALL_ALREADY_ENDED" ->
                    RenewResult.Fatal(code, message)
                else -> RenewResult.Failed(message)
            }
        }
        val o = r.o("data") ?: r
        val token = o.s("token")
            ?: return RenewResult.Failed(CallApiErrors.messageForCode(CallApiErrors.codeOf(r)))
        val expiresAtSec = o.i("expiresAt")?.toLong()
            ?: o.s("expiresAt")?.toLongOrNull()
        return RenewResult.Ok(RenewedToken(token, expiresAtSec?.times(1000L)))
    }
}
