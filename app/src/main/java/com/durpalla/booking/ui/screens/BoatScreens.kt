package com.durpalla.booking.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsBoat
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.durpalla.booking.R
import com.durpalla.booking.core.*
import com.durpalla.booking.ui.components.*
import com.durpalla.booking.ui.nav.LocalContainer
import com.durpalla.booking.ui.nav.LocalNav
import com.durpalla.booking.ui.nav.Routes
import com.durpalla.booking.ui.theme.Brand
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Composable
fun BoatListScreen(query: String = "", startsAt: String = "", endsAt: String = "") {
    val c = LocalContainer.current
    val nav = LocalNav.current
    val start = startsAt.ifBlank { LocalDate.now().atTime(9, 0).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) }
    val end = endsAt.ifBlank { LocalDate.now().plusDays(1).atTime(18, 0).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) }
    val vm = rememberLoad("boats-$query-$start-$end") { c.boats.search(query.ifBlank { null }, start, end) }
    val state by vm.state.collectAsState()
    Scaffold(
        topBar = { DTopBar(stringResource(R.string.svc_boat), onBack = nav::back) },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = { nav.go(Routes.boatCustomTrip()) }) {
                Text("Custom trip")
            }
        },
    ) { pad ->
        Box(Modifier.padding(pad)) {
            when (val s = state) {
                Load.Loading -> LoadingList()
                is Load.Err -> ErrorState(s.message) { vm.reload() }
                is Load.Ok -> if (s.value.isEmpty()) {
                    EmptyState(Icons.Default.DirectionsBoat, "No boats found", "Try other dates or a custom trip.")
                } else LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    items(s.value, key = { it.id }) { e ->
                        SoftCard(onClick = { nav.go(Routes.boatDetails(e.id, start, end)) }) {
                            if (e.photoUrl != null) {
                                AsyncImage(e.photoUrl, null, Modifier.fillMaxWidth().height(150.dp), contentScale = ContentScale.Crop)
                            }
                            Column(Modifier.padding(14.dp)) {
                                Text(e.title, style = MaterialTheme.typography.titleMedium)
                                Text("${e.location} • ${e.duration}", color = Brand.Muted, style = MaterialTheme.typography.bodySmall)
                                Text(money(e.price), color = Brand.AmberDark, fontWeight = FontWeight.ExtraBold)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun BoatDetailsScreen(id: Int, startsAt: String, endsAt: String) {
    val c = LocalContainer.current
    val nav = LocalNav.current
    val vm = rememberLoad("boat-$id") { c.boats.details(id) }
    val state by vm.state.collectAsState()
    var guests by remember { mutableIntStateOf(2) }
    var name by remember { mutableStateOf("") }
    var mobile by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val self = Routes.boatDetails(id, startsAt, endsAt)

    Scaffold(topBar = { DTopBar(stringResource(R.string.svc_boat), onBack = nav::back) }) { pad ->
        when (val s = state) {
            Load.Loading -> Box(Modifier.padding(pad)) { LoadingList() }
            is Load.Err -> Box(Modifier.padding(pad)) { ErrorState(s.message) { vm.reload() } }
            is Load.Ok -> {
                val d = s.value
                Column(
                    Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(d.str("name", "title", default = "Boat #$id"), style = MaterialTheme.typography.headlineSmall)
                    Text(d.str("description", "details"), color = Brand.Muted)
                    OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.full_name)) })
                    OutlinedTextField(mobile, { mobile = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.mobile_number)) })
                    Row {
                        Text("Guests", Modifier.weight(1f))
                        IconButton({ if (guests > 1) guests-- }) { Text("-") }
                        Text("$guests")
                        IconButton({ guests++ }) { Text("+") }
                    }
                    // Demo Call driver when mock calls enabled (bid/booking not yet confirmed)
                    if (com.durpalla.booking.BuildConfig.USE_MOCK_CALLS) {
                        CallDriverButton(
                            tripId = id,
                            driverName = d.str("driver_name", "name", "title", default = stringResource(R.string.call_driver)),
                            tripActive = true,
                        )
                        CallLogSection(id)
                    }
                    err?.let { Text(it, color = Brand.Error) }
                    GradientButton(stringResource(R.string.book_now), Modifier.fillMaxWidth(), loading = loading) {
                        if (!nav.ensureAuth(self)) return@GradientButton
                        if (name.isBlank() || mobile.isBlank()) {
                            err = "Guest name and mobile are required"
                            return@GradientButton
                        }
                        scope.launch {
                            loading = true
                            err = null
                            runCatching {
                                val hold = c.boats.hold(
                                    boatId = id,
                                    rentalMode = d.str("rental_mode", default = "day"),
                                    startsAt = startsAt,
                                    endsAt = endsAt,
                                    guests = guests,
                                    guestName = name.trim(),
                                    guestMobile = mobile.trim(),
                                )
                                val holdId = hold.o("data").i("hold_id", "id") ?: hold.i("hold_id")
                                    ?: throw ApiException("Hold failed")
                                val conf = c.boats.confirm(holdId, name.trim(), mobile.trim())
                                val bid = conf.o("data").i("booking_id", "order_id", "id") ?: conf.i("booking_id")
                                    ?: throw ApiException("Booking ID missing")
                                val amt = conf.o("data").d("total_payable", "amount", "total")
                                    ?: d.d("price", "price_from") ?: 0.0
                                bid to amt
                            }.onSuccess { (bid, amt) -> nav.go(Routes.payment(bid, amt)) }
                                .onFailure { err = it.message }
                            loading = false
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun BoatCustomTripScreen() {
    val c = LocalContainer.current
    val nav = LocalNav.current
    val vm = rememberLoad("boat-stoppages") { c.boats.stoppages() }
    val state by vm.state.collectAsState()
    var selected by remember { mutableStateOf(setOf<Int>()) }
    var guests by remember { mutableIntStateOf(4) }
    var notes by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf<String?>(null) }
    var requestId by remember { mutableStateOf<Int?>(null) }
    var bids by remember { mutableStateOf<List<kotlinx.serialization.json.JsonObject>>(emptyList()) }
    var acceptedBidIds by remember { mutableStateOf(setOf<Int>()) }
    val scope = rememberCoroutineScope()
    val starts = LocalDateTime.now().plusDays(1).withHour(9).withMinute(0)
        .format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
    val ends = LocalDateTime.now().plusDays(1).withHour(18).withMinute(0)
        .format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)

    Scaffold(topBar = { DTopBar("Custom boat trip", onBack = nav::back) }) { pad ->
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Pick at least one stoppage", style = MaterialTheme.typography.titleMedium)
            when (val s = state) {
                Load.Loading -> LoadingList(2)
                is Load.Err -> ErrorState(s.message) { vm.reload() }
                is Load.Ok -> s.value.forEach { st ->
                    val id = st.i("id") ?: return@forEach
                    val name = st.str("name", "title")
                    Row {
                        Checkbox(selected.contains(id), {
                            selected = if (it) selected + id else selected - id
                        })
                        Text(name, Modifier.padding(top = 12.dp))
                    }
                }
            }
            OutlinedTextField(notes, { notes = it }, Modifier.fillMaxWidth(), label = { Text("Notes (optional)") })
            Row {
                Text("Guests", Modifier.weight(1f))
                IconButton({ if (guests > 1) guests-- }) { Text("-") }
                Text("$guests")
                IconButton({ guests++ }) { Text("+") }
            }
            err?.let { Text(it, color = Brand.Error) }
            GradientButton("Request bids", Modifier.fillMaxWidth(), enabled = selected.isNotEmpty(), loading = loading) {
                if (!nav.ensureAuth(Routes.boatCustomTrip())) return@GradientButton
                scope.launch {
                    loading = true
                    err = null
                    runCatching {
                        c.boats.createTripRequest(selected.toList(), starts, ends, guests, notes.ifBlank { null })
                    }.onSuccess { r ->
                        requestId = r.o("data").i("id") ?: r.i("id")
                        requestId?.let { bids = c.boats.bids(it) }
                    }.onFailure { err = it.message }
                    loading = false
                }
            }
            if (bids.isNotEmpty()) {
                Text("Bids", style = MaterialTheme.typography.titleMedium)
                bids.forEach { b ->
                    val bidId = b.i("id") ?: return@forEach
                    val status = b.str("status").lowercase()
                    val acceptable = status in listOf("pending", "open", "")
                    val accepted = status in listOf("accepted", "confirmed") || acceptedBidIds.contains(bidId)
                    val driverName = b.str("driver_name", "boat_name", "operator", default = stringResource(R.string.call_driver))
                    val tripIdForCall = requestId ?: b.i("trip_id", "id") ?: bidId
                    SoftCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp)) {
                            Text(money(b.d("amount", "price", "fare")), fontWeight = FontWeight.Bold)
                            Text(b.str("boat_name", "operator", "message"), color = Brand.Muted)
                            if (acceptable) {
                                GradientButton("Accept bid", Modifier.fillMaxWidth()) {
                                    scope.launch {
                                        loading = true
                                        runCatching {
                                            val hold = c.boats.acceptBid(bidId)
                                            val holdId = hold.o("data").i("hold_id", "id") ?: hold.i("hold_id")
                                                ?: throw ApiException("No hold")
                                            val user = c.session.user.value
                                            val conf = c.boats.confirm(
                                                holdId,
                                                user.str("name", default = "Guest"),
                                                user.str("mobile", "phone", default = ""),
                                            )
                                            val bookingId = conf.o("data").i("booking_id", "order_id", "id")
                                                ?: throw ApiException("No booking")
                                            bookingId to (conf.o("data").d("total_payable", "amount") ?: 0.0)
                                        }.onSuccess { (bid, amt) ->
                                            acceptedBidIds = acceptedBidIds + bidId
                                            nav.go(Routes.payment(bid, amt))
                                        }.onFailure {
                                            // Mock path: still mark accepted so Call driver can be demoed
                                            if (com.durpalla.booking.BuildConfig.USE_MOCK_CALLS) {
                                                acceptedBidIds = acceptedBidIds + bidId
                                            }
                                            err = it.message
                                        }
                                        loading = false
                                    }
                                }
                            }
                            if (accepted) {
                                Spacer(Modifier.height(8.dp))
                                CallDriverButton(
                                    tripId = tripIdForCall,
                                    driverName = driverName,
                                    bidAccepted = true,
                                )
                                CallLogSection(tripIdForCall)
                            }
                        }
                    }
                }
            }
        }
    }
}
