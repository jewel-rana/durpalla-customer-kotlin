package com.durpalla.booking.data.repo

import com.durpalla.booking.core.*
import com.durpalla.booking.R
import com.durpalla.booking.data.net.ApiClient
import com.durpalla.booking.data.net.requireSuccess
import com.durpalla.booking.data.session.SessionStore
import com.durpalla.booking.security.RecaptchaProvider
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * Flutter `auth_api.dart` on dev/master (email + mobile).
 * Every call except forgot / push-bind sends `channel` = mobile|email and exactly one of `email`/`mobile`.
 */
class AuthRepository(
    private val api: ApiClient,
    private val session: SessionStore,
    private val recaptcha: RecaptchaProvider,
) {

    private fun tokenOf(r: JsonObject) =
        r.s("token") ?: r.s("access_token") ?: r.o("data").s("token") ?: r.o("data").s("access_token")

    private fun userOf(r: JsonObject) =
        r.o("user") ?: r.o("data").o("user") ?: r.o("data").o("customer")

    /** `channel` + `email` XOR `mobile` (E7). */
    private fun AuthIdentifier.identityFields(): Map<String, String> = buildMap {
        put("channel", channel)
        if (isEmail) put("email", email.orEmpty()) else put("mobile", mobile.orEmpty())
    }

    private suspend fun Map<String, String>.withRecaptcha(): Map<String, String> =
        this + ("recaptchaToken" to recaptcha.token("login"))

    fun isUseEmail(r: JsonObject): Boolean {
        val v = r["use_email"] ?: r.o("data")?.get("use_email") ?: return false
        val p = (v as? JsonPrimitive)?.contentOrNull ?: return false
        return p == "true" || p == "1"
    }

    /**
     * `POST auth/check` — handles `use_email` before step inference (E1).
     */
    suspend fun checkStep(id: AuthIdentifier): AuthCheckResult {
        val r = api.postForm("auth/check", id.identityFields().withRecaptcha())
        if (isUseEmail(r)) {
            return AuthCheckResult.UseEmail(
                r.message?.takeIf { it.isNotBlank() }
                    ?: AppCtx.str(R.string.use_email_intl_sms),
            )
        }
        val step = inferStep(r)
            ?: throw ApiException(
                r.message
                    ?: r.o("errors").s("mobile")
                    ?: r.o("errors").s("email")
                    ?: r.s("error")
                    ?: AppCtx.str(R.string.unable_to_continue),
            )
        return AuthCheckResult.Step(step)
    }

    private fun inferStep(r: JsonObject): String? {
        val raw = (r.s("step") ?: r.o("data").s("step"))?.lowercase()?.trim()
        if (raw != null) {
            if (raw.startsWith("log")) return "login"
            if (raw == "otp") return "otp"
            if (raw.startsWith("reg") || raw == "signup" || raw == "sign_up") return "register"
            if (raw == "reset") return "reset"
        }
        fun flag(key: String): Boolean? {
            val v = r.s(key) ?: r.o("data").s(key) ?: return null
            return when (v.lowercase()) {
                "true", "1" -> true
                "false", "0" -> false
                else -> null
            }
        }
        flag("exists")?.let { return if (it) "login" else "register" }
        flag("found")?.let { return if (it) "login" else "register" }
        flag("account_exists")?.let { return if (it) "login" else "register" }
        val msg = (r.message ?: r.s("error")).orEmpty().lowercase()
        if (msg.contains("not found") || msg.contains("no account")) return "register"
        if (msg.contains("exist") || msg.contains("found") || msg.contains("already")) return "login"
        return null
    }

    suspend fun sendOtp(id: AuthIdentifier): JsonObject =
        api.postForm("auth/otp/send", id.identityFields().withRecaptcha())

    /** Register/login verify — field **`otp`**, plus `channel`. */
    suspend fun verifyOtp(id: AuthIdentifier, otp: String, type: String = "register"): JsonObject {
        val r = api.postForm(
            "auth/verify",
            (id.identityFields() + mapOf("otp" to otp, "type" to type)).withRecaptcha(),
        )
        val t = tokenOf(r)
        if (r.success && !t.isNullOrEmpty()) session.saveLogin(t, userOf(r))
        return r
    }

    suspend fun login(id: AuthIdentifier, password: String): JsonObject {
        val device = session.deviceId().orEmpty()
        val r = api.postForm(
            "auth/login",
            (id.identityFields() + mapOf(
                "password" to password.trim(),
                "device_id" to device,
                "device_platform" to "android",
                "fcm_token" to device,
            )).withRecaptcha(),
        )
        if (isUseEmail(r)) return r
        val t = tokenOf(r)
        if (r.success && !t.isNullOrEmpty()) {
            session.saveLogin(t, userOf(r))
            return r
        }
        var msg = r.message ?: r.s("error") ?: r.s("msg") ?: r.s("detail")
            ?: r.o("data").s("message") ?: r.o("data").s("error")
            ?: AppCtx.str(R.string.login_failed)
        if (msg.lowercase().contains("invalid credential")) {
            msg += AppCtx.str(R.string.err_invalid_credential_hint)
        }
        throw ApiException(msg, r.i("http_status") ?: 0, r.b("unauthorized"))
    }

    /**
     * `POST auth/register` — always sends `mobile`; `email` only if non-empty; OTP as `otp_code`.
     */
    suspend fun register(
        id: AuthIdentifier,
        name: String,
        email: String?,
        mobile: String,
        password: String,
        confirmPassword: String,
        otp: String,
        nid: String? = null,
    ): JsonObject {
        val device = session.deviceId().orEmpty()
        val fields = buildMap {
            put("channel", id.channel)
            put("name", name.trim())
            put("mobile", mobile)
            email?.trim()?.takeIf { it.isNotEmpty() }?.let { put("email", it) }
            put("password", password)
            put("confirm_password", confirmPassword)
            put("otp_code", otp)
            put("device_id", device)
            put("device_platform", "android")
            put("fcm_token", device)
            nid?.takeIf { it.isNotBlank() }?.let { put("nid", it) }
        }.withRecaptcha()
        val r = api.postForm("auth/register", fields)
        r.requireSuccess(AppCtx.str(R.string.registration_failed))
        val t = tokenOf(r)
        if (!t.isNullOrEmpty()) session.saveLogin(t, userOf(r))
        else login(id, password)
        return r
    }

    /** Forgot start — **mobile only**, no `channel`, no reCAPTCHA (E5). */
    suspend fun forgotStart(mobile: String): JsonObject =
        api.postForm("auth/forgot", mapOf("mobile" to mobile))

    /** Forgot verify — `otp_code`, no `channel` (E5). */
    suspend fun forgotVerify(mobile: String, otp: String): JsonObject =
        api.postForm(
            "auth/verify",
            mapOf(
                "mobile" to mobile,
                "otp_code" to otp,
                "type" to "forgot",
                "recaptchaToken" to recaptcha.token("login"),
            ),
        )

    /** `POST auth/reset` JSON — mobile only (E5). */
    suspend fun forgotReset(mobile: String, password: String, confirmPassword: String): JsonObject {
        val r = api.postJson(
            "auth/reset",
            jsonOf(
                "mobile" to mobile,
                "password" to password,
                "confirm_password" to confirmPassword,
                "recaptchaToken" to recaptcha.token("login"),
            ),
        )
        val t = tokenOf(r)
        if (r.success && t != null) session.saveLogin(t, userOf(r))
        return r
    }

    suspend fun bindPushToken() {
        val device = session.deviceId() ?: return
        if (!session.isAuthenticated) return
        runCatching { api.postJson("auth/push/bind", jsonOf("token" to device, "platform" to "android")) }
    }

    suspend fun logout() = session.clear()
}
