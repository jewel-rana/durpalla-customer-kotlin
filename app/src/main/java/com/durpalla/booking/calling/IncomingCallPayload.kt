package com.durpalla.booking.calling

import com.durpalla.booking.R
import com.durpalla.booking.core.AppCtx
import com.durpalla.booking.core.LooseJson
import com.durpalla.booking.core.s
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * FCM / Pusher `call.incoming` payload (contract §2).
 * Optional `provider`: `"agora"` | `"webrtc"` (default webrtc).
 */
data class IncomingCallPayload(
    val callId: String,
    val tripId: String,
    val callerName: String,
    val provider: CallProvider = CallProvider.WEBRTC,
) {
    val tripIdInt: Int
        get() = tripId.toIntOrNull()
            ?: tripId.filter { it.isDigit() }.toIntOrNull()
            ?: tripId.hashCode().and(0x7fff_ffff)

    companion object {
        const val EVENT = "call.incoming"

        fun fromPush(data: Map<String, String>): IncomingCallPayload? {
            val event = data["event"] ?: data["type"]
            if (event != EVENT) return null
            val payload = data["payload"]?.let {
                runCatching { LooseJson.parseToJsonElement(it) as? JsonObject }.getOrNull()
            }
            fun f(k: String): String? =
                data[k]?.takeIf { it.isNotBlank() }
                    ?: (payload?.get(k) as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() }
                    ?: payload?.s(k)
            val callId = f("callId") ?: return null
            val tripId = f("tripId") ?: "0"
            val name = f("callerName").orEmpty().ifBlank { AppCtx.str(R.string.call_driver) }
            val provider = CallProvider.fromWire(f("provider"))
            return IncomingCallPayload(callId, tripId, name, provider)
        }

        fun fromPusher(data: JsonObject?): IncomingCallPayload? {
            if (data == null) return null
            val callId = data.s("callId") ?: return null
            val tripId = data.s("tripId") ?: "0"
            val name = data.s("callerName").orEmpty().ifBlank { AppCtx.str(R.string.call_driver) }
            return IncomingCallPayload(callId, tripId, name, CallProvider.fromWire(data.s("provider")))
        }
    }
}
