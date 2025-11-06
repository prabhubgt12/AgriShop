package com.fertipos.agroshop.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.fertipos.agroshop.R

@Composable
fun AddProductDialog(
    typeOptions: List<String>,
    unitOptions: List<String>,
    onConfirm: (String, String, String, Double, Double, Double, Double) -> Unit,
    onDismiss: () -> Unit
) {
    var name = remember { mutableStateOf("") }
    var type = remember(typeOptions) { mutableStateOf(typeOptions.firstOrNull() ?: "Fertilizer") }
    var unit = remember(unitOptions) { mutableStateOf(unitOptions.firstOrNull() ?: "Pcs") }
    var stock = remember { mutableStateOf("0") }
    var gst = remember { mutableStateOf("") }
    var purchasePrice = remember { mutableStateOf("0") }
    var sellingPrice = remember { mutableStateOf("0") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.add_product)) },
        text = {
            Column {
                // 1) Name (full width)
                OutlinedTextField(
                    value = name.value,
                    onValueChange = { name.value = it },
                    label = { Text(stringResource(R.string.name_required)) },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(6.dp))

                // 2) Type and Unit
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    TypePicker(
                        initial = type.value,
                        label = stringResource(R.string.type_required),
                        modifier = Modifier.weight(1f),
                        onPicked = { picked -> type.value = picked },
                        options = typeOptions
                    )
                    UnitPicker(
                        initial = unit.value,
                        label = stringResource(R.string.unit_required),
                        modifier = Modifier.weight(1f),
                        onPicked = { picked -> unit.value = picked },
                        options = unitOptions
                    )
                }
                Spacer(Modifier.height(6.dp))

                // 3) Stock and GST
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = stock.value,
                        onValueChange = { raw ->
                            val filtered = raw.filter { ch -> ch.isDigit() || ch == '.' }
                            val final = if (filtered.count { it == '.' } > 1) filtered.replaceFirst(".", "") else filtered
                            stock.value = final
                        },
                        label = { Text(stringResource(R.string.stock)) },
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = gst.value,
                        onValueChange = { raw ->
                            val filtered = raw.filter { ch -> ch.isDigit() || ch == '.' }
                            val final = if (filtered.count { it == '.' } > 1) filtered.replaceFirst(".", "") else filtered
                            gst.value = final
                        },
                        label = { Text(stringResource(R.string.gst_percent)) },
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(Modifier.height(6.dp))

                // 4) Buy Price and Sell Price
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = purchasePrice.value,
                        onValueChange = { raw ->
                            val filtered = raw.filter { ch -> ch.isDigit() || ch == '.' }
                            val final = if (filtered.count { it == '.' } > 1) filtered.replaceFirst(".", "") else filtered
                            purchasePrice.value = final
                        },
                        label = { Text(stringResource(R.string.buy_price)) },
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = sellingPrice.value,
                        onValueChange = { raw ->
                            val filtered = raw.filter { ch -> ch.isDigit() || ch == '.' }
                            val final = if (filtered.count { it == '.' } > 1) filtered.replaceFirst(".", "") else filtered
                            sellingPrice.value = final
                        },
                        label = { Text(stringResource(R.string.sell_price)) },
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val sp = sellingPrice.value.toDoubleOrNull() ?: 0.0
                val pp = purchasePrice.value.toDoubleOrNull() ?: 0.0
                val st = stock.value.toDoubleOrNull() ?: 0.0
                val g = gst.value.toDoubleOrNull() ?: 0.0
                onConfirm(name.value, type.value, unit.value, sp, pp, st, g)
            }) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}
