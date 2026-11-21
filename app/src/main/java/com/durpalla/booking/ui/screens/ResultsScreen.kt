package com.durpalla.booking.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.unit.dp
import com.durpalla.booking.R
import com.durpalla.booking.data.repo.TripSummary
import com.durpalla.booking.l10n.t
import com.durpalla.booking.ui.components.*
import com.durpalla.booking.ui.nav.LocalContainer
import com.durpalla.booking.ui.nav.LocalNav
import com.durpalla.booking.ui.nav.Routes
import com.durpalla.booking.ui.theme.Brand
import java.time.LocalDate

@Composable
fun ResultsScreen(
    type: String,
    from: String,
    to: String,
    initialDate: String,
    fromLocationId: Int? = null,
    toLocationId: Int? = null,
) {
    val c = LocalContainer.current; val nav = LocalNav.current
    var date by remember { mutableStateOf(initialDate) }
    var sort by remember { mutableStateOf("time") }
    var acOnly by remember { mutableStateOf(false) }
    var timeWindow by remember { mutableStateOf("all") } // all | morning | afternoon | night
    var selectedOperator by remember { mutableStateOf("") }
    val vm = rememberLoad("res-$type-$from-$to-$date-${fromLocationId ?: 0}-${toLocationId ?: 0}") {
        c.trips.search(type, from, to, date, fromLocationId, toLocationId)
    }
    val state by vm.state.collectAsState()

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        DTopBar("$from → $to • ${prettyDate(date)}", onBack = nav::back)
        val days = (-1..5).map { LocalDate.parse(initialDate).plusDays(it.toLong()) }.filter { !it.isBefore(LocalDate.now()) }
        LazyRow(contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(days) { d ->
                val sel = d.toString() == date
                Column(
                    Modifier.clip(RoundedCornerShape(14.dp))
                        .background(if (sel) Brand.Primary else MaterialTheme.colorScheme.surface)
                        .clickable { date = d.toString() }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(d.dayOfWeek.name.take(3), style = MaterialTheme.typography.labelSmall, color = if (sel) Color.White else Brand.Muted)
                    Text("${d.dayOfMonth}", style = MaterialTheme.typography.titleMedium, color = if (sel) Color.White else MaterialTheme.colorScheme.onSurface)
                }
            }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(sort == "time", { sort = "time" }, { Text(t(R.string.sort_earliest)) }, leadingIcon = { Icon(Icons.Default.Schedule, null, Modifier.size(16.dp)) })
            FilterChip(sort == "price", { sort = "price" }, { Text(t(R.string.sort_cheapest)) }, leadingIcon = { Icon(Icons.Default.Payments, null, Modifier.size(16.dp)) })
            FilterChip(acOnly, { acOnly = !acOnly }, { Text(t(R.string.filter_ac)) }, leadingIcon = { Icon(Icons.Default.AcUnit, null, Modifier.size(16.dp)) })
            listOf(
                "all" to R.string.time_all,
                "morning" to R.string.time_morning,
                "afternoon" to R.string.time_afternoon,
                "night" to R.string.time_night,
            ).forEach { (k, labelRes) ->
                FilterChip(timeWindow == k, { timeWindow = k }, { Text(t(labelRes)) })
            }
        }
        when (val s = state) {
            Load.Loading -> LoadingList()
            is Load.Err -> ErrorState(s.message) { vm.reload() }
            is Load.Ok -> {
                val operators = remember(s.value) {
                    s.value.map { it.vehicleName.trim() }.filter { it.isNotEmpty() }.distinct()
                }
                if (operators.size > 1) {
                    Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selectedOperator.isEmpty(), { selectedOperator = "" }, { Text(t(R.string.all_operators)) })
                        operators.forEach { op ->
                            FilterChip(selectedOperator == op, { selectedOperator = if (selectedOperator == op) "" else op }, { Text(op) })
                        }
                    }
                }
                var list = s.value.filter { trip ->
                    (!acOnly || trip.isAc) &&
                        (selectedOperator.isEmpty() || trip.vehicleName.equals(selectedOperator, true)) &&
                        matchesTimeWindow(trip.leavingAt, timeWindow)
                }
                list = if (sort == "price") list.sortedBy { it.fare ?: Double.MAX_VALUE } else list
                if (list.isEmpty()) EmptyState(Icons.Default.DirectionsBoat, t(R.string.no_trips), t(R.string.try_another_date_route))
                else LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    items(list, key = { it.tripId }) { trip -> TripCard(trip) { nav.go(Routes.trip(trip.tripId, type)) } }
                }
            }
        }
    }
}

private fun matchesTimeWindow(leavingAt: String, window: String): Boolean {
    if (window == "all" || leavingAt.isBlank()) return true
    val lower = leavingAt.lowercase()
    val hour24 = runCatching {
        val m = Regex("(\\d{1,2}):(\\d{2})").find(lower) ?: return@runCatching -1
        var h = m.groupValues[1].toInt()
        if (lower.contains("pm") && h < 12) h += 12
        if (lower.contains("am") && h == 12) h = 0
        h
    }.getOrDefault(-1)
    if (hour24 < 0) return true
    return when (window) {
        "morning" -> hour24 in 6..11
        "afternoon" -> hour24 in 12..17
        "night" -> hour24 >= 18 || hour24 < 6
        else -> true
    }
}

@Composable
fun TripCard(trip: TripSummary, onClick: () -> Unit) {
    val isBus = trip.serviceType.equals("bus", true)
    SoftCard(onClick = onClick) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(trip.vehicleName.ifBlank { t(R.string.trip_numbered, trip.tripId) }, style = MaterialTheme.typography.titleMedium)
                    Text(trip.subLine.ifBlank { trip.routeName }, style = MaterialTheme.typography.bodySmall, color = Brand.Muted)
                }
                Pill(if (trip.isAc) t(R.string.filter_ac) else t(R.string.filter_non_ac))
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column { Text(trip.leavingAt.ifBlank { t(R.string.em_dash) }, style = MaterialTheme.typography.titleSmall); Text(trip.from, style = MaterialTheme.typography.bodySmall, color = Brand.Muted) }
                Box(Modifier.weight(1f).padding(horizontal = 10.dp).height(2.dp).background(Brand.P300))
                Icon(if (isBus) Icons.Default.DirectionsBus else Icons.Default.DirectionsBoat, null, tint = Brand.Primary, modifier = Modifier.size(18.dp))
                Box(Modifier.weight(1f).padding(horizontal = 10.dp).height(2.dp).background(Brand.P300))
                Text(trip.to, style = MaterialTheme.typography.bodySmall, color = Brand.Muted)
            }
            Spacer(Modifier.height(12.dp))
            val avail = if (trip.cabinAvailable > 0 && !isBus) trip.cabinAvailable else trip.seatAvailable
            val total = maxOf(trip.totalSeats, avail, 1)
            LinearProgressIndicator(progress = { avail.toFloat() / total }, Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)), color = Brand.Success, trackColor = Brand.P100)
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (trip.cabinAvailable > 0 && !isBus) t(R.string.cabins_left, trip.cabinAvailable) else t(R.string.seats_left, trip.seatAvailable),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (avail < 5) Brand.Error else Brand.Success,
                    modifier = Modifier.weight(1f),
                )
                Text(t(R.string.price_from), style = MaterialTheme.typography.bodySmall, color = Brand.Muted)
                Text(money(trip.fare), style = MaterialTheme.typography.titleLarge, color = Brand.AmberDark, fontWeight = FontWeight.ExtraBold)
            }
        }
    }
}
