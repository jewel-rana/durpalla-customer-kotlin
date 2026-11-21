package com.durpalla.booking.ui.screens

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.durpalla.booking.R
import com.durpalla.booking.data.repo.GhatPlace
import com.durpalla.booking.l10n.t
import com.durpalla.booking.ui.components.*
import com.durpalla.booking.ui.nav.LocalContainer
import com.durpalla.booking.ui.nav.LocalNav
import com.durpalla.booking.ui.nav.Routes
import com.durpalla.booking.ui.theme.Brand
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId

@Composable
fun SearchScreen(initialService: String) {
    val nav = LocalNav.current
    var service by rememberSaveable { mutableStateOf(initialService) }
    var from by rememberSaveable { mutableStateOf("") }
    var to by rememberSaveable { mutableStateOf("") }
    var fromId by rememberSaveable { mutableIntStateOf(0) }
    var toId by rememberSaveable { mutableIntStateOf(0) }
    var boatType by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(initialService) {
        if (service != initialService) {
            service = initialService
            from = ""; to = ""; fromId = 0; toId = 0
        }
    }
    var date by rememberSaveable { mutableStateOf(todayIso()) }
    var checkOut by rememberSaveable { mutableStateOf(todayIso(1)) }
    var error by remember { mutableStateOf<String?>(null) }
    val selectStoppageHint = t(R.string.select_stoppage_from_list)

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).verticalScroll(rememberScrollState())) {
        Box(Modifier.fillMaxWidth().background(Brand.HeroGradient).statusBarsPadding().padding(20.dp, 16.dp, 20.dp, 64.dp)) {
            Text(t(R.string.where_to), style = MaterialTheme.typography.headlineSmall, color = Color.White)
        }
        SoftCard(Modifier.padding(horizontal = 16.dp).offset(y = (-44).dp).animateContentSize()) {
            Column(Modifier.padding(16.dp)) {
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Brand.P50).padding(4.dp)) {
                    listOf("launch" to R.string.svc_launch, "bus" to R.string.svc_bus, "boat" to R.string.svc_boat, "hotel" to R.string.svc_hotel).forEach { (k, l) ->
                        val sel = service == k
                        Box(
                            Modifier.weight(1f).clip(RoundedCornerShape(10.dp))
                                .background(if (sel) Brand.Primary else Brand.P50)
                                .clickable {
                                    if (service != k) {
                                        service = k
                                        from = ""; to = ""; fromId = 0; toId = 0; error = null
                                    }
                                }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(t(l), color = if (sel) Color.White else Brand.P700, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
                if (service == "hotel" || service == "boat") {
                    if (service == "hotel") {
                        SuggestField(t(R.string.city), from, Icons.Default.LocationCity, { from = it }) { q -> LocalSuggest.city(q) }
                    } else {
                        OutlinedTextField(from, { from = it }, Modifier.fillMaxWidth(), label = { Text(t(R.string.search_boats_optional)) }, singleLine = true, shape = RoundedCornerShape(14.dp))
                        Spacer(Modifier.height(8.dp))
                        val boatTypes = listOf(
                            "" to R.string.boat_type_all,
                            "House Boat" to R.string.boat_type_house,
                            "Speed Boat" to R.string.boat_type_speed,
                            "Steamer" to R.string.boat_type_steamer,
                            "Fishing Boat" to R.string.boat_type_fishing,
                        )
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            boatTypes.forEach { (key, labelRes) ->
                                FilterChip(boatType == key, { boatType = key }, { Text(t(labelRes)) })
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        DateField(if (service == "boat") t(R.string.starts) else t(R.string.check_in), date, Modifier.weight(1f)) {
                            date = it
                            if (checkOut <= it) checkOut = java.time.LocalDate.parse(it).plusDays(1).toString()
                        }
                        DateField(if (service == "boat") t(R.string.ends) else t(R.string.check_out), checkOut, Modifier.weight(1f)) { checkOut = it }
                    }
                } else {
                    Box {
                        Column {
                            GhatPlaceField(
                                label = t(R.string.from),
                                value = from,
                                selectedId = fromId,
                                icon = Icons.Default.TripOrigin,
                                exclude = to,
                                serviceType = service,
                                onPick = { place -> from = place.name; fromId = place.id; error = null },
                                onClear = { from = ""; fromId = 0; error = selectStoppageHint },
                            )
                            Spacer(Modifier.height(10.dp))
                            GhatPlaceField(
                                label = t(R.string.to),
                                value = to,
                                selectedId = toId,
                                icon = Icons.Default.Place,
                                exclude = from,
                                serviceType = service,
                                onPick = { place -> to = place.name; toId = place.id; error = null },
                                onClear = { to = ""; toId = 0; error = selectStoppageHint },
                            )
                        }
                        Box(
                            Modifier.align(Alignment.CenterEnd).padding(end = 12.dp).size(40.dp).clip(CircleShape).background(Brand.Primary)
                                .clickable {
                                    val tmpName = from; from = to; to = tmpName
                                    val tmpId = fromId; fromId = toId; toId = tmpId
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Default.SwapVert, null, tint = Color.White)
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(t(R.string.journey_date), style = MaterialTheme.typography.labelMedium, color = Brand.Muted)
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        FilterChip(date == todayIso(), { date = todayIso() }, { Text(t(R.string.today)) })
                        FilterChip(date == todayIso(1), { date = todayIso(1) }, { Text(t(R.string.tomorrow)) })
                        DateField(t(R.string.pick_date), date, Modifier.weight(1f)) { date = it }
                    }
                }
                error?.let { Text(it, color = Brand.Error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp)) }
                Spacer(Modifier.height(16.dp))
                val errCity = t(R.string.enter_a_city)
                val isTransport = service != "hotel" && service != "boat"
                val searchEnabled = !isTransport || (fromId > 0 && toId > 0 && from.isNotBlank() && to.isNotBlank())
                GradientButton(
                    t(if (service == "hotel") R.string.search_hotels else R.string.search_trips),
                    Modifier.fillMaxWidth(),
                    enabled = searchEnabled,
                ) {
                    error = null
                    when (service) {
                        "hotel" -> if (from.isBlank()) error = errCity
                        else nav.go(Routes.hotelResults(from.trim(), date, checkOut))
                        "boat" -> {
                            val starts = "${date}T09:00:00"
                            val ends = "${checkOut.ifBlank { date }}T18:00:00"
                            val qCombined = listOf(from.trim(), boatType.trim()).filter { it.isNotEmpty() }.joinToString(" ")
                            nav.go(Routes.boatList(qCombined, starts, ends))
                        }
                        else -> if (fromId <= 0 || toId <= 0 || from.isBlank() || to.isBlank()) {
                            error = selectStoppageHint
                        } else {
                            nav.go(Routes.results(service, from.trim(), to.trim(), date, fromId, toId))
                        }
                    }
                }
            }
        }
        Text(t(R.string.more_services), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 20.dp))
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            serviceTiles.filter { it.key == "tour" || it.key == "entertainment" }.forEach { tile ->
                SoftCard(Modifier.weight(1f), onClick = { nav.go(Routes.experiences(tile.key)) }) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(tile.icon, null, tint = tile.tint); Spacer(Modifier.width(10.dp)); Text(t(tile.label), style = MaterialTheme.typography.titleSmall)
                    }
                }
            }
        }
    }
}

object LocalSuggest {
    lateinit var trips: com.durpalla.booking.data.repo.TripRepository
    suspend fun ghatPlaces(q: String, exclude: String, type: String) = trips.ghatPlaceSuggestions(q, exclude, type)
    suspend fun ghat(q: String, exclude: String, type: String) = trips.ghatSuggestions(q, exclude, type)
    suspend fun city(q: String) = trips.citySuggestions(q)
}

/**
 * Mirrors Flutter `GhatLocationField` & Web `GhatPlaceField`:
 * - Focus immediately loads `trip/ghat-suggestions` (`q = ""`).
 * - Typing filters suggestions.
 * - Picking an item sets `(id, name)`.
 * - Blurring without a picked `id > 0` clears the text and prompts to select from list.
 */
@Composable
fun GhatPlaceField(
    label: String,
    value: String,
    selectedId: Int,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    exclude: String,
    serviceType: String,
    onPick: (GhatPlace) -> Unit,
    onClear: () -> Unit,
) {
    val c = LocalContainer.current
    LocalSuggest.trips = c.trips
    var text by remember(value) { mutableStateOf(value) }
    var suggestions by remember { mutableStateOf(emptyList<GhatPlace>()) }
    var focused by remember { mutableStateOf(false) }
    var hadFocus by remember { mutableStateOf(false) }

    LaunchedEffect(focused, text, exclude, serviceType) {
        if (!focused) return@LaunchedEffect
        val q = if (selectedId > 0 && text == value) "" else text
        if (q.isNotEmpty()) delay(180)
        suggestions = runCatching { LocalSuggest.ghatPlaces(q, exclude, serviceType) }
            .getOrDefault(emptyList())
            .take(10)
    }

    Column {
        OutlinedTextField(
            value = text,
            onValueChange = {
                text = it
                if (selectedId > 0 && it != value) {
                    onPick(GhatPlace(0, it))
                }
            },
            modifier = Modifier.fillMaxWidth().onFocusChangedCompat { isFocused ->
                focused = isFocused
                if (isFocused) {
                    hadFocus = true
                } else if (hadFocus) {
                    val exact = suggestions.firstOrNull { it.name.equals(text.trim(), ignoreCase = true) }
                    if (selectedId <= 0 && exact != null) {
                        text = exact.name
                        onPick(exact)
                    } else if (selectedId <= 0 && text.isNotBlank()) {
                        text = ""
                        onClear()
                    }
                    suggestions = emptyList()
                }
            },
            label = { Text(label) },
            leadingIcon = { Icon(icon, null, tint = Brand.Primary) },
            trailingIcon = {
                if (selectedId > 0) Icon(Icons.Default.CheckCircle, null, tint = Brand.Success, modifier = Modifier.size(18.dp))
            },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
        )
        if (focused && suggestions.isNotEmpty()) {
            Card(Modifier.fillMaxWidth().padding(top = 4.dp), shape = RoundedCornerShape(12.dp)) {
                Column {
                    suggestions.forEach { place ->
                        Row(
                            Modifier.fillMaxWidth()
                                .clickable {
                                    text = place.name
                                    onPick(place)
                                    suggestions = emptyList()
                                }
                                .padding(14.dp, 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Default.Place, null, tint = Brand.Primary, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(place.name, Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SuggestField(label: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onChange: (String) -> Unit, fetch: suspend (String) -> List<String>) {
    val c = LocalContainer.current
    LocalSuggest.trips = c.trips
    var suggestions by remember { mutableStateOf(emptyList<String>()) }
    var focused by remember { mutableStateOf(false) }
    var typed by remember { mutableStateOf(false) }
    LaunchedEffect(value, typed, focused) {
        if (!focused) { suggestions = emptyList(); return@LaunchedEffect }
        if (typed && value.isNotEmpty()) delay(200)
        suggestions = runCatching { fetch(value) }.getOrDefault(emptyList()).take(8)
    }
    Column {
        OutlinedTextField(value, { onChange(it); typed = true }, Modifier.fillMaxWidth().onFocusChangedCompat { focused = it },
            label = { Text(label) }, leadingIcon = { Icon(icon, null, tint = Brand.Primary) }, singleLine = true, shape = RoundedCornerShape(14.dp))
        if (focused && suggestions.isNotEmpty()) {
            Card(Modifier.fillMaxWidth().padding(top = 4.dp), shape = RoundedCornerShape(12.dp)) {
                suggestions.forEach { s ->
                    Text(s, Modifier.fillMaxWidth().clickable { onChange(s); typed = false; suggestions = emptyList() }.padding(14.dp, 12.dp))
                }
            }
        }
    }
}

fun Modifier.onFocusChangedCompat(cb: (Boolean) -> Unit): Modifier = this.onFocusChanged { cb(it.isFocused) }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateField(label: String, iso: String, modifier: Modifier = Modifier, onPick: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    OutlinedCard(modifier.clickable { open = true }, shape = RoundedCornerShape(12.dp)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.CalendarMonth, null, tint = Brand.Primary, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Column { Text(label, style = MaterialTheme.typography.labelSmall, color = Brand.Muted); Text(prettyDate(iso), style = MaterialTheme.typography.titleSmall) }
        }
    }
    if (open) {
        val st = rememberDatePickerState(initialSelectedDateMillis = java.time.LocalDate.parse(iso).atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli())
        DatePickerDialog(onDismissRequest = { open = false }, confirmButton = {
            TextButton({ st.selectedDateMillis?.let { onPick(Instant.ofEpochMilli(it).atZone(ZoneId.of("UTC")).toLocalDate().toString()) }; open = false }) { Text(t(R.string.ok)) }
        }) { DatePicker(st) }
    }
}
