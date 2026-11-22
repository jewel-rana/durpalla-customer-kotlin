package com.durpalla.booking.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.durpalla.booking.R
import com.durpalla.booking.core.*
import com.durpalla.booking.data.repo.Booking
import com.durpalla.booking.l10n.t
import com.durpalla.booking.ui.components.*
import com.durpalla.booking.ui.nav.LocalContainer
import com.durpalla.booking.ui.nav.LocalNav
import com.durpalla.booking.ui.nav.Routes
import com.durpalla.booking.ui.theme.Brand
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject

@Composable
fun TicketsScreen() {
    val c = LocalContainer.current; val nav = LocalNav.current
    val vm = rememberLoad("tickets") { c.bookings.myBookings(1) }
    val state by vm.state.collectAsState()
    var tab by remember { mutableIntStateOf(0) }
    var extraItems by remember { mutableStateOf<List<Booking>>(emptyList()) }
    var currentPage by remember { mutableIntStateOf(1) }
    var loadingMore by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Box(Modifier.fillMaxWidth().background(Brand.HeroGradient).statusBarsPadding().padding(20.dp)) {
            Text(t(R.string.my_tickets), style = MaterialTheme.typography.headlineSmall, color = Color.White)
        }
        TabRow(tab) { listOf(R.string.upcoming, R.string.past, R.string.cancelled).forEachIndexed { i, l -> Tab(tab == i, { tab = i }, text = { Text(t(l)) }) } }
        when (val s = state) {
            Load.Loading -> LoadingList()
            is Load.Err -> ErrorState(s.message) { extraItems = emptyList(); currentPage = 1; vm.reload() }
            is Load.Ok -> {
                val (firstPageItems, lastPage) = s.value
                val allBookings = remember(firstPageItems, extraItems) { (firstPageItems + extraItems).distinctBy { it.id } }
                val list = allBookings.filter { b ->
                    val st = (b.status + " " + b.paymentStatus).lowercase()
                    when (tab) { 2 -> st.contains("cancel"); 1 -> b.isPast() && !st.contains("cancel"); else -> !b.isPast() && !st.contains("cancel") }
                }
                if (list.isEmpty() && currentPage >= lastPage) {
                    EmptyState(Icons.Default.ConfirmationNumber, t(R.string.no_tickets_yet), t(R.string.no_tickets_body), t(R.string.find_a_trip)) { nav.tab(Routes.SEARCH) }
                } else LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(list, key = { it.id }) { b -> BookingRow(b) { nav.go(Routes.ticket(b.id)) } }
                    if (currentPage < lastPage) {
                        item {
                            OutlinedButton(
                                onClick = {
                                    if (!loadingMore) {
                                        scope.launch {
                                            loadingMore = true
                                            runCatching { c.bookings.myBookings(currentPage + 1) }
                                                .onSuccess { (more, _) ->
                                                    extraItems = extraItems + more
                                                    currentPage += 1
                                                }
                                            loadingMore = false
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                if (loadingMore) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                                else Text(t(R.string.load_more))
                            }
                        }
                    }
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
                Text(i.str("leaving_time_formated", "leaving_time_formatted", "schedule_date", default = b.bookingDate), style = MaterialTheme.typography.labelSmall, color = Brand.Primary)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(money(b.totalPayable), fontWeight = FontWeight.Bold)
                val paid = b.paymentStatus.contains("paid", true) || b.paymentStatus.contains("success", true)
                Pill(b.paymentStatus.ifBlank { b.status }, if (paid) Brand.Success.copy(.12f) else Brand.Amber.copy(.15f), if (paid) Brand.Success else Brand.AmberDark)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TicketScreen(id: Int) {
    val c = LocalContainer.current; val nav = LocalNav.current
    val ctx = LocalContext.current
    val vm = rememberLoad("ticket-$id") { c.bookings.bookingDetails(id) }
    val state by vm.state.collectAsState()
    val scope = rememberCoroutineScope()
    var confirmCancel by remember { mutableStateOf<Int?>(null) }
    var msg by remember { mutableStateOf<String?>(null) }
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { LogoHorizontal(Modifier.height(26.dp)) },
                navigationIcon = {
                    IconButton(onClick = nav::back) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { pad ->
        when (val s = state) {
            Load.Loading -> Box(Modifier.padding(pad)) { LoadingList(2) }
            is Load.Err -> Box(Modifier.padding(pad)) { ErrorState(s.message) { vm.reload() } }
            is Load.Ok -> {
                val res = s.value
                val dataObj = res.o("booking") ?: res.o("data")?.let { d -> d.o("booking") ?: d } ?: res
                val b = Booking.from(dataObj)
                val qrText = listOf("qr_code", "qr", "qrCode")
                    .firstNotNullOfOrNull { dataObj.s(it) ?: res.s(it) ?: res.o("data").s(it) }
                    ?: "DURPALLA|PNR:${b.pnr.ifBlank { b.id.toString() }}|BID:${b.id}"

                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(pad),
                    contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 36.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    item {
                        val fi = b.firstItem
                        val isHotel = BookingCancelRules.isHotelStay(b)
                        val isBoat = BookingCancelRules.isBoatBooking(b)
                        val rawType = b.raw.str("service_type", "type", "booking_type", "category", "vertical").lowercase()
                        val itemType = fi.str("item_type", "type", "service_type", "vehicle_type").lowercase()
                        val isEntertainment = rawType.contains("entertainment") || itemType.contains("entertainment") || fi.s("venue_name") != null
                        val isTour = rawType.contains("tour") || itemType.contains("tour") || fi.s("tour_title") != null
                        val isBus = rawType.contains("bus") || itemType.contains("bus")
                        val isLaunch = !isBus && !isBoat && (rawType.contains("launch") || itemType.contains("launch") || itemType.contains("cabin") || itemType.contains("deck"))

                        val serviceName = when {
                            isHotel -> t(R.string.svc_hotel)
                            isBoat -> t(R.string.svc_boat)
                            isEntertainment -> "Entertainment"
                            isTour -> t(R.string.svc_tour)
                            isLaunch -> t(R.string.svc_launch)
                            isBus -> t(R.string.svc_bus)
                            else -> fi.str("service_name", "service_type", "type").ifBlank {
                                b.raw.str("service_name", "service_type", "type")
                            }.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }.ifBlank { t(R.string.svc_bus) }
                        }

                        val isSuccessful = run {
                            val blob = listOf(b.paymentStatus, b.status).joinToString(" ").lowercase()
                            val hasSuccess = listOf("complete", "completed", "success", "confirmed", "paid", "successful").any { blob.contains(it) }
                            val hasFailure = listOf("cancel", "fail", "reject", "refund", "incomplete", "pending", "draft", "expired").any { blob.contains(it) }
                            hasSuccess && !hasFailure
                        }

                        val title = fi.str("vehicle_name", "hotel_name", "boat_name").ifBlank {
                            dataObj.str("vehicle_name", "hotel_name", "boat_name", default = t(R.string.label_booking))
                        }
                        val sub = fi.str("route_name", "room_type_title").ifBlank {
                            dataObj.str("route_name", "room_type_title")
                        }
                        val depDate = fi.str("schedule_date", "check_in").ifBlank { b.bookingDate }
                        val depTime = fi.str("leaving_time_formated", "leaving_time_formatted", "leaving_time", "check_out")
                        val depLine = listOf(depDate, depTime).filter { it.isNotBlank() }.joinToString(" • ")

                        Column(
                            Modifier.fillMaxWidth()
                                .shadow(18.dp, RoundedCornerShape(24.dp), ambientColor = Brand.Primary, spotColor = Brand.Primary)
                                .clip(RoundedCornerShape(24.dp))
                                .background(MaterialTheme.colorScheme.surface),
                        ) {
                            Box(Modifier.fillMaxWidth().background(Brand.HeroGradient).padding(20.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(end = 76.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Box(
                                        Modifier.size(46.dp).clip(RoundedCornerShape(14.dp)).background(Color.White.copy(alpha = 0.18f)),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Icon(
                                            when {
                                                isHotel -> Icons.Default.Apartment
                                                isBus -> Icons.Default.DirectionsBus
                                                else -> Icons.Default.DirectionsBoat
                                            },
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(26.dp),
                                        )
                                    }
                                    Spacer(Modifier.width(14.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            title,
                                            color = Color.White,
                                            style = MaterialTheme.typography.titleLarge,
                                            fontWeight = FontWeight.Bold,
                                        )
                                        if (sub.isNotBlank()) {
                                            Text(sub, color = Color.White.copy(.9f), style = MaterialTheme.typography.bodyMedium)
                                        }
                                        if (depLine.isNotBlank()) {
                                            Spacer(Modifier.height(2.dp))
                                            Text(depLine, color = Color.White.copy(.78f), style = MaterialTheme.typography.labelMedium)
                                        }
                                    }
                                }
                                Surface(
                                    modifier = Modifier.align(Alignment.TopEnd),
                                    shape = RoundedCornerShape(10.dp),
                                    color = Color.White.copy(alpha = 0.22f),
                                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.35f)),
                                ) {
                                    Text(
                                        text = serviceName,
                                        color = Color.White,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                                    )
                                }
                            }
                            Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                if (isSuccessful) {
                                    val bmp = remember(qrText) { qr(qrText) }
                                    Box(Modifier.size(204.dp), contentAlignment = Alignment.Center) {
                                        Image(bmp.asImageBitmap(), t(R.string.ticket), Modifier.fillMaxSize())
                                        Surface(
                                            modifier = Modifier.size(44.dp),
                                            shape = CircleShape,
                                            color = Color.White,
                                            shadowElevation = 4.dp,
                                        ) {
                                            Box(Modifier.padding(6.dp), contentAlignment = Alignment.Center) {
                                                Image(
                                                    painter = painterResource(R.drawable.logo_mark),
                                                    contentDescription = null,
                                                    modifier = Modifier.fillMaxSize(),
                                                )
                                            }
                                        }
                                    }
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .size(204.dp)
                                            .clip(RoundedCornerShape(16.dp))
                                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                                            .border(
                                                BorderStroke(1.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
                                                RoundedCornerShape(16.dp),
                                            ),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.Center,
                                            modifier = Modifier.padding(16.dp),
                                        ) {
                                            Icon(
                                                Icons.Default.QrCode2,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                                modifier = Modifier.size(56.dp),
                                            )
                                            Spacer(Modifier.height(8.dp))
                                            Text(
                                                text = t(R.string.label_payment) + ": " + b.paymentStatus.ifBlank { b.status },
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                                            )
                                        }
                                    }
                                }
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    t(R.string.label_pnr, b.pnr.ifBlank { "#${b.id}" }),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                )
                                HorizontalDivider(Modifier.padding(vertical = 12.dp))
                                Info(
                                    t(R.string.label_date),
                                    b.firstItem.str("leaving_time_formated", "leaving_time_formatted", "schedule_date", "check_in", default = b.bookingDate),
                                )
                                Info(t(R.string.label_boarding), b.firstItem.str("boarding_point", default = t(R.string.em_dash)))
                                Info(t(R.string.label_payment), b.paymentStatus.ifBlank { b.status })
                                val payMethod = listOf("payment_method", "method")
                                    .firstNotNullOfOrNull { dataObj.s(it) ?: dataObj.o("payment")?.s(it) }
                                    ?: b.raw.str("payment_method")
                                val gwName = listOf("gateway_name", "gateway", "payment_gateway")
                                    .firstNotNullOfOrNull { dataObj.s(it) ?: dataObj.o("payment")?.s(it) ?: dataObj.o("gateway")?.s("name") }
                                    ?: b.raw.str("gateway_name", "gateway")
                                if (payMethod.isNotBlank()) {
                                    Info(t(R.string.payment_method), payMethod.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() })
                                }
                                if (gwName.isNotBlank() && !gwName.equals(payMethod, ignoreCase = true)) {
                                    Info(t(R.string.label_gateway), gwName)
                                }
                                Info(t(R.string.label_status), b.status.ifBlank { b.paymentStatus })
                                Info(t(R.string.label_transaction), b.transactionId ?: t(R.string.em_dash))
                                val subTotal = dataObj.d("total_amount")
                                val discount = dataObj.d("total_discount")
                                val vatTotal = dataObj.d("vat_total")
                                val chargeTotal = dataObj.d("charge_total")
                                if (subTotal != null && subTotal > 0.0) Info(t(R.string.fare), money(subTotal))
                                if (discount != null && discount > 0.0) Info("Discount", money(discount))
                                if (vatTotal != null && vatTotal > 0.0) Info(t(R.string.vat), money(vatTotal))
                                if (chargeTotal != null && chargeTotal > 0.0) Info(t(R.string.service_charge), money(chargeTotal))
                                Info(t(R.string.total), money(b.totalPayable))
                            }
                        }
                    }

                    b.invoiceUrl?.takeIf { it.isNotBlank() && b.downloadable }?.let { url ->
                        item {
                            Button(
                                onClick = {
                                    runCatching {
                                        ctx.startActivity(
                                            android.content.Intent(
                                                android.content.Intent.ACTION_VIEW,
                                                android.net.Uri.parse(url),
                                            ),
                                        )
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().height(48.dp),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Brand.Primary,
                                    contentColor = Color.White,
                                ),
                                elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp),
                            ) {
                                Icon(Icons.Default.Download, contentDescription = null, Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    t(R.string.download_invoice),
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleSmall,
                                )
                            }
                        }
                    }

                    if (b.items.isNotEmpty()) {
                        item {
                            Text(
                                "${t(R.string.label_items)} (${b.items.size})",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        items(b.items) { it0 ->
                            TicketItemCard(
                                booking = b,
                                item = it0,
                                onCancel = { confirmCancel = it0.i("id") },
                            )
                        }
                    }

                    b.firstItem?.let { first ->
                        item {
                            PassengerAndTripCard(first)
                        }
                    }

                    msg?.let { m ->
                        item {
                            Text(m, color = Brand.Primary, style = MaterialTheme.typography.bodyMedium)
                        }
                    }

                    val driverTripId = b.raw.i("driver_trip_id", "driverTripId", "trip_id")
                        ?: b.firstItem.i("driver_trip_id", "driverTripId", "trip_id")
                        ?: b.id
                    val tripActive = !b.isPast() &&
                        BookingCancelRules.isBoatBooking(b) &&
                        driverTripId > 0 &&
                        listOf(b.paymentStatus, b.status).joinToString(" ").lowercase().let { st ->
                            listOf("paid", "success", "confirmed", "complete").any { st.contains(it) } &&
                                listOf("cancel", "fail", "refund").none { st.contains(it) }
                        }
                    if (tripActive) {
                        item {
                            val driverName = b.firstItem.str("driver_name", "vehicle_name", "operator_name", default = t(R.string.call_driver))
                            Column {
                                CallDriverButton(tripId = driverTripId, driverName = driverName, tripActive = true)
                                val tripLog = b.raw.a("callLog").objects()
                                    .ifEmpty { b.firstItem.a("callLog").objects() }
                                CallLogSection(driverTripId, tripCallLog = tripLog)
                            }
                        }
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

@Composable
private fun TicketItemCard(
    booking: Booking,
    item: JsonObject,
    onCancel: () -> Unit,
) {
    val isHotel = BookingCancelRules.isHotelStay(booking, item)
    val fare = money(item.d("fare", "price", "amount"))
    val canCancel = BookingCancelRules.canCancelItem(booking, item)

    SoftCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (isHotel) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Apartment, null, tint = Brand.Primary, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        item.str("hotel_name", default = t(R.string.svc_hotel)),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                    )
                    Text(fare, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.ExtraBold, color = Brand.Primary)
                }
                val room = item.str("room_type_title", default = t(R.string.room))
                Text(room, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                val ci = item.str("check_in")
                val co = item.str("check_out")
                if (ci.isNotBlank() || co.isNotBlank()) {
                    Text("$ci → $co", style = MaterialTheme.typography.bodySmall, color = Brand.Muted)
                }
                val adults = item.i("adults") ?: 0
                val children = item.i("children") ?: 0
                if (adults > 0 || children > 0) {
                    Text("${t(R.string.adults)}: $adults" + if (children > 0) ", ${t(R.string.children)}: $children" else "", style = MaterialTheme.typography.bodySmall, color = Brand.Muted)
                }
            } else {
                val cabinNo = item.str("cabin_no", "seat_no", "label")
                val cabinType = item.str("cabin_type", "item_type", "type").uppercase()
                val acText = if (item.b("is_ac")) t(R.string.filter_ac) else t(R.string.filter_non_ac)
                val title = listOf(cabinNo, cabinType, acText).filter { it.isNotBlank() }.joinToString(" · ")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.EventSeat, null, tint = Brand.Primary, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        title.ifBlank { t(R.string.ticket) },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                    )
                    Text(fare, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.ExtraBold, color = Brand.Primary)
                }
                val sub = listOf(item.str("vehicle_name"), item.str("route_name")).filter { it.isNotBlank() }.joinToString(" · ")
                if (sub.isNotBlank()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.DirectionsBoat, null, tint = Brand.Muted, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(sub, style = MaterialTheme.typography.bodySmall, color = Brand.Muted)
                    }
                }
                val dep = listOf(
                    item.str("schedule_date"),
                    item.str("leaving_time_formated", "leaving_time_formatted", "leaving_time"),
                ).filter { it.isNotBlank() }.joinToString(" · ")
                if (dep.isNotBlank()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Schedule, null, tint = Brand.Muted, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(dep, style = MaterialTheme.typography.bodySmall, color = Brand.Muted)
                    }
                }
            }
            if (canCancel) {
                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Brand.Error),
                    border = BorderStroke(1.dp, Brand.Error.copy(alpha = 0.6f)),
                ) {
                    Icon(Icons.Default.Cancel, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(t(R.string.cancel_ticket))
                }
            }
        }
    }
}

@Composable
private fun PassengerAndTripCard(item: JsonObject) {
    val p = item.o("passenger")
    val pName = p.str("name", default = item.str("passenger_name", "guest_name"))
    val pMobile = p.str("mobile", "phone", default = item.str("passenger_mobile", "guest_mobile"))
    val pType = p.str("type")
    val pPersons = p.i("person", "persons") ?: 1

    if (pName.isBlank() && pMobile.isBlank() && item.str("route_name").isBlank()) return

    SoftCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(t(R.string.passenger), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            if (pName.isNotBlank()) {
                Info(t(R.string.full_name), if (pType.isNotBlank()) "$pName ($pType)" else pName)
            }
            if (pMobile.isNotBlank()) {
                Info(t(R.string.mobile_number), pMobile)
            }
            if (pPersons > 0) {
                Info(t(R.string.guests), "$pPersons")
            }
        }
    }
}

@Composable private fun Info(k: String, v: String) = Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
    Text(k, Modifier.weight(1f), color = Brand.Muted); Text(v, fontWeight = FontWeight.SemiBold)
}

fun qr(text: String, size: Int = 512): Bitmap {
    val hints = mapOf(
        EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.H,
        EncodeHintType.MARGIN to 1,
    )
    val m = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size, hints)
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
    for (x in 0 until size) for (y in 0 until size) bmp.setPixel(x, y, if (m[x, y]) 0xFF0A2E46.toInt() else 0xFFFFFFFF.toInt())
    return bmp
}
