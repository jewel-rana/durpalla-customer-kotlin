package com.durpalla.booking.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.durpalla.booking.R
import com.durpalla.booking.calling.CallStatus
import com.durpalla.booking.core.BanglaDigits
import com.durpalla.booking.ui.nav.LocalContainer
import com.durpalla.booking.ui.nav.LocalNav
import com.durpalla.booking.ui.theme.Brand
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
fun OutgoingCallScreen() {
    val c = LocalContainer.current
    val nav = LocalNav.current
    val session by c.calls.session.collectAsState()
    val scope = rememberCoroutineScope()
    val locale = LocalConfiguration.current.locales[0] ?: Locale.getDefault()
    var elapsed by remember { mutableLongStateOf(0L) }

    LaunchedEffect(session?.status, session?.answeredAtMs) {
        elapsed = 0
        while (session?.status == CallStatus.ACCEPTED) {
            val start = session?.answeredAtMs ?: System.currentTimeMillis()
            elapsed = (System.currentTimeMillis() - start) / 1000
            delay(1000)
        }
    }
    LaunchedEffect(session?.status) {
        if (session?.status?.isTerminal == true) {
            delay(1200)
            nav.back()
        }
    }

    val s = session
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Brand.Navy, Brand.Primary.copy(0.85f), Brand.Navy))),
    ) {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(48.dp))
            Text(
                s?.peerName ?: stringResource(R.string.call_driver),
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(8.dp))
            Text(statusLabel(s?.status), color = Color.White.copy(0.85f))
            if (s?.status == CallStatus.ACCEPTED) {
                Text(
                    BanglaDigits.formatDuration(elapsed, locale),
                    style = MaterialTheme.typography.displaySmall,
                    color = Color.White,
                    modifier = Modifier.padding(top = 16.dp),
                )
            }
            Spacer(Modifier.weight(1f))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CallRoundButton(
                    icon = if (s?.muted == true) Icons.Default.MicOff else Icons.Default.Mic,
                    label = stringResource(R.string.call_mute),
                    selected = s?.muted == true,
                ) { scope.launch { c.calls.setMuted(!(s?.muted ?: false)) } }
                CallRoundButton(
                    icon = Icons.Default.CallEnd,
                    label = stringResource(R.string.call_end),
                    danger = true,
                ) { scope.launch { c.calls.end() } }
                CallRoundButton(
                    icon = if (s?.speakerOn == true) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                    label = stringResource(R.string.call_speaker),
                    selected = s?.speakerOn == true,
                ) { scope.launch { c.calls.setSpeaker(!(s?.speakerOn ?: false)) } }
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
fun IncomingCallScreen(peerName: String, onAccept: () -> Unit, onDecline: () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Brand.Navy, Color(0xFF0F3D56)))),
    ) {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(64.dp))
            Text(stringResource(R.string.call_incoming_title), color = Color.White.copy(0.8f))
            Spacer(Modifier.height(12.dp))
            Text(peerName, style = MaterialTheme.typography.headlineLarge, color = Color.White, fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.call_status_ringing), color = Color.White.copy(0.75f), modifier = Modifier.padding(top = 8.dp))
            Spacer(Modifier.weight(1f))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                CallRoundButton(Icons.Default.CallEnd, stringResource(R.string.call_decline), danger = true, onClick = onDecline)
                CallRoundButton(Icons.Default.Call, stringResource(R.string.call_accept), success = true, onClick = onAccept)
            }
            Spacer(Modifier.height(40.dp))
        }
    }
}

@Composable
fun CallDriverButton(
    tripId: Int,
    driverName: String,
    bidAccepted: Boolean = false,
    tripActive: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val c = LocalContainer.current
    val nav = LocalNav.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    if (!c.calls.canCallDriver(tripId, bidAccepted, tripActive)) return

    fun startCall() = scope.launch {
        val display = with(com.durpalla.booking.core) { c.session.user.value.str("name", default = "You") }
        c.calls.startOutgoing(tripId, driverName, display)
        nav.go("call")
    }

    val mic = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) startCall()
    }

    Button(
        onClick = {
            val granted = ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
            if (granted) startCall() else mic.launch(Manifest.permission.RECORD_AUDIO)
        },
        modifier = modifier.fillMaxWidth(),
        colors = ButtonDefaults.buttonColors(containerColor = Brand.Primary),
    ) {
        Icon(Icons.Default.Call, null)
        Spacer(Modifier.width(8.dp))
        Text(stringResource(R.string.call_driver))
    }
}

@Composable
fun CallLogSection(tripId: Int) {
    val c = LocalContainer.current
    val log by c.calls.logForTrip(tripId).collectAsState(initial = emptyList())
    val locale = LocalConfiguration.current.locales[0] ?: Locale.getDefault()
    if (log.isEmpty()) return
    Column(Modifier.padding(vertical = 8.dp)) {
        Text(stringResource(R.string.call_log_title), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        log.forEach { e ->
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (e.status == CallStatus.MISSED) Icons.Default.CallEnd else Icons.Default.Call,
                    null,
                    tint = if (e.status == CallStatus.MISSED) Brand.Error else Brand.Primary,
                )
                Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                    Text(e.peerName, style = MaterialTheme.typography.titleSmall)
                    Text(
                        when (e.status) {
                            CallStatus.MISSED -> stringResource(R.string.call_status_missed)
                            CallStatus.DECLINED -> stringResource(R.string.call_status_declined)
                            CallStatus.ENDED -> stringResource(R.string.call_status_ended)
                            else -> e.status.name
                        },
                        color = Brand.Muted,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (e.durationSec > 0) {
                    Text(BanglaDigits.formatDuration(e.durationSec, locale), style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@Composable
private fun CallRoundButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    selected: Boolean = false,
    danger: Boolean = false,
    success: Boolean = false,
    onClick: () -> Unit,
) {
    val bg = when {
        danger -> Brand.Error
        success -> Brand.Success
        selected -> Color.White
        else -> Color.White.copy(0.18f)
    }
    val tint = when {
        selected && !danger && !success -> Brand.Navy
        else -> Color.White
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        FilledIconButton(
            onClick = onClick,
            modifier = Modifier.size(72.dp).clip(CircleShape),
            colors = IconButtonDefaults.filledIconButtonColors(containerColor = bg, contentColor = tint),
        ) { Icon(icon, label, Modifier.size(28.dp)) }
        Spacer(Modifier.height(6.dp))
        Text(label, color = Color.White.copy(0.9f), style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun statusLabel(status: CallStatus?): String = when (status) {
    // Outgoing before answer still shows "Calling…" while wire status is ringing
    CallStatus.RINGING -> stringResource(R.string.call_status_calling)
    CallStatus.ACCEPTED -> stringResource(R.string.call_status_connected)
    CallStatus.ENDED -> stringResource(R.string.call_status_ended)
    CallStatus.DECLINED -> stringResource(R.string.call_status_declined)
    CallStatus.MISSED -> stringResource(R.string.call_status_missed)
    else -> ""
}
