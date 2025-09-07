package com.fertipos.agroshop.ui.reports

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.People
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
import androidx.compose.ui.text.style.TextAlign
import kotlinx.coroutines.launch
import android.content.Intent
import androidx.core.content.ContextCompat
import com.fertipos.agroshop.util.ReportExporter
import com.fertipos.agroshop.util.CurrencyFormatter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.Calendar
import com.fertipos.agroshop.ui.common.DateField
import android.widget.Toast

@Composable
fun ReportsScreen() {
    val vm: ReportsViewModel = hiltViewModel()
    val state = vm.state.collectAsState()
    // Monetization state for remove-ads
    val monetVm: com.fertipos.agroshop.billing.MonetizationViewModel = hiltViewModel()
    val hasRemoveAds by monetVm.hasRemoveAds.collectAsState()

    val currency = CurrencyFormatter.inr
    val dateFmt = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 16.dp)) {
            Text(text = "Reports", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
            Spacer(Modifier.height(16.dp))
            var section by remember { mutableStateOf("home") } // home | pl | customer
            if (section == "home") {
                // Home tiles
                Row(
                    Modifier.fillMaxWidth(),
                ) {
                    androidx.compose.material3.Card(
                        modifier = Modifier
                            .weight(1f)
                            .height(100.dp)
                            .clickable { section = "pl" }
                            .padding(end = 6.dp)
                    ) {
                        Column(Modifier.fillMaxSize().padding(12.dp)) {
                            Icon(Icons.Filled.Assessment, contentDescription = null)
                            Spacer(Modifier.height(8.dp))
                            Text("Product P & L", fontWeight = FontWeight.SemiBold)
                        }
                    }
                    androidx.compose.material3.Card(
                        modifier = Modifier
                            .weight(1f)
                            .height(100.dp)
                            .clickable { section = "customer" }
                            .padding(start = 6.dp)
                    ) {
                        Column(Modifier.fillMaxSize().padding(12.dp)) {
                            Icon(Icons.Filled.People, contentDescription = null)
                            Spacer(Modifier.height(8.dp))
                            Text("Customer Reports", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                return@Column
            } else {
                // Back button
                Row(Modifier.fillMaxWidth()) {
                    Button(onClick = { section = "home" }) { Text("Back") }
                }
                Spacer(Modifier.height(8.dp))
            }
            // Profit & Loss view
            val plScope = rememberCoroutineScope()
            val context = LocalContext.current
            var fromMillis by remember { mutableStateOf<Long?>(null) }
            var toMillis by remember { mutableStateOf<Long?>(null) }
            var plError by remember { mutableStateOf<String?>(null) }
            var plResult by remember { mutableStateOf<ReportsViewModel.PLResult?>(null) }
            //
            var costingExpanded by remember { mutableStateOf(false) }
            var costingMethod by remember { mutableStateOf(ReportsViewModel.CostingMethod.FIFO) }

            if (section == "pl") Card(Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth().padding(12.dp).verticalScroll(rememberScrollState())) {
                    Text(text = "Profit & Loss", fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(6.dp))
                    // Quick presets (fit 3 in one row)
                    Row(Modifier.fillMaxWidth()) {
                        fun setRange(calStart: Calendar, calEnd: Calendar) {
                            fromMillis = calStart.timeInMillis
                            toMillis = calEnd.timeInMillis
                        }
                        Button(
                            onClick = {
                            val c = Calendar.getInstance()
                            val start = (c.clone() as Calendar).apply { set(Calendar.HOUR_OF_DAY,0); set(Calendar.MINUTE,0); set(Calendar.SECOND,0); set(Calendar.MILLISECOND,0) }
                            val end = (c.clone() as Calendar).apply { set(Calendar.HOUR_OF_DAY,23); set(Calendar.MINUTE,59); set(Calendar.SECOND,59); set(Calendar.MILLISECOND,999) }
                            setRange(start, end)
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            modifier = Modifier.weight(1f)
                        ) { Text("Today") }
                        Spacer(Modifier.width(6.dp))
                        Button(
                            onClick = {
                            val c = Calendar.getInstance()
                            val start = (c.clone() as Calendar).apply { set(Calendar.DAY_OF_WEEK, firstDayOfWeek); set(Calendar.HOUR_OF_DAY,0); set(Calendar.MINUTE,0); set(Calendar.SECOND,0); set(Calendar.MILLISECOND,0) }
                            val end = (c.clone() as Calendar).apply { set(Calendar.DAY_OF_WEEK, firstDayOfWeek + 6); set(Calendar.HOUR_OF_DAY,23); set(Calendar.MINUTE,59); set(Calendar.SECOND,59); set(Calendar.MILLISECOND,999) }
                            setRange(start, end)
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            modifier = Modifier.weight(1f)
                        ) { Text("Week") }
                        Spacer(Modifier.width(6.dp))
                        Button(
                            onClick = {
                            val c = Calendar.getInstance()
                            val start = (c.clone() as Calendar).apply { set(Calendar.DAY_OF_MONTH,1); set(Calendar.HOUR_OF_DAY,0); set(Calendar.MINUTE,0); set(Calendar.SECOND,0); set(Calendar.MILLISECOND,0) }
                            val end = (c.clone() as Calendar).apply { set(Calendar.DAY_OF_MONTH, getActualMaximum(Calendar.DAY_OF_MONTH)); set(Calendar.HOUR_OF_DAY,23); set(Calendar.MINUTE,59); set(Calendar.SECOND,59); set(Calendar.MILLISECOND,999) }
                            setRange(start, end)
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            modifier = Modifier.weight(1f)
                        ) { Text("Month") }
                    }
                    Spacer(Modifier.height(6.dp))
                    // Date pickers: stack on narrow widths
                    BoxWithConstraints(Modifier.fillMaxWidth()) {
                        if (maxWidth < 360.dp) {
                            Column(Modifier.fillMaxWidth()) {
                                DateField(label = "From", value = fromMillis, onChange = { fromMillis = it }, modifier = Modifier.fillMaxWidth())
                                Spacer(Modifier.height(6.dp))
                                DateField(label = "To", value = toMillis, onChange = { toMillis = it }, modifier = Modifier.fillMaxWidth())
                            }
                        } else {
                            Row(Modifier.fillMaxWidth()) {
                                DateField(label = "From", value = fromMillis, onChange = { fromMillis = it }, modifier = Modifier.weight(1f))
                                Spacer(Modifier.width(8.dp))
                                DateField(label = "To", value = toMillis, onChange = { toMillis = it }, modifier = Modifier.weight(1f))
                            }
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    // Costing method selector
                    Text(text = "Costing Method")
                    Row(Modifier.fillMaxWidth()) {
                        androidx.compose.foundation.layout.Box(Modifier.weight(1f)) {
                            OutlinedTextField(
                                value = costingMethod.name,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Method") },
                                trailingIcon = {
                                    Button(onClick = { costingExpanded = !costingExpanded }) { Text(if (costingExpanded) "▲" else "▼") }
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                            DropdownMenu(
                                expanded = costingExpanded,
                                onDismissRequest = { costingExpanded = false }
                            ) {
                                ReportsViewModel.CostingMethod.values().forEach { m ->
                                    DropdownMenuItem(
                                        text = { Text(m.name) },
                                        onClick = {
                                            costingMethod = m
                                            costingExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    // Action row 1
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                        Button(onClick = {
                            plError = null
                            plResult = null
                            val f = fromMillis
                            val t = toMillis
                            if (f == null || t == null) {
                                plError = "Please select both From and To dates"
                            } else {
                                val toEnd = t + 86_399_999 // include end day
                                plScope.launch {
                                    plResult = vm.computeProfitAndLoss(f, toEnd, costingMethod)
                                }
                            }
                        }) { Text("Compute P&L") }
                        Spacer(Modifier.width(8.dp))
                        Button(onClick = {
                            plError = null
                            val f = fromMillis
                            val t = toMillis
                            if (f == null || t == null) {
                                plError = "Please select both From and To dates"
                            } else {
                                val toEnd = t + 86_399_999
                                plScope.launch {
                                    val rows = vm.computeProductWisePl(f, toEnd)
                                    val uri = com.fertipos.agroshop.util.ProductPlPdfGenerator.generate(
                                        context,
                                        context.packageName + ".fileprovider",
                                        f,
                                        toEnd,
                                        rows,
                                        hasRemoveAds = hasRemoveAds
                                    )
                                    val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                        type = "application/pdf"
                                        putExtra(android.content.Intent.EXTRA_STREAM, uri)
                                        addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    androidx.core.content.ContextCompat.startActivity(
                                        context,
                                        android.content.Intent.createChooser(intent, "Share Product P&L PDF"),
                                        null
                                    )
                                }
                            }
                        }) { Text("Product P & L") }
                    }
                    Spacer(Modifier.height(6.dp))
                    // Action row 2 (P&L exports)
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                        val context = LocalContext.current
                        Button(onClick = {
                            val r = plResult
                            if (r != null) {
                                val uri = com.fertipos.agroshop.util.ReportExporter.exportPLCsv(
                                    context,
                                    context.packageName + ".fileprovider",
                                    r.from, r.to,
                                    r.salesSubtotal, r.salesGst, r.salesTotal,
                                    r.purchasesSubtotal, r.purchasesGst, r.purchasesTotal,
                                    r.grossProfit, r.netAmount
                                )
                                val intent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/csv"
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                ContextCompat.startActivity(context, Intent.createChooser(intent, "Share P&L"), null)
                            }
                        }, enabled = plResult != null) { Text("Export P&L") }
                        Spacer(Modifier.width(8.dp))
                        Button(onClick = {
                            val r = plResult
                            if (r != null) {
                                val uri = com.fertipos.agroshop.util.PLPdfGenerator.generate(
                                    context,
                                    context.packageName + ".fileprovider",
                                    r.from, r.to,
                                    r.salesSubtotal, r.salesGst, r.salesTotal,
                                    r.purchasesSubtotal, r.purchasesGst, r.purchasesTotal,
                                    r.grossProfit, r.netAmount,
                                    hasRemoveAds = hasRemoveAds
                                )
                                val intent = Intent(Intent.ACTION_SEND).apply {
                                    type = "application/pdf"
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                ContextCompat.startActivity(context, Intent.createChooser(intent, "Share P&L PDF"), null)
                            }
                        }, enabled = plResult != null) { Text("Export P&L PDF") }
                    }
                    Spacer(Modifier.height(8.dp))
                    // Action row 3 (Sales/Purchase exports) - always visible without horizontal scroll
                    Row(Modifier.fillMaxWidth()) {
                        val context = LocalContext.current
                        // Export Sales PDF (by date range)
                        Button(onClick = {
                            val f = fromMillis
                            val t = toMillis
                            if (f != null && t != null) {
                                val toEnd = t + 86_399_999
                                plScope.launch {
                                    val invoices = vm.getInvoicesBetween(f, toEnd)
                                    if (invoices.isNotEmpty()) {
                                        val uri = com.fertipos.agroshop.util.SalesRangePdfGenerator.generate(
                                            context,
                                            context.packageName + ".fileprovider",
                                            f,
                                            toEnd,
                                            invoices,
                                            hasRemoveAds = hasRemoveAds
                                        )
                                        val intent = Intent(Intent.ACTION_SEND).apply {
                                            type = "application/pdf"
                                            putExtra(Intent.EXTRA_STREAM, uri)
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        ContextCompat.startActivity(context, Intent.createChooser(intent, "Share Sales PDF"), null)
                                    } else {
                                        Toast.makeText(context, "No sales found in date range", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        }, enabled = fromMillis != null && toMillis != null) { Text("Export Sale") }

                        Spacer(Modifier.width(8.dp))
                        // Export Purchases PDF (by date range)
                        Button(onClick = {
                            val f = fromMillis
                            val t = toMillis
                            if (f != null && t != null) {
                                val toEnd = t + 86_399_999
                                plScope.launch {
                                    val purchases = vm.getPurchasesBetween(f, toEnd)
                                    if (purchases.isNotEmpty()) {
                                        val uri = com.fertipos.agroshop.util.PurchasesRangePdfGenerator.generate(
                                            context,
                                            context.packageName + ".fileprovider",
                                            f,
                                            toEnd,
                                            purchases,
                                            hasRemoveAds = hasRemoveAds
                                        )
                                        val intent = Intent(Intent.ACTION_SEND).apply {
                                            type = "application/pdf"
                                            putExtra(Intent.EXTRA_STREAM, uri)
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        ContextCompat.startActivity(context, Intent.createChooser(intent, "Share Purchases PDF"), null)
                                    } else {
                                        Toast.makeText(context, "No purchases found in date range", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        }, enabled = fromMillis != null && toMillis != null) { Text("Export Purchase") }
                    }
                    if (plError != null) {
                        Spacer(Modifier.height(4.dp))
                        Text(text = plError!!)
                    }
                    plResult?.let { r ->
                        Spacer(Modifier.height(8.dp))
                        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                            Text("Sales", modifier = Modifier.weight(1f))
                            Text(
                                currency.format(r.salesSubtotal),
                                modifier = Modifier.weight(1f),
                                fontWeight = FontWeight.SemiBold,
                                textAlign = TextAlign.End
                            )
                        }
                        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                            Text("Purchases", modifier = Modifier.weight(1f))
                            Text(
                                currency.format(r.purchasesSubtotal),
                                modifier = Modifier.weight(1f),
                                fontWeight = FontWeight.SemiBold,
                                textAlign = TextAlign.End
                            )
                        }
                        HorizontalDivider()
                        Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                            Text("Gross Profit", modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold)
                            Text(
                                currency.format(r.grossProfit),
                                modifier = Modifier.weight(1f),
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.End
                            )
                        }
                        Row(Modifier.fillMaxWidth().padding(top = 2.dp)) {
                            Text("Net Amount", modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold)
                            Text(
                                currency.format(r.netAmount),
                                modifier = Modifier.weight(1f),
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.End
                            )
                        }
                    }

                    // Product-wise details are not shown on screen; use the export button above
                }
            }
            if (section == "pl") {
                Spacer(Modifier.height(12.dp))
            }
            // Customer reports view
            var expanded by remember { mutableStateOf(false) }
            val customers = state.value.customers
            val selectedId = state.value.selectedCustomerId
            val selectedName = customers.firstOrNull { it.id == selectedId }?.name ?: "Select customer"
            val scope = rememberCoroutineScope()

            if (section == "customer") Card(Modifier.fillMaxWidth()) {
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

            if (section == "customer") {
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
}
