package com.durpalla.booking.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsBoat
import androidx.compose.material.icons.filled.ListAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.durpalla.booking.R
import com.durpalla.booking.core.*
import com.durpalla.booking.l10n.t
import com.durpalla.booking.ui.components.*
import com.durpalla.booking.ui.nav.LocalContainer
import com.durpalla.booking.ui.nav.LocalNav
import com.durpalla.booking.ui.nav.Routes
import com.durpalla.booking.ui.theme.Brand
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import androidx.compose.ui.graphics.Color
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Enforces 6:00 AM – 10:00 PM boat operating window (mirrors web `lib/boatServiceHours.ts` & Flutter `boat_service_hours.dart`). */
object BoatServiceHours {
    const val OPEN_HOUR = 6
    const val CLOSE_HOUR = 22
    fun isWithinServiceHours(startHour: Int, endHour: Int): Boolean =
        startHour in OPEN_HOUR..CLOSE_HOUR && endHour in OPEN_HOUR..CLOSE_HOUR && endHour > startHour
}

@Composable
fun BoatListScreen(query: String = "", startsAt: String = "", endsAt: String = "") {
    val c = LocalContainer.current
    val nav = LocalNav.current
    var selectedType by remember { mutableStateOf("") }
    val start = startsAt.ifBlank { LocalDate.now().atTime(9, 0).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) }
    val end = endsAt.ifBlank { LocalDate.now().plusDays(1).atTime(18, 0).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) }
    val effectiveQuery = listOf(query.trim(), selectedType.trim()).filter { it.isNotEmpty() }.joinToString(" ")
    val vm = rememberLoad("boats-$effectiveQuery-$start-$end") { c.boats.search(effectiveQuery.ifBlank { null }, start, end) }
    val state by vm.state.collectAsState()
    val boatTypes = listOf(
        "" to R.string.boat_type_all,
        "House Boat" to R.string.boat_type_house,
        "Speed Boat" to R.string.boat_type_speed,
        "Steamer" to R.string.boat_type_steamer,
        "Fishing Boat" to R.string.boat_type_fishing,
    )

    Scaffold(
        topBar = {
            DTopBar(
                t(R.string.svc_boat),
                onBack = nav::back,
                actions = {
                    TextButton(onClick = { nav.go(Routes.BOAT_MY_REQUESTS) }) {
                        Icon(Icons.Default.ListAlt, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(t(R.string.my_trip_requests))
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = { nav.go(Routes.boatCustomTrip()) }) {
                Text(t(R.string.custom_trip))
            }
        },
    ) { pad ->
        Column(Modifier.padding(pad)) {
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                boatTypes.forEach { (key, labelRes) ->
                    FilterChip(selectedType == key, { selectedType = key }, { Text(t(labelRes)) })
                }
            }
            Box(Modifier.weight(1f)) {
                when (val s = state) {
                    Load.Loading -> LoadingList()
                    is Load.Err -> ErrorState(s.message) { vm.reload() }
                    is Load.Ok -> {
                        val filtered = if (selectedType.isEmpty()) s.value else s.value.filter { e ->
                            val hay = "${e.title} ${e.location} ${e.duration} ${e.raw.str("boat_type", "category", "description")}"
                            hay.contains(selectedType, ignoreCase = true)
                        }.ifEmpty { s.value }
                        if (filtered.isEmpty()) {
                            EmptyState(Icons.Default.DirectionsBoat, t(R.string.no_boats_found), t(R.string.no_boats_body))
                        } else LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            items(filtered, key = { it.id }) { e ->
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

    Scaffold(topBar = { DTopBar(t(R.string.svc_boat), onBack = nav::back) }) { pad ->
        when (val s = state) {
            Load.Loading -> Box(Modifier.padding(pad)) { LoadingList() }
            is Load.Err -> Box(Modifier.padding(pad)) { ErrorState(s.message) { vm.reload() } }
            is Load.Ok -> {
                val d = s.value
                val packages = d.a("packages").objects()
                var selectedPkgId by remember(d) { mutableStateOf(packages.firstOrNull()?.i("id")) }
                Column(
                    Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(d.str("name", "title", default = t(R.string.boat_numbered, id)), style = MaterialTheme.typography.headlineSmall)
                    Text(d.str("description", "details"), color = Brand.Muted)
                    if (packages.isNotEmpty()) {
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            packages.forEach { pkg ->
                                val pid = pkg.i("id") ?: return@forEach
                                val pTitle = pkg.str("name", "title", default = "#$pid")
                                FilterChip(selectedPkgId == pid, { selectedPkgId = pid }, { Text("$pTitle • ${money(pkg.d("price", "fare"))}") })
                            }
                        }
                    }
                    OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text(t(R.string.full_name)) })
                    OutlinedTextField(mobile, { mobile = it }, Modifier.fillMaxWidth(), label = { Text(t(R.string.mobile_number)) })
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(t(R.string.guests), Modifier.weight(1f))
                        IconButton({ if (guests > 1) guests-- }) { Text("-") }
                        Text("$guests")
                        IconButton({ guests++ }) { Text("+") }
                    }
                    val callTripId = d.i("driver_trip_id", "driverTripId", "trip_id") ?: id
                    CallDriverButton(
                        tripId = callTripId,
                        driverName = d.str("driver_name", "name", "title", default = t(R.string.call_driver)),
                        tripActive = true,
                    )
                    CallLogSection(callTripId, tripCallLog = d.a("callLog").objects())
                    err?.let { Text(it, color = Brand.Error) }
                    val guestErr = t(R.string.guest_name_mobile_required)
                    GradientButton(t(R.string.book_now), Modifier.fillMaxWidth(), loading = loading) {
                        if (!nav.ensureAuth(self)) return@GradientButton
                        if (name.isBlank() || mobile.isBlank()) {
                            err = guestErr
                            return@GradientButton
                        }
                        scope.launch {
                            loading = true
                            err = null
                            runCatching {
                                val rentalMode = d.str("rental_mode", default = if (selectedPkgId != null) "package" else "day")
                                c.boats.quote(
                                    jsonOf(
                                        "boat_id" to id,
                                        "rental_mode" to rentalMode,
                                        "starts_at" to startsAt,
                                        "ends_at" to endsAt,
                                        "guests" to guests,
                                        "package_id" to selectedPkgId,
                                    ),
                                )
                                val hold = c.boats.hold(
                                    boatId = id,
                                    rentalMode = rentalMode,
                                    startsAt = startsAt,
                                    endsAt = endsAt,
                                    guests = guests,
                                    guestName = name.trim(),
                                    guestMobile = mobile.trim(),
                                    packageId = selectedPkgId,
                                )
                                val holdId = hold.o("data").i("hold_id", "id") ?: hold.i("hold_id")
                                    ?: throw ApiException(AppCtx.str(R.string.err_hold_failed))
                                val conf = c.boats.confirm(holdId, name.trim(), mobile.trim())
                                val bid = conf.o("data").i("booking_id", "order_id", "id") ?: conf.i("booking_id")
                                    ?: throw ApiException(AppCtx.str(R.string.err_booking_id_short))
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
    var tripDate by remember { mutableStateOf(todayIso()) }
    var startHour by remember { mutableIntStateOf(9) }
    var endHour by remember { mutableIntStateOf(18) }
    var guests by remember { mutableIntStateOf(4) }
    var notes by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf<String?>(null) }
    var requestId by remember { mutableStateOf<Int?>(null) }
    var bids by remember { mutableStateOf<List<JsonObject>>(emptyList()) }
    var acceptedBidIds by remember { mutableStateOf(setOf<Int>()) }
    val scope = rememberCoroutineScope()
    val serviceHoursErr = t(R.string.boat_service_hours_blocked)

    Scaffold(
        topBar = {
            DTopBar(
                t(R.string.custom_boat_trip),
                onBack = nav::back,
                actions = {
                    TextButton(onClick = { nav.go(Routes.BOAT_MY_REQUESTS) }) {
                        Text(t(R.string.my_trip_requests))
                    }
                },
            )
        },
    ) { pad ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(pad)
                .dismissKeyboardOnTap()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            DateField(t(R.string.journey_date), tripDate, Modifier.fillMaxWidth()) { tripDate = it }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SoftCard(Modifier.weight(1f)) {
                    Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("${t(R.string.starts)}: ${startHour}:00", Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
                        IconButton({ if (startHour > 0) startHour-- }) { Text("-") }
                        IconButton({ if (startHour < 23) startHour++ }) { Text("+") }
                    }
                }
                SoftCard(Modifier.weight(1f)) {
                    Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("${t(R.string.ends)}: ${endHour}:00", Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
                        IconButton({ if (endHour > 0) endHour-- }) { Text("-") }
                        IconButton({ if (endHour < 23) endHour++ }) { Text("+") }
                    }
                }
            }
            if (!BoatServiceHours.isWithinServiceHours(startHour, endHour)) {
                Text(serviceHoursErr, color = Brand.Error, style = MaterialTheme.typography.bodySmall)
            }
            Text(t(R.string.pick_stoppage), style = MaterialTheme.typography.titleMedium)
            when (val s = state) {
                Load.Loading -> LoadingList(2)
                is Load.Err -> ErrorState(s.message) { vm.reload() }
                is Load.Ok -> s.value.forEach { st ->
                    val id = st.i("id") ?: return@forEach
                    val name = st.str("name", "title")
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(selected.contains(id), {
                            selected = if (it) selected + id else selected - id
                        })
                        Text(name)
                    }
                }
            }
            OutlinedTextField(notes, { notes = it }, Modifier.fillMaxWidth(), label = { Text(t(R.string.notes_optional)) })
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(t(R.string.guests), Modifier.weight(1f))
                IconButton({ if (guests > 1) guests-- }) { Text("-") }
                Text("$guests")
                IconButton({ guests++ }) { Text("+") }
            }
            err?.let { Text(it, color = Brand.Error) }
            GradientButton(
                t(R.string.request_bids),
                Modifier.fillMaxWidth(),
                enabled = selected.isNotEmpty() && BoatServiceHours.isWithinServiceHours(startHour, endHour),
                loading = loading,
            ) {
                if (!nav.ensureAuth(Routes.boatCustomTrip())) return@GradientButton
                if (!BoatServiceHours.isWithinServiceHours(startHour, endHour)) {
                    err = serviceHoursErr
                    return@GradientButton
                }
                val starts = LocalDate.parse(tripDate).atTime(startHour, 0).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                val ends = LocalDate.parse(tripDate).atTime(endHour, 0).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
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
                BoatBidsList(
                    requestId = requestId,
                    bids = bids,
                    acceptedBidIds = acceptedBidIds,
                    onAccepted = { bidId, bookingId, amt ->
                        acceptedBidIds = acceptedBidIds + bidId
                        nav.go(Routes.payment(bookingId, amt))
                    },
                    onMockAccepted = { bidId -> acceptedBidIds = acceptedBidIds + bidId },
                    onError = { err = it },
                )
            }
        }
    }
}

private fun parseDateTimeOrNull(raw: String?): LocalDateTime? {
    if (raw.isNullOrBlank()) return null
    val trimmed = raw.trim()
    val dateTimeFormats = listOf(
        DateTimeFormatter.ISO_DATE_TIME,
        DateTimeFormatter.ISO_LOCAL_DATE_TIME,
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"),
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"),
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm"),
        DateTimeFormatter.ofPattern("d MMM yyyy, h:mm a", Locale.US),
    )
    for (f in dateTimeFormats) {
        val dt = runCatching { LocalDateTime.parse(trimmed, f) }.getOrNull()
        if (dt != null) return dt
    }
    val d = runCatching { LocalDate.parse(trimmed.take(10)) }.getOrNull()
    if (d != null) return d.atStartOfDay()
    return null
}

private fun hasExplicitTime(raw: String?): Boolean {
    if (raw.isNullOrBlank()) return false
    return raw.contains(":")
}

private fun formatHumanDateTime(startsAt: String, endsAt: String? = null): String {
    if (startsAt.isBlank()) return ""
    val startDt = parseDateTimeOrNull(startsAt)
    val endDt = parseDateTimeOrNull(endsAt)
    val hasStartTime = hasExplicitTime(startsAt)
    val hasEndTime = hasExplicitTime(endsAt)
    val dateFormatter = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.US)
    val timeFormatter = DateTimeFormatter.ofPattern("h:mm a", Locale.US)

    return when {
        startDt != null && endDt != null -> {
            val startDateStr = startDt.format(dateFormatter)
            if (startDt.toLocalDate() == endDt.toLocalDate()) {
                if (hasStartTime && hasEndTime) {
                    val startTimeStr = startDt.format(timeFormatter)
                    val endTimeStr = endDt.format(timeFormatter)
                    "$startDateStr, $startTimeStr – $endTimeStr"
                } else if (hasStartTime) {
                    "$startDateStr, ${startDt.format(timeFormatter)}"
                } else {
                    startDateStr
                }
            } else {
                val endDateStr = endDt.format(dateFormatter)
                if (hasStartTime && hasEndTime) {
                    "$startDateStr, ${startDt.format(timeFormatter)} – $endDateStr, ${endDt.format(timeFormatter)}"
                } else {
                    "$startDateStr – $endDateStr"
                }
            }
        }
        startDt != null -> {
            val startDateStr = startDt.format(dateFormatter)
            if (hasStartTime) {
                "$startDateStr, ${startDt.format(timeFormatter)}"
            } else {
                startDateStr
            }
        }
        else -> startsAt
    }
}

@Composable
fun BoatMyTripRequestsScreen() {
    val c = LocalContainer.current
    val nav = LocalNav.current
    val vm = rememberLoad("my-boat-requests") { c.boats.listTripRequests() }
    val state by vm.state.collectAsState()
    var selectedReqId by remember { mutableStateOf<Int?>(null) }
    var detail by remember { mutableStateOf<JsonObject?>(null) }
    var bids by remember { mutableStateOf<List<JsonObject>>(emptyList()) }
    var acceptedBidIds by remember { mutableStateOf(setOf<Int>()) }
    var err by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Scaffold(topBar = { DTopBar(t(R.string.my_trip_requests), onBack = nav::back) }) { pad ->
        Box(Modifier.padding(pad)) {
            when (val s = state) {
                Load.Loading -> LoadingList()
                is Load.Err -> ErrorState(s.message) { vm.reload() }
                is Load.Ok -> if (s.value.isEmpty()) {
                    EmptyState(Icons.Default.DirectionsBoat, t(R.string.no_trip_requests), t(R.string.no_boats_body))
                } else LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(s.value) { req ->
                        val reqId = req.i("id") ?: return@items
                        val rawStatus = req.str("status", default = "open").lowercase()
                        val starts = req.str("starts_at", "date", "schedule_date")
                        val ends = req.str("ends_at", "end_date")
                        val expiresAt = req.str("expires_at", "expiry_time")
                        val guests = req.i("guests") ?: 1
                        val isExpanded = selectedReqId == reqId

                        val startDt = parseDateTimeOrNull(starts)
                        val endDt = parseDateTimeOrNull(ends)
                        val expiresDt = parseDateTimeOrNull(expiresAt)
                        val now = LocalDateTime.now()

                        val isAwarded = rawStatus in listOf("awarded", "confirmed", "accepted")
                        val isCancelled = rawStatus in listOf("cancelled", "canceled")
                        val isExpired = !isAwarded && (rawStatus == "expired" ||
                            (expiresDt != null && now.isAfter(expiresDt)) ||
                            (expiresDt == null && endDt != null && (if (hasExplicitTime(ends)) now.isAfter(endDt) else now.toLocalDate().isAfter(endDt.toLocalDate()))) ||
                            (expiresDt == null && endDt == null && startDt != null && (if (hasExplicitTime(starts)) now.isAfter(startDt) else now.toLocalDate().isAfter(startDt.toLocalDate()))))

                        val displayStatus = when {
                            isCancelled -> t(R.string.cancelled)
                            isExpired -> t(R.string.auto_cancelled)
                            isAwarded -> "Awarded"
                            else -> rawStatus.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
                        }

                        SoftCard(
                            onClick = {
                                if (isExpanded) {
                                    selectedReqId = null
                                } else {
                                    selectedReqId = reqId
                                    scope.launch {
                                        detail = runCatching { c.boats.tripRequest(reqId) }.getOrNull()
                                        bids = runCatching { c.boats.bids(reqId) }.getOrDefault(emptyList())
                                    }
                                }
                            },
                        ) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    val dateTimeLabel = formatHumanDateTime(starts, ends)
                                    Text(
                                        "#$reqId • $dateTimeLabel",
                                        style = MaterialTheme.typography.titleMedium,
                                        modifier = Modifier.weight(1f),
                                    )
                                    when {
                                        isCancelled || isExpired -> Pill(
                                            displayStatus,
                                            bg = Color(0xFFFEE2E2),
                                            fg = Brand.Error,
                                        )
                                        isAwarded -> Pill(
                                            displayStatus,
                                            bg = Color(0xFFDCFCE7),
                                            fg = Brand.Success,
                                        )
                                        else -> Pill(displayStatus)
                                    }
                                }
                                Text("${t(R.string.guests)}: $guests • ${req.str("notes")}", color = Brand.Muted, style = MaterialTheme.typography.bodySmall)
                                if (!isCancelled && !isExpired && !isAwarded && rawStatus == "open") {
                                    TextButton(
                                        onClick = {
                                            scope.launch {
                                                runCatching { c.boats.cancelTripRequest(reqId) }
                                                vm.reload()
                                            }
                                        },
                                    ) {
                                        Text(t(R.string.cancel_request), color = Brand.Error)
                                    }
                                }
                                if (isExpanded) {
                                    detail?.a("stoppages")?.objects()?.map { it.str("name") }?.filter { it.isNotBlank() }?.takeIf { it.isNotEmpty() }?.let { stNames ->
                                        Text(stNames.joinToString(" → "), style = MaterialTheme.typography.bodySmall, color = Brand.Primary)
                                    }
                                    err?.let { Text(it, color = Brand.Error) }
                                    BoatBidsList(
                                        requestId = reqId,
                                        bids = bids,
                                        acceptedBidIds = acceptedBidIds,
                                        canAccept = !isCancelled && !isExpired && !isAwarded,
                                        onAccepted = { bidId, bookingId, amt ->
                                            acceptedBidIds = acceptedBidIds + bidId
                                            nav.go(Routes.payment(bookingId, amt))
                                        },
                                        onMockAccepted = { bidId -> acceptedBidIds = acceptedBidIds + bidId },
                                        onError = { err = it },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BoatBidsList(
    requestId: Int?,
    bids: List<JsonObject>,
    acceptedBidIds: Set<Int>,
    canAccept: Boolean = true,
    onAccepted: (Int, Int, Double) -> Unit,
    onMockAccepted: (Int) -> Unit,
    onError: (String?) -> Unit,
) {
    val c = LocalContainer.current
    val scope = rememberCoroutineScope()
    var loadingBid by remember { mutableStateOf<Int?>(null) }

    Text(t(R.string.bids), style = MaterialTheme.typography.titleMedium)
    bids.forEach { b ->
        val bidId = b.i("id") ?: return@forEach
        val status = b.str("status").lowercase()
        val acceptable = status in listOf("pending", "open", "") && canAccept
        val accepted = status in listOf("accepted", "confirmed") || acceptedBidIds.contains(bidId)
        val driverName = b.str("driver_name", "boat_name", "operator", default = t(R.string.call_driver))
        val tripIdForCall = requestId ?: b.i("trip_id", "id") ?: bidId
        SoftCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp)) {
                Text(money(b.d("amount", "price", "fare")), fontWeight = FontWeight.Bold)
                Text(b.str("boat_name", "operator", "message"), color = Brand.Muted)
                if (acceptable) {
                    GradientButton(t(R.string.accept_bid), Modifier.fillMaxWidth(), loading = loadingBid == bidId) {
                        scope.launch {
                            loadingBid = bidId
                            runCatching {
                                val hold = c.boats.acceptBid(bidId)
                                val holdId = hold.o("data").i("hold_id", "id") ?: hold.i("hold_id")
                                    ?: throw ApiException(AppCtx.str(R.string.err_no_hold))
                                val user = c.session.user.value
                                val conf = c.boats.confirm(
                                    holdId,
                                    user.str("name", default = AppCtx.str(R.string.guest)),
                                    user.str("mobile", "phone", default = ""),
                                )
                                val bookingId = conf.o("data").i("booking_id", "order_id", "id")
                                    ?: throw ApiException(AppCtx.str(R.string.err_no_booking))
                                bookingId to (conf.o("data").d("total_payable", "amount") ?: 0.0)
                            }.onSuccess { (bookingId, amt) ->
                                onAccepted(bidId, bookingId, amt)
                            }.onFailure {
                                if (com.durpalla.booking.BuildConfig.USE_MOCK_CALLS) {
                                    onMockAccepted(bidId)
                                }
                                onError(it.message)
                            }
                            loadingBid = null
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
                    CallLogSection(
                        tripIdForCall,
                        tripCallLog = b.a("callLog").objects(),
                    )
                }
            }
        }
    }
}
