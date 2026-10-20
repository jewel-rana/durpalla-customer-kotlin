package com.durpalla.booking.push

import com.durpalla.booking.DurpallaApp
import com.durpalla.booking.calling.CallNotifications
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.launch

/**
 * FCM handlers + high-priority `call.incoming` for driver calling.
 * TODO(calling): Durpalla/durpalla-driver docs/calling-contract.md
 * Pusher private channels are a secondary ring path (POST /realtime/auth).
 */
class DurpallaMessagingService : FirebaseMessagingService() {
    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data
        when (data["type"]) {
            "call.incoming" -> {
                // High-priority data: {callId, tripId, callerName}
                val callId = data["callId"].orEmpty()
                val tripId = data["tripId"]?.toIntOrNull() ?: 0
                val callerName = data["callerName"].orEmpty().ifBlank { getString(com.durpalla.booking.R.string.call_driver) }
                if (callId.isBlank()) return
                val app = application as DurpallaApp
                app.container.appScope.launch {
                    app.container.calls.handleIncoming(callId, tripId, callerName)
                }
                CallNotifications.showIncoming(this, callId, tripId, callerName)
                return
            }
            "hotel_review_prompt" -> {
                val hid = data["hotel_id"]
                val route = if (!hid.isNullOrBlank()) {
                    "hotel/$hid/${java.time.LocalDate.now()}/${java.time.LocalDate.now().plusDays(1)}"
                } else data["route"]
                val title = message.notification?.title ?: data["title"]
                    ?: getString(com.durpalla.booking.R.string.notif_default_title)
                val body = message.notification?.body ?: data["body"]
                    ?: data["hotel_name"]?.let { "How was your stay at $it?" }
                    ?: getString(com.durpalla.booking.R.string.notif_default_body)
                Notifications.show(this, title, body, false, route)
                return
            }
        }

        val isReminder = data["is_reminder"] == "true"
        val title = message.notification?.title ?: data["title"]
            ?: if (isReminder) getString(com.durpalla.booking.R.string.notif_reminder_title)
            else getString(com.durpalla.booking.R.string.notif_default_title)
        val body = message.notification?.body ?: data["body"]
            ?: if (isReminder) getString(com.durpalla.booking.R.string.notif_reminder_body)
            else getString(com.durpalla.booking.R.string.notif_default_body)
        Notifications.show(this, title, body, isReminder, data["route"])
    }

    override fun onNewToken(token: String) {
        val c = (application as DurpallaApp).container
        c.appScope.launch { c.session.saveDeviceId(token); c.auth.bindPushToken() }
    }
}
