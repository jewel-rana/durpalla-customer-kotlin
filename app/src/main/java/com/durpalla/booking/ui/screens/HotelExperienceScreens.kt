package com.durpalla.booking.ui.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
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
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonPrimitive

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
                is Load.Ok -> if (s.value.isEmpty()) EmptyState(Icons.Default.Hotel, "No hotels found", "Try another city or dates.")
                else LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    items(s.value, key = { it.id }) { h -> HotelCard(h, Modifier.fillMaxWidth()) { nav.go(Routes.hotel(h.id, ci, co)) } }
                }
            }
        }
    }
}

/** hotel/{id} + hotel/{id}/rooms → hotel/quote → hotel/hold (Idempotency-Key) → hotel/booking/confirm → payment. */
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
                                Text(h.str("name", default = "Hotel"), style = MaterialTheme.typography.headlineSmall, color = Color.White)
                                Text(h.str("address", "location", "city"), color = Color.White.copy(.85f))
                            }
                        }
                    }
                    item {
                        val amen = h.a("amenities")?.mapNotNull { (it as? JsonPrimitive)?.content } ?: emptyList()
                        if (amen.isNotEmpty()) LazyRow(contentPadding = PaddingValues(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) { items(amen) { Pill(it) } }
                        h.s("description")?.let { Text(it, Modifier.padding(horizontal = 16.dp), color = Brand.Muted, maxLines = 5) }
                        SectionHeader("Rooms • ${prettyDate(ci)} – ${prettyDate(co)}")
                        err?.let { Text(it, color = Brand.Error, modifier = Modifier.padding(horizontal = 16.dp)) }
                    }
                    if (rooms.isEmpty()) item { EmptyState(Icons.Default.Bed, "No rooms available for these dates") }
                    items(rooms) { r ->
                        val rtId = r.i("room_type_id", "id") ?: 0
                        SoftCard(Modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth()) {
                            Column(Modifier.padding(16.dp)) {
                                Text(r.str("title", "name", "room_type_title", default = "Room"), style = MaterialTheme.typography.titleMedium)
                                Text("Up to ${r.i("max_adults", "capacity") ?: 2} guests", color = Brand.Muted, style = MaterialTheme.typography.bodySmall)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(money(r.d("total", "price_total", "price", "price_per_night")), Modifier.weight(1f), color = Brand.AmberDark, fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleLarge)
                                    GradientButton(stringResource(R.string.book_now), Modifier.width(140.dp), loading = busy == "$rtId") {
                                        if (!nav.ensureAuth(self)) return@GradientButton
                                        scope.launch {
                                            busy = "$rtId"; err = null
                                            runCatching {
                                                val body = jsonOf("hotel_id" to id, "room_type_id" to rtId, "check_in" to ci, "check_out" to co, "rooms" to 1, "adults" to 2)
                                                c.hotels.quote(body)
                                                val hold = c.hotels.hold(body)
                                                val holdId = hold.o("data").i("hold_id", "id") ?: hold.i("hold_id") ?: throw ApiException("Hold failed")
                                                val conf = c.hotels.confirm(holdId)
                                                val bid = conf.o("data").i("booking_id", "id") ?: conf.i("booking_id") ?: throw ApiException("Booking ID missing")
                                                bid to (conf.o("data").d("total_payable", "amount", "total") ?: r.d("total", "price") ?: 0.0)
                                            }.onSuccess { (bid, amt) -> nav.go(Routes.payment(bid, amt)) }.onFailure { err = it.message }
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
    val svc = if (service == "tour") Service.TOUR else Service.ENTERTAINMENT
    var q by remember { mutableStateOf("") }
    val vm = rememberLoad("exp-$service-$q") { c.experiences.list(svc, q) }
    val state by vm.state.collectAsState()
    Scaffold(topBar = { DTopBar(stringResource(if (svc == Service.TOUR) R.string.svc_tour else R.string.svc_entertainment), onBack = nav::back) }) { pad ->
        Column(Modifier.padding(pad)) {
            OutlinedTextField(q, { q = it }, Modifier.fillMaxWidth().padding(16.dp, 8.dp), leadingIcon = { Icon(Icons.Default.Search, null) }, placeholder = { Text("Search destinations or events") }, shape = RoundedCornerShape(14.dp), singleLine = true)
            Pill(stringResource(R.string.sample_data), Brand.Amber.copy(.15f), Brand.AmberDark, Icons.Default.Info).let { Box(Modifier.padding(horizontal = 16.dp)) { it } }
            when (val s = state) {
                Load.Loading -> LoadingList()
                is Load.Err -> ErrorState(s.message) { vm.reload() }
                is Load.Ok -> LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    items(s.value) { e ->
                        SoftCard(onClick = { nav.go(Routes.experience(e.id)) }) {
                            Box {
                                Image(painterResource(e.photoRes), null, Modifier.fillMaxWidth().height(160.dp), contentScale = ContentScale.Crop)
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
fun ExperienceDetailScreen(id: Int) {
    val c = LocalContainer.current; val nav = LocalNav.current
    val vm = rememberLoad("expd-$id") { c.experiences.details(id) ?: throw ApiException("Not found") }
    val state by vm.state.collectAsState()
    (state as? Load.Ok)?.value?.let { e ->
        Scaffold(bottomBar = {
            Surface(shadowElevation = 16.dp) {
                Row(Modifier.navigationBarsPadding().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(money(e.price), Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, color = Brand.AmberDark, fontWeight = FontWeight.ExtraBold)
                    GradientButton(stringResource(R.string.book_now), Modifier.width(170.dp)) { nav.go(Routes.bookExperience(e.id)) }
                }
            }
        }) { pad ->
            Column(Modifier.padding(pad).verticalScroll(rememberScrollState())) {
                Box(Modifier.fillMaxWidth().height(280.dp)) {
                    Image(painterResource(e.photoRes), null, Modifier.matchParentSize(), contentScale = ContentScale.Crop)
                    Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(Color.Black.copy(.3f), Color.Transparent, Brand.Navy.copy(.85f)))))
                    Box(Modifier.statusBarsPadding().padding(12.dp)) { FrostedIcon(Icons.AutoMirrored.Filled.ArrowBack) { nav.back() } }
                    Column(Modifier.align(Alignment.BottomStart).padding(16.dp)) {
                        Text(e.title, style = MaterialTheme.typography.headlineSmall, color = Color.White)
                        Text("${e.location} • ${e.duration}", color = Color.White.copy(.85f))
                    }
                }
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pill(stringResource(R.string.sample_data), Brand.Amber.copy(.15f), Brand.AmberDark, Icons.Default.Info)
                    Text("Highlights", style = MaterialTheme.typography.titleMedium)
                    e.highlights.forEach { Row { Icon(Icons.Default.CheckCircle, null, tint = Brand.Success, modifier = Modifier.size(18.dp)); Text("  $it") } }
                    Text("Schedule: ${e.date}", color = Brand.Muted)
                }
            }
        }
    } ?: LoadingList(2)
}

/** Protected. TODO(backend): no Tour/Entertainment booking API yet — UI only. */
@Composable
fun ExperienceBookScreen(id: Int) {
    val c = LocalContainer.current; val nav = LocalNav.current
    val vm = rememberLoad("expb-$id") { c.experiences.details(id) ?: throw ApiException("Not found") }
    val e = (vm.state.collectAsState().value as? Load.Ok)?.value
    var qty by remember { mutableIntStateOf(1) }; var done by remember { mutableStateOf(false) }
    Scaffold(topBar = { DTopBar(stringResource(R.string.checkout), onBack = nav::back) }) { pad ->
        if (e == null) LoadingList(1) else Column(Modifier.padding(pad).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            StepIndicator(1)
            SoftCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(e.title, style = MaterialTheme.typography.titleMedium); Text(e.date, color = Brand.Muted)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Guests", Modifier.weight(1f))
                        IconButton({ if (qty > 1) qty-- }) { Icon(Icons.Default.RemoveCircleOutline, null) }
                        Text("$qty", style = MaterialTheme.typography.titleMedium)
                        IconButton({ qty++ }) { Icon(Icons.Default.AddCircleOutline, null, tint = Brand.Primary) }
                    }
                    HorizontalDivider()
                    Row(Modifier.padding(top = 8.dp)) { Text(stringResource(R.string.total), Modifier.weight(1f)); Text(money(e.price * qty), fontWeight = FontWeight.ExtraBold, color = Brand.AmberDark) }
                }
            }
            if (done) Text("Request saved locally. Online booking for this service is coming soon — our team will contact you.", color = Brand.Primary)
            GradientButton(stringResource(R.string.confirm_booking), Modifier.fillMaxWidth(), enabled = !done) { done = true }
        }
    }
}
