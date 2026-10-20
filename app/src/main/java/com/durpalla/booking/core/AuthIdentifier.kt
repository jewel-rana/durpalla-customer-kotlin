package com.durpalla.booking.core

import android.net.Uri

/**
 * Auth identity for Flutter email+mobile flow (dev/master).
 * Every auth call (except forgot) sends `channel` = mobile|email plus exactly one of `email`/`mobile`.
 */
data class AuthIdentifier(
    val channel: String,
    val mobile: String? = null,
    val email: String? = null,
) {
    init {
        require(channel == CHANNEL_MOBILE || channel == CHANNEL_EMAIL) { "channel=$channel" }
    }

    val display: String
        get() = if (channel == CHANNEL_EMAIL) email.orEmpty() else mobile.orEmpty()

    val isEmail: Boolean get() = channel == CHANNEL_EMAIL
    val isMobile: Boolean get() = channel == CHANNEL_MOBILE

    fun encode(): String =
        listOf(channel, Uri.encode(mobile.orEmpty()), Uri.encode(email.orEmpty())).joinToString("|")

    companion object {
        const val CHANNEL_MOBILE = "mobile"
        const val CHANNEL_EMAIL = "email"

        fun mobile(mobile: String) = AuthIdentifier(CHANNEL_MOBILE, mobile = mobile)
        fun email(email: String) = AuthIdentifier(CHANNEL_EMAIL, email = email)

        fun decode(raw: String?): AuthIdentifier? {
            if (raw.isNullOrBlank()) return null
            val parts = raw.split("|")
            if (parts.size < 3) return null
            val channel = parts[0]
            val mobile = Uri.decode(parts[1]).ifBlank { null }
            val email = Uri.decode(parts[2]).ifBlank { null }
            return runCatching { AuthIdentifier(channel, mobile, email) }.getOrNull()
        }
    }
}

/** Result of `POST auth/check` including Flutter's `use_email` signal. */
sealed class AuthCheckResult {
    data class UseEmail(val message: String) : AuthCheckResult()
    data class Step(val step: String) : AuthCheckResult()
}
