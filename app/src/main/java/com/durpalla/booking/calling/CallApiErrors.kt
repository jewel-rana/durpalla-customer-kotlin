package com.durpalla.booking.calling

import com.durpalla.booking.R
import com.durpalla.booking.core.AppCtx
import com.durpalla.booking.core.ApiException
import com.durpalla.booking.core.i
import com.durpalla.booking.core.o
import com.durpalla.booking.core.s
import kotlinx.serialization.json.JsonObject

/**
 * Contract error shape `{ "error": { "code": "CALL_BUSY", "message": "…" } }` → localized copy.
 */
object CallApiErrors {
    fun codeOf(r: JsonObject): String? =
        r.o("error").s("code") ?: r.s("code") ?: r.o("data").o("error").s("code")

    fun messageForCode(code: String?): String = when (code?.uppercase()) {
        "UNAUTHORIZED" -> AppCtx.str(R.string.err_call_unauthorized)
        "CALL_TRIP_NOT_FOUND" -> AppCtx.str(R.string.err_call_trip_not_found)
        "CALL_NOT_PARTICIPANT" -> AppCtx.str(R.string.err_call_not_participant)
        "CALL_TRIP_NOT_ACTIVE" -> AppCtx.str(R.string.err_call_trip_not_active)
        "CALL_BUSY" -> AppCtx.str(R.string.err_call_busy)
        "CALL_NOT_FOUND" -> AppCtx.str(R.string.err_call_not_found)
        "CALL_ALREADY_ENDED" -> AppCtx.str(R.string.err_call_already_ended)
        "RATE_LIMITED" -> AppCtx.str(R.string.err_call_rate_limited)
        else -> AppCtx.str(R.string.err_could_not_start_call)
    }

    fun toException(r: JsonObject, fallback: String = AppCtx.str(R.string.err_could_not_start_call)): ApiException {
        val code = codeOf(r)
        val msg = if (code != null) messageForCode(code) else {
            r.o("error").s("message") ?: r.s("message") ?: fallback
        }
        return ApiException(msg, r.i("http_status") ?: 0, code.equals("UNAUTHORIZED", true))
    }

    fun requireCallSuccess(r: JsonObject, fallback: String = AppCtx.str(R.string.err_could_not_start_call)): JsonObject {
        val http = r.i("http_status") ?: 200
        if (http in 200..299) return r
        throw toException(r, fallback)
    }
}

/** End-call reasons accepted by `POST /calls/{id}/end`. */
enum class CallEndReason(val wire: String) {
    HANGUP("hangup"),
    CANCELLED("cancelled"),
    MISSED("missed"),
    FAILED("failed"),
}
