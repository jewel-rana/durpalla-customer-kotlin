package com.durpalla.booking.ui.screens

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.webkit.*
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import com.durpalla.booking.R
import com.durpalla.booking.core.*
import com.durpalla.booking.ui.components.*
import com.durpalla.booking.ui.nav.LocalContainer
import com.durpalla.booking.ui.nav.LocalNav
import com.durpalla.booking.ui.nav.Routes
import com.durpalla.booking.ui.theme.Brand
import kotlinx.coroutines.launch
import com.durpalla.booking.l10n.t

@Composable
fun CheckoutScreen() {
    val c = LocalContainer.current; val nav = LocalNav.current
    val cart by c.cart.items.collectAsState()
    val user by c.session.user.collectAsState()
    var forSelf by remember { mutableStateOf(true) }
    var name by remember { mutableStateOf("") }; var mobile by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }; var err by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val subtotal = c.cart.subtotal()
    val charge = c.cart.totalCharge()
    val vat = c.cart.totalVat()
    val total = c.cart.amountWithCharges()

    Scaffold(topBar = { DTopBar(t(R.string.checkout), onBack = nav::back) }, bottomBar = {
        Surface(shadowElevation = 16.dp) {
            Column(Modifier.navigationBarsPadding().padding(16.dp)) {
                err?.let { Text(it, color = Brand.Error, style = MaterialTheme.typography.bodySmall) }
                GradientButton(t(R.string.confirm_booking) + " • " + money(total), Modifier.fillMaxWidth(), enabled = cart.isNotEmpty(), loading = loading) {
                    if (!forSelf && (name.isBlank() || mobile.isBlank())) {
                        err = t(R.string.enter_passenger_name_mobile)
                        return@GradientButton
                    }
                    scope.launch {
                        loading = true; err = null
                        runCatching { c.bookings.confirm(cart, if (forSelf) null else name, if (forSelf) null else mobile) }
                            .onSuccess { (id, _) ->
                                c.cart.retainHoldsAfterBooking()
                                c.cart.clearLocal()
                                nav.go(Routes.payment(id, total))
                            }
                            .onFailure { err = it.message }
                        loading = false
                    }
                }
            }
        }
    }) { pad ->
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            StepIndicator(1)
            if (cart.isEmpty()) EmptyState(Icons.Default.ShoppingCart, t(R.string.cart_empty), t(R.string.cart_empty_body))
            cart.groupBy { it.tripId }.forEach { (_, items) ->
                SoftCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text(items.first().vehicleName, style = MaterialTheme.typography.titleMedium)
                        Text(items.first().routeName, style = MaterialTheme.typography.bodySmall, color = Brand.Muted)
                        HorizontalDivider(Modifier.padding(vertical = 10.dp))
                        items.forEach { ci ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.EventSeat, null, tint = Brand.Primary, modifier = Modifier.size(18.dp))
                                Text(" ${ci.type.replaceFirstChar { it.uppercase() }} ${ci.label}", Modifier.weight(1f))
                                Text(money(ci.lineTotal), fontWeight = FontWeight.SemiBold)
                                IconButton({
                                    scope.launch {
                                        if (c.trips.releaseLock(ci.lockId, ci.itemId)) c.cart.remove(ci)
                                    }
                                }) { Icon(Icons.Default.Close, null) }
                            }
                            ci.expiresAt?.let { Text(t(R.string.held_until, it), style = MaterialTheme.typography.labelSmall, color = Brand.AmberDark) }
                        }
                    }
                }
            }
            SoftCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(t(R.string.passenger), style = MaterialTheme.typography.titleMedium)
                    Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(forSelf, { forSelf = it }); Text(t(R.string.travelling_as_me, user.str("name", default = t(R.string.me)))) }
                    if (!forSelf) {
                        OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text(t(R.string.full_name)) })
                        OutlinedTextField(mobile, { mobile = it }, Modifier.fillMaxWidth(), label = { Text(t(R.string.mobile_number)) })
                    }
                }
            }
            SoftCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(t(R.string.price_breakdown), style = MaterialTheme.typography.titleMedium); Spacer(Modifier.height(8.dp))
                    Row { Text(t(R.string.fare), Modifier.weight(1f)); Text(money(subtotal)) }
                    Row { Text(t(R.string.service_charge), Modifier.weight(1f), color = Brand.Muted); Text(money(charge), color = Brand.Muted) }
                    Row { Text(t(R.string.vat), Modifier.weight(1f), color = Brand.Muted); Text(money(vat), color = Brand.Muted) }
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    Row { Text(t(R.string.total), Modifier.weight(1f), fontWeight = FontWeight.Bold); Text(money(total), color = Brand.AmberDark, fontWeight = FontWeight.ExtraBold) }
                }
            }
        }
    }
}

@Composable
fun StepIndicator(step: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        listOf(t(R.string.step_details), t(R.string.step_pay), t(R.string.step_ticket)).forEachIndexed { i, l ->
            val on = i + 1 <= step
            Box(Modifier.size(26.dp).clip(CircleShape).background(if (on) Brand.Primary else Brand.Line), contentAlignment = Alignment.Center) {
                Text("${i + 1}", color = if (on) Color.White else Brand.Muted, style = MaterialTheme.typography.labelMedium)
            }
            Text(" $l", style = MaterialTheme.typography.labelMedium, color = if (on) Brand.Ink else Brand.Muted)
            if (i < 2) Box(Modifier.weight(1f).padding(horizontal = 6.dp).height(2.dp).background(if (i + 1 < step) Brand.Primary else Brand.Line))
        }
    }
}

@Composable
fun PaymentMethodsScreen(bookingId: Int, amount: Double) {
    val c = LocalContainer.current; val nav = LocalNav.current
    val vm = rememberLoad("gw-$bookingId") {
        val rc = com.durpalla.booking.data.remote.RemoteAppConfig.fetch()
        c.payments.gateways(bookingId).filter {
            com.durpalla.booking.data.remote.RemoteAppConfig.paymentEnabled(rc, it.name)
        }
    }
    val state by vm.state.collectAsState()
    var selected by remember { mutableStateOf<Int?>(null) }
    var loading by remember { mutableStateOf(false) }; var err by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    Scaffold(topBar = { DTopBar(t(R.string.payment_method), onBack = nav::back) }, bottomBar = {
        Surface(shadowElevation = 16.dp) {
            Column(Modifier.navigationBarsPadding().padding(16.dp)) {
                err?.let { Text(it, color = Brand.Error) }
                GradientButton(t(R.string.pay_now) + " • " + money(amount), Modifier.fillMaxWidth(), enabled = selected != null, loading = loading) {
                    scope.launch {
                        loading = true; err = null
                        runCatching { c.payments.make(bookingId, selected!!, amount) }
                            .onSuccess { (url, bid) -> nav.go(Routes.payWeb(url, bid)) }.onFailure { err = it.message }
                        loading = false
                    }
                }
            }
        }
    }) { pad ->
        Column(Modifier.padding(pad).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            StepIndicator(2)
            Text(t(R.string.booking_numbered, bookingId), style = MaterialTheme.typography.bodySmall, color = Brand.Muted)
            when (val s = state) {
                Load.Loading -> LoadingList(3)
                is Load.Err -> ErrorState(s.message) { vm.reload() }
                is Load.Ok -> s.value.forEach { g ->
                    SoftCard(Modifier.fillMaxWidth(), onClick = { selected = g.id }) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (g.icon != null) AsyncImage(g.icon, null, Modifier.size(40.dp)) else Icon(Icons.Default.Payment, null, Modifier.size(40.dp), tint = Brand.Primary)
                            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                                Text(g.name, style = MaterialTheme.typography.titleSmall)
                                g.fee?.takeIf { it > 0 }?.let {
                                    Text(t(R.string.fee_amount, money(it)), style = MaterialTheme.typography.labelSmall, color = Brand.Muted)
                                }
                            }
                            RadioButton(selected == g.id, { selected = g.id })
                        }
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.VerifiedUser, null, tint = Brand.Success, modifier = Modifier.size(16.dp))
                Text(" " + t(R.string.secured_payment), style = MaterialTheme.typography.bodySmall, color = Brand.Muted)
            }
        }
    }
}

/**
 * Port of pages/global_payment_page.dart: loads the gateway URL; when the redirect path contains
 * `/payment/status` → verify via `GET payment/verify?booking_id=`; success/fail URL fragments are
 * treated the same way; `intent://` / non-http schemes (bKash/Nagad apps) launch externally with
 * browser_fallback_url fallback; back press asks to cancel.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun PaymentWebViewScreen(url: String, bookingId: Int) {
    val nav = LocalNav.current; val ctx = LocalContext.current
    var progress by remember { mutableFloatStateOf(0f) }
    var confirmExit by remember { mutableStateOf(false) }
    var done by remember { mutableStateOf(false) }
    // Master global_payment_page hints (+ sslcommerz / payment_id / result)
    val successHints = listOf(
        "/payment/success", "/sslcommerz/success", "/success",
        "status=success", "payment_status=success", "result=success", "paid", "complete", "payment_id=",
    )
    val failHints = listOf(
        "/payment/failed", "/payment/fail", "/sslcommerz/fail", "/failed", "/cancel",
        "status=failed", "payment_status=failed", "result=failed", "result=canceled", "result=error", "declined",
    )
    fun finish(cancelled: Boolean) {
        if (done) return
        done = true
        nav.nav.navigate(Routes.payResult(bookingId, cancelled)) {
            popUpTo("payment/{bid}/{amount}") { inclusive = true }
        }
    }

    BackHandler { confirmExit = true }
    Scaffold(topBar = { DTopBar(t(R.string.payment), onBack = { confirmExit = true }) }) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            AndroidView(factory = {
                WebView(it).apply {
                    settings.javaScriptEnabled = true; settings.domStorageEnabled = true
                    webChromeClient = object : WebChromeClient() { override fun onProgressChanged(v: WebView?, p: Int) { progress = p / 100f } }
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(v: WebView?, req: WebResourceRequest?): Boolean {
                            val u = req?.url ?: return false
                            val raw = u.toString()
                            if (u.scheme != "http" && u.scheme != "https") {
                                runCatching {
                                    if (raw.startsWith("intent:")) {
                                        val i = Intent.parseUri(raw, Intent.URI_INTENT_SCHEME)
                                        runCatching { ctx.startActivity(i) }.onFailure {
                                            i.getStringExtra("browser_fallback_url")?.let { f -> ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(f))) }
                                        }
                                    } else ctx.startActivity(Intent(Intent.ACTION_VIEW, u))
                                }
                                return true
                            }
                            if (u.path?.contains("/payment/status") == true || successHints.any { raw.contains(it) } || failHints.any { raw.contains(it) }) {
                                v?.loadData("<html><body></body></html>", "text/html", "utf-8"); finish(false); return true
                            }
                            return false
                        }
                        override fun onPageStarted(v: WebView?, u: String?, f: Bitmap?) {
                            if (u != null && Uri.parse(u).path?.contains("/payment/status") == true) finish(false)
                        }
                    }
                    loadUrl(url)
                }
            }, modifier = Modifier.fillMaxSize())
            if (progress < 1f) LinearProgressIndicator(progress = { progress }, Modifier.fillMaxWidth())
        }
    }
    if (confirmExit) AlertDialog(onDismissRequest = { confirmExit = false },
        title = { Text(t(R.string.cancel_payment)) }, text = { Text(t(R.string.cancel_payment_body)) },
        confirmButton = { Button({ confirmExit = false; finish(true) }) { Text(t(R.string.leave)) } },
        dismissButton = { TextButton({ confirmExit = false }) { Text(t(R.string.stay)) } })
}

/** booking_result_page.dart equivalent; always verifies with the backend. */
@Composable
fun PaymentResultScreen(bookingId: Int, cancelled: Boolean) {
    val c = LocalContainer.current; val nav = LocalNav.current
    val vm = rememberLoad("verify-$bookingId") { c.payments.verify(bookingId) }
    val state by vm.state.collectAsState()
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding(), contentAlignment = Alignment.Center) {
        when (val s = state) {
            Load.Loading -> CircularProgressIndicator()
            else -> {
                val r = (s as? Load.Ok)?.value
                val ok = r?.success == true
                val data = r.o("data")
                val scale = remember { androidx.compose.animation.core.Animatable(.4f) }
                LaunchedEffect(Unit) { scale.animateTo(1f, androidx.compose.animation.core.spring(dampingRatio = .45f)) }
                Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    if (ok) Confetti()
                    Box(Modifier.size(110.dp).scale(scale.value).clip(CircleShape).background(if (ok) Brand.Success.copy(.12f) else Brand.Error.copy(.12f)), contentAlignment = Alignment.Center) {
                        Icon(if (ok) Icons.Default.CheckCircle else Icons.Default.Cancel, null, Modifier.size(72.dp), tint = if (ok) Brand.Success else Brand.Error)
                    }
                    Spacer(Modifier.height(16.dp))
                    Text(t(if (ok) R.string.payment_success else R.string.payment_failed), style = MaterialTheme.typography.headlineSmall)
                    Text(r?.message ?: if (cancelled) t(R.string.payment_cancelled) else t(R.string.could_not_verify_payment), color = Brand.Muted)
                    if (data != null) {
                        Spacer(Modifier.height(12.dp))
                        SoftCard(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp)) {
                                Row { Text(t(R.string.label_booking), Modifier.weight(1f), color = Brand.Muted); Text("#$bookingId") }
                                Row { Text(t(R.string.label_gateway), Modifier.weight(1f), color = Brand.Muted); Text(data.o("gateway").str("name", default = t(R.string.em_dash))) }
                                Row { Text(t(R.string.label_paid), Modifier.weight(1f), color = Brand.Muted); Text("${data.str("paid_amount", default = t(R.string.em_dash))} ${data.str("currency")}") }
                                Row { Text(t(R.string.label_status), Modifier.weight(1f), color = Brand.Muted); Text(data.str("nice_status", default = if (ok) t(R.string.status_success) else t(R.string.status_failed))) }
                            }
                        }
                    }
                    Spacer(Modifier.height(20.dp))
                    if (ok) GradientButton(t(R.string.view_ticket), Modifier.fillMaxWidth()) { nav.nav.navigate(Routes.ticket(bookingId)) { popUpTo(Routes.HOME) } }
                    else GradientButton(t(R.string.retry), Modifier.fillMaxWidth()) { nav.back() }
                    TextButton({ nav.nav.navigate(Routes.HOME) { popUpTo(Routes.HOME) { inclusive = true } } }) { Text(t(R.string.back_home)) }
                }
            }
        }
    }
}

@Composable
private fun Confetti() {
    val t = rememberInfiniteTransition(label = "c")
    val y by t.animateFloat(0f, 1f, infiniteRepeatable(tween(2200)), label = "y")
    Canvas(Modifier.fillMaxWidth().height(60.dp)) {
        val colors = listOf(Brand.Amber, Brand.Primary, Brand.Success, Color(0xFFDB2777))
        repeat(28) { i ->
            val x = (i * 37 % 100) / 100f * size.width
            val yy = ((y + i * .13f) % 1f) * size.height
            drawRect(colors[i % 4], topLeft = androidx.compose.ui.geometry.Offset(x, yy), size = androidx.compose.ui.geometry.Size(8f, 14f))
        }
    }
}
