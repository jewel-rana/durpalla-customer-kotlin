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
import com.durpalla.booking.l10n.t

@Composable
fun OffersScreen() {
    val c = LocalContainer.current
    val vm = rememberLoad("offers") { c.home.offers() }
    val state by vm.state.collectAsState()
    val clip = androidx.compose.ui.platform.LocalClipboardManager.current
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Box(Modifier.fillMaxWidth().background(Brand.HeroGradient).statusBarsPadding().padding(20.dp)) {
            Text(t(R.string.offers), style = MaterialTheme.typography.headlineSmall, color = Color.White)
        }
        when (val s = state) {
            Load.Loading -> LoadingList()
            is Load.Err -> ErrorState(s.message) { vm.reload() }
            is Load.Ok -> if (s.value.isEmpty()) EmptyState(Icons.Default.LocalOffer, t(R.string.no_offers), t(R.string.offers_check_back))
            else LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(s.value) { o ->
                    SoftCard {
                        PhotoBox(o.image, R.drawable.photo_river, Modifier.fillMaxWidth().height(140.dp))
                        Column(Modifier.padding(16.dp)) {
                            Text(o.title, style = MaterialTheme.typography.titleMedium); Text(o.subtitle, color = Brand.Muted)
                            o.code?.let { code -> Spacer(Modifier.height(8.dp)); AssistChip({ clip.setText(androidx.compose.ui.text.AnnotatedString(code)) }, { Text(t(R.string.copy_code, code)) }, leadingIcon = { Icon(Icons.Default.ContentCopy, null) }) }
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
                    Text(if (loggedIn) user.str("name", default = t(R.string.traveller_cap)) else t(R.string.guest), style = MaterialTheme.typography.titleLarge, color = Color.White)
                    Text(if (loggedIn) user.str("mobile", "phone", "email") else t(R.string.guest_banner), color = Color.White.copy(.8f), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        if (!loggedIn) GradientButton(t(R.string.sign_in), Modifier.padding(16.dp).fillMaxWidth()) { nav.go(Routes.login(Routes.ACCOUNT)) }
        SoftCard(Modifier.padding(16.dp)) {
            MenuRow(Icons.Default.Edit, t(R.string.edit_profile)) { nav.go(Routes.PROFILE) }
            MenuRow(Icons.Default.ConfirmationNumber, t(R.string.my_tickets)) { nav.tab(Routes.TICKETS) }
            MenuRow(Icons.Default.Favorite, t(R.string.saved)) { nav.go(Routes.SAVED) }
            MenuRow(Icons.Default.Notifications, t(R.string.notifications)) { nav.go(Routes.NOTIFICATIONS) }
        }
        SoftCard(Modifier.padding(horizontal = 16.dp)) {
            MenuRow(Icons.Default.Settings, t(R.string.settings)) { nav.go(Routes.SETTINGS) }
            MenuRow(Icons.Default.SupportAgent, t(R.string.support)) { nav.go(Routes.SUPPORT) }
            MenuRow(Icons.Default.HelpOutline, t(R.string.faq)) { nav.go(Routes.FAQ) }
            MenuRow(Icons.Default.Info, t(R.string.about)) { nav.go(Routes.ABOUT) }
            MenuRow(Icons.Default.Policy, t(R.string.terms)) { nav.go(Routes.TERMS) }
        }
        if (loggedIn) SoftCard(Modifier.padding(16.dp)) { MenuRow(Icons.AutoMirrored.Filled.Logout, t(R.string.sign_out), Brand.Error) { confirmLogout = true } }
        Spacer(Modifier.height(24.dp))
    }
    if (confirmLogout) AlertDialog({ confirmLogout = false }, title = { Text(t(R.string.sign_out) + "?") },
        confirmButton = { Button({ confirmLogout = false; scope.launch { c.auth.logout() } }) { Text(t(R.string.sign_out)) } },
        dismissButton = { TextButton({ confirmLogout = false }) { Text(t(R.string.not_now)) } })
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
    Scaffold(topBar = { DTopBar(t(R.string.edit_profile), onBack = nav::back) }) { pad ->
        Column(Modifier.padding(pad).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text(t(R.string.full_name)) })
            OutlinedTextField(email, { email = it }, Modifier.fillMaxWidth(), label = { Text(t(R.string.email)) })
            OutlinedTextField(user.str("mobile", "phone"), {}, Modifier.fillMaxWidth(), enabled = false, label = { Text(t(R.string.mobile_number)) })
            msg?.let { Text(it, color = Brand.Primary) }
            GradientButton(t(R.string.save), Modifier.fillMaxWidth(), loading = loading) {
                scope.launch { loading = true; msg = runCatching { c.profile.update(name, email, null).message ?: AppCtx.str(R.string.saved) }.getOrElse { it.message }; loading = false }
            }
        }
    }
}

@Composable
fun SavedScreen() {
    val c = LocalContainer.current; val nav = LocalNav.current
    val vm = rememberLoad("saved") { c.hotels.favourites() }
    val state by vm.state.collectAsState()
    Scaffold(topBar = { DTopBar(t(R.string.saved), onBack = nav::back) }) { pad ->
        Box(Modifier.padding(pad)) {
            when (val s = state) {
                Load.Loading -> LoadingList()
                is Load.Err -> ErrorState(s.message) { vm.reload() }
                is Load.Ok -> if (s.value.isEmpty()) EmptyState(Icons.Default.FavoriteBorder, t(R.string.nothing_saved), t(R.string.nothing_saved_body))
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
    val title = when (kind) { InfoKind.SUPPORT -> t(R.string.support); InfoKind.FAQ -> t(R.string.faq); InfoKind.ABOUT -> t(R.string.about); InfoKind.TERMS -> t(R.string.terms); InfoKind.NOTIFICATIONS -> t(R.string.notifications) }
    Scaffold(topBar = { DTopBar(title, onBack = nav::back) }) { pad ->
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            when (kind) {
                InfoKind.SUPPORT -> {
                    SoftCard {
                        MenuRow(Icons.Default.Call, t(R.string.call_support_hours)) { ctx.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:16000"))) }
                        MenuRow(Icons.Default.Email, "support@durpalla.com") { ctx.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:support@durpalla.com"))) }
                        MenuRow(Icons.Default.HelpOutline, t(R.string.browse_faq)) { nav.go(Routes.FAQ) }
                    }
                }
                InfoKind.FAQ -> listOf(
                    t(R.string.faq_q_ticket) to t(R.string.faq_a_ticket),
                    t(R.string.faq_q_cancel) to t(R.string.faq_a_cancel),
                    t(R.string.faq_q_payment) to t(R.string.faq_a_payment),
                    t(R.string.faq_q_hold) to t(R.string.faq_a_hold),
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
                    Text(t(R.string.about_body), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    Text(t(R.string.version_label, com.durpalla.booking.BuildConfig.VERSION_NAME), color = Brand.Muted)
                }
                InfoKind.TERMS -> Text(t(R.string.terms_placeholder))
                InfoKind.NOTIFICATIONS -> EmptyState(Icons.Default.NotificationsNone, t(R.string.notifications_caught_up), t(R.string.notifications_empty_body))
            }
        }
    }
}

fun Modifier.animateContentSizeCompat() = this.then(Modifier.animateContentSize())

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen() {
    val c = LocalContainer.current; val nav = LocalNav.current
    val dark by c.session.darkMode.collectAsState(false)
    val lang by c.session.language.collectAsState("en")
    val scope = rememberCoroutineScope()
    var showLangSheet by remember { mutableStateOf(false) }
    val localeLabel = com.durpalla.booking.l10n.SupportedLocales.find(lang).nativeName
    Scaffold(topBar = { DTopBar(t(R.string.settings), onBack = nav::back) }) { pad ->
        Column(Modifier.padding(pad).padding(16.dp)) {
            SoftCard {
                MenuRow(Icons.Default.DarkMode, t(R.string.dark_mode), trailing = { Switch(dark, { v -> scope.launch { c.session.setDark(v) } }) }) { scope.launch { c.session.setDark(!dark) } }
                MenuRow(
                    Icons.Default.Translate,
                    t(R.string.language),
                    trailing = { Text(localeLabel, color = Brand.Primary) },
                ) { showLangSheet = true }
            }
        }
    }
    if (showLangSheet) {
        ModalBottomSheet(onDismissRequest = { showLangSheet = false }) {
            LanguagePickerSheet(onDismiss = { showLangSheet = false })
        }
    }
}

/** @deprecated Prefer [com.durpalla.booking.l10n.LocaleHelper.apply]. */
fun applyLocale(ctx: android.content.Context, lang: String) {
    com.durpalla.booking.l10n.LocaleHelper.apply(lang)
    if (android.os.Build.VERSION.SDK_INT < 33) (ctx as? Activity)?.recreate()
}
