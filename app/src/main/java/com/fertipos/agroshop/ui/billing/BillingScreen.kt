package com.fertipos.agroshop.ui.billing

import android.content.Context
import android.app.Activity
import android.content.ContextWrapper
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.text.font.FontWeight
import androidx.activity.compose.BackHandler
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.hilt.navigation.compose.hiltViewModel
import com.fertipos.agroshop.ui.history.InvoiceHistoryScreen
import com.fertipos.agroshop.ui.screens.AppNavViewModel
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
import androidx.compose.ui.res.stringResource
import com.fertipos.agroshop.R
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.rememberCoroutineScope
import com.fertipos.agroshop.ui.customer.CustomerViewModel
import kotlinx.coroutines.launch
import com.fertipos.agroshop.ui.common.PartyForm
import com.fertipos.agroshop.ui.common.AddProductDialog
import com.fertipos.agroshop.ui.settings.CompanyProfileViewModel
import com.fertipos.agroshop.ui.product.ProductViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BillingScreen(navVm: AppNavViewModel) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        // Single screen: New Bill only (history available elsewhere)
        NewBillContent(navVm)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NewBillContent(navVm: AppNavViewModel) {
    val vm: BillingViewModel = hiltViewModel()
    val custVm: CustomerViewModel = hiltViewModel()
    val prodVm: ProductViewModel = hiltViewModel()
    val state = vm.state.collectAsState()
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    // Currency settings
    val currencyVm: com.fertipos.agroshop.ui.settings.CurrencyViewModel = hiltViewModel()
    val currencyCode by currencyVm.currencyCode.collectAsState()
    val showSymbol by currencyVm.showSymbol.collectAsState()
    val currencyFormat: (Double) -> String = remember(currencyCode, showSymbol) {
        { amt -> com.fertipos.agroshop.util.CurrencyFormatter.format(amt, currencyCode, showSymbol) }
    }
    val prevTab = navVm.previousSelected.collectAsState()
    val backOverride = navVm.backOverrideTab.collectAsState()
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
    var pendingSelectProductId by remember { mutableStateOf<Int?>(null) }
    // Add Customer dialog state
    var showAddCustomer by remember { mutableStateOf(false) }
    var newCustName by remember { mutableStateOf("") }
    var newCustPhone by remember { mutableStateOf("") }
    var newCustAddress by remember { mutableStateOf("") }
    // Add Product dialog state
    var showAddProduct by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // Auto-select newly added product when the list updates
    LaunchedEffect(state.value.products, pendingSelectProductId) {
        val pid = pendingSelectProductId
        if (pid != null) {
            state.value.products.firstOrNull { it.id == pid }?.let { newly ->
                selectedProduct = newly
                pendingSelectProductId = null
            }
        }
    }

    // If there's a pending edit request from nav, load it once
    val pendingEditId = navVm.pendingEditInvoiceId.collectAsState()
    LaunchedEffect(pendingEditId.value) {
        pendingEditId.value?.let { id ->
            vm.loadInvoiceForEdit(id)
            navVm.clearPendingEdit()
        }
    }

    // Always intercept back to return to the intended tab.
    BackHandler(enabled = true) {
        val target = backOverride.value ?: prevTab.value
        navVm.clearBackOverrideTab()
        navVm.navigateTo(target)
    }

    // On first open (fresh new bill), reset the date to today if not editing and clear any stale error
    LaunchedEffect(Unit) {
        if (pendingEditId.value == null && state.value.editingInvoiceId == null) {
            vm.setBillDate(System.currentTimeMillis())
        }
        vm.clearError()
    }

    // If user explicitly chose New Bill from Home, reset draft (only when not editing)
    val newBillTick = navVm.newBillTick.collectAsState()
    LaunchedEffect(newBillTick.value) {
        if (pendingEditId.value == null) {
            vm.resetForNewBill()
        }
    }

    // Show errors as Toasts so they are always visible
    LaunchedEffect(state.value.error) {
        val err = state.value.error
        if (!err.isNullOrBlank()) {
            val msg = when {
                err == "ERR_SELECT_CUSTOMER" -> context.getString(R.string.err_select_customer)
                err == "ERR_ADD_ONE_ITEM" -> context.getString(R.string.err_add_one_item)
                err.startsWith("ERR_INSUFFICIENT_STOCK|") -> {
                    val parts = err.split('|')
                    "Insufficient stock for ${parts.getOrNull(1) ?: ""}"
                }
                else -> err
            }
            android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
            vm.clearError()
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
            val header = if (state.value.editingInvoiceId != null) stringResource(R.string.edit_invoice_with_id, state.value.editingInvoiceId!!) else stringResource(R.string.create_invoice)
            Text(text = header, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
            Spacer(Modifier.height(8.dp))
        }

        // Card 1: Customer & Meta
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                shape = CardDefaults.elevatedShape
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                    // Customer selector
                    val selectedName = state.value.customers.firstOrNull { it.id == state.value.selectedCustomerId }?.name ?: ""
                    CustomerPicker(
                        customers = state.value.customers,
                        label = stringResource(R.string.customer_required),
                        initialQuery = selectedName,
                        modifier = Modifier.fillMaxWidth(),
                        onPicked = { vm.setCustomer(it.id) },
                        onAddNew = { showAddCustomer = true }
                    )
                    Spacer(Modifier.height(8.dp))
                    // Invoice Date
                    DateField(
                        label = stringResource(R.string.date_label),
                        value = state.value.dateMillis,
                        onChange = { millis -> vm.setBillDate(millis) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        // Card 2: Items
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                shape = CardDefaults.elevatedShape
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                    // Product selector
                    ProductPicker(
                        products = state.value.products,
                        label = stringResource(R.string.product_required),
                        modifier = Modifier.fillMaxWidth(),
                        initialQuery = selectedProduct?.name ?: "",
                        onPicked = { p ->
                            selectedProduct = p
                            priceText = p.sellingPrice.toString()
                        },
                        addNewLabel = stringResource(R.string.add),
                        onAddNew = { showAddProduct = true }
                    )
                    Spacer(Modifier.height(8.dp))
                    // Qty and Price row
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = qtyText,
                            onValueChange = { raw ->
                                val filtered = raw.filter { ch -> ch.isDigit() || ch == '.' }
                                val final = if (filtered.count { it == '.' } > 1) filtered.replaceFirst(".", "").let { it } else filtered
                                qtyText = final
                            },
                            label = { Text(stringResource(R.string.qty_label)) },
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
                            label = { Text(stringResource(R.string.price)) },
                            singleLine = true,
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
                            enabled = selectedProduct != null,
                            placeholder = { Text(selectedProduct?.sellingPrice?.toString() ?: "") },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        Spacer(Modifier.weight(1f))
                        Button(onClick = {
                            val prod = selectedProduct
                            val qty = qtyText.toDoubleOrNull()
                            if (prod != null && qty != null && qty > 0) {
                                vm.addItem(prod, qty)
                                val enteredPrice = priceText.toDoubleOrNull()
                                if (enteredPrice != null) {
                                    vm.updateItem(prod.id, quantity = qty, unitPrice = enteredPrice, gstPercent = null)
                                }
                            }
                            qtyText = ""
                            // Hide keyboard and clear focus so the list below is visible
                            focusManager.clearFocus(force = true)
                            keyboard?.hide()
                        }, enabled = selectedProduct != null) { Text(stringResource(R.string.add_item)) }
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(text = stringResource(R.string.items_title), style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(6.dp))
                    state.value.items.forEach { item ->
                        DraftItemRow(item = item, format = currencyFormat, onRemove = { vm.removeItem(item.product.id) })
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        // Card 3: Totals & Actions
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                shape = CardDefaults.elevatedShape
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                    Text(stringResource(R.string.subtotal_with_amount, currencyFormat(state.value.subtotal)), modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.End)
                    Text(stringResource(R.string.gst_with_amount, currencyFormat(state.value.gstAmount)), modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.End)
                    Text(stringResource(R.string.total_with_amount, currencyFormat(state.value.total)), modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.End)
                    Spacer(Modifier.height(6.dp))
                    var paidInFull by remember { mutableStateOf(false) }
                    LaunchedEffect(paidInFull, state.value.total) {
                        if (paidInFull) vm.setPaid(state.value.total.toString())
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
                                if (checked) vm.setPaid(state.value.total.toString())
                            }
                        )
                        Text(stringResource(R.string.paid_label), modifier = Modifier.padding(start = 4.dp))
                        TextField(
                            value = if (state.value.paid == 0.0) "" else String.format(Locale.getDefault(), "%.2f", state.value.paid),
                            onValueChange = { vm.setPaid(it); if (paidInFull && it.toDoubleOrNull() != state.value.total) paidInFull = false },
                            singleLine = true,
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
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Spacer(Modifier.weight(1f))
                        Text(
                            text = stringResource(R.string.balance_colon, currencyFormat(state.value.balance)),
                            textAlign = TextAlign.End
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        Spacer(Modifier.weight(1f))
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
                        }, enabled = !state.value.loading && state.value.selectedCustomerId != null && state.value.items.isNotEmpty()) { Text(stringResource(R.string.submit_and_print)) }
                    }
                    
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }

    // Add Product dialog (outside list like Purchase)
    if (showAddProduct) {
        val profVm: CompanyProfileViewModel = hiltViewModel()
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
            onConfirm = { n, t, u, sp, pp, st, g ->
                // insert and capture new id
                scope.launch {
                    val id = prodVm.addAndReturnId(n, t, u, sp, pp, st, g)
                    if (id > 0) pendingSelectProductId = id
                }
                priceText = sp.toString()
                showAddProduct = false
            },
            onDismiss = { showAddProduct = false }
        )
    }

    // Auto-open print dialog once invoice is saved (observe at the composable root)
    val successId = state.value.successInvoiceId
    val profVm: CompanyProfileViewModel = hiltViewModel()
    val profile by profVm.profile.collectAsState()
    // Monetization (remove ads) state to control PDF footer
    val monetVm: com.fertipos.agroshop.billing.MonetizationViewModel = hiltViewModel()
    val hasRemoveAds by monetVm.hasRemoveAds.collectAsState()
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
                balance = pendingPrint!!.balance,
                hasRemoveAds = hasRemoveAds
            )
            val jobName = "Invoice #$successId"
            navVm.requestPrintPreview(uri, jobName)
            // Reset triggers to avoid duplicate actions
            pendingPrint = null
            vm.clearSuccess()
            navVm.clearBackOverrideTab()
        }
    }

    if (showAddCustomer) {
        AlertDialog(
            onDismissRequest = { showAddCustomer = false },
            confirmButton = {},
            dismissButton = {},
            text = {
                PartyForm(
                    title = "Add new customer",
                    name = newCustName,
                    onNameChange = { newCustName = it },
                    phone = newCustPhone,
                    onPhoneChange = { newCustPhone = it },
                    address = newCustAddress,
                    onAddressChange = { newCustAddress = it },
                    showSupplierToggle = false,
                    isSupplier = false,
                    onIsSupplierChange = {},
                    onSubmit = {
                        scope.launch {
                            val id = custVm.addAndReturnId(newCustName, newCustPhone.takeIf { it.isNotBlank() }, newCustAddress.takeIf { it.isNotBlank() }, false)
                            if (id > 0) vm.setCustomer(id)
                            newCustName = ""; newCustPhone = ""; newCustAddress = ""
                            showAddCustomer = false
                        }
                    },
                    onCancel = { showAddCustomer = false },
                    submitText = "Add"
                )
            }
        )
    }
}

@Composable
private fun DraftItemRow(
    item: BillingViewModel.DraftItem,
    format: (Double) -> String,
    onRemove: () -> Unit
) {
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
                Text(text = item.product.name)
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onRemove) { Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.delete)) }
            }
            Spacer(Modifier.height(4.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(text = stringResource(R.string.qty_label) + ": ${item.quantity}")
                Spacer(Modifier.weight(1f))
                Text(text = stringResource(R.string.price_label) + ": ${format(item.unitPrice)}")
            }
            Spacer(Modifier.height(2.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(text = stringResource(R.string.gst_percent_colon, String.format(java.util.Locale.getDefault(), "%.1f", item.gstPercent)))
                Spacer(Modifier.weight(1f))
                Text(text = stringResource(R.string.gst_amt_colon, format(gstAmt)))
            }
            Spacer(Modifier.height(2.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                Spacer(Modifier.weight(1f))
                Text(text = stringResource(R.string.total_with_amount, format(lineTotal)))
            }
        }
    }
}

// Simple text invoice print using Android Print Framework
private fun triggerPrint(context: Context, jobName: String, lines: List<String>) {
    val activity = context.asActivity()
    if (activity == null) {
        android.widget.Toast.makeText(context, context.getString(R.string.print_failed_after_save_try_view_bills), android.widget.Toast.LENGTH_SHORT).show()
        return
    }
    val printManager = activity.getSystemService(Context.PRINT_SERVICE) as PrintManager
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

// Unwrap a Context to an Activity if possible
private tailrec fun Context.asActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.asActivity()
    else -> null
}