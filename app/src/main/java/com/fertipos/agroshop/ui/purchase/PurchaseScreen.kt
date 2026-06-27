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
import androidx.compose.foundation.background
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontWeight
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
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Edit
import androidx.activity.compose.BackHandler
import androidx.compose.ui.res.stringResource
import com.fertipos.agroshop.R
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.material3.AlertDialog
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.runtime.rememberCoroutineScope
import com.fertipos.agroshop.ui.customer.CustomerViewModel
import com.fertipos.agroshop.ui.common.AddProductDialog
import com.fertipos.agroshop.ui.settings.CompanyProfileViewModel
import com.fertipos.agroshop.ui.product.ProductViewModel
import com.fertipos.agroshop.ui.BarcodeScannerScreen
import com.fertipos.agroshop.data.api.ProductInfo
import kotlinx.coroutines.launch
import com.fertipos.agroshop.ui.common.PartyForm
import android.content.Context
import android.widget.Toast

@Composable
fun PurchaseScreen(navVm: AppNavViewModel) {
    val vm: PurchaseViewModel = hiltViewModel()
    val custVm: CustomerViewModel = hiltViewModel()
    val prodVm: ProductViewModel = hiltViewModel()
    val profVm: CompanyProfileViewModel = hiltViewModel()
    val state by vm.state.collectAsState()
    // Snackbar replaced with Toast to avoid blocking navigation and being hidden by scroll
    val prevTab = navVm.previousSelected.collectAsState()
    val backOverride = navVm.backOverrideTab.collectAsState()

    // Hoisted form state like Billing
    var selectedProduct by remember { mutableStateOf<Product?>(null) }
    var qtyText by remember { mutableStateOf("") }
    var priceText by remember { mutableStateOf("") }
    var pendingSelectProductId by remember { mutableStateOf<Int?>(null) }
    // Date handled by VM (newDateMillis for new, editingDateMillis for edit)
    // Add Supplier dialog state
    var showAddSupplier by remember { mutableStateOf(false) }
    var newSuppName by remember { mutableStateOf("") }
    var newSuppPhone by remember { mutableStateOf("") }
    var newSuppAddress by remember { mutableStateOf("") }
    // Add Product dialog state
    var showAddProduct by remember { mutableStateOf(false) }
    var showBarcodeScanner by remember { mutableStateOf(false) }
    var scannedProduct by remember { mutableStateOf<ProductInfo?>(null) }
    val scope = rememberCoroutineScope()

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

    // When products list updates and we have a pending product id, auto-select it
    LaunchedEffect(state.products, pendingSelectProductId) {
        val pid = pendingSelectProductId
        if (pid != null) {
            state.products.firstOrNull { it.id == pid }?.let { newly ->
                selectedProduct = newly
                pendingSelectProductId = null
            }
        }
    }
    // Always intercept system back to return to the intended tab.
    BackHandler(enabled = true) {
        val target = backOverride.value ?: prevTab.value
        navVm.clearBackOverrideTab()
        navVm.navigateTo(target)
    }

    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val context = LocalContext.current
    LaunchedEffect(state.error) {
        val err = state.error
        if (err != null) {
            android.widget.Toast.makeText(context, err, android.widget.Toast.LENGTH_SHORT).show()
            vm.clearError()
        }
    }
    LaunchedEffect(state.successPurchaseId) {
        val id = state.successPurchaseId
        if (id != null) {
            android.widget.Toast.makeText(
                context,
                context.getString(R.string.toast_purchase_created, id),
                android.widget.Toast.LENGTH_SHORT
            ).show()
            vm.clearSuccess()
            // Close the screen immediately: navigate back to intended tab
            val target = backOverride.value ?: prevTab.value
            navVm.clearBackOverrideTab()
            navVm.navigateTo(target)
        }
    }

    LaunchedEffect(state.successEditedId) {
        val id = state.successEditedId
        if (id != null) {
            android.widget.Toast.makeText(
                context,
                context.getString(R.string.toast_purchase_updated, id),
                android.widget.Toast.LENGTH_SHORT
            ).show()
            vm.clearSuccess()
            // Close the screen immediately: navigate back to intended tab
            val target = backOverride.value ?: prevTab.value
            navVm.clearBackOverrideTab()
            navVm.navigateTo(target)
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
            .imePadding()
            .navigationBarsPadding()
    ) {
        item {
            // Header
            val header = if (state.editingPurchaseId != null) stringResource(R.string.edit_purchase_with_id, state.editingPurchaseId!!) else stringResource(R.string.create_purchase)
            Text(text = header, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
        }

        // Card 1: Supplier & Date
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                    val selectedName = state.suppliers.firstOrNull { it.id == state.selectedSupplierId }?.name ?: ""
                    CustomerPicker(
                        customers = state.suppliers,
                        label = stringResource(R.string.supplier_label),
                        initialQuery = selectedName,
                        modifier = Modifier.fillMaxWidth(),
                        onPicked = { vm.setSupplier(it.id) },
                        onAddNew = { showAddSupplier = true }
                    )
                    Spacer(Modifier.height(8.dp))
                    DateField(
                        label = stringResource(R.string.date),
                        value = if (state.editingPurchaseId != null) (state.editingDateMillis ?: state.newDateMillis) else state.newDateMillis,
                        onChange = { millis -> if (state.editingPurchaseId != null) vm.setEditingDate(millis) else vm.setNewDate(millis) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        // Card 2: Items (product picker, qty/price, add, list)
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        ProductPicker(
                            products = state.products,
                            label = stringResource(R.string.product_label),
                            modifier = Modifier.weight(1f),
                            initialQuery = selectedProduct?.name ?: "",
                            onPicked = { p ->
                                selectedProduct = p
                                priceText = p.purchasePrice.toStringAsFixed(2)
                            },
                            addNewLabel = stringResource(R.string.add),
                            onAddNew = { showAddProduct = true }
                        )
                        IconButton(onClick = { showBarcodeScanner = true }) {
                            Icon(Icons.Default.QrCodeScanner, contentDescription = "Scan Barcode")
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = qtyText,
                            onValueChange = { raw ->
                                val filtered = raw.filter { ch -> ch.isDigit() || ch == '.' }
                                val final = if (filtered.count { it == '.' } > 1) filtered.replaceFirst(".", "") else filtered
                                qtyText = final
                            },
                            label = { Text(stringResource(R.string.qty)) },
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
                            label = { Text(stringResource(R.string.unit_price)) },
                            singleLine = true,
                            enabled = selectedProduct != null,
                            placeholder = { Text(selectedProduct?.purchasePrice?.toStringAsFixed(2) ?: "") },
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        Spacer(Modifier.weight(1f))
                        Button(onClick = {
                            val p = selectedProduct ?: return@Button
                            val q = qtyText.toDoubleOrNull() ?: return@Button
                            vm.addItem(p, q)
                            val up = priceText.toDoubleOrNull()
                            if (up != null) {
                                vm.updateItem(p.id, quantity = q, unitPrice = up, gstPercent = null)
                            }
                            qtyText = ""
                            // Hide keyboard and clear focus
                            focusManager.clearFocus(force = true)
                            keyboard?.hide()
                        }) { Text(stringResource(R.string.add_item)) }
                    }
                    Spacer(Modifier.height(10.dp))
                    state.items.forEach { it ->
                        PurchaseItemCard(item = it, onRemove = { vm.removeItem(it.product.id) }, onUpdate = { newQty, newPrice -> vm.updateItem(it.product.id, quantity = newQty, unitPrice = newPrice, gstPercent = it.gstPercent) })
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        // Card 3: Totals & Submit
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                    Text(
                        stringResource(R.string.subtotal_with_amount, state.subtotal.toStringAsFixed(2)),
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = androidx.compose.ui.text.style.TextAlign.End
                    )
                    Text(
                        stringResource(R.string.gst_with_amount, state.gstAmount.toStringAsFixed(2)),
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = androidx.compose.ui.text.style.TextAlign.End
                    )
                    Text(
                        stringResource(R.string.total_with_amount, state.total.toStringAsFixed(2)),
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
                        Text(stringResource(R.string.paid_label), modifier = Modifier.padding(start = 4.dp))
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
                            text = stringResource(R.string.balance_colon, String.format(Locale.getDefault(), "%.2f", state.balance))
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val buttonText = if (state.editingPurchaseId != null) stringResource(R.string.save_purchase) else stringResource(R.string.create_purchase)
                        Spacer(Modifier.weight(1f))
                        Button(onClick = { vm.submit() }, enabled = state.items.isNotEmpty()) { Text(buttonText) }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }

    // Add Product dialog (outside list)
    if (showAddProduct) {
        val profile by profVm.profile.collectAsState()
        val typeOptions = remember(profile.productTypesCsv) {
            profile.productTypesCsv.split(',').map { it.trim() }.filter { it.isNotEmpty() }
                .ifEmpty { listOf("Fertilizer", "Pecticide", "Fungi", "GP", "Other") }
        }
        val unitOptions = remember(profile.unitsCsv) {
            profile.unitsCsv.split(',').map { it.trim() }.filter { it.isNotEmpty() }
                .ifEmpty { listOf("Kg", "Pcs", "L") }
        }
        AddProductDialog(
            typeOptions = typeOptions,
            unitOptions = unitOptions,
            onConfirm = { n, t, u, sp, pp, st, g, barcode ->
                scope.launch {
                    val id = prodVm.addAndReturnId(n, t, u, sp, pp, st, g, barcode)
                    if (id > 0) pendingSelectProductId = id
                }
                // prefill with purchase price
                priceText = pp.toStringAsFixed(2)
                showAddProduct = false
            },
            onDismiss = { showAddProduct = false }
        )
    }

    // Add Supplier Dialog (opened via CustomerPicker onAddNew)
    if (showAddSupplier) {
        AlertDialog(
            onDismissRequest = { showAddSupplier = false },
            confirmButton = {},
            dismissButton = {},
            text = {
                PartyForm(
                    title = "Add new supplier",
                    name = newSuppName,
                    onNameChange = { newSuppName = it },
                    phone = newSuppPhone,
                    onPhoneChange = { newSuppPhone = it },
                    address = newSuppAddress,
                    onAddressChange = { newSuppAddress = it },
                    showSupplierToggle = false,
                    isSupplier = true,
                    onIsSupplierChange = {},
                    onSubmit = {
                        scope.launch {
                            val id = custVm.addAndReturnId(newSuppName, newSuppPhone.takeIf { it.isNotBlank() }, newSuppAddress.takeIf { it.isNotBlank() }, true)
                            if (id > 0) vm.setSupplier(id)
                            newSuppName = ""; newSuppPhone = ""; newSuppAddress = ""
                            showAddSupplier = false
                        }
                    },
                    onCancel = { showAddSupplier = false },
                    submitText = "Add"
                )
            }
        )
    }

    // Barcode scanner overlay
    if (showBarcodeScanner) {
        BarcodeScannerScreen(
            onProductDetected = { product ->
                // Find product by ID and auto-add with qty 1
                product.productId?.let { pid ->
                    state.products.firstOrNull { it.id == pid }?.let { p ->
                        // Check if product already exists in items
                        val existingItem = state.items.firstOrNull { it.product.id == p.id }
                        if (existingItem != null) {
                            // Increase qty by 1
                            val newQty = existingItem.quantity + 1.0
                            vm.updateItem(p.id, quantity = newQty, unitPrice = existingItem.unitPrice, gstPercent = existingItem.gstPercent)
                            Toast.makeText(context, "${p.name} quantity increased to ${newQty}", Toast.LENGTH_SHORT).show()
                        } else {
                            // Add new item with qty 1
                            vm.addItem(p, 1.0)
                            vm.updateItem(p.id, quantity = 1.0, unitPrice = p.purchasePrice, gstPercent = p.gstPercent)
                            Toast.makeText(context, "${p.name} added (qty: 1)", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                // Close scanner after detection
                showBarcodeScanner = false
            },
            onBack = { showBarcodeScanner = false },
            lookupProduct = true,
            isOverlay = true
        )
    }

}

@Composable
private fun PurchaseItemCard(
        item: PurchaseViewModel.DraftItem,
        onRemove: () -> Unit,
        onUpdate: (Double, Double) -> Unit
    ) {
    var isEditing by remember { mutableStateOf(false) }
    var editQty by remember { mutableStateOf(item.quantity.toString()) }
    var editPrice by remember { mutableStateOf(item.unitPrice.toString()) }
    
    val lineBase = item.quantity * item.unitPrice
    val gstAmt = lineBase * (item.gstPercent / 100.0)
    val lineTotal = lineBase + gstAmt
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(text = item.product.name, style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { 
                    isEditing = true
                    editQty = item.quantity.toString()
                    editPrice = item.unitPrice.toString()
                }) { Icon(Icons.Filled.Edit, contentDescription = "Edit") }
                IconButton(onClick = onRemove) { Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.delete)) }
            }
            Spacer(Modifier.height(4.dp))
            if (isEditing) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = editQty,
                        onValueChange = { raw ->
                            val filtered = raw.filter { ch -> ch.isDigit() || ch == '.' }
                            val final = if (filtered.count { it == '.' } > 1) filtered.replaceFirst(".", "") else filtered
                            editQty = final
                        },
                        label = { Text(stringResource(R.string.qty_label)) },
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = editPrice,
                        onValueChange = { raw ->
                            val filtered = raw.filter { ch -> ch.isDigit() || ch == '.' }
                            val final = if (filtered.count { it == '.' } > 1) filtered.replaceFirst(".", "") else filtered
                            editPrice = final
                        },
                        label = { Text(stringResource(R.string.price_label)) },
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(onClick = {
                        val newQty = editQty.toDoubleOrNull() ?: item.quantity
                        val newPrice = editPrice.toDoubleOrNull() ?: item.unitPrice
                        onUpdate(newQty, newPrice)
                        isEditing = false
                    }, modifier = Modifier.weight(1f)) {
                        Text("Save")
                    }
                    Button(onClick = { isEditing = false }, modifier = Modifier.weight(1f)) {
                        Text("Cancel")
                    }
                }
            } else {
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text(text = stringResource(R.string.qty_label) + ": " + item.quantity.toStringAsFixed(2) + " " + item.product.unit)
                    Spacer(Modifier.weight(1f))
                    Text(text = stringResource(R.string.price_label) + ": " + item.unitPrice.toStringAsFixed(2))
                }
                Spacer(Modifier.height(2.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text(text = stringResource(R.string.gst_percent_colon, String.format(Locale.getDefault(), "%.1f", item.gstPercent)))
                    Spacer(Modifier.weight(1f))
                    Text(text = stringResource(R.string.gst_amt_colon, gstAmt.toStringAsFixed(2)))
                }
                Spacer(Modifier.height(2.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    Spacer(Modifier.weight(1f))
                    Text(text = stringResource(R.string.total_with_amount, lineTotal.toStringAsFixed(2)))
                }
            }
        }
    }
}

    private fun Double.toStringAsFixed(digits: Int): String = String.format(Locale.getDefault(), "%.${digits}f", this)
