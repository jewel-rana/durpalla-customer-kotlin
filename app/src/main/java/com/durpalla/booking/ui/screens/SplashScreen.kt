package com.durpalla.booking.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.durpalla.booking.R
import com.durpalla.booking.ui.theme.Brand
import kotlinx.coroutines.delay

/** Splash intro: logo fades & grows over the brand gradient, then lands on (guest) Home. */
@Composable
fun SplashScreen(onDone: () -> Unit) {
    val a = remember { Animatable(0f) }
    LaunchedEffect(Unit) { a.animateTo(1f, tween(900, easing = FastOutSlowInEasing)); delay(500); onDone() }
    Box(Modifier.fillMaxSize().background(Brand.HeroGradient), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Image(painterResource(R.drawable.logo_mark_white), null, Modifier.size(110.dp).scale(.7f + .3f * a.value).alpha(a.value))
            Spacer(Modifier.height(18.dp))
            Image(painterResource(R.drawable.logo_horizontal_white), null, Modifier.width(180.dp).alpha(a.value))
            Spacer(Modifier.height(10.dp))
            Text("Launch • Bus • Boat • Tour • Hotels", color = Color.White.copy(.75f), style = MaterialTheme.typography.bodySmall, modifier = Modifier.alpha(a.value))
        }
    }
}
