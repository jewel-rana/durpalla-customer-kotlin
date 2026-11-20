package com.durpalla.booking.calling

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.durpalla.booking.MainActivity
import com.durpalla.booking.R

/** Foreground service with microphone type while a call is active. */
class CallForegroundService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, startFlags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        CallNotifications.ensureChannels(this)
        val peer = intent?.getStringExtra(CallNotifications.EXTRA_PEER_NAME) ?: getString(R.string.call_driver)
        val open = Intent(this, MainActivity::class.java).apply {
            this.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("route", "call")
        }
        val pi = PendingIntent.getActivity(this, 5, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n: Notification = NotificationCompat.Builder(this, CallNotifications.CHANNEL_INCOMING)
            .setSmallIcon(R.drawable.ic_stat_notify)
            .setContentTitle(getString(R.string.call_ongoing_title))
            .setContentText(peer)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setContentIntent(pi)
            .build()
        if (Build.VERSION.SDK_INT >= 30) {
            startForeground(CallNotifications.ID_ONGOING, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            @Suppress("DEPRECATION")
            startForeground(CallNotifications.ID_ONGOING, n)
        }
        return START_STICKY
    }

    override fun onDestroy() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    companion object {
        private const val ACTION_STOP = "com.durpalla.booking.CALL_FG_STOP"

        fun start(ctx: Context, peerName: String, outgoing: Boolean) {
            val i = Intent(ctx, CallForegroundService::class.java)
                .putExtra(CallNotifications.EXTRA_PEER_NAME, peerName)
                .putExtra("outgoing", outgoing)
            if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(i) else ctx.startService(i)
        }

        fun stop(ctx: Context) {
            ctx.startService(Intent(ctx, CallForegroundService::class.java).setAction(ACTION_STOP))
            ctx.stopService(Intent(ctx, CallForegroundService::class.java))
        }
    }
}
