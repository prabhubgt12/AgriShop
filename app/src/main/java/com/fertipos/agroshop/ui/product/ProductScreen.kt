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
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.fertipos.agroshop.ui.screens.AppNavViewModel
import com.fertipos.agroshop.data.local.entities.Product
import com.fertipos.agroshop.ui.common.UnitPicker
import com.fertipos.agroshop.ui.common.TypePicker
import com.fertipos.agroshop.ui.settings.CompanyProfileViewModel
import java.text.NumberFormat
import java.util.Locale

@Composable
fun ProductScreen() {
    val vm: ProductViewModel = hiltViewModel()
    val navVm: AppNavViewModel = hiltViewModel()
    val companyVm: CompanyProfileViewModel = hiltViewModel()
    val state = vm.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val profileState = companyVm.profile.collectAsState()
    val typeOptions = remember(profileState.value.productTypesCsv) {
        profileState.value.productTypesCsv.split(',')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .ifEmpty { listOf("Fertilizer", "Pecticide", "Fungi", "GP", "Other") }
    }

    Surface(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Column(modifier = Modifier.fillMaxSize()) {
            SnackbarHost(hostState = snackbarHostState)
            // Header with Add button
            var showAdd by remember { mutableStateOf(false) }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(text = "Products", style = MaterialTheme.typography.titleMedium)
                Button(onClick = { showAdd = true }) { Text("Add") }
            }
            Spacer(Modifier.height(8.dp))
            HorizontalDivider()
            Spacer(Modifier.height(6.dp))

            // List area
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                items(state.value.products, key = { it.id }) { p ->
                    ProductRow(
                        p = p,
                        typeOptions = typeOptions,
                        onUpdate = { prod, n, t, u, sp, pp, st, g -> vm.update(prod, n, t, u, sp, pp, st, g) },
                        onDelete = { vm.delete(p) },
                        onAdjustStock = { delta -> vm.adjustStock(p.id, delta) },
                        onHistory = {
                            navVm.requestPurchaseHistoryForProduct(p.id)
                            navVm.navigateTo(8)
                        }
                    )
                    HorizontalDivider()
                }
            }

            if (showAdd) {
                AddProductDialog(
                    typeOptions = typeOptions,
                    onConfirm = { n, t, u, sp, pp, st, g ->
                        vm.add(n, t, u, sp, pp, st, g)
                        showAdd = false
                    },
                    onDismiss = { showAdd = false }
                )
            }
            // Error handling
            val err = state.value.error
            LaunchedEffect(err) {
                if (err != null) {
                    snackbarHostState.showSnackbar(err)
                    vm.clearError()
                }
            }
        }
    }
}

@Composable
private fun ProductRow(
    p: Product,
    typeOptions: List<String>,
    onUpdate: (Product, String, String, String, Double, Double, Double, Double) -> Unit,
    onDelete: () -> Unit,
    onAdjustStock: (Double) -> Unit,
    onHistory: () -> Unit
) {
    var showEdit by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val priceFmt = remember {
        NumberFormat.getNumberInstance(Locale.getDefault()).apply {
            minimumFractionDigits = 2
            maximumFractionDigits = 2
        }
    }
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(text = p.name, style = MaterialTheme.typography.titleMedium)
        Text(
            text = "Type: ${p.type}  |  Unit: ${p.unit}  |  Sell: ${priceFmt.format(p.sellingPrice)}  |  Buy: ${priceFmt.format(p.purchasePrice)}  |  Stock: ${p.stockQuantity}  |  GST: ${p.gstPercent}%",
            style = MaterialTheme.typography.bodySmall
        )
        Spacer(Modifier.height(6.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Spacer(modifier = Modifier.weight(1f))
            TextButton(onClick = onHistory) { Text("History") }
            TextButton(onClick = { showEdit = true }) { Text("Edit") }
            TextButton(onClick = { confirmDelete = true }) { Text("Delete") }
        }
    }
    if (showEdit) {
        EditProductDialog(initial = p, typeOptions = typeOptions, onConfirm = { n, t, u, sp, pp, st, g ->
            onUpdate(p, n, t, u, sp, pp, st, g)
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
    typeOptions: List<String>,
    onConfirm: (String, String, String, Double, Double, Double, Double) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(initial.name) }
    var type by remember { mutableStateOf(initial.type) }
    var unit by remember { mutableStateOf(initial.unit) }
    var sellingPrice by remember { mutableStateOf(initial.sellingPrice.toString()) }
    var purchasePrice by remember { mutableStateOf(initial.purchasePrice.toString()) }
    var stock by remember { mutableStateOf(initial.stockQuantity.toString()) }
    var gst by remember { mutableStateOf(initial.gstPercent.toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Product") },
        text = {
            Column {
                // 1) Name (full width)
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name*") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(6.dp))

                // 2) Type and Unit
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    TypePicker(
                        initial = type,
                        label = "Type*",
                        modifier = Modifier.weight(1f),
                        onPicked = { picked -> type = picked },
                        options = typeOptions
                    )
                    UnitPicker(
                        initial = unit,
                        label = "Unit*",
                        modifier = Modifier.weight(1f),
                        onPicked = { picked -> unit = picked }
                    )
                }
                Spacer(Modifier.height(6.dp))

                // 3) Stock and GST
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = stock,
                        onValueChange = { raw ->
                            val filtered = raw.filter { ch -> ch.isDigit() || ch == '.' }
                            val final = if (filtered.count { it == '.' } > 1) filtered.replaceFirst(".", "") else filtered
                            stock = final
                        },
                        label = { Text("Stock") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = gst,
                        onValueChange = { raw ->
                            val filtered = raw.filter { ch -> ch.isDigit() || ch == '.' }
                            val final = if (filtered.count { it == '.' } > 1) filtered.replaceFirst(".", "") else filtered
                            gst = final
                        },
                        label = { Text("GST%") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(Modifier.height(6.dp))

                // 4) Buy Price and Sell Price
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = purchasePrice,
                        onValueChange = { raw ->
                            val filtered = raw.filter { ch -> ch.isDigit() || ch == '.' }
                            val final = if (filtered.count { it == '.' } > 1) filtered.replaceFirst(".", "") else filtered
                            purchasePrice = final
                        },
                        label = { Text("Buy Price") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = sellingPrice,
                        onValueChange = { raw ->
                            val filtered = raw.filter { ch -> ch.isDigit() || ch == '.' }
                            val final = if (filtered.count { it == '.' } > 1) filtered.replaceFirst(".", "") else filtered
                            sellingPrice = final
                        },
                        label = { Text("Sell Price") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val sp = sellingPrice.toDoubleOrNull() ?: 0.0
                val pp = purchasePrice.toDoubleOrNull() ?: 0.0
                val st = stock.toDoubleOrNull() ?: 0.0
                val g = gst.toDoubleOrNull() ?: 0.0
                onConfirm(name, type, unit, sp, pp, st, g)
            }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun AddProductDialog(
    typeOptions: List<String>,
    onConfirm: (String, String, String, Double, Double, Double, Double) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    var type by remember(typeOptions) { mutableStateOf(typeOptions.firstOrNull() ?: "Fertilizer") }
    var unit by remember { mutableStateOf("Pcs") }
    var stock by remember { mutableStateOf("0") }
    var gst by remember { mutableStateOf("") }
    var purchasePrice by remember { mutableStateOf("0") }
    var sellingPrice by remember { mutableStateOf("0") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Product") },
        text = {
            Column {
                // 1) Name (full width)
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name*") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(6.dp))

                // 2) Type and Unit
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    TypePicker(
                        initial = type,
                        label = "Type*",
                        modifier = Modifier.weight(1f),
                        onPicked = { picked -> type = picked },
                        options = typeOptions
                    )
                    UnitPicker(
                        initial = unit,
                        label = "Unit*",
                        modifier = Modifier.weight(1f),
                        onPicked = { picked -> unit = picked }
                    )
                }
                Spacer(Modifier.height(6.dp))

                // 3) Stock and GST
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = stock,
                        onValueChange = { raw ->
                            val filtered = raw.filter { ch -> ch.isDigit() || ch == '.' }
                            val final = if (filtered.count { it == '.' } > 1) filtered.replaceFirst(".", "") else filtered
                            stock = final
                        },
                        label = { Text("Stock") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = gst,
                        onValueChange = { raw ->
                            val filtered = raw.filter { ch -> ch.isDigit() || ch == '.' }
                            val final = if (filtered.count { it == '.' } > 1) filtered.replaceFirst(".", "") else filtered
                            gst = final
                        },
                        label = { Text("GST%") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(Modifier.height(6.dp))

                // 4) Buy Price and Sell Price
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = purchasePrice,
                        onValueChange = { raw ->
                            val filtered = raw.filter { ch -> ch.isDigit() || ch == '.' }
                            val final = if (filtered.count { it == '.' } > 1) filtered.replaceFirst(".", "") else filtered
                            purchasePrice = final
                        },
                        label = { Text("Buy Price") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = sellingPrice,
                        onValueChange = { raw ->
                            val filtered = raw.filter { ch -> ch.isDigit() || ch == '.' }
                            val final = if (filtered.count { it == '.' } > 1) filtered.replaceFirst(".", "") else filtered
                            sellingPrice = final
                        },
                        label = { Text("Sell Price") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val sp = sellingPrice.toDoubleOrNull() ?: 0.0
                val pp = purchasePrice.toDoubleOrNull() ?: 0.0
                val st = stock.toDoubleOrNull() ?: 0.0
                val g = gst.toDoubleOrNull() ?: 0.0
                onConfirm(name, type, unit, sp, pp, st, g)
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
