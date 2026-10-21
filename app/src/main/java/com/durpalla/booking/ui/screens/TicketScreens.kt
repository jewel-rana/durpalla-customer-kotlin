package com.durpalla.booking.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.durpalla.booking.R
import com.durpalla.booking.core.*
import com.durpalla.booking.data.repo.Booking
import com.durpalla.booking.ui.components.*
import com.durpalla.booking.ui.nav.LocalContainer
import com.durpalla.booking.ui.nav.LocalNav
import com.durpalla.booking.ui.nav.Routes
import com.durpalla.booking.ui.theme.Brand
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.launch
import com.durpalla.booking.l10n.t

@Composable
fun TicketsScreen() {
    val c = LocalContainer.current; val nav = LocalNav.current
    val vm = rememberLoad("tickets") { c.bookings.myBookings(1).first }
    val state by vm.state.collectAsState()
    var tab by remember { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Box(Modifier.fillMaxWidth().background(Brand.HeroGradient).statusBarsPadding().padding(20.dp)) {
            Text(t(R.string.my_tickets), style = MaterialTheme.typography.headlineSmall, color = Color.White)
        }
        TabRow(tab) { listOf(R.string.upcoming, R.string.past, R.string.cancelled).forEachIndexed { i, l -> Tab(tab == i, { tab = i }, text = { Text(t(l)) }) } }
        when (val s = state) {
            Load.Loading -> LoadingList()
            is Load.Err -> ErrorState(s.message) { vm.reload() }
            is Load.Ok -> {
                val list = s.value.filter { b ->
                    val st = (b.status + " " + b.paymentStatus).lowercase()
                    when (tab) { 2 -> st.contains("cancel"); 1 -> b.isPast() && !st.contains("cancel"); else -> !b.isPast() && !st.contains("cancel") }
                }
                if (list.isEmpty()) EmptyState(Icons.Default.ConfirmationNumber, t(R.string.no_tickets_yet), t(R.string.no_tickets_body), t(R.string.find_a_trip)) { nav.tab(Routes.SEARCH) }
                else LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(list, key = { it.id }) { b -> BookingRow(b) { nav.go(Routes.ticket(b.id)) } }
                }
            }
        }
    }
}

private fun Booking.isPast(): Boolean {
    val d = firstItem.str("schedule_date", "check_out").take(10)
    return runCatching { java.time.LocalDate.parse(d).isBefore(java.time.LocalDate.now()) }.getOrDefault(false)
}

@Composable
private fun BookingRow(b: Booking, onClick: () -> Unit) {
    val i = b.firstItem
    SoftCard(onClick = onClick) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)).background(Brand.P100), contentAlignment = Alignment.Center) {
                Icon(if (i.str("item_type", "type").contains("hotel", true)) Icons.Default.Hotel else Icons.Default.DirectionsBoat, null, tint = Brand.Primary)
            }
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(i.str("vehicle_name", "hotel_name", default = t(R.string.booking_numbered, b.id)), style = MaterialTheme.typography.titleSmall)
                Text(i.str("route_name", "room_type_title"), style = MaterialTheme.typography.bodySmall, color = Brand.Muted)
                Text(i.str("leaving_time_formated", "schedule_date", default = b.bookingDate), style = MaterialTheme.typography.labelSmall, color = Brand.Primary)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(money(b.totalPayable), fontWeight = FontWeight.Bold)
                val paid = b.paymentStatus.contains("paid", true) || b.paymentStatus.contains("success", true)
                Pill(b.paymentStatus.ifBlank { b.status }, if (paid) Brand.Success.copy(.12f) else Brand.Amber.copy(.15f), if (paid) Brand.Success else Brand.AmberDark)
            }
        }
    }
}

@Composable
fun TicketScreen(id: Int) {
    val c = LocalContainer.current; val nav = LocalNav.current
    val ctx = LocalContext.current
    val vm = rememberLoad("ticket-$id") { c.bookings.bookingDetails(id) }
    val state by vm.state.collectAsState()
    val scope = rememberCoroutineScope()
    var confirmCancel by remember { mutableStateOf<Int?>(null) }
    var msg by remember { mutableStateOf<String?>(null) }
    Scaffold(topBar = { DTopBar(t(R.string.ticket), onBack = nav::back) }) { pad ->
        when (val s = state) {
            Load.Loading -> Box(Modifier.padding(pad)) { LoadingList(2) }
            is Load.Err -> Box(Modifier.padding(pad)) { ErrorState(s.message) { vm.reload() } }
            is Load.Ok -> {
                val root = s.value.o("data") ?: s.value.o("booking") ?: s.value
                val b = Booking.from(root.o("booking") ?: root)
                Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(16.dp)) {
                    Column(Modifier.fillMaxWidth().shadow(18.dp, RoundedCornerShape(24.dp), ambientColor = Brand.Primary, spotColor = Brand.Primary)
                        .clip(RoundedCornerShape(24.dp)).background(MaterialTheme.colorScheme.surface)) {
                        Box(Modifier.fillMaxWidth().background(Brand.HeroGradient).padding(20.dp)) {
                            Column {
                                LogoHorizontal(Modifier.height(24.dp), white = true)
                                Spacer(Modifier.height(10.dp))
                                Text(b.firstItem.str("vehicle_name", "hotel_name", default = t(R.string.label_booking)), color = Color.White, style = MaterialTheme.typography.titleLarge)
                                Text(b.firstItem.str("route_name", "room_type_title"), color = Color.White.copy(.85f))
                            }
                        }
                        Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            val qrText = root.s("qr_code") ?: root.s("qr") ?: root.s("qrCode")
                                ?: "DURPALLA|PNR:${b.pnr}|BID:${b.id}"
                            val bmp = remember(qrText) { qr(qrText) }
                            Image(bmp.asImageBitmap(), t(R.string.ticket), Modifier.size(200.dp))
                            Text(t(R.string.label_pnr, b.pnr), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            HorizontalDivider(Modifier.padding(vertical = 12.dp))
                            Info(t(R.string.label_date), b.firstItem.str("leaving_time_formated", "schedule_date", default = b.bookingDate))
                            Info(t(R.string.label_boarding), b.firstItem.str("boarding_point", default = t(R.string.em_dash)))
                            Info(t(R.string.label_payment), b.paymentStatus)
                            Info(t(R.string.label_transaction), b.transactionId ?: t(R.string.em_dash))
                            Info(t(R.string.total), money(b.totalPayable))
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    Text(t(R.string.label_items), style = MaterialTheme.typography.titleMedium)
                    b.items.forEach { it0 ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("${it0.str("cabin_type", "item_type", "type")} ${it0.str("cabin_no")}", Modifier.weight(1f))
                            Text(money(it0.d("fare", "price")))
                            if (com.durpalla.booking.core.BookingCancelRules.canCancelItem(b, it0)) {
                                TextButton({ confirmCancel = it0.i("id") }) {
                                    Text(t(R.string.cancel_ticket), color = Brand.Error)
                                }
                            }
                            b.invoiceUrl?.takeIf { it.isNotBlank() && b.downloadable }?.let { url ->
                                TextButton({
                                    runCatching {
                                        ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url)))
                                    }
                                }) { Text(t(R.string.invoice)) }
                            }
                        }
                    }
                    msg?.let { Text(it, color = Brand.Primary) }
                    val tripActive = !b.isPast() &&
                        !com.durpalla.booking.core.BookingCancelRules.isHotelStay(b) &&
                        listOf(b.paymentStatus, b.status).joinToString(" ").lowercase().let { st ->
                            listOf("paid", "success", "confirmed", "complete").any { st.contains(it) } &&
                                listOf("cancel", "fail", "refund").none { st.contains(it) }
                        }
                    val driverName = b.firstItem.str("driver_name", "vehicle_name", "operator_name", default = t(R.string.call_driver))
                    if (tripActive) {
                        Spacer(Modifier.height(12.dp))
                        CallDriverButton(tripId = b.id, driverName = driverName, tripActive = true)
                        val tripLog = b.raw.a("callLog").objects()
                            .ifEmpty { b.firstItem.a("callLog").objects() }
                        CallLogSection(b.id, tripCallLog = tripLog)
                    }
                }
            }
        }
    }
    confirmCancel?.let { itemId ->
        AlertDialog(onDismissRequest = { confirmCancel = null }, title = { Text(t(R.string.cancel_ticket) + "?") },
            text = { Text(t(R.string.cancel_refund_policy)) },
            confirmButton = { Button({ confirmCancel = null; scope.launch { msg = runCatching { c.bookings.cancelItem(itemId).message ?: AppCtx.str(R.string.cancelled) }.getOrElse { it.message }; vm.reload() } }) { Text(t(R.string.confirm)) } },
            dismissButton = { TextButton({ confirmCancel = null }) { Text(t(R.string.keep)) } })
    }
}

@Composable private fun Info(k: String, v: String) = Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
    Text(k, Modifier.weight(1f), color = Brand.Muted); Text(v, fontWeight = FontWeight.SemiBold)
}

fun qr(text: String, size: Int = 512): Bitmap {
    val m = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size)
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
    for (x in 0 until size) for (y in 0 until size) bmp.setPixel(x, y, if (m[x, y]) 0xFF0A3550.toInt() else 0xFFFFFFFF.toInt())
    return bmp
}
