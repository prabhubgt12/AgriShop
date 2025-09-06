package com.fertipos.agroshop.ui.history

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Print
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.navigation.compose.hiltViewModel
import com.fertipos.agroshop.ui.common.DateField
import com.fertipos.agroshop.ui.screens.AppNavViewModel
import com.fertipos.agroshop.ui.settings.CompanyProfileViewModel
import com.fertipos.agroshop.util.InvoicePdfGenerator
import com.fertipos.agroshop.util.CurrencyFormatter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalConfiguration

@Composable
fun InvoiceHistoryScreen(navVm: AppNavViewModel) {
    val vm: InvoiceHistoryViewModel = hiltViewModel()
    val list by vm.listState.collectAsState()
    val profVm: CompanyProfileViewModel = hiltViewModel()
    val profile by profVm.profile.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Offsets are relative to bottom-end base position (0 = bottom-right). Negative = move left/up.
    var fabDx by remember { mutableStateOf(0.dp) }
    var fabDy by remember { mutableStateOf(0.dp) }
    val density = LocalDensity.current
    val config = LocalConfiguration.current
    val fabSize = 56.dp
    val margin = 16.dp
    val travelX = (config.screenWidthDp.dp - fabSize - margin * 2)
    val travelY = (config.screenHeightDp.dp - fabSize - margin * 2)
    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = { navVm.navigateTo(3) },
                modifier = Modifier
                    .offset(x = fabDx, y = fabDy)
                    .pointerInput(Unit) {
                        detectDragGestures(onDrag = { _, dragAmount ->
                            val dx = with(density) { dragAmount.x.toDp() }
                            val dy = with(density) { dragAmount.y.toDp() }
                            // Clamp within [-travel, 0] so FAB cannot go beyond screen bounds
                            fabDx = (fabDx + dx).coerceIn(-travelX, 0.dp)
                            fabDy = (fabDy + dy).coerceIn(-travelY, 0.dp)
                        })
                    }
            ) {
                Icon(imageVector = Icons.Filled.Add, contentDescription = "New Bill")
            }
        }
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding).padding(horizontal = 8.dp, vertical = 8.dp)) {
            var showFilters by remember { mutableStateOf(false) }
            // Header row: Title + Filters toggle
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Invoices", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                TextButton(onClick = { showFilters = !showFilters }) { Text(if (showFilters) "Hide filters" else "Filters") }
            }

            // Search bar
            var searchQuery by remember { mutableStateOf("") }
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Search by customer name") }
            )

            // Collapsible date filters (hidden by default)
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

            // Apply search filtering on UI list
            val filtered = remember(list, searchQuery) {
                if (searchQuery.isBlank()) list else list.filter { it.customerName.contains(searchQuery, ignoreCase = true) }
            }

            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(filtered) { row ->
                    var confirmDelete by remember { mutableStateOf(false) }
                    var menuExpanded by remember { mutableStateOf(false) }
                    var showReadonly by remember { mutableStateOf(false) }
                    var readonlyItems by remember { mutableStateOf(emptyList<InvoicePdfGenerator.ItemWithProduct>()) }

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                // Load items and show read-only dialog
                                scope.launch {
                                    val items = vm.getItemRowsOnce(row.invoice.id)
                                    readonlyItems = items.map { InvoicePdfGenerator.ItemWithProduct(it.item, it.product) }
                                    showReadonly = true
                                }
                            }
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            val dfCard = remember { SimpleDateFormat("dd MMM yy", Locale.getDefault()) }
                            val balance = (row.invoice.total - row.invoice.paid).coerceAtLeast(0.0)

                            // Row 1: Customer (start), Invoice # (center), Date (end)
                            androidx.compose.foundation.layout.Box(modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    text = row.customerName,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.align(androidx.compose.ui.Alignment.CenterStart),
                                    maxLines = 1
                                )
                                Text(
                                    text = "#${row.invoice.id}",
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.align(androidx.compose.ui.Alignment.Center)
                                )
                                val dateStr = remember(row.invoice.date) { dfCard.format(Date(row.invoice.date)).uppercase(Locale.getDefault()) }
                                Text(
                                    text = dateStr,
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.align(androidx.compose.ui.Alignment.CenterEnd)
                                )
                            }

                            Spacer(Modifier.height(6.dp))

                            // Row 2: Total + Balance on the left, Icons (Print, More) on the right
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                    Column {
                                        Text("Total", style = MaterialTheme.typography.labelSmall)
                                        Text(CurrencyFormatter.formatInr(row.invoice.total), fontWeight = FontWeight.SemiBold)
                                    }
                                    Column {
                                        Text("Balance", style = MaterialTheme.typography.labelSmall)
                                        Text(CurrencyFormatter.formatInr(balance), fontWeight = FontWeight.SemiBold)
                                    }
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    IconButton(onClick = {
                                        scope.launch {
                                            val items = vm.getItemRowsOnce(row.invoice.id)
                                            val uri: Uri = InvoicePdfGenerator.generate(
                                                context = context,
                                                authority = context.packageName + ".fileprovider",
                                                invoice = row.invoice,
                                                customerName = row.customerName,
                                                company = profile,
                                                items = items.map { InvoicePdfGenerator.ItemWithProduct(it.item, it.product) },
                                                paid = row.invoice.paid,
                                                balance = balance
                                            )
                                            val intent = Intent(Intent.ACTION_SEND).apply {
                                                type = "application/pdf"
                                                putExtra(Intent.EXTRA_STREAM, uri)
                                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                            }
                                            context.startActivity(Intent.createChooser(intent, "Print"))
                                        }
                                    }) { Icon(Icons.Outlined.Print, contentDescription = "Print") }
                                    IconButton(onClick = { menuExpanded = true }) { Icon(Icons.Outlined.MoreVert, contentDescription = "More") }
                                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                                        DropdownMenuItem(text = { Text("Edit") }, onClick = {
                                            menuExpanded = false
                                            navVm.requestEditInvoice(row.invoice.id)
                                            navVm.navigateTo(3)
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
                    // Read-only popup dialog
                    if (showReadonly) {
                        val dfCard = remember { SimpleDateFormat("dd MMM yy", Locale.getDefault()) }
                        ReadonlyInvoiceDialog(
                            onDismiss = { showReadonly = false },
                            header = "#${row.invoice.id} • ${row.customerName}",
                            dateText = dfCard.format(Date(row.invoice.date)).uppercase(Locale.getDefault()),
                            subtotal = row.invoice.subtotal,
                            gst = row.invoice.gstAmount,
                            total = row.invoice.total,
                            paid = row.invoice.paid,
                            items = readonlyItems
                        )
                    }

                    // Delete confirmation dialog
                    if (confirmDelete) {
                        AlertDialog(
                            onDismissRequest = { confirmDelete = false },
                            title = { Text("Delete invoice?") },
                            text = { Text("This action cannot be undone.") },
                            confirmButton = {
                                TextButton(onClick = {
                                    confirmDelete = false
                                    vm.deleteInvoice(row.invoice.id)
                                }) { Text("Delete") }
                            },
                            dismissButton = {
                                TextButton(onClick = { confirmDelete = false }) { Text("Cancel") }
                            }
                        )
                    }
                }
            }
        }
    }
}

// Row-item composable removed in favor of card layout above

@Composable
private fun rememberDateFormat(): SimpleDateFormat {
    // Simple helper to avoid creating format repeatedly in preview
    return SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
}

@Composable
private fun ReadonlyInvoiceDialog(
    onDismiss: () -> Unit,
    header: String,
    dateText: String,
    subtotal: Double,
    gst: Double,
    total: Double,
    paid: Double,
    items: List<InvoicePdfGenerator.ItemWithProduct>
) {
    val balance = (total - paid).coerceAtLeast(0.0)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(header, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) },
        text = {
            Column {
                // Date small
                Text(dateText, style = MaterialTheme.typography.labelSmall)
                Spacer(Modifier.height(8.dp))
                Spacer(Modifier.height(10.dp))
                // Items list with constrained height
                Column(modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp)) {
                    items.forEach { itw ->
                        val pname = itw.product?.name ?: "Item ${itw.item.productId}"
                        val qty = itw.item.quantity
                        val price = itw.item.unitPrice
                        val lineTotal = itw.item.lineTotal
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(pname)
                                Text("x ${qty} @ ${CurrencyFormatter.formatInr(price)}", style = MaterialTheme.typography.labelSmall)
                            }
                            Text(CurrencyFormatter.formatInr(lineTotal), fontWeight = FontWeight.SemiBold)
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
                Text(
                    text = "Paid: ${CurrencyFormatter.formatInr(paid)}",
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.End
                )
                Text(
                    text = "Balance: ${CurrencyFormatter.formatInr(balance)}",
                    modifier = Modifier.fillMaxWidth(),
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.End
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}
