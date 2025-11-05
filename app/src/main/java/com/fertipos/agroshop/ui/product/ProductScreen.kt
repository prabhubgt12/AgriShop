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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import com.fertipos.agroshop.ui.screens.AppNavViewModel
import com.fertipos.agroshop.data.local.entities.Product
import com.fertipos.agroshop.ui.common.UnitPicker
import com.fertipos.agroshop.ui.common.TypePicker
import com.fertipos.agroshop.ui.settings.CompanyProfileViewModel
import androidx.compose.material.icons.outlined.MoreVert
import java.text.NumberFormat
import java.util.Locale
import androidx.compose.ui.res.stringResource
import com.fertipos.agroshop.R
import androidx.compose.ui.platform.LocalContext

import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.border
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.Icons
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
@Composable
fun ProductScreen() {
    val vm: ProductViewModel = hiltViewModel()
    val navVm: AppNavViewModel = hiltViewModel()
    val companyVm: CompanyProfileViewModel = hiltViewModel()
    val state = vm.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    val context = LocalContext.current
    val profileState = companyVm.profile.collectAsState()
    val typeOptions = remember(profileState.value.productTypesCsv) {
        profileState.value.productTypesCsv.split(',')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .ifEmpty { listOf("Fertilizer", "Pecticide", "Fungi", "GP", "Other") }
    }
    val unitOptions = remember(profileState.value.unitsCsv) {
        profileState.value.unitsCsv.split(',')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .ifEmpty { listOf("Kg", "Pcs", "L") }
    }
    val lowThreshold = profileState.value.lowStockThreshold

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(horizontal = 8.dp, vertical = 8.dp)) {
            SnackbarHost(hostState = snackbarHostState)
            // Header with Add button
            var showAdd by remember { mutableStateOf(false) }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(text = stringResource(R.string.products_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Button(onClick = { showAdd = true }) { Text(stringResource(R.string.add)) }
            }
            Spacer(Modifier.height(8.dp))
            HorizontalDivider()
            Spacer(Modifier.height(8.dp))

            // Search bar (compact)
            var searchQuery by remember { mutableStateOf("") }
            CompactSearchBar(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = stringResource(R.string.search_by_product),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))

            // Compute filtered list outside LazyColumn (Composable scope)
            val filtered = remember(state.value.products, searchQuery) {
                if (searchQuery.isBlank()) state.value.products else state.value.products.filter { it.name.contains(searchQuery, ignoreCase = true) }
            }

            // List area
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 8.dp)
            ) {
                items(filtered, key = { it.id }) { p ->
                    ProductRow(
                        p = p,
                        typeOptions = typeOptions,
                        unitOptions = unitOptions,
                        onUpdate = { prod, n, t, u, sp, pp, st, g -> vm.update(prod, n, t, u, sp, pp, st, g) },
                        onDelete = { vm.delete(p) },
                        onAdjustStock = { delta -> vm.adjustStock(p.id, delta) },
                        onHistory = {
                            navVm.requestPurchaseHistoryForProduct(p.id)
                            navVm.navigateTo(8)
                        },
                        lowStockThreshold = lowThreshold
                    )
                }
            }

            if (showAdd) {
                AddProductDialog(
                    typeOptions = typeOptions,
                    unitOptions = unitOptions,
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
                    val msg = when (err) {
                        "ERR_PRODUCT_REFERENCED" -> context.getString(R.string.err_product_referenced)
                        else -> err
                    }
                    snackbarHostState.showSnackbar(msg)
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
    unitOptions: List<String>,
    onUpdate: (Product, String, String, String, Double, Double, Double, Double) -> Unit,
    onDelete: () -> Unit,
    onAdjustStock: (Double) -> Unit,
    onHistory: () -> Unit,
    lowStockThreshold: Int
) {
    var showEdit by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val priceFmt = remember {
        NumberFormat.getNumberInstance(Locale.getDefault()).apply {
            minimumFractionDigits = 2
            maximumFractionDigits = 2
        }
    }
    val qtyFmt = remember {
        NumberFormat.getNumberInstance(Locale.getDefault()).apply {
            minimumFractionDigits = 0
            maximumFractionDigits = 0
        }
    }
    Card(
        modifier = Modifier.fillMaxWidth().clickable { showEdit = true },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            // Row 1: Name (start), menu (end)
            var menuExpanded by remember { mutableStateOf(false) }
            val lowStock = p.stockQuantity < lowStockThreshold.toDouble()
            val chipBg = if (lowStock) androidx.compose.ui.graphics.Color(0xFFFFE2E0) else androidx.compose.ui.graphics.Color(0xFFDFF6DD)
            val chipFg = if (lowStock) androidx.compose.ui.graphics.Color(0xFF9A0007) else androidx.compose.ui.graphics.Color(0xFF0B6A0B)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
                Text(text = p.name, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))

                // Right column area (fixed width) with left-aligned content: Stock label + chip
                Box(modifier = Modifier.width(100.dp), contentAlignment = Alignment.CenterStart) {
                    Column(horizontalAlignment = Alignment.Start) {
                        Text(stringResource(R.string.stock_label), style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Start)
                        Spacer(Modifier.height(0.dp))
                        androidx.compose.foundation.layout.Box(
                            modifier = Modifier
                                .clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                                .background(chipBg)
                                .padding(vertical = 4.dp, horizontal = 8.dp)
                        ) {
                            Text(
                                "${qtyFmt.format(p.stockQuantity)} ${p.unit}",
                                color = chipFg,
                                style = MaterialTheme.typography.labelSmall,
                                textAlign = TextAlign.Start
                            )
                        }
                    }
                }
                Box(modifier = Modifier.width(48.dp)) {
                    IconButton(onClick = { menuExpanded = true }) { Icon(Icons.Outlined.MoreVert, contentDescription = stringResource(R.string.more_cd)) }
                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.history)) }, onClick = { menuExpanded = false; onHistory() })
                        DropdownMenuItem(text = { Text(stringResource(R.string.delete)) }, onClick = { menuExpanded = false; confirmDelete = true })
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            // Row 2: Type (left) and GST (right column)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
                Text(
                    text = stringResource(R.string.type_colon, p.type),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f)
                )
                Box(modifier = Modifier.width(100.dp), contentAlignment = Alignment.CenterStart) {
                    Text(
                        text = stringResource(R.string.gst_percent_colon, String.format(Locale.getDefault(), "%.1f", p.gstPercent)),
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Start
                    )
                }
                Spacer(Modifier.width(48.dp))
            }
            Spacer(Modifier.height(2.dp))
            // Row 3: Buy (left) and Sell (right column)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
                Text(
                    text = stringResource(R.string.buy_price_colon, priceFmt.format(p.purchasePrice)),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f)
                )
                Box(modifier = Modifier.width(100.dp), contentAlignment = Alignment.CenterStart) {
                    Text(
                        text = stringResource(R.string.sell_price_colon, priceFmt.format(p.sellingPrice)),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Start
                    )
                }
                Spacer(Modifier.width(48.dp))
            }
        }
    }
    if (showEdit) {
        EditProductDialog(initial = p, typeOptions = typeOptions, unitOptions = unitOptions, onConfirm = { n, t, u, sp, pp, st, g ->
            onUpdate(p, n, t, u, sp, pp, st, g)
            showEdit = false
        }, onDismiss = { showEdit = false })
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.delete_product_title)) },
            text = { Text(stringResource(R.string.delete_product_message, p.name)) },
            confirmButton = {
                TextButton(onClick = { onDelete(); confirmDelete = false }) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }
}

@Composable
private fun EditProductDialog(
    initial: Product,
    typeOptions: List<String>,
    unitOptions: List<String>,
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
        title = { Text(stringResource(R.string.edit_product)) },
        text = {
            Column {
                // 1) Name (full width)
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.name_required)) },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(6.dp))

                // 2) Type and Unit
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    TypePicker(
                        initial = type,
                        label = stringResource(R.string.type_required),
                        modifier = Modifier.weight(1f),
                        onPicked = { picked -> type = picked },
                        options = typeOptions
                    )
                    UnitPicker(
                        initial = unit,
                        label = stringResource(R.string.unit_required),
                        modifier = Modifier.weight(1f),
                        onPicked = { picked -> unit = picked },
                        options = unitOptions
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
                        label = { Text(stringResource(R.string.stock)) },
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
                        label = { Text(stringResource(R.string.gst_percent)) },
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
                        label = { Text(stringResource(R.string.buy_price)) },
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
                        label = { Text(stringResource(R.string.sell_price)) },
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
            }) { Text(stringResource(R.string.save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}

@Composable
private fun CompactSearchBar(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(12.dp)
    val focusRequester = remember { FocusRequester() }
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier = modifier
            .clip(shape)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
            .background(MaterialTheme.colorScheme.surface)
            .heightIn(min = 40.dp)
            .clickable(interactionSource = interaction, indication = null) { focusRequester.requestFocus() }
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Outlined.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Box(modifier = Modifier.weight(1f)) {
            if (value.isEmpty()) {
                Text(placeholder, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier.fillMaxWidth().focusRequester(focusRequester)
            )
        }
    }
}

@Composable
private fun AddProductDialog(
    typeOptions: List<String>,
    unitOptions: List<String>,
    onConfirm: (String, String, String, Double, Double, Double, Double) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    var type by remember(typeOptions) { mutableStateOf(typeOptions.firstOrNull() ?: "Fertilizer") }
    var unit by remember(unitOptions) { mutableStateOf(unitOptions.firstOrNull() ?: "Pcs") }
    var stock by remember { mutableStateOf("0") }
    var gst by remember { mutableStateOf("") }
    var purchasePrice by remember { mutableStateOf("0") }
    var sellingPrice by remember { mutableStateOf("0") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.add_product)) },
        text = {
            Column {
                // 1) Name (full width)
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.name_required)) },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(6.dp))

                // 2) Type and Unit
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    TypePicker(
                        initial = type,
                        label = stringResource(R.string.type_required),
                        modifier = Modifier.weight(1f),
                        onPicked = { picked -> type = picked },
                        options = typeOptions
                    )
                    UnitPicker(
                        initial = unit,
                        label = stringResource(R.string.unit_required),
                        modifier = Modifier.weight(1f),
                        onPicked = { picked -> unit = picked },
                        options = unitOptions
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
                        label = { Text(stringResource(R.string.stock)) },
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
                        label = { Text(stringResource(R.string.gst_percent)) },
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
                        label = { Text(stringResource(R.string.buy_price)) },
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
                        label = { Text(stringResource(R.string.sell_price)) },
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
            }) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}
