package com.durpalla.booking.ui.components

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import com.durpalla.booking.R
import com.durpalla.booking.ui.theme.Brand
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/** Single swap point for hero photos: bus → launch → hotel → tour → boat. */
object HeroSlides {
    val images = intArrayOf(
        R.drawable.hero_bus,
        R.drawable.hero_launch,
        R.drawable.hero_hotel,
        R.drawable.hero_tour,
        R.drawable.hero_boat,
    )
}

enum class HeroOverlay { Splash, Home }

/**
 * Full-bleed hero photos with a fixed teal gradient. Crossfade 600ms every 3s.
 * Pauses off-screen and when the lifecycle is stopped. Reduced motion shows the first image.
 */
@Composable
fun HeroBackgroundSlideshow(
    modifier: Modifier = Modifier,
    overlay: HeroOverlay = HeroOverlay.Home,
    contentAlignment: Alignment = Alignment.TopStart,
    content: @Composable BoxScope.() -> Unit,
) {
    val reduced = rememberReducedMotion()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var started by remember { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) }
    DisposableEffect(lifecycle) {
        val obs = LifecycleEventObserver { _, _ ->
            started = lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        }
        lifecycle.addObserver(obs)
        onDispose { lifecycle.removeObserver(obs) }
    }
    var onScreen by remember { mutableStateOf(true) }
    var index by remember { mutableIntStateOf(0) }
    val fade = remember { Animatable(0f) }
    val count = HeroSlides.images.size
    val playing = started && onScreen && !reduced
    LaunchedEffect(reduced) {
        if (reduced) {
            index = 0
            fade.snapTo(0f)
        }
    }
    LaunchedEffect(playing) {
        if (!playing) return@LaunchedEffect
        while (isActive) {
            delay(3_000)
            fade.animateTo(1f, tween(600))
            index = (index + 1) % count
            fade.snapTo(0f)
        }
    }
    val next = (index + 1) % count
    Box(
        modifier.onGloballyPositioned { coords ->
            val b = coords.boundsInWindow()
            onScreen = b.width > 1f && b.height > 1f && b.bottom > 0f && b.right > 0f
        },
        contentAlignment = contentAlignment,
    ) {
        Image(
            painterResource(HeroSlides.images[index]),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
        if (!reduced && fade.value > 0f) {
            Image(
                painterResource(HeroSlides.images[next]),
                contentDescription = null,
                modifier = Modifier.fillMaxSize().alpha(fade.value),
                contentScale = ContentScale.Crop,
            )
        }
        Box(Modifier.fillMaxSize().heroOverlay(overlay))
        content()
    }
}

private fun Modifier.heroOverlay(overlay: HeroOverlay): Modifier {
    val (a0, a1, a2) = when (overlay) {
        HeroOverlay.Splash -> Triple(0.82f, 0.88f, 0.95f)
        HeroOverlay.Home -> Triple(0.78f, 0.85f, 0.92f)
    }
    return drawBehind {
        drawRect(
            brush = Brush.linearGradient(
                colorStops = arrayOf(
                    0f to Brand.Primary.copy(alpha = a0),
                    0.5f to Brand.HeroMid.copy(alpha = a1),
                    1f to Brand.Navy.copy(alpha = a2),
                ),
                start = Offset(size.width * 0.318f, 0f),
                end = Offset(size.width * 0.682f, size.height),
            ),
        )
    }
}

@Composable
private fun rememberReducedMotion(): Boolean {
    val ctx = LocalContext.current
    return remember {
        Settings.Global.getFloat(ctx.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
}
