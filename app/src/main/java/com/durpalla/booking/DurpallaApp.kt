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
        container = AppContainer(this)
        Notifications.init(this)
        CallNotifications.ensureChannels(this)
        container.pusher.connect { container.session.token }
        // Firebase is initialised automatically by google-services; guard in case the json is missing.
        if (FirebaseApp.getApps(this).isNotEmpty() || FirebaseApp.initializeApp(this) != null) {
            container.appScope.launch {
                runCatching {
                    val token = FirebaseMessaging.getInstance().token.await()
                    if (token.isNotEmpty()) container.session.saveDeviceId(token)
                    container.auth.bindPushToken()
                }.onFailure {
                    // Fallback like PushDeviceService: soft device hint
                    if (container.session.deviceId() == null) container.session.saveDeviceId("fbk_" + android.provider.Settings.Secure.getString(contentResolver, android.provider.Settings.Secure.ANDROID_ID))
                }
            }
        }
    }
}
