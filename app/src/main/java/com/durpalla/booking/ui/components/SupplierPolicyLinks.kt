package com.durpalla.booking.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.durpalla.booking.R
import com.durpalla.booking.core.s
import com.durpalla.booking.l10n.t
import kotlinx.serialization.json.JsonObject

@Composable
fun SupplierPolicyLinks(policies: JsonObject?, modifier: Modifier = Modifier) {
    val terms = policies.s("terms_and_conditions")
    val cancellation = policies.s("cancellation_policy")
    if (terms == null && cancellation == null) return

    val termsTitle = t(R.string.terms_conditions)
    val cancellationTitle = t(R.string.cancellation_refund_policy)
    var selected by remember(policies) { mutableStateOf<Pair<String, String>?>(null) }
    Column(modifier) {
        policies.s("supplier_name")?.let { Text(it, style = MaterialTheme.typography.titleSmall) }
        terms?.let { TextButton(onClick = { selected = termsTitle to it }) { Text(termsTitle) } }
        cancellation?.let { TextButton(onClick = { selected = cancellationTitle to it }) { Text(cancellationTitle) } }
    }

    selected?.let { (title, body) ->
        AlertDialog(
            onDismissRequest = { selected = null },
            title = { Text(title) },
            text = {
                Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                    Text(body)
                }
            },
            confirmButton = { TextButton(onClick = { selected = null }) { Text(t(R.string.ok)) } },
        )
    }
}
