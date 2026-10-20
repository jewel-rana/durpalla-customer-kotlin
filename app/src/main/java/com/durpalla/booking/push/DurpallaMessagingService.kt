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
        Notifications.show(this, message.notification?.title ?: message.data["title"],
            message.notification?.body ?: message.data["body"], isReminder, message.data["route"])
    }

    override fun onNewToken(token: String) {
        val c = (application as DurpallaApp).container
        c.appScope.launch { c.session.saveDeviceId(token); c.auth.bindPushToken() }
    }
}
