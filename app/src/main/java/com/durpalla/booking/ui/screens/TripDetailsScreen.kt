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
import com.durpalla.booking.ui.components.*
import com.durpalla.booking.ui.nav.LocalContainer
import com.durpalla.booking.ui.nav.LocalNav
import com.durpalla.booking.ui.nav.Routes
import com.durpalla.booking.ui.theme.Brand
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.serialization.json.*
import com.durpalla.booking.l10n.t

/** Port of trip_details_screen.dart: layout parsing, cart/add lock, cart/unlock release, Pusher live updates. */
class TripDetailsVm(private val c: AppContainer, val tripId: Int, val serviceType: String) : ViewModel() {
    val trip = MutableStateFlow<Load<JsonObject>>(Load.Loading)
    val tab = MutableStateFlow("")
    val floor = MutableStateFlow(1)
    val busy = MutableStateFlow<Int?>(null)
    val snack = MutableSharedFlow<String>(extraBufferCapacity = 4)
    /** item ids locked/booked/released live via Pusher. */
    val liveLocked = MutableStateFlow<Set<String>>(emptySet())
    val liveReleased = MutableStateFlow<Set<String>>(emptySet())
    private val cartStore = c.cart
    private val channel = "trip.$tripId"

    init {
        load()
        c.pusher.subscribe(channel)
        viewModelScope.launch {
            c.pusher.events.filter { it.channel == channel }.collect { e ->
                val id = e.data.s("item_id") ?: return@collect
                val label = e.data.o("data").str("cabin_no")
                when (e.name) {
                    "cart.item.locked", "cart.item.booked" -> {
                        liveLocked.value += id; liveReleased.value -= id
                        if (cartStore.items.value.none { it.itemId.toString() == id }) {
                            snack.tryEmit(AppCtx.str(R.string.cabin_locked, label, e.data.o("data").str("fare")))
                        }
                    }
                    "cart.item.released" -> {
                        liveLocked.value -= id
                        liveReleased.value += id
                        snack.tryEmit(AppCtx.str(R.string.cabin_available, label))
                    }
                }
            }
        }
    }

    fun load() = viewModelScope.launch {
        trip.value = Load.Loading
        trip.value = runCatching { c.trips.tripDetails(tripId) }.fold({ d ->
            val tabs = availableTabs(d); if (tab.value !in tabs) tab.value = tabs.firstOrNull() ?: ""
            val fl = floors(d); if (fl.none { it.first == floor.value }) floor.value = fl.firstOrNull()?.first ?: 1
            Load.Ok(d)
        }, { Load.Err(it.message ?: AppCtx.str(R.string.generic_error)) })
    }

    private fun floorKey(f: Int) = when (f) { 1 -> "first_floor"; 2 -> "second_floor"; 3 -> "third_floor"; else -> "fourth_floor" }
    private fun containerKey(kind: String) = when (kind) { "seat" -> "seats"; "sofa" -> "sofas"; else -> "cabins" }

    private fun floorRaw(d: JsonObject, kind: String, f: Int): JsonElement? {
        val raw = d.o(containerKey(kind))?.get(floorKey(f))
        return if (raw is JsonPrimitive && raw.isString) runCatching { LooseJson.parseToJsonElement(raw.content) }.getOrNull() else raw
    }

    /** Columns (rows) of items for kind+floor, sorted by row key like `_compareRowKeys`. */
    fun columns(d: JsonObject, kind: String, f: Int): List<List<SeatItem>> {
        val raw = floorRaw(d, kind, f) ?: return emptyList()
        fun item(m: JsonObject) = SeatItem(m.i("item_id", "id"), m.str("cabin_no", "seat_no", "label", "name", default = "?"), m.d("fare", "price") ?: 0.0,
            kind, m.s("cabin_type"), m.str("cabin_class", "seat_class"), m.i("status"), m.s("booking_id"), m)
        return when (raw) {
            is JsonObject -> raw.entries.sortedWith(compareBy({ it.key.filter(Char::isDigit).toIntOrNull() ?: Int.MAX_VALUE }, { it.key }))
                .mapNotNull { (it.value as? JsonArray)?.objects()?.map(::item) }
            is JsonArray -> raw.map { r -> (r as? JsonArray)?.objects()?.map(::item) ?: listOfNotNull((r as? JsonObject)?.let(::item)) }
            else -> emptyList()
        }
    }

    fun availableTabs(d: JsonObject) = listOf("cabin", "seat", "sofa").filter { k -> (1..4).any { f -> columns(d, k, f).any { it.isNotEmpty() } } }

    fun floors(d: JsonObject): List<Pair<Int, String>> {
        val api = d.a("floors").objects().mapNotNull { m -> m.i("value")?.let { it to m.str("label", default = AppCtx.str(R.string.floor_numbered, it)) } }
            .filter { (v, _) -> listOf("cabin", "seat", "sofa").any { k -> columns(d, k, v).any { it.isNotEmpty() } } }
        if (api.isNotEmpty()) return api
        return (1..4).filter { f -> listOf("cabin", "seat", "sofa").any { k -> columns(d, k, f).any { it.isNotEmpty() } } }.map { it to AppCtx.str(R.string.floor_numbered, it) }
    }

    fun isLocked(s: SeatItem): Boolean {
        val id = s.itemId?.toString() ?: return true
        if (cartStore.items.value.any { it.itemId == s.itemId }) return false
        if (id in liveLocked.value) return true
        if (id in liveReleased.value && s.bookingId == null) return false
        return s.isBooked || s.isTempLocked
    }

    fun toggle(s: SeatItem, d: JsonObject) {
        val id = s.itemId ?: return
        val existing = cartStore.items.value.firstOrNull { it.itemId == id }
        viewModelScope.launch {
            busy.value = id
            try {
                if (existing != null) {
                    if (c.trips.releaseLock(existing.lockId, id)) cartStore.remove(existing)
                    else snack.emit(AppCtx.str(R.string.could_not_release))
                } else {
                    val r = c.trips.lockItem(tripId, s.kind, id, c.pusher.socketId)
                    val data = r.o("data")
                    val lockId = data.s("lock_id") ?: data.s("reservation_id") ?: r.s("lock_id") ?: ""
                    val meta = data
                    cartStore.add(
                        CartItem(
                            itemId = id,
                            type = s.kind,
                            price = data.d("fare", "price") ?: s.fare,
                            lockId = lockId,
                            label = s.label,
                            tripId = tripId,
                            vehicleName = d.str("vehicle_name"),
                            routeName = d.str("route_name"),
                            expiresAt = data.s("expires_at") ?: data.s("locked_until"),
                            meta = meta,
                            totalCharge = data.d("total_charge") ?: meta.d("total_charge") ?: 0.0,
                            totalVat = data.d("total_vat") ?: meta.d("total_vat") ?: 0.0,
                        ),
                    )
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

@Composable
fun TripDetailsScreen(tripId: Int, type: String) {
    val c = LocalContainer.current; val nav = LocalNav.current
    val vm: TripDetailsVm = viewModel(key = "trip-$tripId") { TripDetailsVm(c, tripId, type) }
    val state by vm.trip.collectAsState()
    val tab by vm.tab.collectAsState(); val floor by vm.floor.collectAsState()
    val cart by c.cart.items.collectAsState(); val busy by vm.busy.collectAsState()
    vm.liveLocked.collectAsState().value; vm.liveReleased.collectAsState().value
    val snackHost = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { vm.snack.collect { snackHost.showSnackbar(it) } }
    val mine = cart.filter { it.tripId == tripId }

    Scaffold(
        topBar = { DTopBar(t(R.string.trip_details), onBack = nav::back) },
        snackbarHost = { SnackbarHost(snackHost) },
        bottomBar = {
            AnimatedVisibility(mine.isNotEmpty(), enter = slideInVertically { it } + fadeIn(), exit = slideOutVertically { it } + fadeOut()) {
                Surface(shadowElevation = 16.dp) {
                    Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(t(R.string.selected_count, mine.size, mine.joinToString { it.label }), style = MaterialTheme.typography.bodySmall, color = Brand.Muted, maxLines = 1)
                            Text(money(mine.sumOf { it.lineTotal }), style = MaterialTheme.typography.titleLarge, color = Brand.AmberDark, fontWeight = FontWeight.ExtraBold)
                        }
                        GradientButton(t(R.string.continue_checkout), Modifier.width(190.dp)) { nav.go(Routes.CHECKOUT) }
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
                Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState())) {
                    Box(Modifier.fillMaxWidth().height(170.dp)) {
                        PhotoBox(d.s("vehicle_photo"), if (type == "bus") R.drawable.photo_river else R.drawable.photo_launch, Modifier.matchParentSize())
                        Box(Modifier.matchParentSize().background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color.Transparent, Brand.Navy.copy(.85f)))))
                        Column(Modifier.align(Alignment.BottomStart).padding(16.dp)) {
                            Text(d.str("vehicle_name", default = t(R.string.trip_numbered, tripId)), color = Color.White, style = MaterialTheme.typography.titleLarge)
                            Text(d.str("route_name"), color = Color.White.copy(.85f))
                            val stops = d.a("stoppages").objects().map { it.str("name") }.filter { it.isNotBlank() }
                            if (stops.isNotEmpty()) Text(stops.joinToString(" • "), color = Brand.Amber, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                        }
                    }
                    val tabs = vm.availableTabs(d)
                    if (tabs.size > 1) TabRow(tabs.indexOf(tab).coerceAtLeast(0)) {
                        tabs.forEach { k -> Tab(k == tab, { vm.tab.value = k }, text = { Text(k.replaceFirstChar { it.uppercase() }) }) }
                    }
                    val fl = vm.floors(d)
                    if (fl.size > 1) Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        fl.forEach { (v, l) -> FilterChip(v == floor, { vm.floor.value = v }, { Text(l) }) }
                    }
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Legend(Brand.P50, Brand.P300, t(R.string.available)); Legend(Brand.Amber, Brand.Amber, t(R.string.selected))
                        Legend(Brand.Line, Brand.Line, t(R.string.booked)); Legend(Color(0xFFFEE2E2), Color(0xFFFCA5A5), t(R.string.held))
                    }
                    val cols = vm.columns(d, tab.ifEmpty { "cabin" }, floor)
                    if (cols.all { it.isEmpty() }) EmptyState(Icons.Default.EventSeat, t(R.string.no_layout_floor))
                    SoftCard(Modifier.padding(16.dp).fillMaxWidth()) {
                        Column(Modifier.padding(12.dp).horizontalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            cols.forEach { row ->
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    row.forEach { item ->
                                        if (item.isEmptySlot) Spacer(Modifier.size(58.dp))
                                        else SeatCell(item, selected = mine.any { it.itemId == item.itemId }, locked = vm.isLocked(item), busy = busy == item.itemId) { vm.toggle(item, d) }
                                    }
                                }
                            }
                        }
                    }
                    // Call driver on active boat / in-progress trip (contract: only when trip active)
                    val tripActive = type.equals("boat", true) ||
                        d.str("status", "trip_status").lowercase() in listOf("active", "ongoing", "started", "boarding")
                    if (tripActive) {
                        val driver = d.str("driver_name", "captain_name", "vehicle_name", default = t(R.string.call_driver))
                        Column(Modifier.padding(horizontal = 16.dp)) {
                            CallDriverButton(tripId = tripId, driverName = driver, tripActive = true)
                            CallLogSection(tripId)
                        }
                    }
                    Spacer(Modifier.height(90.dp))
                }
            }
        }
    }
}

@Composable
private fun Legend(bg: Color, border: Color, label: String) = Row(verticalAlignment = Alignment.CenterVertically) {
    Box(Modifier.size(14.dp).clip(RoundedCornerShape(4.dp)).background(bg).border(1.dp, border, RoundedCornerShape(4.dp)))
    Spacer(Modifier.width(4.dp)); Text(label, style = MaterialTheme.typography.labelSmall)
}

@Composable
private fun SeatCell(s: SeatItem, selected: Boolean, locked: Boolean, busy: Boolean, onClick: () -> Unit) {
    val bg by androidx.compose.animation.animateColorAsState(when { selected -> Brand.Amber; s.isBooked -> Brand.Line; locked -> Color(0xFFFEE2E2); else -> Brand.P50 }, label = "seat")
    Box(Modifier.size(58.dp).clip(RoundedCornerShape(12.dp)).background(bg).border(1.dp, if (selected) Brand.AmberDark else Brand.P300.copy(.5f), RoundedCornerShape(12.dp))
        .clickable(enabled = !locked && !busy, onClick = onClick), contentAlignment = Alignment.Center) {
        if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
        else Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(s.label, fontWeight = FontWeight.Bold, fontSize = 12.sp, textAlign = TextAlign.Center, maxLines = 1, color = if (s.isBooked) Brand.Muted else Brand.Ink)
            if (s.fare > 0) Text("৳${s.fare.toInt()}", fontSize = 9.sp, color = Brand.Muted)
        }
    }
}
