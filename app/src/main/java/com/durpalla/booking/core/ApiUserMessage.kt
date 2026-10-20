package com.durpalla.booking.core

import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/**
 * Flutter `lib/helpers/api_user_message.dart` — friendly EN copy (BN via string resources in UI).
 */
object ApiUserMessage {
    const val OFFLINE = "You appear to be offline. Check your connection and try again."
    const val TIMEOUT = "The request timed out. Please check your connection and try again."
    const val GENERIC = "Something went wrong. Please try again."
    const val SERVER_UNAVAILABLE = "Our servers are temporarily unavailable. Please try again in a moment."
    const val BAD_RESPONSE = "Received an unexpected response. Please try again."
    const val TLS = "A secure connection could not be established. Please try again."

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
