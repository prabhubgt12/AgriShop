package com.fertipos.agroshop.ui.history

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
import androidx.compose.material3.Card
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.width
import androidx.hilt.navigation.compose.hiltViewModel
import com.fertipos.agroshop.ui.history.PurchaseHistoryViewModel.Row
import com.fertipos.agroshop.ui.screens.AppNavViewModel
import com.fertipos.agroshop.ui.common.CustomerPicker
import com.fertipos.agroshop.ui.common.ProductPicker
import com.fertipos.agroshop.ui.common.DateField
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

@Composable
fun PurchaseHistoryScreen(navVm: AppNavViewModel) {
    val vm: PurchaseHistoryViewModel = hiltViewModel()
    val pendingFilter by navVm.pendingPurchaseHistoryProductId.collectAsState()

    LaunchedEffect(pendingFilter) {
        if (pendingFilter != null) {
            vm.setProductFilter(pendingFilter)
            navVm.clearPendingPurchaseHistoryProduct()
        }
    }
    val list by vm.listState.collectAsState(initial = emptyList())
    val df = remember { SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()) }
    val scope = rememberCoroutineScope()

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    text = if (pendingFilter != null) "Purchase History (Product #$pendingFilter)" else "Purchases",
                    style = MaterialTheme.typography.titleMedium
                )
                Row {
                    TextButton(onClick = { vm.setProductFilter(null) }) { Text("All") }
                    Spacer(Modifier.height(0.dp))
                }
            }
            Spacer(Modifier.height(8.dp))
            var fromMillis by remember { mutableStateOf<Long?>(null) }
            var toMillis by remember { mutableStateOf<Long?>(null) }
            // Date pickers row
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DateField(label = "From", value = fromMillis, onChange = { v -> fromMillis = v }, modifier = Modifier.weight(1f))
                DateField(label = "To", value = toMillis, onChange = { v -> toMillis = v }, modifier = Modifier.weight(1f))
            }
            // Actions row keeps buttons separate so fields have space to show date
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Spacer(Modifier.weight(1f))
                Button(onClick = { vm.setDateRange(fromMillis, toMillis) }) { Text("Apply") }
                TextButton(onClick = { fromMillis = null; toMillis = null; vm.setDateRange(null, null) }) { Text("Clear") }
            }
            Spacer(Modifier.height(8.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(list) { row ->
                    when (row) {
                        is Row.Purchase -> {
                            val item = row.row
                            var confirmDelete by remember { mutableStateOf(false) }
                            // Editing is handled in Purchase screen
                            var showView by remember { mutableStateOf(false) }
                            var full by remember { mutableStateOf<PurchaseHistoryViewModel.FullPurchase?>(null) }
                            Card(modifier = Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(12.dp)) {
                                    Text("#${item.id} • ${item.supplierName ?: "Unknown Supplier"}", fontWeight = FontWeight.SemiBold)
                                    Text(df.format(Date(item.date)))
                                    Text("Total: ${item.total.toStringAsFixed(2)}")
                                    Spacer(Modifier.height(8.dp))
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Button(onClick = {
                                            scope.launch {
                                                full = vm.getFullPurchaseOnce(item.id)
                                                showView = true
                                            }
                                        }) { Text("View") }
                                        Button(onClick = {
                                            navVm.requestEditPurchase(item.id)
                                            navVm.navigateTo(7)
                                        }) { Text("Edit") }
                                        Spacer(Modifier.width(8.dp))
                                        Button(onClick = { confirmDelete = true }) { Text("Delete") }
                                    }
                                }
                            }
                            if (showView && full != null) {
                                AlertDialog(
                                    onDismissRequest = { showView = false },
                                    title = { Text("Purchase #${full!!.id}") },
                                    text = {
                                        Column {
                                            Text("Supplier: ${full!!.supplierName ?: "Unknown"}")
                                            Text("Date: ${df.format(Date(full!!.date))}")
                                            Spacer(Modifier.height(6.dp))
                                            Text("Subtotal: ${full!!.subtotal.toStringAsFixed(2)}")
                                            Text("GST: ${full!!.gstAmount.toStringAsFixed(2)}")
                                            Text("Total: ${full!!.total.toStringAsFixed(2)}")
                                            if (!full!!.notes.isNullOrBlank()) {
                                                Spacer(Modifier.height(6.dp))
                                                Text("Notes: ${full!!.notes}")
                                            }
                                            Spacer(Modifier.height(8.dp))
                                            Text("Items:")
                                            full!!.items.forEach { d ->
                                                Text("• ${d.productName ?: "Unknown"}: ${d.item.quantity.toStringAsFixed(2)} × ${d.item.unitPrice.toStringAsFixed(2)} = ${d.item.lineTotal.toStringAsFixed(2)}")
                                            }
                                        }
                                    },
                                    confirmButton = { TextButton(onClick = { showView = false }) { Text("Close") } }
                                )
                            }
                            // Inline edit dialog removed; edits now open Purchase screen with data
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
                            Card(modifier = Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(12.dp)) {
                                    Text("#${item.purchaseId} • ${item.supplierName ?: "Unknown Supplier"}", fontWeight = FontWeight.SemiBold)
                                    Text(df.format(Date(item.date)))
                                    Text("Qty: ${item.quantity.toStringAsFixed(2)} @ ${item.unitPrice.toStringAsFixed(2)} • Line: ${item.lineTotal.toStringAsFixed(2)}")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun Double.toStringAsFixed(digits: Int): String = String.format(java.util.Locale.getDefault(), "% .${digits}f", this).trimStart()

