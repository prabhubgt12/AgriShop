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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.activity.compose.BackHandler
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.ArrowForward
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.border
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.outlined.Search
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.clickable
import com.fertipos.agroshop.ui.common.DateField
import com.fertipos.agroshop.ui.screens.AppNavViewModel
import com.fertipos.agroshop.ui.settings.CompanyProfileViewModel
import com.fertipos.agroshop.util.InvoicePdfGenerator
import com.fertipos.agroshop.util.CurrencyFormatter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.Calendar
import kotlinx.coroutines.launch
import androidx.compose.ui.input.pointer.pointerInput
import android.app.DatePickerDialog
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext

import androidx.compose.ui.res.stringResource
import com.fertipos.agroshop.R
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing

private enum class DurationOption { MONTH, QUARTER, YEAR, CUSTOM }

@Composable
private fun DateChip(value: Long?, placeholder: String, onChange: (Long?) -> Unit, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val df = remember { SimpleDateFormat("dd/MM/yy", Locale.getDefault()) }
    val text = remember(value) { value?.let { df.format(Date(it)) } ?: placeholder }
    Card(modifier = modifier, onClick = {
        val cal = Calendar.getInstance().apply {
            if (value != null) timeInMillis = value
        }
        val dlg = DatePickerDialog(
            ctx,
            { _, y, m, d ->
                val set = Calendar.getInstance().apply {
                    set(Calendar.YEAR, y)
                    set(Calendar.MONTH, m)
                    set(Calendar.DAY_OF_MONTH, d)
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                onChange(set.timeInMillis)
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        )
        dlg.show()
    }) {
        Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.DateRange, contentDescription = null)
            Text(text, style = MaterialTheme.typography.bodySmall)
        }
    }
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
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
            )
        }
    }
}

@Composable
fun InvoiceHistoryScreen(navVm: AppNavViewModel) {
    val vm: InvoiceHistoryViewModel = hiltViewModel()
    val list by vm.listState.collectAsState()
    // Apply one-shot customer filter coming from other screens (e.g., Customer page)
    val pendingCustomer by navVm.pendingInvoiceHistoryCustomerId.collectAsState()
    val preserveFilterOnReturn by navVm.preserveInvoiceHistoryFilterOnReturn.collectAsState()
    var lockedCustomer by remember { mutableStateOf(false) }
    // Ensure we don't retain previous customer-specific filter when entering normally.
    // If returning with preserve flag, keep locked state and existing filter.
    LaunchedEffect(Unit) {
        if (preserveFilterOnReturn) {
            lockedCustomer = true
            navVm.clearPreserveInvoiceHistoryFilterOnReturn()
        } else if (pendingCustomer == null) {
            lockedCustomer = false
            vm.setCustomerFilter(null)
        }
    }
    LaunchedEffect(pendingCustomer) {
        if (pendingCustomer != null) {
            lockedCustomer = true
            vm.setCustomerFilter(pendingCustomer)
            navVm.clearPendingInvoiceHistoryCustomer()
        }
    }
    val profVm: CompanyProfileViewModel = hiltViewModel()
    val profile by profVm.profile.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // Monetization for PDF footer
    val monetVm: com.fertipos.agroshop.billing.MonetizationViewModel = hiltViewModel()
    val hasRemoveAds by monetVm.hasRemoveAds.collectAsState()

    // Interstitials are shown when entering history from Home (Dashboard). No FAB interstitial here.

    // Offsets are relative to bottom-end base position (0 = bottom-right). Negative = move left/up.
    var fabDx by remember { mutableStateOf(0.dp) }
    var fabDy by remember { mutableStateOf(0.dp) }
    val density = LocalDensity.current
    val config = LocalConfiguration.current
    val fabSize = 56.dp
    val margin = 16.dp
    val travelX = (config.screenWidthDp.dp - fabSize - margin * 2)
    val travelY = (config.screenHeightDp.dp - fabSize - margin * 2)
    val previousTab by navVm.previousSelected.collectAsState()

    // Handle system back: if previous was Billing (3), send to Home (0) to avoid loop. Else, go to previous.
    BackHandler {
        val target = if (previousTab == 3) 0 else previousTab
        navVm.navigateTo(target)
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        floatingActionButton = {
            if (!lockedCustomer) {
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
                    Icon(imageVector = Icons.Filled.Add, contentDescription = stringResource(R.string.new_bill_cd))
                }
            }
        }
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding).padding(horizontal = 8.dp, vertical = 8.dp)) {
            // Header row: Title (filters are always visible, no toggle)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.invoices_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }

            // Date filters (always visible)
            var fromMillis by remember { mutableStateOf<Long?>(null) }
            var toMillis by remember { mutableStateOf<Long?>(null) }
            Spacer(Modifier.height(6.dp))
            // Compact toolbar: Duration dropdown + From/To date chips
            var durationExpanded by remember { mutableStateOf(false) }
            var selectedDuration by remember { mutableStateOf<DurationOption?>(DurationOption.YEAR) }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
                    // Duration button
                    Card(onClick = { durationExpanded = true }) {
                        Row(
                            modifier = Modifier
                                .padding(horizontal = 6.dp, vertical = 6.dp)
                                .widthIn(min = 68.dp, max = 80.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = when (selectedDuration) {
                                    DurationOption.MONTH -> stringResource(R.string.filter_month)
                                    DurationOption.QUARTER -> stringResource(R.string.filter_quarter)
                                    DurationOption.YEAR -> stringResource(R.string.filter_year)
                                    DurationOption.CUSTOM -> "Custom"
                                    null -> stringResource(R.string.filter_year)
                                },
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = null)
                        }
                    }
                    DropdownMenu(expanded = durationExpanded, onDismissRequest = { durationExpanded = false }) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.filter_month)) }, onClick = { selectedDuration = DurationOption.MONTH; durationExpanded = false })
                        DropdownMenuItem(text = { Text(stringResource(R.string.filter_quarter)) }, onClick = { selectedDuration = DurationOption.QUARTER; durationExpanded = false })
                        DropdownMenuItem(text = { Text(stringResource(R.string.filter_year)) }, onClick = { selectedDuration = DurationOption.YEAR; durationExpanded = false })
                        DropdownMenuItem(text = { Text("Custom") }, onClick = { selectedDuration = DurationOption.CUSTOM; durationExpanded = false })
                    }

                    // Date chips
                    val ctx = LocalContext.current
                    DateChip(
                        value = fromMillis,
                        placeholder = "01/01/25",
                        onChange = { fromMillis = it; selectedDuration = DurationOption.CUSTOM; vm.setDateRange(fromMillis, toMillis) },
                        modifier = Modifier.weight(1f).widthIn(min = 124.dp)
                    )
                    Text("TO", style = MaterialTheme.typography.labelSmall)
                    DateChip(
                        value = toMillis,
                        placeholder = "31/12/25",
                        onChange = { toMillis = it; selectedDuration = DurationOption.CUSTOM; vm.setDateRange(fromMillis, toMillis) },
                        modifier = Modifier.weight(1f).widthIn(min = 124.dp)
                    )
                }
            // Auto-apply when duration changes
            when (selectedDuration) {
                DurationOption.MONTH -> {
                    val cal = java.util.Calendar.getInstance()
                    cal.set(java.util.Calendar.DAY_OF_MONTH, 1)
                    cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
                    cal.set(java.util.Calendar.MINUTE, 0)
                    cal.set(java.util.Calendar.SECOND, 0)
                    cal.set(java.util.Calendar.MILLISECOND, 0)
                    val from = cal.timeInMillis
                    cal.add(java.util.Calendar.MONTH, 1)
                    cal.add(java.util.Calendar.MILLISECOND, -1)
                    val to = cal.timeInMillis
                    if (fromMillis != from || toMillis != to) { fromMillis = from; toMillis = to; vm.setDateRange(from, to) }
                }
                DurationOption.QUARTER -> {
                    val cal = java.util.Calendar.getInstance()
                    val month = cal.get(java.util.Calendar.MONTH)
                    val startMonth = month / 3 * 3
                    cal.set(java.util.Calendar.MONTH, startMonth)
                    cal.set(java.util.Calendar.DAY_OF_MONTH, 1)
                    cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
                    cal.set(java.util.Calendar.MINUTE, 0)
                    cal.set(java.util.Calendar.SECOND, 0)
                    cal.set(java.util.Calendar.MILLISECOND, 0)
                    val from = cal.timeInMillis
                    cal.add(java.util.Calendar.MONTH, 3)
                    cal.add(java.util.Calendar.MILLISECOND, -1)
                    val to = cal.timeInMillis
                    if (fromMillis != from || toMillis != to) { fromMillis = from; toMillis = to; vm.setDateRange(from, to) }
                }
                DurationOption.YEAR -> {
                    val cal = java.util.Calendar.getInstance()
                    cal.set(java.util.Calendar.MONTH, 0)
                    cal.set(java.util.Calendar.DAY_OF_MONTH, 1)
                    cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
                    cal.set(java.util.Calendar.MINUTE, 0)
                    cal.set(java.util.Calendar.SECOND, 0)
                    cal.set(java.util.Calendar.MILLISECOND, 0)
                    val from = cal.timeInMillis
                    cal.add(java.util.Calendar.YEAR, 1)
                    cal.add(java.util.Calendar.MILLISECOND, -1)
                    val to = cal.timeInMillis
                    if (fromMillis != from || toMillis != to) { fromMillis = from; toMillis = to; vm.setDateRange(from, to) }
                }
                DurationOption.CUSTOM, null -> { /* handled by DateChip onChange */ }
            }

            // Search bar (placed below filters) - hidden when locked to a specific customer
            var searchQuery by remember { mutableStateOf("") }
            if (!lockedCustomer) {
                CompactSearchBar(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = stringResource(R.string.search_customer_label),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(Modifier.height(8.dp))

            // Apply search filtering on UI list (do this first so summary matches filtered view)
            val filtered = remember(list, searchQuery, lockedCustomer) {
                val base = list
                if (lockedCustomer) base
                else if (searchQuery.isBlank()) base
                else base.filter { it.customerName.contains(searchQuery, ignoreCase = true) }
            }

            // Summary card computed from filtered list
            val totals = remember(filtered) {
                val totalSale = filtered.sumOf { it.invoice.total }
                val balanceDue = filtered.sumOf { (it.invoice.total - it.invoice.paid).coerceAtLeast(0.0) }
                totalSale to balanceDue
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Card(modifier = Modifier.weight(1f)) {
                    Column(Modifier.padding(12.dp)) {
                        Text(stringResource(R.string.summary_total_sale), style = MaterialTheme.typography.labelSmall)
                        Spacer(Modifier.height(4.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFFDFF6DD))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = CurrencyFormatter.formatInr(totals.first),
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF0B6A0B),
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }
                Card(modifier = Modifier.weight(1f)) {
                    Column(Modifier.padding(12.dp)) {
                        Text(stringResource(R.string.summary_balance_due), style = MaterialTheme.typography.labelSmall)
                        Spacer(Modifier.height(4.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFFFDE0E0))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = CurrencyFormatter.formatInr(totals.second),
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFFB00020),
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }
                // Results card
                Card(modifier = Modifier.weight(1f)) {
                    Column(Modifier.padding(12.dp)) {
                        Text(stringResource(R.string.results_label), style = MaterialTheme.typography.labelSmall)
                        Spacer(Modifier.height(4.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFFE3F2FD))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = filtered.size.toString(),
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF1565C0),
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))

            // filtered is already computed above so the list and summary stay in sync

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
                                        Text(stringResource(R.string.total_label), style = MaterialTheme.typography.labelSmall)
                                        Text(CurrencyFormatter.formatInr(row.invoice.total), fontWeight = FontWeight.SemiBold)
                                    }
                                    Column {
                                        Text(stringResource(R.string.balance_label), style = MaterialTheme.typography.labelSmall)
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
                                                balance = balance,
                                                hasRemoveAds = hasRemoveAds
                                            )
                                            val intent = Intent(Intent.ACTION_SEND).apply {
                                                type = "application/pdf"
                                                putExtra(Intent.EXTRA_STREAM, uri)
                                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                            }
                                            context.startActivity(Intent.createChooser(intent, context.getString(R.string.print)))
                                        }
                                    }) { Icon(Icons.Outlined.Print, contentDescription = stringResource(R.string.print)) }
                                    IconButton(onClick = { menuExpanded = true }) { Icon(Icons.Outlined.MoreVert, contentDescription = stringResource(R.string.more_cd)) }
                                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                                        DropdownMenuItem(text = { Text(stringResource(R.string.edit)) }, onClick = {
                                            menuExpanded = false
                                            // If this history is locked to a specific customer (opened from Customer menu),
                                            // return to Invoice History (tab 6) and keep the filter on back from Billing.
                                            if (lockedCustomer) {
                                                navVm.setBackOverrideTab(6)
                                                navVm.setPreserveInvoiceHistoryFilterOnReturn()
                                            }
                                            navVm.requestEditInvoice(row.invoice.id)
                                            navVm.navigateTo(3)
                                        })
                                        DropdownMenuItem(text = { Text(stringResource(R.string.delete)) }, onClick = {
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
                            title = { Text(stringResource(R.string.delete_invoice_title)) },
                            text = { Text(stringResource(R.string.delete_invoice_message)) },
                            confirmButton = {
                                TextButton(onClick = {
                                    confirmDelete = false
                                    vm.deleteInvoice(row.invoice.id)
                                }) { Text(stringResource(R.string.delete)) }
                            },
                            dismissButton = {
                                TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) }
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
                        val pname = itw.product?.name ?: stringResource(R.string.item_placeholder, itw.item.productId)
                        val qty = itw.item.quantity
                        val price = itw.item.unitPrice
                        val lineTotal = itw.item.lineTotal
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(pname)
                                Text(stringResource(R.string.line_item_qty_price, qty.toString(), CurrencyFormatter.formatInr(price)), style = MaterialTheme.typography.labelSmall)
                            }
                            Text(CurrencyFormatter.formatInr(lineTotal), fontWeight = FontWeight.SemiBold)
                        }
                        Spacer(Modifier.height(6.dp))
                    }
                }
                // Totals at the end, right aligned
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.subtotal_with_amount, CurrencyFormatter.formatInr(subtotal)),
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.End
                )
                Text(
                    text = stringResource(R.string.gst_with_amount, CurrencyFormatter.formatInr(gst)),
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.End
                )
                Text(
                    text = stringResource(R.string.total_with_amount, CurrencyFormatter.formatInr(total)),
                    modifier = Modifier.fillMaxWidth(),
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.End
                )
                Text(
                    text = stringResource(R.string.paid_with_amount, CurrencyFormatter.formatInr(paid)),
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.End
                )
                Text(
                    text = stringResource(R.string.balance_with_amount, CurrencyFormatter.formatInr(balance)),
                    modifier = Modifier.fillMaxWidth(),
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.End
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } }
    )
}
