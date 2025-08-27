package com.fertipos.agroshop.ui.purchase

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete

@Composable
fun PurchaseScreen(navVm: AppNavViewModel) {
    val vm: PurchaseViewModel = hiltViewModel()
    val state by vm.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }

    // Hoisted form state like Billing
    var selectedProduct by remember { mutableStateOf<Product?>(null) }
    var qtyText by remember { mutableStateOf("") }
    var priceText by remember { mutableStateOf("") }
    // Date handled by VM (newDateMillis for new, editingDateMillis for edit)

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

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .imePadding()
            .navigationBarsPadding()
    ) {
        item {
            // Snackbar host
            SnackbarHost(hostState = snackbar)

            // Header
            val header = if (state.editingPurchaseId != null) "Edit Purchase #${state.editingPurchaseId}" else "Create Purchase"
            Text(text = header, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
        }

        // Date (always visible at top). Bound to VM for both new and edit.
        item {
            DateField(
                label = "Date",
                value = if (state.editingPurchaseId != null) (state.editingDateMillis ?: state.newDateMillis) else state.newDateMillis,
                onChange = { millis -> if (state.editingPurchaseId != null) vm.setEditingDate(millis) else vm.setNewDate(millis) },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
        }

        // Supplier selection
        item {
            val selectedName = state.suppliers.firstOrNull { it.id == state.selectedSupplierId }?.name ?: ""
            CustomerPicker(
                customers = state.suppliers,
                label = "Supplier",
                initialQuery = selectedName,
                modifier = Modifier.fillMaxWidth(),
                onPicked = { vm.setSupplier(it.id) }
            )
            Spacer(Modifier.height(8.dp))
        }

        // Product picker (own row) + qty & price (grouped row)
        item {
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
                    singleLine = true,
                    enabled = selectedProduct != null,
                    placeholder = { Text(selectedProduct?.purchasePrice?.toStringAsFixed(2) ?: "") },
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
                    // Keep product selected like Billing; just clear qty for fast repeated adds
                    qtyText = ""
                }) { Text("Add Item") }
                TextButton(onClick = { vm.setNotes(""); vm.clearSuccess() }) { Text("Clear") }
            }
            Spacer(Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(Modifier.height(8.dp))
        }

        // Items list as Cards (use stable key like Billing to avoid state reuse issues)
        items(state.items, key = { it.product.id }) { it ->
            PurchaseItemCard(item = it, onRemove = { vm.removeItem(it.product.id) })
        }

        // Totals + submit at end of scroll
        item {
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

    @Composable
    private fun PurchaseItemCard(
        item: PurchaseViewModel.DraftItem,
        onRemove: () -> Unit
    ) {
        val lineBase = item.quantity * item.unitPrice
        val gstAmt = lineBase * (item.gstPercent / 100.0)
        val lineTotal = lineBase + gstAmt
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
            colors = CardDefaults.cardColors()
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text(text = item.product.name, style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = onRemove) { Icon(Icons.Filled.Delete, contentDescription = "Delete") }
                }
                Spacer(Modifier.height(4.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text(text = "Qty: ${item.quantity.toStringAsFixed(2)} ${item.product.unit}")
                    Spacer(Modifier.weight(1f))
                    Text(text = "Price: ${item.unitPrice.toStringAsFixed(2)}")
                }
                Spacer(Modifier.height(2.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text(text = "GST ${String.format(Locale.getDefault(), "%.1f", item.gstPercent)}%")
                    Spacer(Modifier.weight(1f))
                    Text(text = "GST Amt: ${gstAmt.toStringAsFixed(2)}")
                }
                Spacer(Modifier.height(2.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    Spacer(Modifier.weight(1f))
                    Text(text = "Total: ${lineTotal.toStringAsFixed(2)}")
                }
            }
        }
    }

    private fun Double.toStringAsFixed(digits: Int): String = String.format(Locale.getDefault(), "%.${digits}f", this)
