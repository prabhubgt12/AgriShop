package com.fertipos.agroshop.ui.billing

import android.content.Context
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.hilt.navigation.compose.hiltViewModel
import com.fertipos.agroshop.ui.history.InvoiceHistoryScreen
import com.fertipos.agroshop.ui.screens.AppNavViewModel
import com.fertipos.agroshop.ui.settings.CompanyProfileViewModel
import com.fertipos.agroshop.data.local.entities.Product
import com.fertipos.agroshop.data.local.entities.Invoice
import com.fertipos.agroshop.data.local.entities.InvoiceItem
import com.fertipos.agroshop.util.InvoicePdfGenerator
import com.fertipos.agroshop.util.CurrencyFormatter
import java.io.FileOutputStream
import java.text.NumberFormat
import java.util.Locale
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import com.fertipos.agroshop.ui.common.CustomerPicker
import com.fertipos.agroshop.ui.common.ProductPicker
import com.fertipos.agroshop.ui.common.DateField

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BillingScreen(navVm: AppNavViewModel) {
    Surface(modifier = Modifier.fillMaxSize()) {
        // Single screen: New Bill only (history available elsewhere)
        NewBillContent(navVm)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NewBillContent(navVm: AppNavViewModel) {
    val vm: BillingViewModel = hiltViewModel()
    val state = vm.state.collectAsState()
    val context = LocalContext.current
    val currency = remember { CurrencyFormatter.inr }
    // Hoisted pending print data to avoid being lost on recompositions
    data class PendingPrintItem(val product: Product, val quantity: Double, val unitPrice: Double, val gstPercent: Double)
    data class PendingPrintData(
        val selectedCustomerId: Int?,
        val subtotal: Double,
        val gstAmount: Double,
        val total: Double,
        val items: List<PendingPrintItem>,
        val paid: Double,
        val balance: Double
    )
    var pendingPrint by remember { mutableStateOf<PendingPrintData?>(null) }
    // Form state
    var selectedProduct by remember { mutableStateOf<Product?>(null) }
    var qtyText by remember { mutableStateOf("") }
    var priceText by remember { mutableStateOf("") }

    // If there's a pending edit request from nav, load it once
    val pendingEditId = navVm.pendingEditInvoiceId.collectAsState()
    LaunchedEffect(pendingEditId.value) {
        pendingEditId.value?.let { id ->
            vm.loadInvoiceForEdit(id)
            navVm.clearPendingEdit()
        }
    }

    // On first open (fresh new bill), reset the date to today if not editing
    LaunchedEffect(Unit) {
        if (pendingEditId.value == null && state.value.editingInvoiceId == null) {
            vm.setBillDate(System.currentTimeMillis())
        }
    }

    // If user explicitly chose New Bill from Home, reset draft (only when not editing)
    val newBillTick = navVm.newBillTick.collectAsState()
    LaunchedEffect(newBillTick.value) {
        if (pendingEditId.value == null) {
            vm.resetForNewBill()
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
            val header = if (state.value.editingInvoiceId != null) "Edit Invoice #${state.value.editingInvoiceId}" else "Create Invoice"
            Text(text = header)
            Spacer(Modifier.height(6.dp))
        }

        // Date at top (bound to VM so create/update and PDF use same date)
        item {
            DateField(
                label = "Date",
                value = state.value.dateMillis,
                onChange = { millis -> vm.setBillDate(millis) },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(6.dp))
        }

        // Customer selector (typeahead)
        item {
            val selectedName = state.value.customers.firstOrNull { it.id == state.value.selectedCustomerId }?.name ?: ""
            CustomerPicker(
                customers = state.value.customers,
                label = "Customer*",
                initialQuery = selectedName,
                modifier = Modifier.fillMaxWidth(),
                onPicked = { vm.setCustomer(it.id) }
            )
            Spacer(Modifier.height(6.dp))
        }

        // Product selector (typeahead)
        item {
            ProductPicker(
                products = state.value.products,
                label = "Product*",
                modifier = Modifier.fillMaxWidth(),
                onPicked = { p ->
                    selectedProduct = p
                    priceText = p.sellingPrice.toString()
                }
            )
            Spacer(Modifier.height(6.dp))
        }

        // Compact row for qty and price
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = qtyText,
                    onValueChange = { raw ->
                        val filtered = raw.filter { ch -> ch.isDigit() || ch == '.' }
                        val final = if (filtered.count { it == '.' } > 1) filtered.replaceFirst(".", "").let { it } else filtered
                        qtyText = final
                    },
                    label = { Text("Qty") },
                    singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = priceText,
                    onValueChange = { raw ->
                        val filtered = raw.filter { ch -> ch.isDigit() || ch == '.' }
                        val final = if (filtered.count { it == '.' } > 1) filtered.replaceFirst(".", "").let { it } else filtered
                        priceText = final
                    },
                    label = { Text("Price") },
                    singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
                    enabled = selectedProduct != null,
                    placeholder = { Text(selectedProduct?.sellingPrice?.toString() ?: "") },
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    val prod = selectedProduct
                    val qty = qtyText.toDoubleOrNull()
                    if (prod != null && qty != null && qty > 0) {
                        vm.addItem(prod, qty)
                        // If user entered a custom price, override unit price for this line
                        val enteredPrice = priceText.toDoubleOrNull()
                        if (enteredPrice != null) {
                            vm.updateItem(prod.id, quantity = qty, unitPrice = enteredPrice, gstPercent = null)
                        }
                    }
                    qtyText = ""
                }, enabled = selectedProduct != null) { Text("Add Item") }
            }
            Spacer(Modifier.height(8.dp))
            HorizontalDivider()
            Spacer(Modifier.height(6.dp))
        }

        // Items list
        item { Text(text = "Items") }
        items(state.value.items, key = { it.product.id }) { item ->
            DraftItemRow(item = item, currency = currency, onRemove = { vm.removeItem(item.product.id) })
        }

        // Totals and actions as part of list (end of scroll)
        item {
            Spacer(Modifier.height(8.dp))
            Text("Subtotal: ${currency.format(state.value.subtotal)}", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.End)
            Text("GST: ${currency.format(state.value.gstAmount)}", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.End)
            Text("Total: ${currency.format(state.value.total)}", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.End)
            Spacer(Modifier.height(6.dp))
            var paidInFull by remember { mutableStateOf(false) }
            // Keep paid synced to total when checked
            LaunchedEffect(paidInFull, state.value.total) {
                if (paidInFull) vm.setPaid(state.value.total.toString())
            }
            // Row aligned to end: checkbox (no label) + Paid field
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                androidx.compose.material3.Checkbox(
                    checked = paidInFull,
                    onCheckedChange = { checked ->
                        paidInFull = checked
                        if (checked) vm.setPaid(state.value.total.toString())
                    }
                )
                Text("Paid", modifier = Modifier.padding(start = 4.dp))
                TextField(
                    value = if (state.value.paid == 0.0) "" else String.format(Locale.getDefault(), "%.2f", state.value.paid),
                    onValueChange = { vm.setPaid(it); if (paidInFull && it.toDoubleOrNull() != state.value.total) paidInFull = false },
                    singleLine = true,
                    // no label/placeholder to avoid extra height and inline text
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
                    textStyle = androidx.compose.ui.text.TextStyle(textAlign = TextAlign.End),
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
            // Balance line aligned to end
            Row(modifier = Modifier.fillMaxWidth()) {
                Spacer(Modifier.weight(1f))
                Text(
                    text = "Balance: ${currency.format(state.value.balance)}",
                    textAlign = TextAlign.End
                )
            }
            Spacer(Modifier.height(8.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    val snapshot = PendingPrintData(
                        selectedCustomerId = state.value.selectedCustomerId,
                        subtotal = state.value.subtotal,
                        gstAmount = state.value.gstAmount,
                        total = state.value.total,
                        items = state.value.items.map { PendingPrintItem(it.product, it.quantity, it.unitPrice, it.gstPercent) },
                        paid = state.value.paid,
                        balance = state.value.balance
                    )
                    pendingPrint = snapshot
                    vm.submit()
                }, enabled = !state.value.loading && state.value.selectedCustomerId != null && state.value.items.isNotEmpty()) { Text("Submit & Print") }
            }
            if (state.value.error != null) {
                Spacer(Modifier.height(4.dp))
                Text(text = state.value.error ?: "", color = androidx.compose.ui.graphics.Color.Red)
            }
        }
    }

    // Auto-open print dialog once invoice is saved (observe at the composable root)
    val successId = state.value.successInvoiceId
    val profVm: CompanyProfileViewModel = hiltViewModel()
    val profile by profVm.profile.collectAsState()
    LaunchedEffect(successId, profile) {
        if (successId != null && pendingPrint != null) {
            val invoice = Invoice(
                id = successId,
                customerId = pendingPrint!!.selectedCustomerId ?: 0,
                date = state.value.dateMillis,
                subtotal = pendingPrint!!.subtotal,
                gstAmount = pendingPrint!!.gstAmount,
                total = pendingPrint!!.total,
                notes = null,
                paid = pendingPrint!!.paid
            )
            val items = pendingPrint!!.items.map {
                InvoicePdfGenerator.ItemWithProduct(
                    item = InvoiceItem(
                        id = 0,
                        invoiceId = successId,
                        productId = it.product.id,
                        quantity = it.quantity,
                        unitPrice = it.unitPrice,
                        gstPercent = it.gstPercent,
                        lineTotal = it.quantity * it.unitPrice * (1 + it.gstPercent / 100.0)
                    ),
                    product = it.product
                )
            }
            val customerName = state.value.customers.firstOrNull { c -> c.id == pendingPrint!!.selectedCustomerId }?.name ?: "Customer"
            val uri = InvoicePdfGenerator.generate(
                context = context,
                authority = context.packageName + ".fileprovider",
                invoice = invoice,
                customerName = customerName,
                company = profile,
                items = items,
                paid = pendingPrint!!.paid,
                balance = pendingPrint!!.balance
            )
            val printManager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
            val jobName = "Invoice #$successId"
            val adapter = object : PrintDocumentAdapter() {
                override fun onLayout(
                    oldAttributes: PrintAttributes?,
                    newAttributes: PrintAttributes?,
                    cancellationSignal: android.os.CancellationSignal?,
                    callback: LayoutResultCallback?,
                    extras: android.os.Bundle?
                ) {
                    if (cancellationSignal?.isCanceled == true) {
                        callback?.onLayoutCancelled()
                        return
                    }
                    val info = PrintDocumentInfo.Builder("invoice_$successId.pdf")
                        .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                        .build()
                    callback?.onLayoutFinished(info, true)
                }
                override fun onWrite(
                    pages: Array<out PageRange>?,
                    destination: android.os.ParcelFileDescriptor?,
                    cancellationSignal: android.os.CancellationSignal?,
                    callback: WriteResultCallback?
                ) {
                    try {
                        context.contentResolver.openInputStream(uri)?.use { input ->
                            destination?.fileDescriptor?.let { fd ->
                                FileOutputStream(fd).use { out ->
                                    val buf = ByteArray(8192)
                                    while (true) {
                                        val r = input.read(buf)
                                        if (r <= 0) break
                                        out.write(buf, 0, r)
                                    }
                                }
                            }
                        }
                        callback?.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
                    } catch (e: Exception) {
                        callback?.onWriteFailed(e.message)
                    }
                }
            }
            printManager.print(jobName, adapter, null)
            // Reset triggers to avoid duplicate prints
            pendingPrint = null
            vm.clearSuccess()
        }
    }
}

@Composable
private fun DraftItemRow(
    item: BillingViewModel.DraftItem,
    currency: NumberFormat,
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
                Text(text = item.product.name)
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onRemove) { Icon(Icons.Filled.Delete, contentDescription = "Delete") }
            }
            Spacer(Modifier.height(4.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(text = "Qty: ${item.quantity}")
                Spacer(Modifier.weight(1f))
                Text(text = "Price: ${currency.format(item.unitPrice)}")
            }
            Spacer(Modifier.height(2.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(text = "GST ${String.format(java.util.Locale.getDefault(), "%.1f", item.gstPercent)}%")
                Spacer(Modifier.weight(1f))
                Text(text = "GST Amt: ${currency.format(gstAmt)}")
            }
            Spacer(Modifier.height(2.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                Spacer(Modifier.weight(1f))
                Text(text = "Total: ${currency.format(lineTotal)}")
            }
        }
    }
}

// Simple text invoice print using Android Print Framework
private fun triggerPrint(context: Context, jobName: String, lines: List<String>) {
    val printManager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
    val adapter = object : PrintDocumentAdapter() {
        private var pdfDocument: PdfDocument? = null

        override fun onLayout(
            oldAttributes: PrintAttributes?,
            newAttributes: PrintAttributes?,
            cancellationSignal: android.os.CancellationSignal?,
            callback: LayoutResultCallback?,
            extras: android.os.Bundle?
        ) {
            pdfDocument = PdfDocument()
            val info = PrintDocumentInfo.Builder("invoice.pdf")
                .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                .build()
            callback?.onLayoutFinished(info, true)
        }

        override fun onWrite(
            pageRanges: Array<out PageRange>?,
            destination: android.os.ParcelFileDescriptor?,
            cancellationSignal: android.os.CancellationSignal?,
            callback: WriteResultCallback?
        ) {
            val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create() // A4 approx in points
            val page = pdfDocument!!.startPage(pageInfo)
            val canvas = page.canvas
            val paint = Paint().apply { textSize = 12f }
            var y = 40f
            lines.forEach { line ->
                canvas.drawText(line, 40f, y, paint)
                y += 18f
            }
            pdfDocument!!.finishPage(page)
            try {
                destination?.fileDescriptor?.let { fd ->
                    FileOutputStream(fd).use { out ->
                        pdfDocument!!.writeTo(out)
                    }
                }
                callback?.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
            } catch (e: Exception) {
                callback?.onWriteFailed(e.message)
            } finally {
                pdfDocument?.close()
                pdfDocument = null
            }
        }
    }
    printManager.print(jobName, adapter, PrintAttributes.Builder().build())
}

