package com.fertipos.agroshop.ui.product

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.fertipos.agroshop.data.local.entities.Product
 

@Composable
fun ProductScreen() {
    val vm: ProductViewModel = hiltViewModel()
    val state = vm.state.collectAsState()

    Surface(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        // Split layout: top form (scrollable) and bottom list (scrollable), each half screen via weight(1f)
        var name by remember { mutableStateOf("") }
        var type by remember { mutableStateOf("Fertilizer") }
        var unit by remember { mutableStateOf("") }
        var price by remember { mutableStateOf("") }
        var stock by remember { mutableStateOf("") }
        var gst by remember { mutableStateOf("") }

        Column(modifier = Modifier.fillMaxSize().imePadding().navigationBarsPadding()) {
            // Form area
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Text(text = "Add Product")
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Name*") },
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = type,
                        onValueChange = { type = it },
                        label = { Text("Type*") },
                        placeholder = { Text("Fertilizer, Pecticide") },
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = unit,
                        onValueChange = { unit = it },
                        label = { Text("Unit*") },
                        placeholder = { Text("KG / L / PCS") },
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = price,
                        onValueChange = { price = it },
                        label = { Text("Price per unit*") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = stock,
                        onValueChange = { stock = it },
                        label = { Text("Stock qty*") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = gst,
                        onValueChange = { gst = it },
                        label = { Text("GST %") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        val p = price.toDoubleOrNull() ?: return@Button
                        val s = stock.toDoubleOrNull() ?: return@Button
                        val g = gst.toDoubleOrNull() ?: 0.0
                        vm.add(name, type, unit, p, s, g)
                        name = ""; type = "Fertilizer"; unit = ""; price = ""; stock = ""; gst = ""
                    }) { Text("Save") }
                }
            }

            Spacer(Modifier.height(8.dp))
            HorizontalDivider()
            Spacer(Modifier.height(6.dp))

            // List area
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                items(state.value.products, key = { it.id }) { p ->
                    ProductRow(
                        p = p,
                        onUpdate = { prod, n, t, u, pr, st, g -> vm.update(prod, n, t, u, pr, st, g) },
                        onDelete = { vm.delete(p) },
                        onAdjustStock = { delta -> vm.adjustStock(p.id, delta) }
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun ProductRow(
    p: Product,
    onUpdate: (Product, String, String, String, Double, Double, Double) -> Unit,
    onDelete: () -> Unit,
    onAdjustStock: (Double) -> Unit
) {
    var showEdit by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(text = p.name, style = MaterialTheme.typography.titleMedium)
        Text(
            text = "Type: ${p.type}  |  Unit: ${p.unit}  |  Price: ${p.pricePerUnit}  |  Stock: ${p.stockQuantity}  |  GST: ${p.gstPercent}%",
            style = MaterialTheme.typography.bodySmall
        )
        Spacer(Modifier.height(6.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Spacer(modifier = Modifier.weight(1f))
            TextButton(onClick = { showEdit = true }) { Text("Edit") }
            TextButton(onClick = { confirmDelete = true }) { Text("Delete") }
        }
    }
    if (showEdit) {
        EditProductDialog(initial = p, onConfirm = { n, t, u, pr, st, g ->
            onUpdate(p, n, t, u, pr, st, g)
            showEdit = false
        }, onDismiss = { showEdit = false })
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete Product?") },
            text = { Text("Are you sure you want to delete '${p.name}'? This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = { onDelete(); confirmDelete = false }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun EditProductDialog(
    initial: Product,
    onConfirm: (String, String, String, Double, Double, Double) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(initial.name) }
    var type by remember { mutableStateOf(initial.type) }
    var unit by remember { mutableStateOf(initial.unit) }
    var price by remember { mutableStateOf(initial.pricePerUnit.toString()) }
    var stock by remember { mutableStateOf(initial.stockQuantity.toString()) }
    var gst by remember { mutableStateOf(initial.gstPercent.toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Product") },
        text = {
            Column {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Name*") },
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = type,
                        onValueChange = { type = it },
                        label = { Text("Type*") },
                        placeholder = { Text("Fertilizer, Pecticide") },
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(Modifier.height(6.dp))
                // Unit (editable)
                OutlinedTextField(
                    value = unit,
                    onValueChange = { unit = it },
                    label = { Text("Unit*") },
                    placeholder = { Text("KG / L / PCS") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(value = price, onValueChange = { price = it }, label = { Text("Price per unit*") })
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(value = stock, onValueChange = { stock = it }, label = { Text("Stock qty*") })
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(value = gst, onValueChange = { gst = it }, label = { Text("GST %") })
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val pr = price.toDoubleOrNull() ?: return@TextButton
                val st = stock.toDoubleOrNull() ?: return@TextButton
                val g = gst.toDoubleOrNull() ?: 0.0
                onConfirm(name, type, unit, pr, st, g)
            }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
