package com.durpalla.booking.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.durpalla.booking.R
import com.durpalla.booking.core.CountryInfo
import com.durpalla.booking.core.PhoneNumbers
import com.durpalla.booking.ui.nav.LocalContainer
import java.util.Locale
import com.durpalla.booking.l10n.t

/**
 * Country picker (default BD +880) + national field.
 * API `mobile` is composed with AuthValidators.composeMobileForApi (BD `01…`, else `+CC…` as typed).
 * No `country_code` / `dial_code` are sent to the API.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhoneInputField(
    nationalNumber: String,
    onNationalNumberChange: (String) -> Unit,
    regionIso: String,
    onRegionChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    label: String = t(R.string.mobile_number),
    hint: String? = null,
) {
    var showPicker by remember { mutableStateOf(false) }
    val country = remember(regionIso) { PhoneNumbers.country(regionIso) }
    val placeholder = hint ?: if (regionIso.equals("BD", true)) t(R.string.phone_hint_bd) else t(R.string.phone_hint_intl)

    OutlinedTextField(
        value = nationalNumber,
        onValueChange = { raw ->
            // Paste of +CC… → switch country (picker UX); value stays filterable as [0-9+]
            if (raw.trimStart().startsWith("+")) {
                PhoneNumbers.detectFromPlus(raw)?.let { (iso, national) ->
                    onRegionChange(iso)
                    onNationalNumberChange(national.filter { it.isDigit() }.take(15))
                    return@OutlinedTextField
                }
            }
            // Flutter mobile filter: [0-9+], max 20 (national portion here)
            val filtered = raw.filter { it.isDigit() || it == '+' }.take(20)
            onNationalNumberChange(
                if (regionIso.equals("BD", true)) {
                    PhoneNumbers.formatAsYouType(filtered.filter { it.isDigit() }, regionIso)
                } else {
                    filtered.filter { it.isDigit() }.take(15)
                },
            )
        },
        modifier = modifier.fillMaxWidth(),
        enabled = enabled,
        label = { Text(label) },
        placeholder = { Text(placeholder) },
        prefix = {
            Row(
                Modifier
                    .clickable(enabled = enabled) { showPicker = true }
                    .padding(end = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(country.flag, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.width(4.dp))
                Text("+${country.dialCode}", fontWeight = FontWeight.SemiBold)
                Icon(Icons.Default.ArrowDropDown, contentDescription = t(R.string.pick_country))
            }
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
        singleLine = true,
        shape = RoundedCornerShape(14.dp),
    )

    if (showPicker) {
        CountryPickerSheet(
            selectedIso = regionIso,
            onSelect = {
                onRegionChange(it)
                val digits = nationalNumber.filter { ch -> ch.isDigit() }
                onNationalNumberChange(
                    if (it.equals("BD", true)) PhoneNumbers.formatAsYouType(digits, it) else digits,
                )
                showPicker = false
            },
            onDismiss = { showPicker = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CountryPickerSheet(
    selectedIso: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val lang by LocalContainer.current.session.language.collectAsState(initial = "en")
    val bn = lang.equals("bn", ignoreCase = true)
    var query by remember { mutableStateOf("") }
    val list = remember(query, bn) { PhoneNumbers.filterCountries(query, bn) }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).navigationBarsPadding()) {
            Text(t(R.string.pick_country), style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.Search, null) },
                placeholder = { Text(t(R.string.search_country)) },
                shape = RoundedCornerShape(14.dp),
            )
            Spacer(Modifier.height(8.dp))
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 420.dp)) {
                items(list, key = { it.iso }) { c ->
                    CountryRow(c, bn, selected = c.iso.equals(selectedIso, true)) { onSelect(c.iso) }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun CountryRow(c: CountryInfo, bn: Boolean, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(c.flag, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(c.displayName(bn), fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
            Text(c.iso.uppercase(Locale.US), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text("+${c.dialCode}", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
    }
}
