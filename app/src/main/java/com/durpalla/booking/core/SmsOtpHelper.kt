package com.durpalla.booking.core

import android.app.Activity
import android.app.Notification
import android.content.BroadcastReceiver
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner
import com.google.android.gms.auth.api.phone.SmsRetriever
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.common.api.Status
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

object OtpBus {
    private val _otpEvents = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val otpEvents = _otpEvents.asSharedFlow()

    fun postOtp(code: String) {
        val clean = extractOtp(code) ?: code.filter(Char::isDigit).take(6)
        if (clean.length == 6) {
            _otpEvents.tryEmit(clean)
        }
    }

    fun extractOtp(text: String?): String? {
        if (text.isNullOrBlank()) return null
        return Regex("(?<!\\d)(\\d{6})(?!\\d)").find(text)?.groupValues?.get(1)
    }

    fun isNotificationListenerEnabled(context: Context): Boolean {
        return try {
            val enabledListeners = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
            enabledListeners != null && enabledListeners.contains(context.packageName)
        } catch (_: Exception) {
            false
        }
    }
}

/**
 * Intercepts status bar notifications from Email apps (Gmail, Outlook, Mail) or SMS
 * to auto-read 6-digit OTP verification codes without leaving the app.
 */
class OtpNotificationListenerService : NotificationListenerService() {
    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        val n = sbn?.notification ?: return
        val extras = n.extras ?: return
        val title = extras.getString(Notification.EXTRA_TITLE).orEmpty()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString().orEmpty()
        val combined = "$title $text $bigText"

        val lower = combined.lowercase()
        if (lower.contains("otp") || lower.contains("code") || lower.contains("durpalla") ||
            lower.contains("verification") || lower.contains("verify") || lower.contains("one-time") ||
            lower.contains("পাসওয়ার্ড") || lower.contains("কোড")
        ) {
            val code = OtpBus.extractOtp(combined)
            if (!code.isNullOrBlank()) {
                OtpBus.postOtp(code)
            }
        }
    }
}

class OtpAutoReaderState(
    val pasteFromClipboard: () -> Boolean,
    val hasClipboardCode: () -> Boolean,
    val isNotificationAccessEnabled: () -> Boolean,
    val openNotificationSettings: () -> Unit,
)

/**
 * Reusable Composable hook to auto-read OTP from:
 * 1. Google Play Services SMS User Consent API (zero-permission automatic one-tap prompt)
 * 2. Status bar notification listener (Email notifications e.g. Gmail/Outlook, SMS)
 * 3. Clipboard auto-detection on app resume (when user copies code from notification)
 */
@Composable
fun rememberOtpAutoReader(
    onOtpReceived: (String) -> Unit,
): OtpAutoReaderState {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var lastClipboardCode by remember { mutableStateOf<String?>(null) }

    val smsConsentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val message = result.data?.getStringExtra(SmsRetriever.EXTRA_SMS_MESSAGE)
            val code = OtpBus.extractOtp(message)
            if (!code.isNullOrBlank()) {
                onOtpReceived(code)
            }
        }
    }

    val checkClipboard: () -> String? = remember(context) {
        {
            try {
                val clipMgr = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                val clipText = clipMgr?.primaryClip?.getItemAt(0)?.text?.toString()
                OtpBus.extractOtp(clipText)
            } catch (_: Exception) {
                null
            }
        }
    }

    // Collect from OtpBus (NotificationListenerService)
    LaunchedEffect(Unit) {
        OtpBus.otpEvents.collect { code ->
            if (code.length == 6) {
                onOtpReceived(code)
            }
        }
    }

    // Auto-read clipboard when app resumes
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val clipCode = checkClipboard()
                if (clipCode != null && clipCode != lastClipboardCode) {
                    lastClipboardCode = clipCode
                    onOtpReceived(clipCode)
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Register SMS Consent receiver
    DisposableEffect(Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                if (SmsRetriever.SMS_RETRIEVED_ACTION == intent?.action) {
                    val extras = intent.extras ?: return
                    val status = extras.get(SmsRetriever.EXTRA_STATUS) as? Status ?: return
                    if (status.statusCode == CommonStatusCodes.SUCCESS) {
                        val consentIntent = if (Build.VERSION.SDK_INT >= 33) {
                            extras.getParcelable(SmsRetriever.EXTRA_CONSENT_INTENT, Intent::class.java)
                        } else {
                            @Suppress("DEPRECATION")
                            extras.getParcelable(SmsRetriever.EXTRA_CONSENT_INTENT)
                        }
                        if (consentIntent != null) {
                            try {
                                smsConsentLauncher.launch(consentIntent)
                            } catch (_: Exception) {
                            }
                        }
                    }
                }
            }
        }

        val filter = IntentFilter(SmsRetriever.SMS_RETRIEVED_ACTION)
        try {
            if (Build.VERSION.SDK_INT >= 33) {
                context.registerReceiver(receiver, filter, SmsRetriever.SEND_PERMISSION, null, Context.RECEIVER_EXPORTED)
            } else {
                context.registerReceiver(receiver, filter, SmsRetriever.SEND_PERMISSION, null)
            }
            SmsRetriever.getClient(context).startSmsUserConsent(null)
        } catch (_: Exception) {
        }

        onDispose {
            try {
                context.unregisterReceiver(receiver)
            } catch (_: Exception) {
            }
        }
    }

    return remember(context) {
        OtpAutoReaderState(
            pasteFromClipboard = {
                val code = checkClipboard()
                if (code != null) {
                    onOtpReceived(code)
                    true
                } else false
            },
            hasClipboardCode = { checkClipboard() != null },
            isNotificationAccessEnabled = { OtpBus.isNotificationListenerEnabled(context) },
            openNotificationSettings = {
                try {
                    context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                } catch (_: Exception) {
                }
            }
        )
    }
}
