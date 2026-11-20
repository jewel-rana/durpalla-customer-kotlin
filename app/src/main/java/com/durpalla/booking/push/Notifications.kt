package com.durpalla.booking.push

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
import com.durpalla.booking.MainActivity
import com.durpalla.booking.R

/**
 * Port of lib/services/notification_service.dart. Same channel ids:
 * - `trip_reminder_channel_v2` "Trip Reminders" (Flutter uses raw/event_reminder_voice.mp3 — drop the file into
 *   res/raw/event_reminder_voice.mp3 to restore the custom sound; default sound is used otherwise)
 * - `default_channel` "General Notifications"
 */
object Notifications {
    const val REMINDER = "trip_reminder_channel_v2"
    const val DEFAULT = "default_channel"

    fun init(ctx: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val nm = ctx.getSystemService(NotificationManager::class.java)
        val soundId = ctx.resources.getIdentifier("event_reminder_voice", "raw", ctx.packageName)
        val sound = if (soundId != 0) android.net.Uri.parse("android.resource://${ctx.packageName}/$soundId")
                    else RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        nm.createNotificationChannel(NotificationChannel(REMINDER, ctx.getString(R.string.channel_reminder), NotificationManager.IMPORTANCE_HIGH).apply {
            description = ctx.getString(R.string.channel_reminder_desc)
            setSound(sound, AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION).build())
        })
        nm.createNotificationChannel(NotificationChannel(DEFAULT, ctx.getString(R.string.channel_general), NotificationManager.IMPORTANCE_HIGH).apply {
            description = ctx.getString(R.string.channel_general_desc)
        })
    }

    fun show(ctx: Context, title: String?, body: String?, isReminder: Boolean, deepLink: String? = null) {
        val intent = Intent(ctx, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            deepLink?.let { putExtra("route", it) }
        }
        val pi = PendingIntent.getActivity(ctx, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n = NotificationCompat.Builder(ctx, if (isReminder) REMINDER else DEFAULT)
            .setSmallIcon(R.drawable.ic_stat_notify)
            .setColor(0xFF106E9D.toInt())
            .setContentTitle(title ?: ctx.getString(if (isReminder) R.string.notif_reminder_title else R.string.notif_default_title))
            .setContentText(body ?: ctx.getString(if (isReminder) R.string.notif_reminder_body else R.string.notif_default_body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .build()
        runCatching { NotificationManagerCompat.from(ctx).notify((System.currentTimeMillis() / 1000).toInt(), n) }
    }
}
