package com.durpalla.booking

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.durpalla.booking.ui.nav.AppRoot
import com.durpalla.booking.ui.theme.DurpallaTheme

class MainActivity : ComponentActivity() {
    private val notifPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= 33) notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        val container = (application as DurpallaApp).container
        val deepLink = intent?.getStringExtra("route")
        setContent {
            val dark by container.session.darkMode.collectAsState(initial = false)
            DurpallaTheme(dark = dark) { AppRoot(container, deepLink) }
        }
    }
}
