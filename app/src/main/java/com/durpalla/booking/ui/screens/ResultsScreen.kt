package com.durpalla.booking.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.durpalla.booking.R
import com.durpalla.booking.data.repo.TripSummary
import com.durpalla.booking.ui.components.*
import com.durpalla.booking.ui.nav.LocalContainer
import com.durpalla.booking.ui.nav.LocalNav
import com.durpalla.booking.ui.nav.Routes
import com.durpalla.booking.ui.theme.Brand
import java.time.LocalDate

@Composable
fun ResultsScreen(type: String, from: String, to: String, initialDate: String) {
    val c = LocalContainer.current; val nav = LocalNav.current
    var date by remember { mutableStateOf(initialDate) }
    var sort by remember { mutableStateOf("time") }
    var acOnly by remember { mutableStateOf(false) }
    val vm = rememberLoad("res-$type-$from-$to-$date") { c.trips.search(type, from, to, date) }
    val state by vm.state.collectAsState()

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        DTopBar("$from → $to", onBack = nav::back)
        val days = (-1..5).map { LocalDate.parse(initialDate).plusDays(it.toLong()) }.filter { !it.isBefore(LocalDate.now()) }
        LazyRow(contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(days) { d ->
                val sel = d.toString() == date
                Column(Modifier.clip(RoundedCornerShape(14.dp)).background(if (sel) Brand.Primary else MaterialTheme.colorScheme.surface)
                    .clickable { date = d.toString() }.padding(horizontal = 14.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(d.dayOfWeek.name.take(3), style = MaterialTheme.typography.labelSmall, color = if (sel) Color.White else Brand.Muted)
                    Text("${d.dayOfMonth}", style = MaterialTheme.typography.titleMedium, color = if (sel) Color.White else MaterialTheme.colorScheme.onSurface)
                }
            }
        }
        Row(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(sort == "time", { sort = "time" }, { Text("Earliest") }, leadingIcon = { Icon(Icons.Default.Schedule, null, Modifier.size(16.dp)) })
            FilterChip(sort == "price", { sort = "price" }, { Text("Cheapest") }, leadingIcon = { Icon(Icons.Default.Payments, null, Modifier.size(16.dp)) })
            FilterChip(acOnly, { acOnly = !acOnly }, { Text("AC") }, leadingIcon = { Icon(Icons.Default.AcUnit, null, Modifier.size(16.dp)) })
        }
        when (val s = state) {
            Load.Loading -> LoadingList()
            is Load.Err -> ErrorState(s.message) { vm.reload() }
            is Load.Ok -> {
                var list = s.value.filter { !acOnly || it.isAc }
                list = if (sort == "price") list.sortedBy { it.fare ?: Double.MAX_VALUE } else list
                if (list.isEmpty()) EmptyState(Icons.Default.DirectionsBoat, stringResource(R.string.no_trips), "Try another date or route.")
                else LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    items(list, key = { it.tripId }) { t -> TripCard(t) { nav.go(Routes.trip(t.tripId, type)) } }
                }
            }
        }
    }
}

@Composable
fun TripCard(t: TripSummary, onClick: () -> Unit) {
    SoftCard(onClick = onClick) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(t.vehicleName.ifBlank { "Trip #${t.tripId}" }, style = MaterialTheme.typography.titleMedium)
                    Text(t.subLine.ifBlank { t.routeName }, style = MaterialTheme.typography.bodySmall, color = Brand.Muted)
                }
                Pill(if (t.isAc) "AC" else "Non-AC")
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column { Text(t.leavingAt.ifBlank { "—" }, style = MaterialTheme.typography.titleSmall); Text(t.from, style = MaterialTheme.typography.bodySmall, color = Brand.Muted) }
                Box(Modifier.weight(1f).padding(horizontal = 10.dp).height(2.dp).background(Brand.P300))
                Icon(Icons.Default.DirectionsBoat, null, tint = Brand.Primary, modifier = Modifier.size(18.dp))
                Box(Modifier.weight(1f).padding(horizontal = 10.dp).height(2.dp).background(Brand.P300))
                Text(t.to, style = MaterialTheme.typography.bodySmall, color = Brand.Muted)
            }
            Spacer(Modifier.height(12.dp))
            val avail = if (t.cabinAvailable > 0) t.cabinAvailable else t.seatAvailable
            val total = maxOf(t.totalSeats, avail, 1)
            LinearProgressIndicator(progress = { avail.toFloat() / total }, Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)), color = Brand.Success, trackColor = Brand.P100)
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (t.cabinAvailable > 0) stringResource(R.string.cabins_left, t.cabinAvailable) else stringResource(R.string.seats_left, t.seatAvailable),
                    style = MaterialTheme.typography.labelMedium, color = if (avail < 5) Brand.Error else Brand.Success, modifier = Modifier.weight(1f))
                Text("from ", style = MaterialTheme.typography.bodySmall, color = Brand.Muted)
                Text(money(t.fare), style = MaterialTheme.typography.titleLarge, color = Brand.AmberDark, fontWeight = FontWeight.ExtraBold)
            }
        }
    }
}
