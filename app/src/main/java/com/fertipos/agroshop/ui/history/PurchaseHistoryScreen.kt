package com.fertipos.agroshop.ui.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.SolidColor
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Search
import android.app.DatePickerDialog
import androidx.compose.ui.platform.LocalContext
import com.fertipos.agroshop.ui.common.DateField
import com.fertipos.agroshop.ui.history.PurchaseHistoryViewModel.Row
import com.fertipos.agroshop.ui.screens.AppNavViewModel
import com.fertipos.agroshop.util.CurrencyFormatter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch
import androidx.compose.ui.res.stringResource
import androidx.activity.compose.BackHandler
import com.fertipos.agroshop.R
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.clickable

private enum class DurationOptionPH { MONTH, QUARTER, YEAR, CUSTOM }

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
private fun DateChipPH(value: Long?, placeholder: String, onChange: (Long?) -> Unit, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val df = remember { SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()) }
    val text = remember(value) { value?.let { df.format(Date(it)) } ?: placeholder }
    Card(modifier = modifier, onClick = {
        val cal = java.util.Calendar.getInstance().apply { if (value != null) timeInMillis = value }
        DatePickerDialog(
            ctx,
            { _, y, m, d ->
                val set = java.util.Calendar.getInstance().apply {
                    set(java.util.Calendar.YEAR, y)
                    set(java.util.Calendar.MONTH, m)
                    set(java.util.Calendar.DAY_OF_MONTH, d)
                    set(java.util.Calendar.HOUR_OF_DAY, 0)
                    set(java.util.Calendar.MINUTE, 0)
                    set(java.util.Calendar.SECOND, 0)
                    set(java.util.Calendar.MILLISECOND, 0)
                }
                onChange(set.timeInMillis)
            },
            cal.get(java.util.Calendar.YEAR),
            cal.get(java.util.Calendar.MONTH),
            cal.get(java.util.Calendar.DAY_OF_MONTH)
        ).show()
    }) {
        Row(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.DateRange, contentDescription = null)
            Text(text, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
fun PurchaseHistoryScreen(navVm: AppNavViewModel) {
    val vm: PurchaseHistoryViewModel = hiltViewModel()
    val pendingFilter by navVm.pendingPurchaseHistoryProductId.collectAsState()
    val preserveOnReturn by navVm.preservePurchaseHistoryFilterOnReturn.collectAsState()
    var lockedProduct by remember { mutableStateOf(false) }

    // Ensure we don't retain previous product-specific filter when entering normally.
    // If returning with preserve flag, keep existing filter.
    LaunchedEffect(Unit) {
        if (preserveOnReturn) {
            lockedProduct = true
            navVm.clearPreservePurchaseHistoryFilterOnReturn()
        } else if (pendingFilter == null) {
            lockedProduct = false
            vm.setProductFilter(null)
        }
    }

    LaunchedEffect(pendingFilter) {
        if (pendingFilter != null) {
            lockedProduct = true
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
    val previousTab by navVm.previousSelected.collectAsState()

    // Handle system back: if previous was Purchase (7), send to Home (0) to avoid loop. Else, go to previous.
    BackHandler {
        val target = if (previousTab == 7) 0 else previousTab
        navVm.navigateTo(target)
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        floatingActionButton = {
            if (!lockedProduct) {
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
                    Icon(imageVector = Icons.Filled.Add, contentDescription = stringResource(R.string.new_purchase_cd))
                }
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
                // Header row without Filters toggle (filters always visible)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        text = if (pendingFilter != null) stringResource(R.string.purchase_history_title_with_product, pendingFilter!!) else stringResource(R.string.purchases_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

            // Date filters (always visible)
            var fromMillis by remember { mutableStateOf<Long?>(null) }
            var toMillis by remember { mutableStateOf<Long?>(null) }
            Spacer(Modifier.height(6.dp))
            // Duration dropdown (Month / Quarter / Year / Custom)
            var durationExpanded by remember { mutableStateOf(false) }
            var selectedDuration by remember { mutableStateOf<DurationOptionPH?>(DurationOptionPH.YEAR) }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
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
                                    DurationOptionPH.MONTH -> stringResource(R.string.filter_month)
                                    DurationOptionPH.QUARTER -> stringResource(R.string.filter_quarter)
                                    DurationOptionPH.YEAR -> stringResource(R.string.filter_year)
                                    DurationOptionPH.CUSTOM -> "Custom"
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
                        DropdownMenuItem(text = { Text(stringResource(R.string.filter_month)) }, onClick = { selectedDuration = DurationOptionPH.MONTH; durationExpanded = false })
                        DropdownMenuItem(text = { Text(stringResource(R.string.filter_quarter)) }, onClick = { selectedDuration = DurationOptionPH.QUARTER; durationExpanded = false })
                        DropdownMenuItem(text = { Text(stringResource(R.string.filter_year)) }, onClick = { selectedDuration = DurationOptionPH.YEAR; durationExpanded = false })
                        DropdownMenuItem(text = { Text("Custom") }, onClick = { selectedDuration = DurationOptionPH.CUSTOM; durationExpanded = false })
                    }

                    // Date chips similar to Invoice screen
                    DateChipPH(
                        value = fromMillis,
                        placeholder = "01/01/25",
                        onChange = { fromMillis = it; selectedDuration = DurationOptionPH.CUSTOM; vm.setDateRange(fromMillis, toMillis) },
                        modifier = Modifier.weight(1f).widthIn(min = 124.dp)
                    )
                    Text("TO", style = MaterialTheme.typography.labelSmall)
                    DateChipPH(
                        value = toMillis,
                        placeholder = "31/12/25",
                        onChange = { toMillis = it; selectedDuration = DurationOptionPH.CUSTOM; vm.setDateRange(fromMillis, toMillis) },
                        modifier = Modifier.weight(1f).widthIn(min = 124.dp)
                    )
                }
            // Auto-apply when duration changes
            when (selectedDuration) {
                DurationOptionPH.MONTH -> {
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
                DurationOptionPH.QUARTER -> {
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
                DurationOptionPH.YEAR -> {
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
                DurationOptionPH.CUSTOM, null -> { /* handled by DateChip changes */ }
            }

            // Search bar (placed below filters)
            var searchQuery by remember { mutableStateOf("") }
            CompactSearchBar(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = stringResource(R.string.search_supplier_label),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(8.dp))

            // Apply supplier search filtering first so summary reflects the filtered view
            val filtered = remember(list, searchQuery) {
                if (searchQuery.isBlank()) list else list.filter { r ->
                    when (r) {
                        is Row.Purchase -> (r.row.supplierName ?: "").contains(searchQuery, ignoreCase = true)
                        is Row.ProductHistory -> (r.row.supplierName ?: "").contains(searchQuery, ignoreCase = true)
                    }
                }
            }

            // Summary card for filtered list
            val (totalPurchase, balanceDue) = remember(filtered) {
                var total = 0.0
                var bal = 0.0
                filtered.forEach { r ->
                    when (r) {
                        is Row.Purchase -> {
                            total += r.row.total
                            bal += (r.row.total - r.row.paid).coerceAtLeast(0.0)
                        }
                        is Row.ProductHistory -> {
                            total += r.row.lineTotal
                        }
                    }
                }
                total to bal
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Card(modifier = Modifier.weight(1f)) {
                    Column(Modifier.padding(12.dp)) {
                        Text(stringResource(R.string.summary_total_purchase), style = MaterialTheme.typography.labelSmall)
                        Spacer(Modifier.height(4.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFFDFF6DD))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = CurrencyFormatter.formatInr(totalPurchase),
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
                                text = CurrencyFormatter.formatInr(balanceDue),
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

            // filtered is already computed above

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
                                        Text(text = item.supplierName ?: stringResource(R.string.unknown_supplier), fontWeight = FontWeight.SemiBold)
                                        Text(text = "#${item.id}", style = MaterialTheme.typography.labelSmall)
                                        val dateStr = remember(item.date) { dfCard.format(Date(item.date)).uppercase(Locale.getDefault()) }
                                        Text(text = dateStr, style = MaterialTheme.typography.labelSmall)
                                    }
                                    Spacer(Modifier.height(6.dp))
                                    // Row 2: Totals on left, menu on right
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                            Column {
                                                Text(stringResource(R.string.total_label), style = MaterialTheme.typography.labelSmall)
                                                Text(CurrencyFormatter.formatInr(item.total), fontWeight = FontWeight.SemiBold)
                                            }
                                            val balance = (item.total - item.paid).coerceAtLeast(0.0)
                                            Column {
                                                Text(stringResource(R.string.balance_label), style = MaterialTheme.typography.labelSmall)
                                                Text(CurrencyFormatter.formatInr(balance), fontWeight = FontWeight.SemiBold)
                                            }
                                        }
                                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            IconButton(onClick = { menuExpanded = true }) { Icon(Icons.Outlined.MoreVert, contentDescription = stringResource(R.string.more_cd)) }
                                            DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                                                DropdownMenuItem(text = { Text(stringResource(R.string.edit)) }, onClick = {
                                                    menuExpanded = false
                                                    // If this history is filtered by a specific product (opened from Product menu),
                                                    // ensure back from Purchase returns to Products tab.
                                                    if (pendingFilter != null) navVm.setBackOverrideTab(2)
                                                    navVm.requestEditPurchase(item.id)
                                                    navVm.navigateTo(7)
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

                            // Read-only dialog
                            if (showReadonly && full != null) {
                                val dfCard = remember { SimpleDateFormat("dd MMM yy", Locale.getDefault()) }
                                PurchaseReadonlyDialog(
                                    onDismiss = { showReadonly = false },
                                    header = "#${full!!.id} • ${full!!.supplierName ?: stringResource(R.string.unknown_supplier)}",
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
                                    title = { Text(stringResource(R.string.delete_purchase_title)) },
                                    text = { Text(stringResource(R.string.delete_purchase_message, item.id)) },
                                    confirmButton = {
                                        TextButton(onClick = {
                                            scope.launch { vm.deletePurchase(item.id) }
                                            confirmDelete = false
                                        }) { Text(stringResource(R.string.delete)) }
                                    },
                                    dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) } }
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
                                        Text(text = item.supplierName ?: stringResource(R.string.unknown_supplier), fontWeight = FontWeight.SemiBold)
                                        Text(text = "#${item.purchaseId}", style = MaterialTheme.typography.labelSmall)
                                        val dateStr = remember(item.date) { dfCard.format(Date(item.date)).uppercase(Locale.getDefault()) }
                                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Text(text = dateStr, style = MaterialTheme.typography.labelSmall)
                                            // More menu for ProductHistory row to allow Edit
                                            var menuExpanded by remember { mutableStateOf(false) }
                                            IconButton(onClick = { menuExpanded = true }) { Icon(Icons.Outlined.MoreVert, contentDescription = stringResource(R.string.more_cd)) }
                                            DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                                                DropdownMenuItem(text = { Text(stringResource(R.string.edit)) }, onClick = {
                                                    menuExpanded = false
                                                    // Return to Purchase History (tab 8) with filter preserved when coming from Product.
                                                    navVm.setBackOverrideTab(8)
                                                    navVm.setPreservePurchaseHistoryFilterOnReturn()
                                                    navVm.requestEditPurchase(item.purchaseId)
                                                    navVm.navigateTo(7)
                                                })
                                            }
                                        }
                                    }
                                    Spacer(Modifier.height(6.dp))
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Column {
                                            Text(stringResource(R.string.qty_at_price), style = MaterialTheme.typography.labelSmall)
                                            Text("${item.quantity.toStringAsFixed(2)} × ${CurrencyFormatter.formatInr(item.unitPrice)}", fontWeight = FontWeight.SemiBold)
                                        }
                                        Column {
                                            Text(stringResource(R.string.total_label), style = MaterialTheme.typography.labelSmall)
                                            Text(CurrencyFormatter.formatInr(item.lineTotal), fontWeight = FontWeight.SemiBold)
                                        }
                                    }
                                }
                            }
                            if (showReadonly && full != null) {
                                val dfCard = remember { SimpleDateFormat("dd MMM yy", Locale.getDefault()) }
                                PurchaseReadonlyDialog(
                                    onDismiss = { showReadonly = false },
                                    header = "#${full!!.id} • ${full!!.supplierName ?: stringResource(R.string.unknown_supplier)}",
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
                    Text(stringResource(R.string.notes_label), style = MaterialTheme.typography.labelSmall)
                    Text(notes)
                }
                Spacer(Modifier.height(10.dp))
                Column(modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp)) {
                    items.forEach { d ->
                        val pname = d.productName ?: stringResource(R.string.item_placeholder, d.item.productId)
                        val qty = d.item.quantity
                        val price = d.item.unitPrice
                        val line = d.item.lineTotal
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(pname)
                                Text(stringResource(R.string.line_item_qty_price, qty.toStringAsFixed(2), CurrencyFormatter.formatInr(price)), style = MaterialTheme.typography.labelSmall)
                            }
                            Text(CurrencyFormatter.formatInr(line), fontWeight = FontWeight.SemiBold)
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
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } }
    )
}

private fun Double.toStringAsFixed(digits: Int): String = String.format(java.util.Locale.getDefault(), "% .${digits}f", this).trimStart()

