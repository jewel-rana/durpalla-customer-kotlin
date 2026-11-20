package com.durpalla.booking.calling

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import com.durpalla.booking.MainActivity
import com.durpalla.booking.R

object CallNotifications {
    const val CHANNEL_INCOMING = "call_incoming_v1"
    const val CHANNEL_MISSED = "call_missed_v1"
    const val ID_INCOMING = 71001
    const val ID_MISSED = 71002
    const val ID_ONGOING = 71003

    fun ensureChannels(ctx: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val nm = ctx.getSystemService(NotificationManager::class.java)
        val ring = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_INCOMING, ctx.getString(R.string.call_channel_incoming), NotificationManager.IMPORTANCE_HIGH).apply {
                description = ctx.getString(R.string.channel_incoming_desc)
                setSound(ring, AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE).build())
                enableVibration(true)
                setBypassDnd(true)
            },
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_MISSED, ctx.getString(R.string.call_channel_missed), NotificationManager.IMPORTANCE_DEFAULT),
        )
    }

    fun showIncoming(ctx: Context, callId: String, tripId: Int, callerName: String) {
        ensureChannels(ctx)
        val fullScreen = Intent(ctx, IncomingCallActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_CALL_ID, callId)
            putExtra(EXTRA_TRIP_ID, tripId)
            putExtra(EXTRA_PEER_NAME, callerName)
        }
        val fullPi = PendingIntent.getActivity(
            ctx, 1, fullScreen,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val acceptPi = PendingIntent.getBroadcast(
            ctx, 2,
            Intent(ctx, CallActionReceiver::class.java).setAction(ACTION_ACCEPT)
                .putExtra(EXTRA_CALL_ID, callId).putExtra(EXTRA_TRIP_ID, tripId).putExtra(EXTRA_PEER_NAME, callerName),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val declinePi = PendingIntent.getBroadcast(
            ctx, 3,
            Intent(ctx, CallActionReceiver::class.java).setAction(ACTION_DECLINE)
                .putExtra(EXTRA_CALL_ID, callId),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val person = Person.Builder().setName(callerName).setImportant(true).build()
        val style = NotificationCompat.CallStyle.forIncomingCall(person, declinePi, acceptPi)
        val n = NotificationCompat.Builder(ctx, CHANNEL_INCOMING)
            .setSmallIcon(R.drawable.ic_stat_notify)
            .setContentTitle(ctx.getString(R.string.call_incoming_title))
            .setContentText(callerName)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setOngoing(true)
            .setFullScreenIntent(fullPi, true)
            .setContentIntent(fullPi)
            .setStyle(style)
            .addPerson(person)
            .build()
        runCatching { NotificationManagerCompat.from(ctx).notify(ID_INCOMING, n) }
    }

    fun cancelIncoming(ctx: Context) {
        NotificationManagerCompat.from(ctx).cancel(ID_INCOMING)
    }

    fun showMissed(ctx: Context, peerName: String, tripId: Int) {
        ensureChannels(ctx)
        val open = Intent(ctx, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("route", "ticket/$tripId")
        }
        val pi = PendingIntent.getActivity(ctx, 4, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n = NotificationCompat.Builder(ctx, CHANNEL_MISSED)
            .setSmallIcon(R.drawable.ic_stat_notify)
            .setContentTitle(ctx.getString(R.string.call_missed_title))
            .setContentText(ctx.getString(R.string.call_missed_body, peerName))
            .setAutoCancel(true)
            .setContentIntent(pi)
            .build()
        runCatching { NotificationManagerCompat.from(ctx).notify(ID_MISSED, n) }
    }

    const val EXTRA_CALL_ID = "callId"
    const val EXTRA_TRIP_ID = "tripId"
    const val EXTRA_PEER_NAME = "peerName"
    const val ACTION_ACCEPT = "com.durpalla.booking.CALL_ACCEPT"
    const val ACTION_DECLINE = "com.durpalla.booking.CALL_DECLINE"
}
