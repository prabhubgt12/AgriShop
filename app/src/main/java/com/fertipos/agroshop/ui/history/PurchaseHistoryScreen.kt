package com.fertipos.agroshop.ui.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.navigation.compose.hiltViewModel
import com.fertipos.agroshop.ui.common.DateField
import com.fertipos.agroshop.ui.history.PurchaseHistoryViewModel.Row
import com.fertipos.agroshop.ui.screens.AppNavViewModel
import com.fertipos.agroshop.util.CurrencyFormatter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

@Composable
fun PurchaseHistoryScreen(navVm: AppNavViewModel) {
    val vm: PurchaseHistoryViewModel = hiltViewModel()
    val pendingFilter by navVm.pendingPurchaseHistoryProductId.collectAsState()

    // Ensure we don't retain previous product-specific filter when entering normally
    LaunchedEffect(Unit) {
        if (pendingFilter == null) {
            vm.setProductFilter(null)
        }
    }

    LaunchedEffect(pendingFilter) {
        if (pendingFilter != null) {
            vm.setProductFilter(pendingFilter)
            navVm.clearPendingPurchaseHistoryProduct()
        }
    }
    val list by vm.listState.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()

    var fabDx by remember { mutableStateOf(0.dp) }
    var fabDy by remember { mutableStateOf(0.dp) }
    val density = LocalDensity.current
    val config = LocalConfiguration.current
    val fabSize = 56.dp
    val margin = 16.dp
    // Content size; fallback to screen size if not yet measured
    var contentW by remember { mutableStateOf(config.screenWidthDp.dp) }
    var contentH by remember { mutableStateOf(config.screenHeightDp.dp) }
    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = { navVm.navigateTo(7) },
                modifier = Modifier
                    .offset(x = fabDx, y = fabDy)
                    .pointerInput(Unit) {
                        detectDragGestures(onDrag = { _, dragAmount ->
                            val dx = with(density) { dragAmount.x.toDp() }
                            val dy = with(density) { dragAmount.y.toDp() }
                            val travelX = (contentW - fabSize - margin * 2).coerceAtLeast(0.dp)
                            val travelY = (contentH - fabSize - margin * 2).coerceAtLeast(0.dp)
                            // Relative to bottom-end base: negative offsets up to -travel, 0 is base
                            fabDx = (fabDx + dx).coerceIn(-travelX, 0.dp)
                            fabDy = (fabDy + dy).coerceIn(-travelY, 0.dp)
                        })
                    }
            ) {
                Icon(imageVector = Icons.Filled.Add, contentDescription = "New Purchase")
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 8.dp, vertical = 8.dp)
                .onGloballyPositioned { coords ->
                    contentW = with(density) { coords.size.width.toDp() }
                    contentH = with(density) { coords.size.height.toDp() }
                }
        ) {
                var showFilters by remember { mutableStateOf(false) }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        text = if (pendingFilter != null) "Purchase History (Product #$pendingFilter)" else "Purchases",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    TextButton(onClick = { showFilters = !showFilters }) { Text(if (showFilters) "Hide filters" else "Filters") }
                }

                // Search bar (supplier name)
                var searchQuery by remember { mutableStateOf("") }
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Search by supplier name") }
                )

            // Collapsible date filters
            var fromMillis by remember { mutableStateOf<Long?>(null) }
            var toMillis by remember { mutableStateOf<Long?>(null) }
            if (showFilters) {
                Spacer(Modifier.height(6.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DateField(label = "From", value = fromMillis, onChange = { v -> fromMillis = v }, modifier = Modifier.weight(1f))
                    DateField(label = "To", value = toMillis, onChange = { v -> toMillis = v }, modifier = Modifier.weight(1f))
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = { vm.setDateRange(fromMillis, toMillis) }) { Text("Apply") }
                    TextButton(onClick = { fromMillis = null; toMillis = null; vm.setDateRange(null, null) }) { Text("Clear") }
                }
            }

            Spacer(Modifier.height(8.dp))

            // Apply supplier search filtering
            val filtered = remember(list, searchQuery) {
                if (searchQuery.isBlank()) list else list.filter { r ->
                    when (r) {
                        is Row.Purchase -> (r.row.supplierName ?: "").contains(searchQuery, ignoreCase = true)
                        is Row.ProductHistory -> (r.row.supplierName ?: "").contains(searchQuery, ignoreCase = true)
                    }
                }
            }

            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(filtered) { row ->
                    when (row) {
                        is Row.Purchase -> {
                            val item = row.row
                            var confirmDelete by remember { mutableStateOf(false) }
                            var menuExpanded by remember { mutableStateOf(false) }
                            var showReadonly by remember { mutableStateOf(false) }
                            var full by remember { mutableStateOf<PurchaseHistoryViewModel.FullPurchase?>(null) }

                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        scope.launch {
                                            full = vm.getFullPurchaseOnce(item.id)
                                            showReadonly = true
                                        }
                                    }
                            ) {
                                Column(Modifier.padding(12.dp)) {
                                    val dfCard = remember { SimpleDateFormat("dd MMM yy", Locale.getDefault()) }
                                    // Row 1: Supplier left, Date right small
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text(text = item.supplierName ?: "Unknown Supplier", fontWeight = FontWeight.SemiBold)
                                        Text(text = "#${item.id}", style = MaterialTheme.typography.labelSmall)
                                        val dateStr = remember(item.date) { dfCard.format(Date(item.date)).uppercase(Locale.getDefault()) }
                                        Text(text = dateStr, style = MaterialTheme.typography.labelSmall)
                                    }
                                    Spacer(Modifier.height(6.dp))
                                    // Row 2: Totals on left, menu on right
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                            Column {
                                                Text("GST", style = MaterialTheme.typography.labelSmall)
                                                Text(CurrencyFormatter.formatInr(item.gstAmount), fontWeight = FontWeight.SemiBold)
                                            }
                                            Column {
                                                Text("Total", style = MaterialTheme.typography.labelSmall)
                                                Text(CurrencyFormatter.formatInr(item.total), fontWeight = FontWeight.SemiBold)
                                            }
                                        }
                                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            IconButton(onClick = { menuExpanded = true }) { Icon(Icons.Outlined.MoreVert, contentDescription = "More") }
                                            DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                                                DropdownMenuItem(text = { Text("Edit") }, onClick = {
                                                    menuExpanded = false
                                                    navVm.requestEditPurchase(item.id)
                                                    navVm.navigateTo(7)
                                                })
                                                DropdownMenuItem(text = { Text("Delete") }, onClick = {
                                                    menuExpanded = false
                                                    confirmDelete = true
                                                })
                                            }
                                        }
                                    }
                                }
                            }

                            // Read-only dialog
                            if (showReadonly && full != null) {
                                val dfCard = remember { SimpleDateFormat("dd MMM yy", Locale.getDefault()) }
                                PurchaseReadonlyDialog(
                                    onDismiss = { showReadonly = false },
                                    header = "#${full!!.id} • ${full!!.supplierName ?: "Unknown Supplier"}",
                                    dateText = dfCard.format(Date(full!!.date)).uppercase(Locale.getDefault()),
                                    subtotal = full!!.subtotal,
                                    gst = full!!.gstAmount,
                                    total = full!!.total,
                                    notes = full!!.notes,
                                    items = full!!.items
                                )
                            }

                            // Delete confirmation
                            if (confirmDelete) {
                                AlertDialog(
                                    onDismissRequest = { confirmDelete = false },
                                    title = { Text("Delete Purchase?") },
                                    text = { Text("Are you sure you want to delete #${item.id}? This cannot be undone.") },
                                    confirmButton = {
                                        TextButton(onClick = {
                                            scope.launch { vm.deletePurchase(item.id) }
                                            confirmDelete = false
                                        }) { Text("Delete") }
                                    },
                                    dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } }
                                )
                            }
                        }
                        is Row.ProductHistory -> {
                            val item = row.row
                            var showReadonly by remember { mutableStateOf(false) }
                            var full by remember { mutableStateOf<PurchaseHistoryViewModel.FullPurchase?>(null) }
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        scope.launch {
                                            full = vm.getFullPurchaseOnce(item.purchaseId)
                                            showReadonly = true
                                        }
                                    }
                            ) {
                                Column(Modifier.padding(12.dp)) {
                                    val dfCard = remember { SimpleDateFormat("dd MMM yy", Locale.getDefault()) }
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text(text = item.supplierName ?: "Unknown Supplier", fontWeight = FontWeight.SemiBold)
                                        Text(text = "#${item.purchaseId}", style = MaterialTheme.typography.labelSmall)
                                        val dateStr = remember(item.date) { dfCard.format(Date(item.date)).uppercase(Locale.getDefault()) }
                                        Text(text = dateStr, style = MaterialTheme.typography.labelSmall)
                                    }
                                    Spacer(Modifier.height(6.dp))
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Column {
                                            Text("Qty @ Price", style = MaterialTheme.typography.labelSmall)
                                            Text("${item.quantity.toStringAsFixed(2)} × ${CurrencyFormatter.formatInr(item.unitPrice)}", fontWeight = FontWeight.SemiBold)
                                        }
                                        Column {
                                            Text("Total", style = MaterialTheme.typography.labelSmall)
                                            Text(CurrencyFormatter.formatInr(item.lineTotal), fontWeight = FontWeight.SemiBold)
                                        }
                                    }
                                }
                            }
                            if (showReadonly && full != null) {
                                val dfCard = remember { SimpleDateFormat("dd MMM yy", Locale.getDefault()) }
                                PurchaseReadonlyDialog(
                                    onDismiss = { showReadonly = false },
                                    header = "#${full!!.id} • ${full!!.supplierName ?: "Unknown Supplier"}",
                                    dateText = dfCard.format(Date(full!!.date)).uppercase(Locale.getDefault()),
                                    subtotal = full!!.subtotal,
                                    gst = full!!.gstAmount,
                                    total = full!!.total,
                                    notes = full!!.notes,
                                    items = full!!.items
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PurchaseReadonlyDialog(
    onDismiss: () -> Unit,
    header: String,
    dateText: String,
    subtotal: Double,
    gst: Double,
    total: Double,
    notes: String?,
    items: List<PurchaseHistoryViewModel.ItemWithProductName>
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(header, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) },
        text = {
            Column {
                Text(dateText, style = MaterialTheme.typography.labelSmall)
                Spacer(Modifier.height(8.dp))
                if (!notes.isNullOrBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text("Notes", style = MaterialTheme.typography.labelSmall)
                    Text(notes)
                }
                Spacer(Modifier.height(10.dp))
                Column(modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp)) {
                    items.forEach { d ->
                        val pname = d.productName ?: "Item ${d.item.productId}"
                        val qty = d.item.quantity
                        val price = d.item.unitPrice
                        val line = d.item.lineTotal
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(pname)
                                Text("x ${qty.toStringAsFixed(2)} @ ${CurrencyFormatter.formatInr(price)}", style = MaterialTheme.typography.labelSmall)
                            }
                            Text(CurrencyFormatter.formatInr(line), fontWeight = FontWeight.SemiBold)
                        }
                        Spacer(Modifier.height(6.dp))
                    }
                }
                // Totals at the end, right aligned
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Subtotal: ${CurrencyFormatter.formatInr(subtotal)}",
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.End
                )
                Text(
                    text = "GST: ${CurrencyFormatter.formatInr(gst)}",
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.End
                )
                Text(
                    text = "Total: ${CurrencyFormatter.formatInr(total)}",
                    modifier = Modifier.fillMaxWidth(),
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.End
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}

private fun Double.toStringAsFixed(digits: Int): String = String.format(java.util.Locale.getDefault(), "% .${digits}f", this).trimStart()

