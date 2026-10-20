package com.durpalla.booking.push

import com.durpalla.booking.DurpallaApp
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.launch

/**
 * Port of the FCM handlers in lib/main.dart + lib/helpers/push_device_service.dart:
 * - foreground & data messages → local notification; `data.is_reminder == "true"` picks the reminder channel.
 * - the FCM token is stored as `device_id` and sent as device_id / fcm_token on login/register,
 *   and bound with `POST auth/push/bind` when signed in. No topics are used by the Flutter app.
 * - Optional `data.route` (e.g. "ticket/123") is forwarded as an in-app deep link.
 */
class DurpallaMessagingService : FirebaseMessagingService() {
    override fun onMessageReceived(message: RemoteMessage) {
        val isReminder = message.data["is_reminder"] == "true"
        // Master: type=hotel_review_prompt → hotel details with reviews
        val route = when (message.data["type"]) {
            "hotel_review_prompt" -> {
                val hid = message.data["hotel_id"]
                if (!hid.isNullOrBlank()) "hotel/$hid/${java.time.LocalDate.now()}/${java.time.LocalDate.now().plusDays(1)}"
                else message.data["route"]
            }
            else -> message.data["route"]
        }
        val title = message.notification?.title ?: message.data["title"]
            ?: if (isReminder) getString(com.durpalla.booking.R.string.notif_reminder_title)
            else getString(com.durpalla.booking.R.string.notif_default_title)
        val body = message.notification?.body ?: message.data["body"]
            ?: if (message.data["type"] == "hotel_review_prompt") {
                message.data["hotel_name"]?.let { "How was your stay at $it?" }
                    ?: getString(com.durpalla.booking.R.string.notif_default_body)
            } else if (isReminder) getString(com.durpalla.booking.R.string.notif_reminder_body)
            else getString(com.durpalla.booking.R.string.notif_default_body)
        Notifications.show(this, title, body, isReminder, route)
    }

    override fun onNewToken(token: String) {
        val c = (application as DurpallaApp).container
        c.appScope.launch { c.session.saveDeviceId(token); c.auth.bindPushToken() }
    }
}
