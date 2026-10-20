package com.durpalla.booking.ui.screens

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.*
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.durpalla.booking.R
import com.durpalla.booking.core.*
import com.durpalla.booking.data.repo.Service
import com.durpalla.booking.ui.components.*
import com.durpalla.booking.ui.nav.LocalContainer
import com.durpalla.booking.ui.nav.LocalNav
import com.durpalla.booking.ui.nav.Routes
import com.durpalla.booking.ui.theme.Brand
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@Composable
fun OffersScreen() {
    val c = LocalContainer.current
    val vm = rememberLoad("offers") { c.home.offers() }
    val state by vm.state.collectAsState()
    val clip = androidx.compose.ui.platform.LocalClipboardManager.current
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Box(Modifier.fillMaxWidth().background(Brand.HeroGradient).statusBarsPadding().padding(20.dp)) {
            Text(stringResource(R.string.offers), style = MaterialTheme.typography.headlineSmall, color = Color.White)
        }
        when (val s = state) {
            Load.Loading -> LoadingList()
            is Load.Err -> ErrorState(s.message) { vm.reload() }
            is Load.Ok -> if (s.value.isEmpty()) EmptyState(Icons.Default.LocalOffer, stringResource(R.string.no_offers), "Check back soon for deals on launches, buses and hotels.")
            else LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(s.value) { o ->
                    SoftCard {
                        PhotoBox(o.image, R.drawable.photo_river, Modifier.fillMaxWidth().height(140.dp))
                        Column(Modifier.padding(16.dp)) {
                            Text(o.title, style = MaterialTheme.typography.titleMedium); Text(o.subtitle, color = Brand.Muted)
                            o.code?.let { code -> Spacer(Modifier.height(8.dp)); AssistChip({ clip.setText(androidx.compose.ui.text.AnnotatedString(code)) }, { Text("Copy $code") }, leadingIcon = { Icon(Icons.Default.ContentCopy, null) }) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AccountScreen() {
    val c = LocalContainer.current; val nav = LocalNav.current
    val loggedIn by c.session.loggedIn.collectAsState(); val user by c.session.user.collectAsState()
    val scope = rememberCoroutineScope()
    var confirmLogout by remember { mutableStateOf(false) }
    LaunchedEffect(loggedIn) { if (loggedIn) runCatching { c.profile.profile() } }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).verticalScroll(rememberScrollState())) {
        Box(Modifier.fillMaxWidth().background(Brand.HeroGradient).statusBarsPadding().padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(64.dp).clip(CircleShape).background(Color.White.copy(.2f)), contentAlignment = Alignment.Center) {
                    val avatar = user.s("avatar")
                    if (loggedIn && avatar != null) PhotoBox(avatar, R.drawable.logo_mark_white, Modifier.matchParentSize())
                    else Icon(Icons.Default.Person, null, tint = Color.White, modifier = Modifier.size(36.dp))
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(if (loggedIn) user.str("name", default = "Traveller") else "Guest", style = MaterialTheme.typography.titleLarge, color = Color.White)
                    Text(if (loggedIn) user.str("mobile", "phone", "email") else stringResource(R.string.guest_banner), color = Color.White.copy(.8f), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        if (!loggedIn) GradientButton(stringResource(R.string.sign_in), Modifier.padding(16.dp).fillMaxWidth()) { nav.go(Routes.login(Routes.ACCOUNT)) }
        SoftCard(Modifier.padding(16.dp)) {
            MenuRow(Icons.Default.Edit, stringResource(R.string.edit_profile)) { nav.go(Routes.PROFILE) }
            MenuRow(Icons.Default.ConfirmationNumber, stringResource(R.string.my_tickets)) { nav.tab(Routes.TICKETS) }
            MenuRow(Icons.Default.Favorite, stringResource(R.string.saved)) { nav.go(Routes.SAVED) }
            MenuRow(Icons.Default.Notifications, "Notifications") { nav.go(Routes.NOTIFICATIONS) }
        }
        SoftCard(Modifier.padding(horizontal = 16.dp)) {
            MenuRow(Icons.Default.Settings, stringResource(R.string.settings)) { nav.go(Routes.SETTINGS) }
            MenuRow(Icons.Default.SupportAgent, stringResource(R.string.support)) { nav.go(Routes.SUPPORT) }
            MenuRow(Icons.Default.HelpOutline, stringResource(R.string.faq)) { nav.go(Routes.FAQ) }
            MenuRow(Icons.Default.Info, stringResource(R.string.about)) { nav.go(Routes.ABOUT) }
            MenuRow(Icons.Default.Policy, stringResource(R.string.terms)) { nav.go(Routes.TERMS) }
        }
        if (loggedIn) SoftCard(Modifier.padding(16.dp)) { MenuRow(Icons.AutoMirrored.Filled.Logout, stringResource(R.string.sign_out), Brand.Error) { confirmLogout = true } }
        Spacer(Modifier.height(24.dp))
    }
    if (confirmLogout) AlertDialog({ confirmLogout = false }, title = { Text(stringResource(R.string.sign_out) + "?") },
        confirmButton = { Button({ confirmLogout = false; scope.launch { c.auth.logout() } }) { Text(stringResource(R.string.sign_out)) } },
        dismissButton = { TextButton({ confirmLogout = false }) { Text(stringResource(R.string.not_now)) } })
}

@Composable
fun MenuRow(icon: ImageVector, label: String, tint: Color = Brand.Primary, trailing: @Composable () -> Unit = { Icon(Icons.Default.ChevronRight, null, tint = Brand.Muted) }, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(16.dp, 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(tint.copy(.1f)), contentAlignment = Alignment.Center) { Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp)) }
        Text(label, Modifier.weight(1f).padding(start = 12.dp), style = MaterialTheme.typography.bodyLarge)
        trailing()
    }
}

@Composable
fun ProfileScreen() {
    val c = LocalContainer.current; val nav = LocalNav.current
    val user by c.session.user.collectAsState()
    var name by remember { mutableStateOf(user.str("name")) }; var email by remember { mutableStateOf(user.str("email")) }
    var msg by remember { mutableStateOf<String?>(null) }; var loading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    Scaffold(topBar = { DTopBar(stringResource(R.string.edit_profile), onBack = nav::back) }) { pad ->
        Column(Modifier.padding(pad).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.full_name)) })
            OutlinedTextField(email, { email = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.email)) })
            OutlinedTextField(user.str("mobile", "phone"), {}, Modifier.fillMaxWidth(), enabled = false, label = { Text(stringResource(R.string.mobile_number)) })
            msg?.let { Text(it, color = Brand.Primary) }
            GradientButton(stringResource(R.string.save), Modifier.fillMaxWidth(), loading = loading) {
                scope.launch { loading = true; msg = runCatching { c.profile.update(name, email, null).message ?: "Saved" }.getOrElse { it.message }; loading = false }
            }
        }
    }
}

@Composable
fun SavedScreen() {
    val c = LocalContainer.current; val nav = LocalNav.current
    val vm = rememberLoad("saved") { c.hotels.favourites() }
    val state by vm.state.collectAsState()
    Scaffold(topBar = { DTopBar(stringResource(R.string.saved), onBack = nav::back) }) { pad ->
        Box(Modifier.padding(pad)) {
            when (val s = state) {
                Load.Loading -> LoadingList()
                is Load.Err -> ErrorState(s.message) { vm.reload() }
                is Load.Ok -> if (s.value.isEmpty()) EmptyState(Icons.Default.FavoriteBorder, "Nothing saved yet", "Tap the heart on a hotel to save it.")
                else LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(s.value) { h -> HotelCard(h, Modifier.fillMaxWidth()) { nav.go(Routes.hotel(h.id, todayIso(1), todayIso(2))) } }
                }
            }
        }
    }
}

enum class InfoKind { SUPPORT, FAQ, ABOUT, TERMS, NOTIFICATIONS }

@Composable
fun InfoScreen(kind: InfoKind) {
    val nav = LocalNav.current; val ctx = LocalContext.current
    val title = when (kind) { InfoKind.SUPPORT -> stringResource(R.string.support); InfoKind.FAQ -> stringResource(R.string.faq); InfoKind.ABOUT -> stringResource(R.string.about); InfoKind.TERMS -> stringResource(R.string.terms); InfoKind.NOTIFICATIONS -> "Notifications" }
    Scaffold(topBar = { DTopBar(title, onBack = nav::back) }) { pad ->
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            when (kind) {
                InfoKind.SUPPORT -> {
                    SoftCard {
                        MenuRow(Icons.Default.Call, "Call 16xxx (9am–10pm)") { ctx.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:16000"))) }
                        MenuRow(Icons.Default.Email, "support@durpalla.com") { ctx.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:support@durpalla.com"))) }
                        MenuRow(Icons.Default.HelpOutline, "Browse FAQ") { nav.go(Routes.FAQ) }
                    }
                }
                InfoKind.FAQ -> listOf(
                    "How do I get my ticket?" to "After payment, your ticket with a QR code appears under Tickets. Show it at the ghat or counter.",
                    "Can I cancel a booking?" to "Open the ticket and tap Cancel ticket. Refunds follow the operator's cancellation rules.",
                    "Which payment methods are supported?" to "bKash, Nagad, cards and other gateways enabled by Durpalla for your booking.",
                    "How long is my seat held?" to "Selected cabins/seats are held for a short time while you check out.",
                ).forEach { (q, a) ->
                    var open by remember { mutableStateOf(false) }
                    SoftCard(onClick = { open = !open }) {
                        Column(Modifier.padding(16.dp).animateContentSizeCompat()) {
                            Text(q, style = MaterialTheme.typography.titleSmall); if (open) Text(a, color = Brand.Muted, modifier = Modifier.padding(top = 6.dp))
                        }
                    }
                }
                InfoKind.ABOUT -> {
                    Image(painterResource(R.drawable.logo_stacked), null, Modifier.fillMaxWidth().height(140.dp))
                    Text("Durpalla is Bangladesh's travel booking app for launches, buses, boats, tours, events and hotels.", textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    Text("Version ${com.durpalla.booking.BuildConfig.VERSION_NAME}", color = Brand.Muted)
                }
                InfoKind.TERMS -> Text("Terms & privacy are served from durpalla.com. TODO: load CMS page (Flutter common_page_screen.dart) once the CMS endpoint is confirmed.")
                InfoKind.NOTIFICATIONS -> EmptyState(Icons.Default.NotificationsNone, "You're all caught up", "Trip reminders and offers will appear here.")
            }
        }
    }
}

fun Modifier.animateContentSizeCompat() = this.then(Modifier.animateContentSize())

@Composable
fun SettingsScreen() {
    val c = LocalContainer.current; val nav = LocalNav.current; val ctx = LocalContext.current
    val dark by c.session.darkMode.collectAsState(false); val lang by c.session.language.collectAsState("en")
    val scope = rememberCoroutineScope()
    Scaffold(topBar = { DTopBar(stringResource(R.string.settings), onBack = nav::back) }) { pad ->
        Column(Modifier.padding(pad).padding(16.dp)) {
            SoftCard {
                MenuRow(Icons.Default.DarkMode, stringResource(R.string.dark_mode), trailing = { Switch(dark, { v -> scope.launch { c.session.setDark(v) } }) }) { scope.launch { c.session.setDark(!dark) } }
                MenuRow(Icons.Default.Translate, stringResource(R.string.language), trailing = { Text(if (lang == "bn") "বাংলা" else "English", color = Brand.Primary) }) {
                    val next = if (lang == "bn") "en" else "bn"
                    scope.launch { c.session.setLanguage(next); applyLocale(ctx, next) }
                }
            }
        }
    }
}

fun applyLocale(ctx: android.content.Context, lang: String) {
    if (android.os.Build.VERSION.SDK_INT >= 33) {
        ctx.getSystemService(android.app.LocaleManager::class.java).applicationLocales = android.os.LocaleList.forLanguageTags(lang)
    } else {
        val locale = java.util.Locale(lang); java.util.Locale.setDefault(locale)
        @Suppress("DEPRECATION") ctx.resources.updateConfiguration(android.content.res.Configuration(ctx.resources.configuration).apply { setLocale(locale) }, ctx.resources.displayMetrics)
        (ctx as? Activity)?.recreate()
    }
}
