package com.durpalla.booking

import android.app.Application
import com.durpalla.booking.calling.CallNotifications
import com.durpalla.booking.core.AppCtx
import com.durpalla.booking.push.Notifications
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class DurpallaApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        AppCtx.init(this)
        val firebaseReady = runCatching {
            FirebaseApp.getApps(this).isNotEmpty() || FirebaseApp.initializeApp(this) != null
        }.getOrDefault(false)
        container = AppContainer(this)
        Notifications.init(this)
        CallNotifications.ensureChannels(this)
        container.pusher.connect { container.session.token }
        if (firebaseReady) {
            container.appScope.launch {
                runCatching {
                    val token = FirebaseMessaging.getInstance().token.await()
                    if (token.isNotEmpty()) container.session.saveDeviceId(token)
                    container.auth.bindPushToken()
                }.onFailure {
                    if (container.session.deviceId() == null) {
                        container.session.saveDeviceId(
                            "fbk_" + android.provider.Settings.Secure.getString(
                                contentResolver,
                                android.provider.Settings.Secure.ANDROID_ID,
                            ),
                        )
                    }
                }
            }
        }
    }
}
