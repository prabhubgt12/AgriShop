package com.fertipos.agroshop.ui.reports

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch
import android.content.Intent
import androidx.core.content.ContextCompat
import com.fertipos.agroshop.util.ReportExporter
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ReportsScreen() {
    val vm: ReportsViewModel = hiltViewModel()
    val state = vm.state.collectAsState()

    val currency = NumberFormat.getCurrencyInstance()
    val dateFmt = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())

    Surface(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Column(modifier = Modifier.fillMaxSize()) {
            Text(text = "Reports", fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            // Customer filter
            var expanded by remember { mutableStateOf(false) }
            val customers = state.value.customers
            val selectedId = state.value.selectedCustomerId
            val selectedName = customers.firstOrNull { it.id == selectedId }?.name ?: "Select customer"
            val context = LocalContext.current
            val scope = rememberCoroutineScope()

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth().padding(12.dp)) {
                    Text(text = "Customer", fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(6.dp))
                    androidx.compose.foundation.layout.Box(Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = selectedName,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Pick customer") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        // Click overlay to open menu
                        androidx.compose.foundation.layout.Box(
                            modifier = Modifier
                                .matchParentSize()
                                .padding(0.dp)
                                .let { it }
                        )
                        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                            customers.forEach { c ->
                                DropdownMenuItem(
                                    text = { Text(c.name) },
                                    onClick = {
                                        expanded = false
                                        vm.setSelectedCustomer(c.id)
                                    }
                                )
                            }
                        }
                    }
                    // Simple toggle to open menu
                    Spacer(Modifier.height(6.dp))
                    androidx.compose.material3.Button(onClick = { expanded = true }) { Text("Select") }
                }
            }

            Spacer(Modifier.height(12.dp))
            // Export buttons
            Row(Modifier.fillMaxWidth()) {
                val canExportSelected = (selectedId != null && state.value.invoices.isNotEmpty())
                Button(onClick = {
                    if (selectedId != null) {
                        scope.launch {
                            val (name, rows) = vm.getExportRowsForCustomer(selectedId)
                            val uri = ReportExporter.exportCustomerCsv(
                                context,
                                context.packageName + ".fileprovider",
                                name,
                                rows
                            )
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/csv"
                                putExtra(Intent.EXTRA_STREAM, uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            ContextCompat.startActivity(context, Intent.createChooser(intent, "Share CSV"), null)
                        }
                    }
                }, enabled = canExportSelected) { Text("Export Selected") }

                Spacer(Modifier.width(12.dp))

                Button(onClick = {
                    scope.launch {
                        val rows = vm.getExportRowsAll()
                        val uri = ReportExporter.exportAllCsv(
                            context,
                            context.packageName + ".fileprovider",
                            rows
                        )
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/csv"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        ContextCompat.startActivity(context, Intent.createChooser(intent, "Share CSV"), null)
                    }
                }) { Text("Export All") }
            }

            Spacer(Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(Modifier.height(8.dp))

            // Invoices for selected customer
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(state.value.invoices, key = { it.id }) { inv ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                        Text("#${inv.id}", modifier = Modifier.weight(0.3f))
                        Text(dateFmt.format(Date(inv.date)), modifier = Modifier.weight(1f))
                        Text(currency.format(inv.total), modifier = Modifier.weight(0.6f), fontWeight = FontWeight.SemiBold)
                    }
                    HorizontalDivider()
                }

                item {
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                        Text("Subtotal", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1.3f))
                        Text(currency.format(state.value.subtotal), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
