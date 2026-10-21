package com.durpalla.booking.calling

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.durpalla.booking.DurpallaApp
import kotlinx.coroutines.launch

class CallActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val app = context.applicationContext as? DurpallaApp ?: return
        val calls = app.container.calls
        when (intent?.action) {
            CallNotifications.ACTION_ACCEPT -> {
                CallNotifications.cancelIncoming(context)
                val open = Intent(context, IncomingCallActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    putExtra(CallNotifications.EXTRA_CALL_ID, intent.getStringExtra(CallNotifications.EXTRA_CALL_ID))
                    putExtra(CallNotifications.EXTRA_TRIP_ID, intent.getIntExtra(CallNotifications.EXTRA_TRIP_ID, 0))
                    putExtra(CallNotifications.EXTRA_PEER_NAME, intent.getStringExtra(CallNotifications.EXTRA_PEER_NAME))
                    putExtra("autoAccept", true)
                }
                context.startActivity(open)
                app.container.appScope.launch { calls.accept() }
            }
            CallNotifications.ACTION_DECLINE -> {
                CallNotifications.cancelIncoming(context)
                app.container.appScope.launch { calls.decline(reason = "declined") }
            }
        }
    }
}
