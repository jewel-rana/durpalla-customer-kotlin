package com.durpalla.booking.ui.screens

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.unit.dp
import com.durpalla.booking.R
import com.durpalla.booking.ui.components.*
import com.durpalla.booking.ui.nav.LocalContainer
import com.durpalla.booking.ui.nav.LocalNav
import com.durpalla.booking.ui.nav.Routes
import com.durpalla.booking.ui.theme.Brand
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId
import com.durpalla.booking.l10n.t

@Composable
fun SearchScreen(initialService: String) {
    val nav = LocalNav.current
    var service by rememberSaveable { mutableStateOf(initialService) }
    LaunchedEffect(initialService) { service = initialService }
    var from by rememberSaveable { mutableStateOf("") }
    var to by rememberSaveable { mutableStateOf("") }
    var date by rememberSaveable { mutableStateOf(todayIso()) }
    var checkOut by rememberSaveable { mutableStateOf(todayIso(1)) }
    var error by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).verticalScroll(rememberScrollState())) {
        Box(Modifier.fillMaxWidth().background(Brand.HeroGradient).statusBarsPadding().padding(20.dp, 16.dp, 20.dp, 64.dp)) {
            Text(t(R.string.where_to), style = MaterialTheme.typography.headlineSmall, color = androidx.compose.ui.graphics.Color.White)
        }
        SoftCard(Modifier.padding(horizontal = 16.dp).offset(y = (-44).dp).animateContentSize()) {
            Column(Modifier.padding(16.dp)) {
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Brand.P50).padding(4.dp)) {
                    listOf("launch" to R.string.svc_launch, "bus" to R.string.svc_bus, "boat" to R.string.svc_boat, "hotel" to R.string.svc_hotel).forEach { (k, l) ->
                        val sel = service == k
                        Box(Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(if (sel) Brand.Primary else Brand.P50).clickable { service = k }.padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center) {
                            Text(t(l), color = if (sel) androidx.compose.ui.graphics.Color.White else Brand.P700, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
                if (service == "hotel" || service == "boat") {
                    if (service == "hotel") {
                        SuggestField(t(R.string.city), from, Icons.Default.LocationCity, { from = it }) { q -> LocalSuggest.city(q) }
                    } else {
                        OutlinedTextField(from, { from = it }, Modifier.fillMaxWidth(), label = { Text(t(R.string.search_boats_optional)) }, singleLine = true, shape = RoundedCornerShape(14.dp))
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
                            SuggestField(t(R.string.from), from, Icons.Default.TripOrigin, { from = it }) { q -> LocalSuggest.ghat(q, to, service) }
                            Spacer(Modifier.height(10.dp))
                            SuggestField(t(R.string.to), to, Icons.Default.Place, { to = it }) { q -> LocalSuggest.ghat(q, from, service) }
                        }
                        Box(Modifier.align(Alignment.CenterEnd).padding(end = 12.dp).size(40.dp).clip(CircleShape).background(Brand.Primary)
                            .clickable { val tmp = from; from = to; to = tmp }, contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.SwapVert, null, tint = androidx.compose.ui.graphics.Color.White)
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
                val errFromTo = t(R.string.enter_from_and_to)
                GradientButton(t(if (service == "hotel") R.string.search_hotels else R.string.search_trips), Modifier.fillMaxWidth()) {
                    error = null
                    when (service) {
                        "hotel" -> if (from.isBlank()) error = errCity
                        else nav.go(Routes.hotelResults(from.trim(), date, checkOut))
                        // Master: boat → boat-rental/search (not search?type=boat)
                        "boat" -> {
                            val starts = "${date}T09:00:00"
                            val ends = "${checkOut.ifBlank { date }}T18:00:00"
                            nav.go(Routes.boatList(from.trim().ifBlank { to.trim() }, starts, ends))
                        }
                        else -> if (from.isBlank() || to.isBlank()) error = errFromTo
                        else nav.go(Routes.results(service, from.trim(), to.trim(), date))
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

/** Indirection so composables can call repositories via the container. */
object LocalSuggest {
    lateinit var trips: com.durpalla.booking.data.repo.TripRepository
    suspend fun ghat(q: String, exclude: String, type: String) = trips.ghatSuggestions(q, exclude, type)
    suspend fun city(q: String) = trips.citySuggestions(q)
}

@Composable
fun SuggestField(label: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onChange: (String) -> Unit, fetch: suspend (String) -> List<String>) {
    val c = LocalContainer.current
    LocalSuggest.trips = c.trips
    var suggestions by remember { mutableStateOf(emptyList<String>()) }
    var focused by remember { mutableStateOf(false) }
    var typed by remember { mutableStateOf(false) }
    LaunchedEffect(value, typed) {
        if (!typed || value.length < 2) { suggestions = emptyList(); return@LaunchedEffect }
        delay(250); suggestions = runCatching { fetch(value) }.getOrDefault(emptyList()).take(6)
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
