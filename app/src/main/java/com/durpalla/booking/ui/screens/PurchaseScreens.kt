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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import com.durpalla.booking.R
import com.durpalla.booking.core.*
import com.durpalla.booking.data.repo.boardingOptionsFromMeta
import com.durpalla.booking.data.repo.findBoardingOption
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
    var agreedPolicies by remember { mutableStateOf(false) }
    var showPolicySheet by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }; var err by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val subtotal = c.cart.subtotal()
    val charge = c.cart.totalCharge()
    val vat = c.cart.totalVat()
    val total = c.cart.amountWithCharges()
    val mustAgreeMsg = t(R.string.must_agree_policies)
    val boardingOptions = remember(cart) {
        boardingOptionsFromMeta(cart.firstOrNull()?.meta)
    }
    var selectedBoardingId by remember { mutableStateOf("") }
    LaunchedEffect(boardingOptions, cart) {
        val fromCart = cart.firstOrNull()?.meta?.s("boarding_point_id").orEmpty()
        selectedBoardingId = when {
            selectedBoardingId.isNotBlank() &&
                boardingOptions.any { it.id == selectedBoardingId } -> selectedBoardingId
            fromCart.isNotBlank() && boardingOptions.any { it.id == fromCart } -> fromCart
            else -> {
                val fromName = cart.firstOrNull()?.meta?.s("boarding_point_name").orEmpty()
                    .trim().lowercase()
                boardingOptions.firstOrNull {
                    it.name.trim().lowercase() == fromName
                }?.id
                    ?: boardingOptions.singleOrNull()?.id
                    ?: ""
            }
        }
    }
    LaunchedEffect(forSelf, user) {
        if (!forSelf) return@LaunchedEffect
        val u = user
        name = u.str("name", "full_name")
        mobile = u.str("mobile", "phone")
    }

    Scaffold(topBar = { DTopBar(t(R.string.checkout), onBack = nav::back) }, bottomBar = {
        Surface(shadowElevation = 16.dp, modifier = Modifier.fillMaxWidth().imePadding()) {
            Column(Modifier.navigationBarsPadding().padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 8.dp)) {
                    Checkbox(checked = agreedPolicies, onCheckedChange = { agreedPolicies = it; if (it) err = null })
                    Column(Modifier.weight(1f)) {
                        Text(
                            t(R.string.agree_checkout_policies),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.clickable { agreedPolicies = !agreedPolicies },
                        )
                        Text(
                            t(R.string.cancellation_refund_policy) + " • " + t(R.string.terms_conditions) + " • " + t(R.string.privacy_policy),
                            style = MaterialTheme.typography.labelSmall,
                            color = Brand.Primary,
                            modifier = Modifier.clickable { showPolicySheet = true },
                        )
                    }
                }
                err?.let { Text(it, color = Brand.Error, style = MaterialTheme.typography.bodySmall) }
                val passengerErr = t(R.string.enter_passenger_name_mobile)
                val boardingErr = t(R.string.boarding_required)
                GradientButton(
                    t(R.string.confirm_booking) + " • " + money(total),
                    Modifier.fillMaxWidth(),
                    enabled = cart.isNotEmpty() && agreedPolicies,
                    loading = loading,
                ) {
                    if (name.isBlank() || mobile.isBlank()) {
                        err = passengerErr
                        return@GradientButton
                    }
                    if (boardingOptions.isNotEmpty() &&
                        findBoardingOption(boardingOptions, selectedBoardingId) == null
                    ) {
                        err = boardingErr
                        return@GradientButton
                    }
                    val policiesAccepted = agreedPolicies
                    if (!policiesAccepted) {
                        err = mustAgreeMsg
                        return@GradientButton
                    }
                    scope.launch {
                        loading = true; err = null
                        val boarding = findBoardingOption(boardingOptions, selectedBoardingId)
                        runCatching {
                            c.bookings.confirm(
                                cart,
                                name.trim(),
                                mobile.trim(),
                                forSelf = forSelf,
                                boarding = boarding,
                            )
                        }
                            .onSuccess { (id, _) ->
                                c.cart.retainHoldsAfterBooking()
                                c.cart.clearLocal()
                                nav.go(Routes.payment(id, total, policiesAccepted = policiesAccepted))
                            }
                            .onFailure { err = it.message }
                        loading = false
                    }
                }
            }
        }
    }) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).dismissKeyboardOnTap().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
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
                                        if (c.trips.releaseLock(ci)) c.cart.remove(ci)
                                    }
                                }) { Icon(Icons.Default.Close, null) }
                            }
                            ci.expiresAt?.let { Text(t(R.string.held_until, it), style = MaterialTheme.typography.labelSmall, color = Brand.AmberDark) }
                        }
                    }
                }
            }
            if (boardingOptions.isNotEmpty()) {
                SoftCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(t(R.string.boarding_point), style = MaterialTheme.typography.titleMedium)
                        Text(t(R.string.boarding_hint), style = MaterialTheme.typography.bodySmall, color = Brand.Muted)
                        boardingOptions.forEach { option ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedBoardingId = option.id }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RadioButton(
                                    selected = selectedBoardingId == option.id,
                                    onClick = { selectedBoardingId = option.id },
                                )
                                Text(option.label, Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
            SoftCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(t(R.string.passenger), style = MaterialTheme.typography.titleMedium)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(forSelf, {
                            forSelf = it
                            if (it) {
                                name = user.str("name", "full_name")
                                mobile = user.str("mobile", "phone")
                            } else {
                                name = ""
                                mobile = ""
                            }
                        })
                        Text(t(R.string.travelling_as_me, user.str("name", default = t(R.string.me))))
                    }
                    OutlinedTextField(
                        name,
                        { name = it; if (forSelf) forSelf = false },
                        Modifier.fillMaxWidth(),
                        label = { Text(t(R.string.full_name)) },
                        readOnly = forSelf && name.isNotBlank(),
                    )
                    OutlinedTextField(
                        mobile,
                        { mobile = it; if (forSelf) forSelf = false },
                        Modifier.fillMaxWidth(),
                        label = { Text(t(R.string.mobile_number)) },
                        readOnly = forSelf && mobile.isNotBlank(),
                    )
                }
            }
            SoftCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    SupplierPolicyLinks(cart.firstNotNullOfOrNull { it.meta.o("supplier_policies") })
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
    if (showPolicySheet) {
        CancellationPolicySheet(onDismiss = { showPolicySheet = false })
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
fun PaymentMethodsScreen(bookingId: Int, amount: Double, policiesAccepted: Boolean) {
    val c = LocalContainer.current; val nav = LocalNav.current
    val vm = rememberLoad("gw-$bookingId") {
        val rc = com.durpalla.booking.data.remote.RemoteAppConfig.fetch()
        val (gateways, supplierPolicies) = c.payments.gateways(bookingId)
        gateways.filter {
            com.durpalla.booking.data.remote.RemoteAppConfig.paymentEnabled(rc, it.name)
        } to supplierPolicies
    }
    val state by vm.state.collectAsState()
    var selected by remember { mutableStateOf<Int?>(null) }
    var agreedPolicies by rememberSaveable(bookingId, policiesAccepted) { mutableStateOf(policiesAccepted) }
    var showPolicySheet by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }; var err by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val mustAgreeMsg = t(R.string.must_agree_policies)
    Scaffold(topBar = { DTopBar(t(R.string.payment_method), onBack = nav::back) }, bottomBar = {
        Surface(shadowElevation = 16.dp) {
            Column(Modifier.navigationBarsPadding().padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 8.dp)) {
                    Checkbox(checked = agreedPolicies, onCheckedChange = { agreedPolicies = it; if (it) err = null })
                    Column(Modifier.weight(1f)) {
                        Text(
                            t(R.string.agree_checkout_policies),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.clickable { agreedPolicies = !agreedPolicies },
                        )
                        Text(
                            t(R.string.cancellation_refund_policy) + " • " + t(R.string.terms_conditions) + " • " + t(R.string.privacy_policy),
                            style = MaterialTheme.typography.labelSmall,
                            color = Brand.Primary,
                            modifier = Modifier.clickable { showPolicySheet = true },
                        )
                    }
                }
                err?.let { Text(it, color = Brand.Error, style = MaterialTheme.typography.bodySmall) }
                GradientButton(
                    t(R.string.pay_now) + " • " + money(amount),
                    Modifier.fillMaxWidth(),
                    enabled = selected != null && agreedPolicies,
                    loading = loading,
                ) {
                    if (!agreedPolicies) {
                        err = mustAgreeMsg
                        return@GradientButton
                    }
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
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            StepIndicator(2)
            Text(t(R.string.booking_numbered, bookingId), style = MaterialTheme.typography.bodySmall, color = Brand.Muted)
            when (val s = state) {
                Load.Loading -> LoadingList(3)
                is Load.Err -> ErrorState(s.message) { vm.reload() }
                is Load.Ok -> {
                    val (gateways, supplierPolicies) = s.value
                    SupplierPolicyLinks(supplierPolicies)
                    gateways.forEach { g ->
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
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.VerifiedUser, null, tint = Brand.Success, modifier = Modifier.size(16.dp))
                Text(" " + t(R.string.secured_payment), style = MaterialTheme.typography.bodySmall, color = Brand.Muted)
            }
        }
    }
    if (showPolicySheet) {
        CancellationPolicySheet(onDismiss = { showPolicySheet = false })
    }
}

/**
 * Port of pages/global_payment_page.dart: loads the gateway URL; when the redirect path contains
 * `/payment/status` or `/payment/result` → verify via `GET payment/verify?booking_id=`; success/fail URL fragments are
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
    fun isPaymentReturn(uri: Uri): Boolean =
        uri.path?.trimEnd('/')?.let { it.endsWith("/payment/status") || it.endsWith("/payment/result") } == true

    fun finish(cancelled: Boolean) {
        if (done) return
        done = true
        nav.nav.navigate(Routes.payResult(bookingId, cancelled)) {
            popUpTo(Routes.PAYMENT) { inclusive = true }
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
                            if (req == null || !req.isForMainFrame) return false
                            val u = req.url
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
                            if (isPaymentReturn(u) || successHints.any { raw.contains(it) } || failHints.any { raw.contains(it) }) {
                                finish(false)
                                return true
                            }
                            return false
                        }
                        override fun onPageStarted(v: WebView?, u: String?, f: Bitmap?) {
                            if (u != null && isPaymentReturn(Uri.parse(u))) {
                                v?.stopLoading()
                                finish(false)
                            }
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
                val verificationStatus = r.s("verification_status")
                val ok = r?.success == true && verificationStatus == "paid"
                val failed = verificationStatus == "failed"
                val tone = when {
                    ok -> Brand.Success
                    failed -> Brand.Error
                    else -> Brand.AmberDark
                }
                val data = r.o("data")
                val scale = remember { androidx.compose.animation.core.Animatable(.4f) }
                LaunchedEffect(Unit) { scale.animateTo(1f, androidx.compose.animation.core.spring(dampingRatio = .45f)) }
                Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    if (ok) Confetti()
                    Box(Modifier.size(110.dp).scale(scale.value).clip(CircleShape).background(tone.copy(.12f)), contentAlignment = Alignment.Center) {
                        val icon = when {
                            ok -> Icons.Default.CheckCircle
                            failed -> Icons.Default.Cancel
                            else -> Icons.Default.Info
                        }
                        Icon(icon, null, Modifier.size(72.dp), tint = tone)
                    }
                    Spacer(Modifier.height(16.dp))
                    val title = when {
                        ok -> R.string.payment_success
                        failed -> R.string.payment_failed
                        verificationStatus == "pending" -> R.string.payment_confirmation_pending
                        else -> R.string.could_not_verify_payment
                    }
                    Text(t(title), style = MaterialTheme.typography.headlineSmall)
                    val message = r?.message ?: if (cancelled) t(R.string.payment_cancelled) else null
                    if (message != null) Text(message, color = Brand.Muted)
                    if (ok && data != null) {
                        Spacer(Modifier.height(12.dp))
                        SoftCard(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp)) {
                                Row { Text(t(R.string.label_booking), Modifier.weight(1f), color = Brand.Muted); Text("#$bookingId") }
                                Row { Text(t(R.string.label_gateway), Modifier.weight(1f), color = Brand.Muted); Text(data.o("gateway").str("name", default = t(R.string.em_dash))) }
                                Row { Text(t(R.string.label_paid), Modifier.weight(1f), color = Brand.Muted); Text("${data.str("paid_amount", default = t(R.string.em_dash))} ${data.str("currency")}") }
                                Row { Text(t(R.string.label_status), Modifier.weight(1f), color = Brand.Muted); Text(data.str("nice_status", default = t(title))) }
                            }
                        }
                    }
                    Spacer(Modifier.height(20.dp))
                    if (ok) GradientButton(t(R.string.view_ticket), Modifier.fillMaxWidth()) { nav.nav.navigate(Routes.ticket(bookingId)) { popUpTo(Routes.HOME) } }
                    else if (failed) GradientButton(t(R.string.retry), Modifier.fillMaxWidth()) { nav.back() }
                    else GradientButton(t(R.string.check_payment_again), Modifier.fillMaxWidth()) { vm.reload() }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CancellationPolicySheet(
    onDismiss: () -> Unit,
) {
    val c = LocalContainer.current
    val vm = rememberLoad("cancellation-policy-sheet") {
        c.home.cmsPage("cancellation-and-refund-policy")
    }
    val state by vm.state.collectAsState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    t(R.string.cancellation_refund_policy),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = null)
                }
            }
            HorizontalDivider()
            when (val s = state) {
                Load.Loading -> LoadingList(3)
                is Load.Err -> ErrorState(s.message) { vm.reload() }
                is Load.Ok -> {
                    val content = s.value?.second.orEmpty()
                    if (content.isNotBlank()) {
                        Text(
                            content,
                            style = MaterialTheme.typography.bodyMedium,
                            lineHeight = 22.sp,
                        )
                    } else {
                        Text(
                            t(R.string.cancel_refund_policy),
                            style = MaterialTheme.typography.bodyMedium,
                            color = Brand.Muted,
                        )
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
