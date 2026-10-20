package com.durpalla.booking.ui.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.durpalla.booking.R
import com.durpalla.booking.core.*
import com.durpalla.booking.data.repo.Service
import com.durpalla.booking.ui.components.*
import com.durpalla.booking.ui.nav.LocalContainer
import com.durpalla.booking.ui.nav.LocalNav
import com.durpalla.booking.ui.nav.Routes
import com.durpalla.booking.ui.theme.Brand
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonPrimitive
import java.time.LocalDate
import com.durpalla.booking.l10n.t

@Composable
fun HotelResultsScreen(city: String, ci: String, co: String) {
    val c = LocalContainer.current; val nav = LocalNav.current
    val vm = rememberLoad("hotels-$city-$ci-$co") { c.hotels.search(city, ci, co) }
    val state by vm.state.collectAsState()
    Scaffold(topBar = { DTopBar("$city • ${prettyDate(ci)} – ${prettyDate(co)}", onBack = nav::back) }) { pad ->
        Box(Modifier.padding(pad)) {
            when (val s = state) {
                Load.Loading -> LoadingList()
                is Load.Err -> ErrorState(s.message) { vm.reload() }
                is Load.Ok -> if (s.value.isEmpty()) EmptyState(Icons.Default.Hotel, t(R.string.no_hotels_found), t(R.string.no_hotels_body))
                else LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    items(s.value, key = { it.id }) { h -> HotelCard(h, Modifier.fillMaxWidth()) { nav.go(Routes.hotel(h.id, ci, co)) } }
                }
            }
        }
    }
}

/** hotel/{id} + rooms → quote → hold (lines[], Idempotency-Key) → confirm → payment. Release via POST hotel/hold/release. */
@Composable
fun HotelDetailsScreen(id: Int, ci: String, co: String) {
    val c = LocalContainer.current; val nav = LocalNav.current
    val vm = rememberLoad("hotel-$id-$ci-$co") { c.hotels.details(id) to runCatching { c.hotels.rooms(id, ci, co) }.getOrDefault(emptyList()) }
    val state by vm.state.collectAsState()
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf<String?>(null) }; var err by remember { mutableStateOf<String?>(null) }
    var fav by remember { mutableStateOf(false) }
    val self = Routes.hotel(id, ci, co)
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        when (val s = state) {
            Load.Loading -> LoadingList()
            is Load.Err -> ErrorState(s.message) { vm.reload() }
            is Load.Ok -> {
                val (h, rooms) = s.value
                LazyColumn {
                    item {
                        Box(Modifier.fillMaxWidth().height(260.dp)) {
                            PhotoBox(h.s("photo") ?: h.a("gallery")?.firstOrNull()?.let { (it as? JsonPrimitive)?.content ?: (it as? kotlinx.serialization.json.JsonObject).s("url") }, R.drawable.photo_hotel, Modifier.matchParentSize())
                            Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(Color.Black.copy(.35f), Color.Transparent, Brand.Navy.copy(.8f)))))
                            Row(Modifier.statusBarsPadding().padding(12.dp).fillMaxWidth()) {
                                FrostedIcon(Icons.AutoMirrored.Filled.ArrowBack) { nav.back() }; Spacer(Modifier.weight(1f))
                                FrostedIcon(if (fav) Icons.Default.Favorite else Icons.Default.FavoriteBorder) {
                                    if (nav.ensureAuth(self)) scope.launch { if (fav) c.hotels.removeFavourite(id) else c.hotels.addFavourite(id); fav = !fav }
                                }
                            }
                            Column(Modifier.align(Alignment.BottomStart).padding(16.dp)) {
                                Text(h.str("name", default = t(R.string.svc_hotel)), style = MaterialTheme.typography.headlineSmall, color = Color.White)
                                Text(h.str("address", "location", "city"), color = Color.White.copy(.85f))
                            }
                        }
                    }
                    item {
                        val amen = h.a("amenities")?.mapNotNull { (it as? JsonPrimitive)?.content } ?: emptyList()
                        if (amen.isNotEmpty()) LazyRow(contentPadding = PaddingValues(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) { items(amen) { Pill(it) } }
                        h.s("description")?.let { Text(it, Modifier.padding(horizontal = 16.dp), color = Brand.Muted, maxLines = 5) }
                        SectionHeader(t(R.string.rooms_for_dates, prettyDate(ci), prettyDate(co)))
                        err?.let { Text(it, color = Brand.Error, modifier = Modifier.padding(horizontal = 16.dp)) }
                    }
                    if (rooms.isEmpty()) item { EmptyState(Icons.Default.Bed, t(R.string.no_rooms_available)) }
                    items(rooms) { r ->
                        val rtId = r.i("room_type_id", "id") ?: 0
                        SoftCard(Modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth()) {
                            Column(Modifier.padding(16.dp)) {
                                Text(r.str("title", "name", "room_type_title", default = t(R.string.room)), style = MaterialTheme.typography.titleMedium)
                                Text(t(R.string.up_to_guests, r.i("max_adults", "capacity") ?: 2), color = Brand.Muted, style = MaterialTheme.typography.bodySmall)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(money(r.d("total", "price_total", "price", "price_per_night")), Modifier.weight(1f), color = Brand.AmberDark, fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleLarge)
                                    GradientButton(t(R.string.book_now), Modifier.width(140.dp), loading = busy == "$rtId") {
                                        if (!nav.ensureAuth(self)) return@GradientButton
                                        scope.launch {
                                            busy = "$rtId"; err = null
                                            var holdId: Int? = null
                                            runCatching {
                                                val quoteBody = jsonOf(
                                                    "hotel_id" to id,
                                                    "lines" to listOf(mapOf("room_type_id" to rtId, "quantity" to 1)),
                                                    "check_in" to ci, "check_out" to co, "adults" to 2, "children" to 0,
                                                )
                                                c.hotels.quote(quoteBody)
                                                val hold = c.hotels.hold(id, listOf(rtId to 1), ci, co, adults = 2, children = 0)
                                                holdId = hold.o("data").i("hold_id", "id") ?: hold.i("hold_id")
                                                    ?: throw ApiException(AppCtx.str(R.string.err_hold_failed))
                                                val conf = c.hotels.confirm(holdId!!)
                                                val booking = conf.o("data").o("booking") ?: conf.o("booking") ?: conf.o("data")
                                                val bid = booking.i("id") ?: conf.o("data").i("booking_id", "order_id", "id")
                                                    ?: conf.i("booking_id") ?: throw ApiException(AppCtx.str(R.string.err_booking_id_short))
                                                val amt = booking.d("total_payable")
                                                    ?: conf.o("data").d("total_payable", "amount", "total")
                                                    ?: r.d("total", "price") ?: 0.0
                                                bid to amt
                                            }.onSuccess { (bid, amt) -> nav.go(Routes.payment(bid, amt)) }
                                                .onFailure {
                                                    err = it.message
                                                    holdId?.let { hid -> scope.launch { runCatching { c.hotels.releaseHold(hid) } } }
                                                }
                                            busy = null
                                        }
                                    }
                                }
                            }
                        }
                    }
                    item { Spacer(Modifier.height(24.dp)) }
                }
            }
        }
    }
}

@Composable
fun ExperienceListScreen(service: String) {
    val c = LocalContainer.current; val nav = LocalNav.current
    val svc = when (service) {
        "tour", "tours" -> Service.TOUR
        else -> Service.ENTERTAINMENT
    }
    var q by remember { mutableStateOf("") }
    val vm = rememberLoad("exp-$service-$q") { c.experiences.list(svc, q) }
    val state by vm.state.collectAsState()
    Scaffold(topBar = { DTopBar(t(if (svc == Service.TOUR) R.string.svc_tour else R.string.svc_entertainment), onBack = nav::back) }) { pad ->
        Column(Modifier.padding(pad)) {
            OutlinedTextField(q, { q = it }, Modifier.fillMaxWidth().padding(16.dp, 8.dp), leadingIcon = { Icon(Icons.Default.Search, null) }, placeholder = { Text(t(R.string.search_destinations_events)) }, shape = RoundedCornerShape(14.dp), singleLine = true)
            when (val s = state) {
                Load.Loading -> LoadingList()
                is Load.Err -> ErrorState(s.message) { vm.reload() }
                is Load.Ok -> LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    items(s.value) { e ->
                        SoftCard(onClick = { nav.go(Routes.experience(e.id, service)) }) {
                            Box {
                                if (e.photoUrl != null) {
                                    AsyncImage(e.photoUrl, null, Modifier.fillMaxWidth().height(160.dp), contentScale = ContentScale.Crop)
                                } else {
                                    Image(painterResource(e.photoRes), null, Modifier.fillMaxWidth().height(160.dp), contentScale = ContentScale.Crop)
                                }
                                Box(Modifier.padding(10.dp)) { Pill("★ ${e.rating}", Color.White.copy(.9f), Brand.Navy) }
                            }
                            Column(Modifier.padding(14.dp)) {
                                Text(e.title, style = MaterialTheme.typography.titleMedium)
                                Text("${e.location} • ${e.duration} • ${e.date}", color = Brand.Muted, style = MaterialTheme.typography.bodySmall)
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
fun ExperienceDetailScreen(id: Int, service: String = "tour") {
    val c = LocalContainer.current; val nav = LocalNav.current
    val vm = rememberLoad("expd-$service-$id") {
        c.experiences.details(id) ?: throw ApiException(AppCtx.str(R.string.err_not_found))
    }
    val state by vm.state.collectAsState()
    (state as? Load.Ok)?.value?.let { e ->
        Scaffold(bottomBar = {
            Surface(shadowElevation = 16.dp) {
                Row(Modifier.navigationBarsPadding().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(money(e.price), Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, color = Brand.AmberDark, fontWeight = FontWeight.ExtraBold)
                    GradientButton(t(R.string.book_now), Modifier.width(170.dp)) { nav.go(Routes.bookExperience(e.id, service)) }
                }
            }
        }) { pad ->
            Column(Modifier.padding(pad).verticalScroll(rememberScrollState())) {
                Box(Modifier.fillMaxWidth().height(280.dp)) {
                    if (e.photoUrl != null) AsyncImage(e.photoUrl, null, Modifier.matchParentSize(), contentScale = ContentScale.Crop)
                    else Image(painterResource(e.photoRes), null, Modifier.matchParentSize(), contentScale = ContentScale.Crop)
                    Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(Color.Black.copy(.3f), Color.Transparent, Brand.Navy.copy(.85f)))))
                    Box(Modifier.statusBarsPadding().padding(12.dp)) { FrostedIcon(Icons.AutoMirrored.Filled.ArrowBack) { nav.back() } }
                    Column(Modifier.align(Alignment.BottomStart).padding(16.dp)) {
                        Text(e.title, style = MaterialTheme.typography.headlineSmall, color = Color.White)
                        Text("${e.location} • ${e.duration}", color = Color.White.copy(.85f))
                    }
                }
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(t(R.string.highlights), style = MaterialTheme.typography.titleMedium)
                    e.highlights.forEach { Row { Icon(Icons.Default.CheckCircle, null, tint = Brand.Success, modifier = Modifier.size(18.dp)); Text("  $it") } }
                    if (e.date.isNotBlank()) Text(t(R.string.schedule_label, e.date), color = Brand.Muted)
                }
            }
        }
    } ?: LoadingList(2)
}

/** Real tour / entertainment checkout → quote → hold → confirm → PaymentOptionsScreen. */
@Composable
fun ExperienceBookScreen(id: Int, service: String = "tour") {
    val c = LocalContainer.current; val nav = LocalNav.current
    val isTour = service != "entertainment"
    val vm = rememberLoad("expb-$service-$id") {
        if (isTour) c.tours.details(id) else c.entertainment.details(id)
    }
    val state by vm.state.collectAsState()
    var qty by remember { mutableIntStateOf(1) }
    var name by remember { mutableStateOf("") }
    var mobile by remember { mutableStateOf("") }
    var visitDate by remember { mutableStateOf(LocalDate.now().toString()) }
    var loading by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val self = Routes.bookExperience(id, service)

    Scaffold(topBar = { DTopBar(t(R.string.checkout), onBack = nav::back) }) { pad ->
        when (val s = state) {
            Load.Loading -> Box(Modifier.padding(pad)) { LoadingList(1) }
            is Load.Err -> Box(Modifier.padding(pad)) { ErrorState(s.message) { vm.reload() } }
            is Load.Ok -> {
                val d = s.value
                val title = d.str("title", "name")
                val price = d.d("price", "price_from", "min_price") ?: 0.0
                Column(Modifier.padding(pad).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    StepIndicator(1)
                    SoftCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Text(title, style = MaterialTheme.typography.titleMedium)
                            if (!isTour) {
                                OutlinedTextField(visitDate, { visitDate = it }, Modifier.fillMaxWidth(), label = { Text(t(R.string.visit_date_hint)) })
                            }
                            OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text(t(R.string.full_name)) })
                            OutlinedTextField(mobile, { mobile = it }, Modifier.fillMaxWidth(), label = { Text(t(R.string.mobile_number)) })
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(if (isTour) t(R.string.places) else t(R.string.tickets_qty), Modifier.weight(1f))
                                IconButton({ if (qty > 1) qty-- }) { Icon(Icons.Default.RemoveCircleOutline, null) }
                                Text("$qty", style = MaterialTheme.typography.titleMedium)
                                IconButton({ qty++ }) { Icon(Icons.Default.AddCircleOutline, null, tint = Brand.Primary) }
                            }
                            HorizontalDivider()
                            Row(Modifier.padding(top = 8.dp)) {
                                Text(t(R.string.total), Modifier.weight(1f))
                                Text(money(price * qty), fontWeight = FontWeight.ExtraBold, color = Brand.AmberDark)
                            }
                        }
                    }
                    err?.let { Text(it, color = Brand.Error) }
                    GradientButton(t(R.string.confirm_booking), Modifier.fillMaxWidth(), loading = loading) {
                        if (!nav.ensureAuth(self)) return@GradientButton
                        if (name.isBlank() || mobile.isBlank()) {
                            err = t(R.string.guest_name_mobile_required)
                            return@GradientButton
                        }
                        scope.launch {
                            loading = true; err = null
                            runCatching {
                                if (isTour) {
                                    val departureId = d.i("departure_id", "id") ?: id
                                    c.tours.quote(jsonOf("departure_id" to departureId, "places" to qty))
                                    val hold = c.tours.hold(departureId, qty, name.trim(), mobile.trim())
                                    val holdId = hold.o("data").i("hold_id", "id") ?: hold.i("hold_id")
                                        ?: throw ApiException(AppCtx.str(R.string.err_hold_failed))
                                    val conf = c.tours.confirm(holdId, name.trim(), mobile.trim())
                                    val bid = conf.o("data").i("booking_id", "order_id", "id") ?: conf.i("booking_id")
                                        ?: throw ApiException(AppCtx.str(R.string.err_booking_id_short))
                                    bid to (conf.o("data").d("total_payable", "amount", "total") ?: price * qty)
                                } else {
                                    val ticketTypeId = d.a("ticket_types").objects().firstOrNull().i("id")
                                        ?: d.i("ticket_type_id") ?: 0
                                    val lines = listOf(mapOf("ticket_type_id" to ticketTypeId, "qty" to qty))
                                    c.entertainment.quote(jsonOf("venue_id" to id, "visit_date" to visitDate, "lines" to lines))
                                    val hold = c.entertainment.hold(id, visitDate, lines, name.trim(), mobile.trim())
                                    val holdId = hold.o("data").i("hold_id", "id") ?: hold.i("hold_id")
                                        ?: throw ApiException(AppCtx.str(R.string.err_hold_failed))
                                    val conf = c.entertainment.confirm(holdId, name.trim(), mobile.trim())
                                    val bid = conf.o("data").i("booking_id", "order_id", "id") ?: conf.i("booking_id")
                                        ?: throw ApiException(AppCtx.str(R.string.err_booking_id_short))
                                    bid to (conf.o("data").d("total_payable", "amount", "total") ?: price * qty)
                                }
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
