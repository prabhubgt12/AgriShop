package com.fertipos.agroshop.ui.purchase

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.hilt.navigation.compose.hiltViewModel
import com.fertipos.agroshop.data.local.entities.Product
import com.fertipos.agroshop.ui.common.CustomerPicker
import com.fertipos.agroshop.ui.common.ProductPicker
import com.fertipos.agroshop.ui.common.DateField
import com.fertipos.agroshop.ui.screens.AppNavViewModel
import java.util.Locale

@Composable
fun PurchaseScreen(navVm: AppNavViewModel) {
    val vm: PurchaseViewModel = hiltViewModel()
    val state by vm.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }

    // Observe pending edit request from navigation and load once
    val pendingEditId = navVm.pendingEditPurchaseId.collectAsState()
    LaunchedEffect(pendingEditId.value) {
        val id = pendingEditId.value
        if (id != null) {
            vm.loadForEdit(id)
            navVm.clearPendingEditPurchase()
        } else {
            // No edit requested; ensure a clean slate for new purchase
            vm.resetForNewPurchase()
        }
    }

    LaunchedEffect(state.successPurchaseId) {
        val id = state.successPurchaseId
        if (id != null) {
            snackbar.showSnackbar("Purchase #$id created")
            vm.clearSuccess()
        }
    }

    LaunchedEffect(state.successEditedId) {
        val id = state.successEditedId
        if (id != null) {
            snackbar.showSnackbar("Purchase #$id updated")
            vm.clearSuccess()
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        // Snackbar host
        SnackbarHost(hostState = snackbar)

        // Header
        val header = if (state.editingPurchaseId != null) "Edit Purchase #${state.editingPurchaseId}" else "Create Purchase"
        Text(text = header, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))

        // Supplier selection
        CustomerPicker(
            customers = state.suppliers,
            label = "Supplier",
            modifier = Modifier.fillMaxWidth(),
            onPicked = { vm.setSupplier(it.id) }
        )
        Spacer(Modifier.height(8.dp))

        // Date field (shown only in edit mode)
        if (state.editingPurchaseId != null) {
            DateField(
                label = "Date",
                value = state.editingDateMillis,
                onChange = { millis -> vm.setEditingDate(millis) },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
        }

        // Product picker (own row) + qty & price (grouped row)
        var selectedProduct by remember { mutableStateOf<Product?>(null) }
        var qtyText by remember { mutableStateOf("") }
        var priceText by remember { mutableStateOf("") }
        // Product on its own full-width row
        ProductPicker(
            products = state.products,
            label = "Product",
            modifier = Modifier.fillMaxWidth(),
            onPicked = { p ->
                selectedProduct = p
                priceText = p.purchasePrice.toStringAsFixed(2)
            }
        )
        Spacer(Modifier.height(6.dp))
        // Grouped numeric fields
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = qtyText,
                onValueChange = { raw ->
                    val filtered = raw.filter { ch -> ch.isDigit() || ch == '.' }
                    val final = if (filtered.count { it == '.' } > 1) filtered.replaceFirst(".", "") else filtered
                    qtyText = final
                },
                label = { Text("Qty") },
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = priceText,
                onValueChange = { raw ->
                    val filtered = raw.filter { ch -> ch.isDigit() || ch == '.' }
                    val final = if (filtered.count { it == '.' } > 1) filtered.replaceFirst(".", "") else filtered
                    priceText = final
                },
                label = { Text("Unit Price") },
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                val p = selectedProduct ?: return@Button
                val q = qtyText.toDoubleOrNull() ?: return@Button
                // Add with default purchasePrice
                vm.addItem(p, q)
                // If user entered a price, override unit price
                val up = priceText.toDoubleOrNull()
                if (up != null) {
                    vm.updateItem(p.id, quantity = q, unitPrice = up, gstPercent = null)
                }
                selectedProduct = null
                qtyText = ""
                priceText = ""
            }) { Text("Add Item") }
            TextButton(onClick = { vm.setNotes(""); vm.clearSuccess() }) { Text("Clear") }
        }

        Spacer(Modifier.height(12.dp))
        HorizontalDivider()
        Spacer(Modifier.height(8.dp))

        // Items list
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(state.items) { it ->
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(it.product.name, style = MaterialTheme.typography.titleSmall)
                        Text("Qty ${it.quantity.toStringAsFixed(2)} ${it.product.unit} × ${it.unitPrice.toStringAsFixed(2)}")
                    }
                    Text("= ${(it.quantity * it.unitPrice * (1 + it.gstPercent / 100.0)).toStringAsFixed(2)}")
                    TextButton(onClick = { vm.removeItem(it.product.id) }) { Text("Remove") }
                }
                HorizontalDivider()
            }
        }

        // Totals + submit
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                "Subtotal: ${state.subtotal.toStringAsFixed(2)}",
                modifier = Modifier.fillMaxWidth(),
                textAlign = androidx.compose.ui.text.style.TextAlign.End
            )
            Text(
                "GST: ${state.gstAmount.toStringAsFixed(2)}",
                modifier = Modifier.fillMaxWidth(),
                textAlign = androidx.compose.ui.text.style.TextAlign.End
            )
            Text(
                "Total: ${state.total.toStringAsFixed(2)}",
                modifier = Modifier.fillMaxWidth(),
                textAlign = androidx.compose.ui.text.style.TextAlign.End
            )
            Spacer(Modifier.height(6.dp))
            var paidInFull by remember { mutableStateOf(false) }
            LaunchedEffect(paidInFull, state.total) {
                if (paidInFull) vm.setPaid(state.total.toStringAsFixed(2))
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                androidx.compose.material3.Checkbox(
                    checked = paidInFull,
                    onCheckedChange = { checked ->
                        paidInFull = checked
                        if (checked) vm.setPaid(state.total.toStringAsFixed(2))
                    }
                )
                Text("Paid", modifier = Modifier.padding(start = 4.dp))
                TextField(
                    value = if (state.paid == 0.0) "" else state.paid.toStringAsFixed(2),
                    onValueChange = { vm.setPaid(it); if (paidInFull && it.toDoubleOrNull() != state.total) paidInFull = false },
                    singleLine = true,
                    // no label/placeholder to avoid extra height and inline text
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
                    textStyle = androidx.compose.ui.text.TextStyle(textAlign = androidx.compose.ui.text.style.TextAlign.End),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        disabledContainerColor = Color.Transparent,
                        errorContainerColor = Color.Transparent
                    ),
                    modifier = Modifier.fillMaxWidth(0.5f)
                )
            }
            Spacer(Modifier.height(4.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                Spacer(Modifier.weight(1f))
                Text(
                    text = "Balance: ${String.format(Locale.getDefault(), "%.2f", state.balance)}"
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val buttonText = if (state.editingPurchaseId != null) "Save Purchase" else "Create Purchase"
                Button(onClick = { vm.submit() }, enabled = state.items.isNotEmpty()) { Text(buttonText) }
                TextButton(onClick = { vm.clearSuccess() }) { Text("Reset Status") }
            }
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }
}

private fun Double.toStringAsFixed(digits: Int): String = String.format(Locale.getDefault(), "%.${digits}f", this)
