package com.durpalla.booking.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.durpalla.booking.R
import com.durpalla.booking.ui.theme.Brand

/** Soft blue-tinted elevated card used everywhere in the redesign. */
@Composable
fun SoftCard(modifier: Modifier = Modifier, radius: Dp = 18.dp, onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(radius)
    Column(
        modifier
            .shadow(10.dp, shape, ambientColor = Brand.ShadowTint, spotColor = Brand.ShadowTint)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        content = content,
    )
}

/** Amber gradient CTA with a subtle glow. */
@Composable
fun GradientButton(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, loading: Boolean = false,
                   brush: Brush = Brand.CtaGradient, textColor: Color = Color(0xFF1F1300), onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier
            .height(54.dp)
            .shadow(if (enabled) 12.dp else 0.dp, shape, ambientColor = Brand.Amber.copy(.5f), spotColor = Brand.Amber.copy(.5f))
            .clip(shape)
            .background(if (enabled) brush else Brush.horizontalGradient(listOf(Brand.Line, Brand.Line)))
            .clickable(enabled = enabled && !loading, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (loading) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.5.dp, color = textColor)
        else Text(text, style = MaterialTheme.typography.labelLarge, color = if (enabled) textColor else Brand.Muted)
    }
}

@Composable
fun PrimaryButton(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, loading: Boolean = false, onClick: () -> Unit) =
    GradientButton(text, modifier, enabled, loading, Brand.PrimaryGradient, Color.White, onClick)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DTopBar(title: String, onBack: (() -> Unit)? = null, actions: @Composable RowScope.() -> Unit = {}) {
    CenterAlignedTopAppBar(
        title = { Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        navigationIcon = { if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } },
        actions = actions,
        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
    )
}

/** Frosted circular icon used over photos. */
@Composable
fun FrostedIcon(icon: ImageVector, onClick: () -> Unit) {
    Box(Modifier.size(40.dp).clip(CircleShape).background(Color.White.copy(alpha = .28f)).border(1.dp, Color.White.copy(.4f), CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center) { Icon(icon, null, tint = Color.White) }
}

@Composable
fun Pill(text: String, bg: Color = Brand.P100, fg: Color = Brand.P700, icon: ImageVector? = null) {
    Row(Modifier.clip(RoundedCornerShape(50)).background(bg).padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) { Icon(icon, null, Modifier.size(13.dp), tint = fg); Spacer(Modifier.width(4.dp)) }
        Text(text, style = MaterialTheme.typography.labelSmall, color = fg, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun SectionHeader(title: String, action: String? = null, onAction: () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        if (action != null) Text(action, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium, modifier = Modifier.clickable(onClick = onAction))
    }
}

@Composable
fun PhotoBox(url: String?, @DrawableRes fallback: Int, modifier: Modifier = Modifier) {
    if (url.isNullOrBlank()) Image(painterResource(fallback), null, modifier, contentScale = ContentScale.Crop)
    else AsyncImage(model = url, contentDescription = null, modifier = modifier, contentScale = ContentScale.Crop,
        placeholder = painterResource(fallback), error = painterResource(fallback))
}

/** Shimmer skeleton block. */
@Composable
fun Skeleton(modifier: Modifier) {
    val t = rememberInfiniteTransition(label = "sk")
    val a by t.animateFloat(.35f, .8f, infiniteRepeatable(tween(800), RepeatMode.Reverse), label = "a")
    Box(modifier.clip(RoundedCornerShape(14.dp)).background(Brand.P100.copy(alpha = a)))
}

@Composable
fun LoadingList(n: Int = 4) = Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
    repeat(n) { Skeleton(Modifier.fillMaxWidth().height(110.dp)) }
}

@Composable
fun EmptyState(icon: ImageVector, title: String, body: String? = null, action: String? = null, onAction: () -> Unit = {}) {
    Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(96.dp).clip(CircleShape).background(Brush.radialGradient(listOf(Brand.P100, Brand.P50))), contentAlignment = Alignment.Center) {
            Icon(icon, null, Modifier.size(44.dp), tint = Brand.Primary)
        }
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleMedium)
        if (body != null) Text(body, style = MaterialTheme.typography.bodyMedium, color = Brand.Muted, modifier = Modifier.padding(top = 6.dp))
        if (action != null) { Spacer(Modifier.height(16.dp)); OutlinedButton(onClick = onAction) { Text(action) } }
    }
}

@Composable
fun ErrorState(msg: String, onRetry: () -> Unit) = EmptyState(Icons.Default.CloudOff, msg, null, "Retry", onRetry)

fun money(v: Double?): String = if (v == null) "—" else "৳" + String.format("%,.0f", v)

@Composable
fun LogoHorizontal(modifier: Modifier = Modifier, white: Boolean = false) =
    Image(painterResource(if (white) R.drawable.logo_horizontal_white else R.drawable.logo_horizontal_blue), "Durpalla", modifier)
