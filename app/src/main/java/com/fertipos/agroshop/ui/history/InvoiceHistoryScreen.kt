package com.fertipos.agroshop.ui.history

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.fertipos.agroshop.ui.common.DateField
import com.fertipos.agroshop.ui.settings.CompanyProfileViewModel
import com.fertipos.agroshop.util.InvoicePdfGenerator
import com.fertipos.agroshop.ui.screens.AppNavViewModel
import androidx.hilt.navigation.compose.hiltViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

@Composable
fun InvoiceHistoryScreen(navVm: AppNavViewModel) {
    val vm: InvoiceHistoryViewModel = hiltViewModel()
    val list by vm.listState.collectAsState()
    val profVm: CompanyProfileViewModel = hiltViewModel()
    val profile by profVm.profile.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            Text("Invoices", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            var fromMillis by remember { mutableStateOf<Long?>(null) }
            var toMillis by remember { mutableStateOf<Long?>(null) }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DateField(label = "From", value = fromMillis, onChange = { v -> fromMillis = v }, modifier = Modifier.weight(1f))
                DateField(label = "To", value = toMillis, onChange = { v -> toMillis = v }, modifier = Modifier.weight(1f))
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Spacer(Modifier.weight(1f))
                Button(onClick = { vm.setDateRange(fromMillis, toMillis) }) { Text("Apply") }
                TextButton(onClick = { fromMillis = null; toMillis = null; vm.setDateRange(null, null) }) { Text("Clear") }
            }
            Spacer(Modifier.height(8.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(list) { row ->
                    var confirmDelete by remember { mutableStateOf(false) }
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp)) {
                            val df = rememberDateFormat()
                            Text("#${row.invoice.id} • ${row.customerName}", fontWeight = FontWeight.SemiBold)
                            Text(df.format(Date(row.invoice.date)))
                            Text("Total: ${row.invoice.total}")
                            Spacer(Modifier.height(8.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(onClick = {
                                    navVm.requestEditInvoice(row.invoice.id)
                                    navVm.navigateTo(3)
                                }) { Text("Edit") }
                                Button(onClick = {
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
                                            balance = (row.invoice.total - row.invoice.paid).coerceAtLeast(0.0)
                                        )
                                        val intent = Intent(Intent.ACTION_SEND).apply {
                                            type = "application/pdf"
                                            putExtra(Intent.EXTRA_STREAM, uri)
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        context.startActivity(Intent.createChooser(intent, "Print"))
                                    }
                                }) { Text("Print") }
                                
                                Button(onClick = { confirmDelete = true }) { Text("Delete") }
                            }
                            if (confirmDelete) {
                                AlertDialog(
                                    onDismissRequest = { confirmDelete = false },
                                    title = { Text("Delete Invoice?") },
                                    text = { Text("Are you sure you want to delete #${row.invoice.id}? This cannot be undone.") },
                                    confirmButton = {
                                        TextButton(onClick = { vm.deleteInvoice(row.invoice.id); confirmDelete = false }) { Text("Delete") }
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
    }
}

// Row-item composable removed in favor of card layout above

@Composable
private fun rememberDateFormat(): SimpleDateFormat {
    // Simple helper to avoid creating format repeatedly in preview
    return SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
}
