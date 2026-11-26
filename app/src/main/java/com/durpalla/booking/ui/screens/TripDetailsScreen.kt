package com.durpalla.booking.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.durpalla.booking.AppContainer
import com.durpalla.booking.R
import com.durpalla.booking.core.*
import com.durpalla.booking.data.repo.CartItem
import com.durpalla.booking.data.repo.SeatItem
import com.durpalla.booking.data.repo.SupplierLock
import com.durpalla.booking.data.repo.boardingOptionsFromTrip
import com.durpalla.booking.data.repo.findBoardingOption
import com.durpalla.booking.data.repo.withBoarding
import com.durpalla.booking.l10n.t
import com.durpalla.booking.ui.components.*
import com.durpalla.booking.ui.nav.LocalContainer
import com.durpalla.booking.ui.nav.LocalNav
import com.durpalla.booking.ui.nav.Routes
import com.durpalla.booking.ui.theme.Brand
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.serialization.json.*
import java.security.MessageDigest

/**
 * Port of trip_details_screen.dart following `.cursor/rules/seat-layout.mdc`:
 * - Outer floor map keys (`cabin_row`) are VERTICAL columns placed left -> right.
 * - Inner array items (`cabin_position`) are stacked top -> bottom inside each column.
 * - Empty columns `[]` are preserved as narrow aisle spacers.
 * - `cabin_type == "empty"` renders as an invisible spacer with no border/background.
 * - Bus/train prefer `seat` tab; launch prefers `cabin` tab.
 */
class TripDetailsVm(
    private val c: AppContainer,
    val tripId: String,
    val serviceType: String,
    private val bookToken: String? = null,
) : ViewModel() {
    val trip = MutableStateFlow<Load<JsonObject>>(Load.Loading)
    val tab = MutableStateFlow("")
    val floor = MutableStateFlow(1)
    val selectedClass = MutableStateFlow("")
    val selectedBoardingId = MutableStateFlow("")
    val busy = MutableStateFlow<Int?>(null)
    val selectedSeats = MutableStateFlow<List<SeatItem>>(emptyList())
    val snack = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val liveLocked = MutableStateFlow<Set<String>>(emptySet())
    val liveReleased = MutableStateFlow<Set<String>>(emptySet())
    private val cartStore = c.cart
    private val supplierTrip = tripId.startsWith("pb:")
    private val channel = if (supplierTrip) {
        val hash = MessageDigest.getInstance("SHA-256").digest(tripId.toByteArray())
            .joinToString("") { "%02x".format(it) }
        "trip.pb-$hash"
    } else {
        "trip.$tripId"
    }

    init {
        val initialLoad = load()
        viewModelScope.launch {
            c.pusher.events.filter { it.channel == channel }.collect { e ->
                if (e.name == "availability.changed") {
                    initialLoad.join()
                    refreshSupplierAvailability()
                    return@collect
                }
                val id = e.data.s("item_id") ?: return@collect
                val label = e.data.o("data").str("cabin_no", "seat_no", "label")
                when (e.name) {
                    "item.locked", "item.reserved", "item.booked", "cart.item.locked", "cart.item.booked" -> {
                        liveLocked.value += id; liveReleased.value -= id
                        if (cartStore.items.value.none { it.itemId.toString() == id }) {
                            snack.tryEmit(AppCtx.str(R.string.cabin_locked, label, e.data.o("data").str("fare")))
                        }
                    }
                    "item.released", "item.booking_cancelled", "cart.item.released" -> {
                        liveLocked.value -= id
                        liveReleased.value += id
                        snack.tryEmit(AppCtx.str(R.string.cabin_available, label))
                    }
                }
            }
        }
        if (supplierTrip) {
            c.pusher.subscribe(channel, listOf("availability.changed"))
        } else {
            c.pusher.subscribe(channel)
        }
    }

    private suspend fun refreshSupplierAvailability() {
        val current = trip.value as? Load.Ok ?: return
        val updated = runCatching { c.trips.tripDetails(tripId, bookToken) }.getOrNull() ?: return
        if (trip.value === current) trip.value = Load.Ok(updated)
    }

    fun load() = viewModelScope.launch {
        trip.value = Load.Loading
        trip.value = runCatching { c.trips.tripDetails(tripId, bookToken) }.fold({ d ->
            val fl = floors(d)
            val defaultFloor = d.i("default_floor")?.takeIf { df -> fl.any { it.first == df } }
                ?: fl.firstOrNull()?.first ?: 1
            floor.value = defaultFloor
            val tabs = availableTabs(d)
            tab.value = preferredTab(tabs, defaultFloor, d)
            selectedClass.value = ""
            val boarding = boardingOptionsFromTrip(d)
            val current = selectedBoardingId.value
            selectedBoardingId.value = when {
                current.isNotBlank() && boarding.any { it.id == current } -> current
                boarding.isNotEmpty() -> boarding.first().id
                else -> ""
            }
            Load.Ok(d)
        }, { Load.Err(it.message ?: AppCtx.str(R.string.generic_error)) })
    }

    private fun preferredTab(tabs: List<String>, fl: Int, d: JsonObject): String {
        if (tabs.isEmpty()) return ""
        val isBusOrTrain = serviceType.equals("bus", true) || serviceType.equals("train", true) ||
            d.str("service_type", "type").lowercase() in listOf("bus", "train")
        val preferredOrder = if (isBusOrTrain) listOf("seat", "sofa", "cabin") else listOf("cabin", "seat", "sofa")
        // Pick first preferred tab that has items on the current floor, else across any floor
        return preferredOrder.firstOrNull { k -> k in tabs && hasBookableItems(columns(d, k, fl)) }
            ?: preferredOrder.firstOrNull { it in tabs }
            ?: tabs.first()
    }

    fun onFloorSelected(newFloor: Int, d: JsonObject) {
        floor.value = newFloor
        selectedClass.value = ""
        val currentTab = tab.value
        if (!hasBookableItems(columns(d, currentTab, newFloor))) {
            val tabs = availableTabs(d)
            tab.value = preferredTab(tabs, newFloor, d)
        }
    }

    private fun floorKey(f: Int) = when (f) { 1 -> "first_floor"; 2 -> "second_floor"; 3 -> "third_floor"; else -> "fourth_floor" }
    private fun containerKey(kind: String) = when (kind) { "seat" -> "seats"; "sofa" -> "sofas"; else -> "cabins" }

    private fun floorRaw(d: JsonObject, kind: String, f: Int): JsonElement? {
        val raw = d.o(containerKey(kind))?.get(floorKey(f))
        return if (raw is JsonPrimitive && raw.isString) runCatching { LooseJson.parseToJsonElement(raw.content) }.getOrNull() else raw
    }

    /**
     * Vertical columns placed left -> right.
     * NEVER drop empty `[]` columns — they represent vehicle aisle gaps!
     */
    fun columns(d: JsonObject, kind: String, f: Int): List<List<SeatItem>> {
        val raw = floorRaw(d, kind, f) ?: return emptyList()
        fun item(m: JsonObject) = SeatItem(
            m.i("item_id", "id"),
            m.str("cabin_no", "seat_no", "label", "name", default = "?"),
            m.d("fare", "price") ?: 0.0,
            kind,
            m.s("cabin_type"),
            m.str("cabin_class", "seat_class", "class_name"),
            m.i("status"),
            m.s("booking_id"),
            m,
        )
        return when (raw) {
            is JsonObject -> raw.entries
                .sortedWith(compareBy({ it.key.filter(Char::isDigit).toIntOrNull() ?: Int.MAX_VALUE }, { it.key }))
                .map { entry -> (entry.value as? JsonArray)?.objects()?.map(::item) ?: emptyList() }
            is JsonArray -> raw.map { r ->
                when (r) {
                    is JsonArray -> r.objects().map(::item)
                    is JsonObject -> listOf(item(r))
                    else -> emptyList()
                }
            }
            else -> emptyList()
        }
    }

    private fun hasBookableItems(cols: List<List<SeatItem>>): Boolean =
        cols.any { col -> col.any { !it.isEmptySlot } }

    fun availableTabs(d: JsonObject): List<String> {
        val isBusOrTrain = serviceType.equals("bus", true) || serviceType.equals("train", true) ||
            d.str("service_type", "type").lowercase() in listOf("bus", "train")
        val order = if (isBusOrTrain) listOf("seat", "sofa", "cabin") else listOf("cabin", "seat", "sofa")
        return order.filter { k -> (1..4).any { f -> hasBookableItems(columns(d, k, f)) } }
    }

    fun floors(d: JsonObject): List<Pair<Int, String>> {
        val api = d.a("floors").objects().mapNotNull { m ->
            m.i("value")?.let { it to m.str("label", default = AppCtx.str(R.string.floor_numbered, it)) }
        }.filter { (v, _) -> listOf("cabin", "seat", "sofa").any { k -> hasBookableItems(columns(d, k, v)) } }
        if (api.isNotEmpty()) return api
        return (1..4).filter { f ->
            listOf("cabin", "seat", "sofa").any { k -> hasBookableItems(columns(d, k, f)) }
        }.map { it to AppCtx.str(R.string.floor_numbered, it) }
    }

    fun availableClasses(cols: List<List<SeatItem>>): List<String> =
        cols.flatten().filter { !it.isEmptySlot }.mapNotNull { item ->
            item.raw.str("cabin_type_title", "class_title", "cabin_class", "seat_class")
                .takeIf { it.isNotBlank() && !it.equals("available", true) && !it.equals("disable", true) }
        }.distinct()

    fun containsSeat(line: CartItem, id: Int?): Boolean =
        line.tripId == tripId && id != null && (line.itemId == id ||
            line.meta.a("selected_item_ids").orEmpty().any { (it as? JsonPrimitive)?.intOrNull == id })

    fun isLocked(s: SeatItem): Boolean {
        val id = s.itemId?.toString() ?: return true
        if (cartStore.items.value.any { containsSeat(it, s.itemId) }) return false
        if (id in liveLocked.value) return true
        if (id in liveReleased.value && s.bookingId == null) return false
        return s.isBooked || s.isTempLocked
    }

    fun toggle(s: SeatItem, d: JsonObject) {
        if (busy.value != null) return
        val id = s.itemId ?: return
        val existing = cartStore.items.value.firstOrNull { containsSeat(it, id) }
        if (existing == null && (d.s("book_token") ?: bookToken) != null) {
            val seats = selectedSeats.value
            when {
                cartStore.items.value.any { it.tripId == tripId } ->
                    snack.tryEmit(AppCtx.str(R.string.release_before_changing_seats))
                seats.any { it.itemId == id } -> selectedSeats.value = seats.filterNot { it.itemId == id }
                seats.size >= 4 -> snack.tryEmit(AppCtx.str(R.string.max_supplier_seats))
                !isLocked(s) -> selectedSeats.value = seats + s
            }
            return
        }
        updateHold(listOf(s), d, existing)
    }

    fun holdSelected(d: JsonObject) {
        val seats = selectedSeats.value
        if (busy.value != null || seats.isEmpty()) return
        if (cartStore.items.value.isNotEmpty()) {
            snack.tryEmit(AppCtx.str(R.string.release_before_changing_seats))
            return
        }
        updateHold(seats, d)
    }

    private fun updateHold(seats: List<SeatItem>, d: JsonObject, existing: CartItem? = null) {
        val tokenAtSelection = c.session.token
        val s = seats.first()
        val id = s.itemId ?: return
        viewModelScope.launch {
            busy.value = id
            try {
                if (existing != null) {
                    if (c.trips.releaseLock(existing)) cartStore.remove(existing)
                    else snack.emit(AppCtx.str(R.string.could_not_release))
                } else {
                    val user = if (tokenAtSelection != null) c.profile.profile() else null
                    if (c.session.token != tokenAtSelection) return@launch
                    val mobile = user.str("mobile", "phone").trim()
                    if (tokenAtSelection != null && mobile.isBlank()) {
                        snack.emit(AppCtx.str(R.string.seat_hold_registered_mobile_required))
                        return@launch
                    }
                    val options = boardingOptionsFromTrip(d)
                    val boarding = findBoardingOption(options, selectedBoardingId.value)
                    val token = d.s("book_token") ?: bookToken
                    val supplier = if (token != null) {
                        val bp = boarding ?: options.firstOrNull()
                        SupplierLock(
                            bookToken = token,
                            seatIds = seats.map { it.raw.s("supplier_seat_id") ?: it.itemId.toString() },
                            seatLabels = seats.map { it.label },
                            ticketPrices = seats.map { it.fare },
                            passengerName = user?.str("name", "full_name")?.trim(),
                            passengerMobile = mobile.ifBlank { null },
                            boardingPointId = bp?.id,
                            boardingPointName = bp?.name,
                        )
                    } else null
                    val r = c.trips.lockItem(tripId, s.kind, id, c.pusher.socketId, supplier)
                    val body = r.o("data") ?: JsonObject(emptyMap())
                    val data = body.a("items").objects().firstOrNull() ?: body
                    val lockId = data.s("lock_id") ?: data.s("reservation_id") ?: data.s("booking_id")
                        ?: r.s("lock_id") ?: ""
                    val meta = buildJsonObject {
                        data.withBoarding(d, boarding).forEach { (k, v) -> put(k, v) }
                        put("trip_id", tripId)
                        (data.s("supplier") ?: d.s("supplier"))?.let { put("supplier", it) }
                        (data.s("book_token") ?: token)?.let { put("book_token", it) }
                        put("booking_id", data.s("booking_id") ?: lockId)
                        if (supplier != null) put("selected_item_ids", buildJsonArray {
                            seats.mapNotNull { it.itemId }.forEach { add(JsonPrimitive(it)) }
                        })
                    }
                    cartStore.add(
                        CartItem(
                            itemId = id,
                            type = s.kind,
                            price = data.d("fare", "price") ?: seats.sumOf { it.fare },
                            lockId = lockId,
                            label = seats.joinToString { it.label },
                            tripId = tripId,
                            vehicleName = d.str("vehicle_name"),
                            routeName = d.str("route_name"),
                            expiresAt = data.s("expires_at") ?: data.s("locked_until"),
                            meta = meta,
                            totalCharge = data.d("total_charge") ?: 0.0,
                            totalVat = data.d("total_vat") ?: 0.0,
                        ),
                    )
                    if (supplier != null) selectedSeats.value = emptyList()
                }
            } catch (e: Exception) {
                snack.emit(e.message ?: AppCtx.str(R.string.status_failed))
            } finally {
                busy.value = null
            }
        }
    }

    override fun onCleared() { c.pusher.unsubscribe(channel) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripDetailsScreen(tripId: String, type: String, bookToken: String? = null) {
    val c = LocalContainer.current; val nav = LocalNav.current
    val vm: TripDetailsVm = viewModel(key = "trip-$tripId") { TripDetailsVm(c, tripId, type, bookToken) }
    val state by vm.trip.collectAsState()
    val tab by vm.tab.collectAsState()
    val floor by vm.floor.collectAsState()
    val selectedClass by vm.selectedClass.collectAsState()
    val selectedBoardingId by vm.selectedBoardingId.collectAsState()
    val cart by c.cart.items.collectAsState()
    val busy by vm.busy.collectAsState()
    val selectedSeats by vm.selectedSeats.collectAsState()
    vm.liveLocked.collectAsState().value; vm.liveReleased.collectAsState().value
    val snackHost = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { vm.snack.collect { snackHost.showSnackbar(it) } }
    val mine = cart.filter { it.tripId == tripId }

    Scaffold(
        topBar = { DTopBar(t(R.string.trip_details), onBack = nav::back) },
        snackbarHost = { SnackbarHost(snackHost) },
        bottomBar = {
            AnimatedVisibility(mine.isNotEmpty() || selectedSeats.isNotEmpty(), enter = slideInVertically { it } + fadeIn(), exit = slideOutVertically { it } + fadeOut()) {
                Surface(shadowElevation = 16.dp) {
                    Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            val labels = if (selectedSeats.isNotEmpty()) selectedSeats.map { it.label }
                                else mine.flatMap { it.label.split(", ") }
                            Text(t(R.string.selected_count, labels.size, labels.joinToString()), style = MaterialTheme.typography.bodySmall, color = Brand.Muted, maxLines = 1)
                            Text(money(mine.sumOf { it.lineTotal } + selectedSeats.sumOf { it.fare }), style = MaterialTheme.typography.titleLarge, color = Brand.AmberDark, fontWeight = FontWeight.ExtraBold)
                        }
                        if (selectedSeats.isNotEmpty()) {
                            GradientButton(t(R.string.hold_selected_seats), Modifier.width(190.dp), loading = busy != null) {
                                (state as? Load.Ok)?.value?.let(vm::holdSelected)
                            }
                        } else {
                            GradientButton(t(R.string.continue_checkout), Modifier.width(190.dp)) { nav.go(Routes.CHECKOUT) }
                        }
                    }
                }
            }
        },
    ) { pad ->
        when (val s = state) {
            Load.Loading -> Box(Modifier.padding(pad)) { LoadingList(3) }
            is Load.Err -> Box(Modifier.padding(pad)) { ErrorState(s.message) { vm.load() } }
            is Load.Ok -> {
                val d = s.value
                val effectiveType = d.str("service_type", "type", default = type).lowercase()
                Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState())) {
                    Box(Modifier.fillMaxWidth().height(170.dp)) {
                        PhotoBox(d.s("vehicle_photo"), if (effectiveType == "bus") R.drawable.photo_river else R.drawable.photo_launch, Modifier.matchParentSize())
                        Box(Modifier.matchParentSize().background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color.Transparent, Brand.Navy.copy(.85f)))))
                        Column(Modifier.align(Alignment.BottomStart).padding(16.dp)) {
                            Text(d.str("vehicle_name", default = tripId.toIntOrNull()?.let { t(R.string.trip_numbered, it) } ?: tripId), color = Color.White, style = MaterialTheme.typography.titleLarge)
                            Text(d.str("route_name"), color = Color.White.copy(.85f))
                            val stops = d.a("stoppages").objects().map { it.str("name") }.filter { it.isNotBlank() }
                            if (stops.isNotEmpty()) Text(stops.joinToString(" • "), color = Brand.Amber, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                        }
                    }
                    SupplierPolicyLinks(d.o("supplier_policies"), Modifier.padding(horizontal = 16.dp))
                    val boardingOptions = remember(d) { boardingOptionsFromTrip(d) }
                    if (boardingOptions.isNotEmpty()) {
                        SoftCard(Modifier.padding(horizontal = 16.dp, vertical = 10.dp).fillMaxWidth()) {
                            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(t(R.string.boarding_point), style = MaterialTheme.typography.titleSmall)
                                Text(
                                    t(R.string.boarding_hint),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Brand.Muted,
                                )
                                var expanded by remember { mutableStateOf(false) }
                                val selected = findBoardingOption(boardingOptions, selectedBoardingId)
                                    ?: boardingOptions.first()
                                val boardingLocked = mine.isNotEmpty()
                                ExposedDropdownMenuBox(
                                    expanded = expanded && !boardingLocked,
                                    onExpandedChange = { if (!boardingLocked) expanded = !expanded },
                                ) {
                                    OutlinedTextField(
                                        value = selected.label,
                                        onValueChange = {},
                                        readOnly = true,
                                        enabled = !boardingLocked,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .menuAnchor(),
                                        trailingIcon = {
                                            ExposedDropdownMenuDefaults.TrailingIcon(expanded && !boardingLocked)
                                        },
                                        label = { Text(t(R.string.boarding_point)) },
                                    )
                                    ExposedDropdownMenu(
                                        expanded = expanded && !boardingLocked,
                                        onDismissRequest = { expanded = false },
                                    ) {
                                        boardingOptions.forEach { option ->
                                            DropdownMenuItem(
                                                text = { Text(option.label) },
                                                onClick = {
                                                    vm.selectedBoardingId.value = option.id
                                                    expanded = false
                                                },
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    val tabs = vm.availableTabs(d)
                    if (tabs.size > 1) TabRow(tabs.indexOf(tab).coerceAtLeast(0)) {
                        tabs.forEach { k -> Tab(k == tab, { vm.tab.value = k; vm.selectedClass.value = "" }, text = { Text(k.replaceFirstChar { it.uppercase() }) }) }
                    }
                    val fl = vm.floors(d)
                    val numFloors = d.i("number_of_floor") ?: fl.size
                    val showFloors = effectiveType == "launch" || effectiveType == "train" || fl.size > 1 || numFloors > 1
                    if (showFloors && fl.isNotEmpty()) {
                        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            fl.forEach { (v, l) -> FilterChip(v == floor, { vm.onFloorSelected(v, d) }, { Text(l) }) }
                        }
                    }
                    val cols = vm.columns(d, tab.ifEmpty { if (effectiveType == "bus") "seat" else "cabin" }, floor)
                    val classes = remember(cols) { vm.availableClasses(cols) }
                    if (classes.size > 1) {
                        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(selectedClass.isEmpty(), { vm.selectedClass.value = "" }, { Text(t(R.string.all_classes)) })
                            classes.forEach { clsName ->
                                val label = when (clsName.lowercase()) {
                                    "cabin-active" -> t(R.string.seat_class_active)
                                    "cabin-disabled" -> t(R.string.seat_class_disabled)
                                    else -> clsName
                                }
                                FilterChip(selectedClass == clsName, { vm.selectedClass.value = if (selectedClass == clsName) "" else clsName }, { Text(label) })
                            }
                        }
                    }
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Legend(Brand.P50, Brand.P700, t(R.string.available)); Legend(Brand.Amber, Brand.AmberDark, t(R.string.selected))
                        Legend(Brand.Muted, Brand.Muted, t(R.string.booked)); Legend(Color(0xFFFEE2E2), Brand.Error, t(R.string.held))
                    }
                    if (cols.all { it.isEmpty() }) {
                        EmptyState(Icons.Default.EventSeat, t(R.string.no_layout_floor))
                    } else {
                        SoftCard(Modifier.padding(16.dp).fillMaxWidth()) {
                            Column(Modifier.padding(12.dp)) {
                                VehicleOrientationHeader(effectiveType)
                                Spacer(Modifier.height(10.dp))
                                // Outer cols = vertical columns placed left -> right; inner list = slots top -> bottom
                                Row(
                                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                                ) {
                                    cols.forEach { verticalCol ->
                                        if (verticalCol.isEmpty()) {
                                            // Keep empty API columns as narrow aisle gaps
                                            Spacer(Modifier.width(24.dp))
                                        } else {
                                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                                verticalCol.forEach { item ->
                                                    val itemClass = item.raw.str("cabin_type_title", "class_title", "cabin_class", "seat_class")
                                                    val matchesClass = selectedClass.isEmpty() || itemClass.equals(selectedClass, true)
                                                    if (item.isEmptySlot || !matchesClass) {
                                                        // Invisible cell spacer (no border, no background)
                                                        Spacer(Modifier.size(58.dp))
                                                    } else {
                                                        val selectedForHold = selectedSeats.any { it.itemId == item.itemId }
                                                        SeatCell(
                                                            s = item,
                                                            selected = selectedForHold || mine.any { vm.containsSeat(it, item.itemId) },
                                                            locked = !selectedForHold && vm.isLocked(item),
                                                            busy = busy == item.itemId,
                                                        ) { vm.toggle(item, d) }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(90.dp))
                }
            }
        }
    }

}

@Composable
private fun VehicleOrientationHeader(serviceType: String) {
    val (leftLabel, rightLabel) = when (serviceType.lowercase()) {
        "bus" -> t(R.string.orient_bus_left) to t(R.string.orient_bus_right)
        "train" -> t(R.string.orient_train_left) to t(R.string.orient_train_right)
        else -> t(R.string.orient_launch_left) to t(R.string.orient_launch_right)
    }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Brand.P50).padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(leftLabel, style = MaterialTheme.typography.labelMedium, color = Brand.P700, fontWeight = FontWeight.SemiBold)
        Box(Modifier.weight(1f).padding(horizontal = 12.dp).height(1.dp).background(Brand.P300))
        Text(rightLabel, style = MaterialTheme.typography.labelMedium, color = Brand.P700, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun Legend(bg: Color, border: Color, label: String) = Row(verticalAlignment = Alignment.CenterVertically) {
    Box(Modifier.size(14.dp).clip(RoundedCornerShape(4.dp)).background(bg).border(1.dp, border, RoundedCornerShape(4.dp)))
    Spacer(Modifier.width(4.dp)); Text(label, style = MaterialTheme.typography.labelSmall)
}

@Composable
private fun SeatCell(s: SeatItem, selected: Boolean, locked: Boolean, busy: Boolean, onClick: () -> Unit) {
    val booked = locked && s.isBooked
    val border = when {
        selected -> Brand.AmberDark
        booked -> Brand.Muted
        locked -> Brand.Error
        else -> Brand.P700
    }
    val textColor = if (booked) Color.White else Brand.Ink
    val bg by androidx.compose.animation.animateColorAsState(
        when {
            selected -> Brand.Amber
            booked -> Brand.Muted
            locked -> Color(0xFFFEE2E2)
            else -> Brand.P50
        },
        label = "seat",
    )
    Box(
        Modifier.size(58.dp).clip(RoundedCornerShape(12.dp)).background(bg)
            .border(1.dp, border, RoundedCornerShape(12.dp))
            .clickable(enabled = !locked && !busy, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
        else Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(s.label, fontWeight = FontWeight.Bold, fontSize = 12.sp, textAlign = TextAlign.Center, maxLines = 1, color = textColor)
            if (s.fare > 0) Text("৳${s.fare.toInt()}", fontSize = 9.sp, color = if (booked) textColor else Brand.Muted)
        }
    }
}
