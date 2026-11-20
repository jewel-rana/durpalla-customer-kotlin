package com.durpalla.booking.push

import com.durpalla.booking.DurpallaApp
import com.durpalla.booking.R
import com.durpalla.booking.calling.CallNotifications
import com.durpalla.booking.calling.CallStatus
import com.durpalla.booking.calling.IncomingCallPayload
import com.durpalla.booking.core.LooseJson
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * FCM handlers. Calling uses high-priority data messages (contract §2):
 * `{ "event": "call.incoming", "callId", "tripId", "callerName" }`
 * Termination: `call.ended` | `call.declined` | `call.missed` with `{ callId, status }`.
 * Fields may also arrive as a JSON string in `payload`. Accepts legacy `type` as alias for `event`.
 */
class DurpallaMessagingService : FirebaseMessagingService() {
    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data
        IncomingCallPayload.fromPush(data)?.let { call ->
            val app = application as DurpallaApp
            app.container.appScope.launch {
                app.container.calls.handleIncoming(
                    call.callId, call.tripIdInt, call.callerName, call.provider,
                )
            }
            CallNotifications.showIncoming(this, call.callId, call.tripIdInt, call.callerName)
            return
        }
        val event = data["event"] ?: data["type"]
        if (event == "call.ended" || event == "call.declined" || event == "call.missed") {
            val callId = data["callId"] ?: payloadField(data, "callId") ?: return
            val statusWire = data["status"] ?: event.removePrefix("call.")
            val status = CallStatus.fromWire(statusWire).takeIf { it.isTerminal } ?: CallStatus.ENDED
            val app = application as DurpallaApp
            app.container.appScope.launch {
                app.container.calls.onRemoteEnded(callId, status)
            }
            CallNotifications.cancelIncoming(this)
            return
        }

        when (event) {
            "hotel_review_prompt" -> {
                val hid = data["hotel_id"]
                val route = if (!hid.isNullOrBlank()) {
                    "hotel/$hid/${java.time.LocalDate.now()}/${java.time.LocalDate.now().plusDays(1)}"
                } else data["route"]
                val title = message.notification?.title ?: data["title"]
                    ?: getString(R.string.notif_default_title)
                val body = message.notification?.body ?: data["body"]
                    ?: data["hotel_name"]?.let { getString(R.string.hotel_review_prompt, it) }
                    ?: getString(R.string.notif_default_body)
                Notifications.show(this, title, body, false, route)
                return
            }
        }

        val isReminder = data["is_reminder"] == "true"
        val title = message.notification?.title ?: data["title"]
            ?: if (isReminder) getString(R.string.notif_reminder_title)
            else getString(R.string.notif_default_title)
        val body = message.notification?.body ?: data["body"]
            ?: if (isReminder) getString(R.string.notif_reminder_body)
            else getString(R.string.notif_default_body)
        Notifications.show(this, title, body, isReminder, data["route"])
    }

    override fun onNewToken(token: String) {
        val c = (application as DurpallaApp).container
        c.appScope.launch { c.session.saveDeviceId(token); c.auth.bindPushToken() }
    }

    private fun payloadField(data: Map<String, String>, key: String): String? {
        val raw = data["payload"] ?: return null
        val obj = runCatching { LooseJson.parseToJsonElement(raw) as? JsonObject }.getOrNull() ?: return null
        return (obj[key] as? JsonPrimitive)?.content
    }
}
