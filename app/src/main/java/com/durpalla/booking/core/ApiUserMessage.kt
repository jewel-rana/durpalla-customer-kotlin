package com.durpalla.booking.core

import com.durpalla.booking.R
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/**
 * Flutter `lib/helpers/api_user_message.dart` — friendly copy via string resources (remote packs override).
 */
object ApiUserMessage {
    private fun s(id: Int): String = AppCtx.str(id)

    val OFFLINE get() = s(R.string.offline_message)
    val TIMEOUT get() = s(R.string.timeout_message)
    val GENERIC get() = s(R.string.generic_error)
    val SERVER_UNAVAILABLE get() = s(R.string.server_unavailable)
    val BAD_RESPONSE get() = s(R.string.bad_response)
    val TLS get() = s(R.string.tls_message)

    fun fromThrowable(t: Throwable): String = when (t) {
        is ApiException -> when {
            t.message == "offline" -> OFFLINE
            t.message.equals("Invalid server response", true) -> BAD_RESPONSE
            !t.message.isNullOrBlank() -> t.message!!
            else -> GENERIC
        }
        is SocketTimeoutException -> TIMEOUT
        is UnknownHostException, is IOException -> OFFLINE
        is SSLException -> TLS
        else -> t.message?.takeIf { it.isNotBlank() } ?: GENERIC
    }

    fun fromHttp(code: Int, serverMessage: String?): String {
        if (!serverMessage.isNullOrBlank() && !looksTechnical(serverMessage)) return serverMessage
        return when (code) {
            in 500..599 -> SERVER_UNAVAILABLE
            else -> GENERIC
        }
    }

    private fun looksTechnical(msg: String): Boolean {
        val m = msg.lowercase()
        return m.contains("exception") || m.contains("stack") || m.contains("sqlstate") || m.startsWith("<!DOCTYPE")
    }
}
