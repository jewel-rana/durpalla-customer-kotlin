package com.durpalla.booking.calling

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import com.durpalla.booking.DurpallaApp
import com.durpalla.booking.ui.screens.IncomingCallScreen
import com.durpalla.booking.ui.theme.DurpallaTheme
import kotlinx.coroutines.launch

/** Full-screen incoming ring (Accept / Decline). */
class IncomingCallActivity : ComponentActivity() {
    private val micPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) pendingAccept = true
    }
    private var pendingAccept = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_ALLOW_LOCK_WHILE_SCREEN_ON,
        )
        val container = (application as DurpallaApp).container
        val callId = intent.getStringExtra(CallNotifications.EXTRA_CALL_ID).orEmpty()
        val tripId = intent.getIntExtra(CallNotifications.EXTRA_TRIP_ID, 0)
        val peer = intent.getStringExtra(CallNotifications.EXTRA_PEER_NAME).orEmpty()
        val autoAccept = intent.getBooleanExtra("autoAccept", false)

        setContent {
            val dark by container.session.darkMode.collectAsState(initial = false)
            val scope = rememberCoroutineScope()
            LaunchedEffect(callId) {
                if (container.calls.session.value == null && callId.isNotBlank()) {
                    container.calls.handleIncoming(callId, tripId, peer)
                }
                if (autoAccept) tryAccept(container)
            }
            LaunchedEffect(pendingAccept) {
                if (pendingAccept) {
                    pendingAccept = false
                    tryAccept(container)
                }
            }
            DurpallaTheme(dark = dark) {
                IncomingCallScreen(
                    peerName = peer,
                    onAccept = {
                        if (hasMic()) scope.launch { container.calls.accept(); finish() }
                        else micPermission.launch(Manifest.permission.RECORD_AUDIO)
                    },
                    onDecline = {
                        scope.launch { container.calls.decline(reason = "declined"); finish() }
                    },
                )
            }
        }
    }

    private fun hasMic() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    private suspend fun tryAccept(container: com.durpalla.booking.AppContainer) {
        if (!hasMic()) {
            micPermission.launch(Manifest.permission.RECORD_AUDIO)
            return
        }
        container.calls.accept()
        // Hand off to MainActivity call UI
        startActivity(
            android.content.Intent(this, com.durpalla.booking.MainActivity::class.java).apply {
                flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("route", "call")
            },
        )
        finish()
    }
}
