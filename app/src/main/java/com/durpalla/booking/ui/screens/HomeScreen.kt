package com.durpalla.booking.ui.screens

import androidx.compose.foundation.*
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.durpalla.booking.R
import com.durpalla.booking.core.*
import com.durpalla.booking.data.repo.*
import com.durpalla.booking.l10n.t
import com.durpalla.booking.ui.components.*
import com.durpalla.booking.ui.nav.LocalContainer
import com.durpalla.booking.ui.nav.LocalNav
import com.durpalla.booking.ui.nav.Routes
import com.durpalla.booking.ui.theme.Brand
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonObject

data class HomeData(
    val popular: List<TripSummary>,
    val hotels: List<Hotel>,
    val boats: List<Experience>,
    val entertainment: List<Experience>,
    val offers: List<Offer>,
    val upcoming: List<Booking>,
    val activeServiceKeys: Set<String>,
    val maintenanceMessage: String?,
)

data class ServiceTile(val key: String, val label: Int, val icon: ImageVector, val tint: Color)
val serviceTiles = listOf(
    ServiceTile("launch", R.string.svc_launch, Icons.Filled.DirectionsBoat, Color(0xFF106E9D)),
    ServiceTile("bus", R.string.svc_bus, Icons.Filled.DirectionsBus, Color(0xFF0A3550)),
    ServiceTile("boat", R.string.svc_boat, Icons.Filled.Sailing, Color(0xFF0B5A82)),
    ServiceTile("tour", R.string.svc_tour, Icons.Filled.Landscape, Color(0xFF16A34A)),
    ServiceTile("entertainment", R.string.svc_entertainment, Icons.Filled.Celebration, Color(0xFFDB2777)),
    ServiceTile("hotel", R.string.svc_hotel, Icons.Filled.Hotel, Color(0xFFD97706)),
)

fun openService(nav: com.durpalla.booking.ui.nav.AppNavigator, key: String) =
    nav.tab(Routes.searchFor(key))

private fun parseActiveServices(siteInit: JsonObject, appConfig: JsonObject): Set<String> {
    val arr = siteInit.a("services") ?: appConfig.a("services") ?: return emptySet()
    val enabled = arr.objects().mapNotNull { svc ->
        val slug = svc.str("slug", "key", "type", "name").lowercase().trim()
        val status = svc.i("status", "enabled", "is_active")
        val statusBool = svc.b("status") || svc.b("enabled") || status == null || status == 1
        if (slug.isNotBlank() && statusBool) {
            when {
                slug.startsWith("hotel") -> "hotel"
                slug.startsWith("tour") -> "tour"
                slug.startsWith("boat") -> "boat"
                slug.startsWith("launch") -> "launch"
                slug.startsWith("bus") -> "bus"
                slug.startsWith("entertainment") || slug.startsWith("event") -> "entertainment"
                else -> slug
            }
        } else null
    }.toSet()
    return enabled
}

@Composable
fun HomeScreen() {
    val c = LocalContainer.current; val nav = LocalNav.current
    val loggedIn by c.session.loggedIn.collectAsState()
    val user by c.session.user.collectAsState()
    val vm = rememberLoad("home-$loggedIn") {
        coroutineScope {
            val appCfgDef = async { runCatching { c.home.appConfig() }.getOrDefault(JsonObject(emptyMap())) }
            val siteInitDef = async { runCatching { c.home.siteInit() }.getOrDefault(JsonObject(emptyMap())) }
            val popularDef = async { runCatching { c.home.popularTrips() }.getOrDefault(emptyList()) }
            val hotelsDef = async { runCatching { c.home.topHotels() }.getOrDefault(emptyList()) }
            val boatsDef = async { runCatching { c.boats.homeTop(8) }.getOrDefault(emptyList()) }
            val entDef = async { runCatching { c.entertainment.homeTop(8) }.getOrDefault(emptyList()) }
            val offersDef = async { runCatching { c.home.offers() }.getOrDefault(emptyList()) }
            val upcomingDef = async {
                if (loggedIn) runCatching { c.bookings.myBookings(1).first.take(1) }.getOrDefault(emptyList()) else emptyList()
            }
            val appCfg = appCfgDef.await()
            val siteInit = siteInitDef.await()
            val maint = if (appCfg.b("maintenance_mode") || appCfg.b("maintenance")) {
                appCfg.str("maintenance_message", default = "")
            } else null
            HomeData(
                popular = popularDef.await(),
                hotels = hotelsDef.await(),
                boats = boatsDef.await(),
                entertainment = entDef.await(),
                offers = offersDef.await(),
                upcoming = upcomingDef.await(),
                activeServiceKeys = parseActiveServices(siteInit, appCfg),
                maintenanceMessage = maint,
            )
        }
    }
    val state by vm.state.collectAsState()
    val isDark by c.session.darkMode.collectAsState(initial = false)
    val listState = rememberLazyListState()
    val isScrolled by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 48
        }
    }
    val topBarBg by animateColorAsState(
        targetValue = if (isScrolled) MaterialTheme.colorScheme.surface else Color.Transparent,
        animationSpec = tween(200),
        label = "topBarBg",
    )
    val topBarElevation by animateDpAsState(
        targetValue = if (isScrolled) 4.dp else 0.dp,
        animationSpec = tween(200),
        label = "topBarElevation",
    )
    val actionTint by animateColorAsState(
        targetValue = if (isScrolled) MaterialTheme.colorScheme.onSurface else Color.White,
        animationSpec = tween(200),
        label = "actionTint",
    )

    val visibleTiles = remember(state) {
        val active = (state as? Load.Ok)?.value?.activeServiceKeys.orEmpty()
        if (active.isEmpty()) serviceTiles else serviceTiles.filter { it.key in active }.ifEmpty { serviceTiles }
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
        ) {
            item {
                Box(Modifier.fillMaxWidth().height(240.dp)) {
                    PhotoBox(null, R.drawable.photo_launch, Modifier.matchParentSize())
                    Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(Brand.Navy.copy(.55f), Brand.Navy.copy(.92f)))))
                    Column(
                        Modifier
                            .statusBarsPadding()
                            .padding(horizontal = 20.dp)
                            .padding(top = 58.dp, bottom = 18.dp)
                    ) {
                        Text(
                            if (loggedIn) t(R.string.hi_name, user.str("name", default = t(R.string.traveller))) else t(R.string.greeting_guest),
                            color = Color.White.copy(.8f),
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(t(R.string.where_to), style = MaterialTheme.typography.headlineSmall, color = Color.White)
                    }
                }
            }
        item {
            SoftCard(Modifier.padding(horizontal = 16.dp).offset(y = (-48).dp)) {
                Column(Modifier.padding(14.dp)) {
                    visibleTiles.chunked(3).forEach { row ->
                        Row(Modifier.fillMaxWidth()) {
                            row.forEach { tile ->
                                Column(
                                    Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).clickable { openService(nav, tile.key) }.padding(vertical = 10.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    Box(Modifier.size(54.dp).clip(CircleShape).background(Brush.linearGradient(listOf(tile.tint.copy(.16f), tile.tint.copy(.06f)))), contentAlignment = Alignment.Center) {
                                        Icon(tile.icon, null, tint = tile.tint)
                                    }
                                    Spacer(Modifier.height(6.dp))
                                    Text(t(tile.label), style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }
                    }
                }
            }
        }
        if (!loggedIn) item {
            Row(
                Modifier.padding(horizontal = 16.dp).offset(y = (-36).dp).fillMaxWidth().clip(RoundedCornerShape(16.dp))
                    .background(Brand.PrimaryGradient).clickable { nav.go(Routes.login(Routes.HOME)) }.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Lock, null, tint = Brand.Amber)
                Spacer(Modifier.width(12.dp))
                Text(t(R.string.guest_banner), color = Color.White, modifier = Modifier.weight(1f))
                Icon(Icons.Filled.ChevronRight, null, tint = Color.White)
            }
        }
        when (val s = state) {
            is Load.Loading -> item { LoadingList(2) }
            is Load.Err -> item { ErrorState(s.message) { vm.reload() } }
            is Load.Ok -> {
                val d = s.value
                d.maintenanceMessage?.let { mMsg ->
                    item {
                        SoftCard(Modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth()) {
                            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Build, null, tint = Brand.AmberDark)
                                Spacer(Modifier.width(10.dp))
                                Column {
                                    Text(t(R.string.maintenance_title), fontWeight = FontWeight.Bold)
                                    if (mMsg.isNotBlank()) Text(mMsg, style = MaterialTheme.typography.bodySmall, color = Brand.Muted)
                                }
                            }
                        }
                    }
                }
                d.upcoming.firstOrNull()?.let { b ->
                    item { SectionHeader(t(R.string.upcoming_trips)) }
                    item { UpcomingTicketCard(b) { nav.go(Routes.ticket(b.id)) } }
                }
                item { SectionHeader(t(R.string.offers), t(R.string.see_all)) { nav.tab(Routes.OFFERS) } }
                item { OffersCarousel(d.offers) }
                item { SectionHeader(t(R.string.popular_routes)) }
                if (d.popular.isEmpty()) item { PopularFallback() } else item {
                    LazyRow(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(d.popular, key = { it.tripId }, contentType = { "trip" }) { trip -> TripMiniCard(trip) { nav.go(Routes.trip(trip.tripId, trip.serviceType)) } }
                    }
                }
                if (d.hotels.isNotEmpty()) {
                    item { SectionHeader(t(R.string.top_hotels), t(R.string.tab_search)) { nav.tab(Routes.searchFor("hotel")) } }
                    item {
                        LazyRow(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(d.hotels, key = { it.id }, contentType = { "hotel" }) { h -> HotelCard(h, Modifier.width(230.dp)) { nav.go(Routes.hotel(h.id, todayIso(1), todayIso(2))) } }
                        }
                    }
                }
                if (d.boats.isNotEmpty()) {
                    item { SectionHeader(t(R.string.top_boats), t(R.string.see_all)) { nav.go(Routes.boatList("", "${todayIso(1)}T09:00:00", "${todayIso(1)}T18:00:00")) } }
                    item {
                        LazyRow(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(d.boats, key = { it.id }, contentType = { "boat" }) { b ->
                                ExperienceMiniCard(b) { nav.go(Routes.boatDetails(b.id, "${todayIso(1)}T09:00:00", "${todayIso(1)}T18:00:00")) }
                            }
                        }
                    }
                }
                if (d.entertainment.isNotEmpty()) {
                    item { SectionHeader(t(R.string.top_entertainment), t(R.string.see_all)) { nav.go(Routes.experiences("entertainment")) } }
                    item {
                        LazyRow(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(d.entertainment, key = { it.id }, contentType = { "ent" }) { ent ->
                                ExperienceMiniCard(ent) { nav.go(Routes.experience(ent.id, "entertainment")) }
                            }
                        }
                    }
                }
                item { SectionHeader(t(R.string.explore)) }
                item {
                    LazyRow(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        val ex = listOf(
                            Triple("Sajek", R.drawable.photo_sajek, "tour"),
                            Triple("Sundarbans", R.drawable.photo_sundarbans, "tour"),
                            Triple("St. Martin's", R.drawable.photo_stmartin, "tour"),
                            Triple("Concerts", R.drawable.photo_concert, "entertainment"),
                        )
                        items(ex, key = { it.first }, contentType = { "explore" }) { (title, img, svc) ->
                            Box(Modifier.size(150.dp, 180.dp).clickable { nav.go(Routes.experiences(svc)) }) {
                                PhotoBox(null, img, Modifier.matchParentSize())
                                Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(.7f)))))
                                Text(title, color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.BottomStart).padding(12.dp))
                            }
                        }
                    }
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }

        // Sticky Top Bar
        Surface(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth(),
            color = topBarBg,
            shadowElevation = topBarElevation,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LogoHorizontal(
                    modifier = Modifier.height(30.dp),
                    white = if (isDark) true else !isScrolled,
                )
                Spacer(Modifier.weight(1f))
                if (loggedIn) {
                    Surface(
                        shape = CircleShape,
                        color = if (isScrolled) MaterialTheme.colorScheme.surfaceVariant.copy(.6f) else Color.White.copy(.28f),
                        border = BorderStroke(1.dp, if (isScrolled) MaterialTheme.colorScheme.outlineVariant.copy(.5f) else Color.White.copy(.4f)),
                        modifier = Modifier
                            .size(38.dp)
                            .clickable { nav.go(Routes.NOTIFICATIONS) },
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Outlined.Notifications,
                                contentDescription = t(R.string.notifications),
                                tint = actionTint,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                } else {
                    Button(
                        onClick = { nav.go(Routes.login(Routes.HOME)) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isScrolled) MaterialTheme.colorScheme.primary else Color.White.copy(.2f),
                        ),
                        shape = RoundedCornerShape(20.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                    ) {
                        Text(t(R.string.sign_in), color = Color.White, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}

@Composable
fun ExperienceMiniCard(e: Experience, onClick: () -> Unit) {
    SoftCard(Modifier.width(230.dp), onClick = onClick) {
        PhotoBox(e.photoUrl, e.photoRes, Modifier.fillMaxWidth().height(120.dp))
        Column(Modifier.padding(12.dp)) {
            Text(e.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(e.location, style = MaterialTheme.typography.bodySmall, color = Brand.Muted, maxLines = 1)
            Text(money(e.price), color = Brand.AmberDark, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun PopularFallback() {
    val nav = LocalNav.current
    val routes = listOf(
        Triple("Dhaka (Sadar Ghat)", "Barishal", 1 to 2),
        Triple("Dhaka (Sadar Ghat)", "Bhola", 1 to 3),
        Triple("Dhaka (Sadar Ghat)", "Patuakhali", 1 to 4),
    )
    LazyRow(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items(routes) { (f, dest, ids) ->
            SoftCard(Modifier.width(220.dp), onClick = { nav.go(Routes.results("launch", f, dest, todayIso(), ids.first, ids.second)) }) {
                PhotoBox(null, R.drawable.photo_barishal, Modifier.fillMaxWidth().height(100.dp))
                Column(Modifier.padding(12.dp)) {
                    Text("$f → $dest", style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(t(R.string.launch_tonight), style = MaterialTheme.typography.bodySmall, color = Brand.Muted)
                }
            }
        }
    }
}

@Composable
fun TripMiniCard(trip: TripSummary, onClick: () -> Unit) {
    SoftCard(Modifier.width(230.dp), onClick = onClick) {
        PhotoBox(trip.photo, R.drawable.photo_launch, Modifier.fillMaxWidth().height(110.dp))
        Column(Modifier.padding(12.dp)) {
            Text(trip.vehicleName.ifBlank { trip.routeName }, style = MaterialTheme.typography.titleSmall, maxLines = 1)
            Text("${trip.from} → ${trip.to}", style = MaterialTheme.typography.bodySmall, color = Brand.Muted, maxLines = 1)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(trip.leavingAt, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                Text(money(trip.fare), color = Brand.AmberDark, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun HotelCard(h: Hotel, modifier: Modifier = Modifier, onClick: () -> Unit) {
    SoftCard(modifier, onClick = onClick) {
        Box {
            PhotoBox(h.photo, R.drawable.photo_hotel, Modifier.fillMaxWidth().height(130.dp))
            if (h.stars > 0) Box(Modifier.padding(8.dp)) { Pill("★ ${h.stars}", Color.White.copy(.9f), Brand.Navy) }
        }
        Column(Modifier.padding(12.dp)) {
            Text(h.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(h.city, style = MaterialTheme.typography.bodySmall, color = Brand.Muted)
            Row(verticalAlignment = Alignment.Bottom) {
                Text(money(h.pricePerNight), color = Brand.AmberDark, fontWeight = FontWeight.Bold)
                Text(t(R.string.per_night), style = MaterialTheme.typography.bodySmall, color = Brand.Muted)
            }
        }
    }
}

@Composable
fun OffersCarousel(offers: List<Offer>) {
    val list = offers.ifEmpty {
        listOf(
            Offer(0, t(R.string.sample_offer_1_title), t(R.string.sample_offer_1_body), null, "RIVER15"),
            Offer(0, t(R.string.sample_offer_2_title), t(R.string.sample_offer_2_body), null, null),
        )
    }
    LazyRow(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items(list) { o ->
            Box(Modifier.size(290.dp, 130.dp).clip(RoundedCornerShape(20.dp)).background(Brand.HeroGradient)) {
                if (o.image != null) PhotoBox(o.image, R.drawable.photo_river, Modifier.matchParentSize())
                Column(Modifier.padding(16.dp)) {
                    Text(o.title, color = Color.White, style = MaterialTheme.typography.titleMedium, maxLines = 2)
                    Text(o.subtitle, color = Color.White.copy(.8f), style = MaterialTheme.typography.bodySmall, maxLines = 1)
                    Spacer(Modifier.weight(1f))
                    if (o.code != null) Pill(o.code, Brand.Amber, Color(0xFF1F1300))
                }
            }
        }
    }
}

@Composable
fun UpcomingTicketCard(b: Booking, onClick: () -> Unit) {
    val it0 = b.firstItem
    Row(
        Modifier.padding(horizontal = 16.dp).fillMaxWidth().background(Brand.HeroGradient).clickable(onClick = onClick).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(it0.str("vehicle_name", "hotel_name", default = t(R.string.your_trip)), color = Color.White, style = MaterialTheme.typography.titleMedium)
            Text(it0.str("route_name"), color = Color.White.copy(.8f), style = MaterialTheme.typography.bodySmall)
            Text(it0.str("leaving_time_formated", "schedule_date", default = b.bookingDate), color = Brand.Amber, style = MaterialTheme.typography.labelMedium)
        }
        Icon(Icons.Filled.QrCode2, null, tint = Color.White, modifier = Modifier.size(40.dp))
    }
}
