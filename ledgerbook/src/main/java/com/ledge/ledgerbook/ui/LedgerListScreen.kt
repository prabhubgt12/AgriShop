package com.ledge.ledgerbook.ui
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import com.ledge.ledgerbook.ui.theme.ThemeViewModel
import com.ledge.ledgerbook.util.CurrencyFormatter
import com.ledge.ledgerbook.util.PdfShareUtils
import java.text.SimpleDateFormat
import java.util.Date

typealias LedgerItemVM = LedgerViewModel.LedgerItemVM

// Helper to display INR without fractional digits for parent summary
private fun formatInrNoDecimals(value: Double): String {
    val full = CurrencyFormatter.formatInr(value)
    // Strip any decimal portion like .00 or .50; keep sign and currency symbol/grouping
    return full.replace(Regex("\\.[0-9]+"), "")
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun LedgerListScreen(vm: LedgerViewModel = hiltViewModel(), themeViewModel: ThemeViewModel = hiltViewModel()) {
    val state by vm.state.collectAsState()
    // Settings flag
    val groupingEnabled by themeViewModel.groupingEnabled.collectAsState()
    // Precompute groups in composable scope (cannot call remember inside LazyListScope)
    val groups = remember(state.items) { state.items.groupBy { it.name } }
    val sortedGroups = remember(groups) { groups.entries.sortedBy { it.key.lowercase() } }

    // Dialog-local states
    val showAdd = remember { mutableStateOf(false) }
    val addPrefillName = remember { mutableStateOf<String?>(null) }
    val partialForId = remember { mutableStateOf<Int?>(null) }
    val partialAmount = remember { mutableStateOf("") }
    val partialDateMillis = remember { mutableStateOf(System.currentTimeMillis()) }
    val partialNote = remember { mutableStateOf("") }
    val previewInterest = remember { mutableStateOf(0.0) }
    val previewOutstanding = remember { mutableStateOf(0.0) }
    val detailsForId = remember { mutableStateOf<Int?>(null) }
    val confirmDeleteId = remember { mutableStateOf<Int?>(null) }
    val context = LocalContext.current

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { addPrefillName.value = null; showAdd.value = true }) { Icon(Icons.Default.Add, contentDescription = "Add") }
        }
    ) { padding ->
        LazyColumn(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                // In-content header row with title only
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Ledger Book",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(Modifier.height(10.dp))
                // Overview cards row 1
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OverviewCard(
                        title = "Total Lend",
                        value = formatInrNoDecimals(state.totalLend),
                        modifier = Modifier.weight(1f),
                        container = Color(0xFFDFF6DD),
                        content = Color(0xFF0B6A0B)
                    )
                    OverviewCard(
                        title = "Lend Interest",
                        value = formatInrNoDecimals(state.totalLendInterest),
                        modifier = Modifier.weight(1f),
                        container = Color(0xFFDFF6DD),
                        content = Color(0xFF0B6A0B)
                    )
                }
                Spacer(Modifier.height(10.dp))
                // Overview cards row 2
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OverviewCard(
                        title = "Total Borrow",
                        value = formatInrNoDecimals(state.totalBorrow),
                        modifier = Modifier.weight(1f),
                        container = Color(0xFFFFE2E0),
                        content = Color(0xFF9A0007)
                    )
                    OverviewCard(
                        title = "Borrow Interest",
                        value = formatInrNoDecimals(state.totalBorrowInterest),
                        modifier = Modifier.weight(1f),
                        container = Color(0xFFFFE2E0),
                        content = Color(0xFF9A0007)
                    )
                }
                Spacer(Modifier.height(10.dp))
                val isPositive = state.finalAmount >= 0
                OverviewCard(
                    title = "Final Amount",
                    value = formatInrNoDecimals(state.finalAmount),
                    modifier = Modifier.fillMaxWidth(),
                    container = if (isPositive) Color(0xFFDFF6DD) else Color(0xFFFFE2E0),
                    content = if (isPositive) Color(0xFF0B6A0B) else Color(0xFF9A0007)
                )
                Spacer(Modifier.height(10.dp))
            }

            if (groupingEnabled) {
                // Group by user name and show expandable parent + children (sorted A–Z)
                items(sortedGroups, key = { it.key }) { entry ->
                val name = entry.key
                val itemsForUser = entry.value
                val expanded = rememberSaveable(name) { mutableStateOf(false) }

                // Parent card with totals
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { expanded.value = !expanded.value },
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .height(IntrinsicSize.Min)
                            .padding(12.dp)
                    ) {
                        Box(
                            Modifier
                                .width(4.dp)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.primary)
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text(name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                                Icon(
                                    imageVector = if (expanded.value) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                                    contentDescription = if (expanded.value) "Collapse" else "Expand"
                                )
                                Spacer(Modifier.width(4.dp))
                                val parentMenuOpen = remember(name) { mutableStateOf(false) }
                                Box {
                                    IconButton(onClick = { parentMenuOpen.value = true }) {
                                        Icon(
                                            imageVector = Icons.Default.MoreVert,
                                            contentDescription = "More",
                                            tint = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                    DropdownMenu(expanded = parentMenuOpen.value, onDismissRequest = { parentMenuOpen.value = false }) {
                                        DropdownMenuItem(
                                            text = { Text("Share Receipt") },
                                            onClick = {
                                                parentMenuOpen.value = false
                                                PdfShareUtils.shareGroup(context, name, itemsForUser)
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Add to Book") },
                                            onClick = {
                                                parentMenuOpen.value = false
                                                addPrefillName.value = name
                                                showAdd.value = true
                                            }
                                        )
                                    }
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            // Compute net values (LEND - BORROW) per user
                            val lendPrincipal = itemsForUser.filter { it.type == "LEND" }.sumOf { it.principal }
                            val borrowPrincipal = itemsForUser.filter { it.type == "BORROW" }.sumOf { it.principal }
                            val netPrincipal = lendPrincipal - borrowPrincipal

                            val lendInterest = itemsForUser.filter { it.type == "LEND" }.sumOf { it.accrued }
                            val borrowInterest = itemsForUser.filter { it.type == "BORROW" }.sumOf { it.accrued }
                            val netInterest = lendInterest - borrowInterest

                            val lendTotal = itemsForUser.filter { it.type == "LEND" }.sumOf { it.total }
                            val borrowTotal = itemsForUser.filter { it.type == "BORROW" }.sumOf { it.total }
                            val netTotal = lendTotal - borrowTotal

                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                LabelValue(
                                    label = "Total Principal",
                                    value = formatInrNoDecimals(netPrincipal),
                                    modifier = Modifier.weight(1f)
                                )
                                LabelValue(
                                    label = "Total Interest",
                                    value = formatInrNoDecimals(netInterest),
                                    modifier = Modifier.weight(1f)
                                )
                                // Total chip (compact)
                                val pos = netTotal >= 0
                                val chipBg = if (pos) Color(0xFFDFF6DD) else Color(0xFFFFE2E0)
                                val chipFg = if (pos) Color(0xFF0B6A0B) else Color(0xFF9A0007)
                                Column(Modifier.weight(1f)) {
                                    Text("Total Amount", style = MaterialTheme.typography.labelSmall)
                                    Spacer(Modifier.height(2.dp))
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(chipBg)
                                            .padding(vertical = 4.dp, horizontal = 6.dp)
                                    ) {
                                        Text(
                                            formatInrNoDecimals(netTotal),
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = chipFg
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Children list (existing cards) when expanded
                if (expanded.value) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(start = 8.dp, top = 6.dp, bottom = 2.dp),
                    ) {
                        itemsForUser.forEach { item ->
                            Spacer(Modifier.height(8.dp))
                            LedgerRow(
                                vm = item,
                                onClick = {
                                    detailsForId.value = item.id
                                    vm.beginEdit(item.id)
                                },
                                onHistory = { vm.openPayments(item.id) },
                                onEdit = { vm.beginEdit(item.id) },
                                onPartial = {
                                    partialForId.value = item.id
                                    partialAmount.value = ""
                                    partialNote.value = ""
                                    partialDateMillis.value = System.currentTimeMillis()
                                },
                                onDelete = { confirmDeleteId.value = item.id },
                                onShare = { PdfShareUtils.shareEntry(context, item) },
                                showTypeChip = false
                            )
                        }
                    }
                }
            }
            } else {
                // Flat list (original child cards only)
                items(state.items, key = { it.id }) { item ->
                    LedgerRow(
                        vm = item,
                        onClick = {
                            detailsForId.value = item.id
                            vm.beginEdit(item.id)
                        },
                        onHistory = { vm.openPayments(item.id) },
                        onEdit = { vm.beginEdit(item.id) },
                        onPartial = {
                            partialForId.value = item.id
                            partialAmount.value = ""
                            partialNote.value = ""
                            partialDateMillis.value = System.currentTimeMillis()
                        },
                        onDelete = { confirmDeleteId.value = item.id },
                        onShare = { PdfShareUtils.shareEntry(context, item) },
                        showName = true
                    )
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
        // Close Scaffold content lambda
    }

    // Add/Edit dialog
    val editing by vm.editingEntry.collectAsState()
    if (showAdd.value) {
        LedgerAddEditScreen(
            onDismiss = { showAdd.value = false },
            onSave = { entry -> vm.saveNew(entry); showAdd.value = false },
            prefillName = addPrefillName.value
        )
    }
    if (editing != null && (detailsForId.value == null)) {
        LedgerAddEditScreen(
            existing = editing,
            onDismiss = { vm.clearEdit() },
            onSave = { entry -> vm.saveUpdate(entry); vm.clearEdit() }
        )
    }

    // Details dialog (read-only)
    val detailsId = detailsForId.value
    if (detailsId != null) {
        val e = editing
        if (e != null && e.id == detailsId) {
            CenteredAlertDialog(
                onDismissRequest = { detailsForId.value = null; vm.clearEdit() },
                title = { Text("Entry Details") },
                text = {
                    Column(Modifier.fillMaxWidth()) {
                        val isLend = e.type == "LEND"
                        val chipBg = if (isLend) Color(0xFFDFF6DD) else Color(0xFFFFE2E0)
                        val chipFg = if (isLend) Color(0xFF0B6A0B) else Color(0xFF9A0007)
                        AssistChip(onClick = {}, label = { Text(e.type) }, colors = AssistChipDefaults.assistChipColors(containerColor = chipBg, labelColor = chipFg))
                        Spacer(Modifier.height(8.dp))

                        LabelValue(label = "Name", value = e.name)
                        Spacer(Modifier.height(8.dp))
                        LabelValue(label = "Interest Type", value = e.interestType)
                        Spacer(Modifier.height(8.dp))
                        LabelValue(label = "Rate Basis", value = (e.period ?: "MONTHLY").uppercase())
                        if (e.interestType.equals("COMPOUND", true)) {
                            Spacer(Modifier.height(8.dp))
                            LabelValue(label = "Duration Type", value = e.compoundPeriod.uppercase())
                        }
                        Spacer(Modifier.height(8.dp))
                        LabelValue(label = "Principal", value = CurrencyFormatter.formatInr(e.principal))
                        Spacer(Modifier.height(8.dp))
                        LabelValue(label = "Interest Rate", value = "${e.rateRupees}%")
                        Spacer(Modifier.height(8.dp))
                        LabelValue(label = "From Date", value = SimpleDateFormat("dd/MM/yyyy").format(Date(e.fromDate)))
                        if (!e.notes.isNullOrBlank()) {
                            Spacer(Modifier.height(8.dp))
                            LabelValue(label = "Notes", value = e.notes)
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { detailsForId.value = null; vm.clearEdit() }) { Text("Close") } }
            )
        }
    }

    // Partial payment dialog
    val showPartial = partialForId.value != null
    if (showPartial) {
        val entryId = partialForId.value!!
        LaunchedEffect(entryId, partialDateMillis.value, partialAmount.value) {
            val (accrued, _, outstanding) = vm.computeAt(entryId, partialDateMillis.value)
            previewInterest.value = accrued
            val amt = partialAmount.value.toDoubleOrNull() ?: 0.0
            previewOutstanding.value = (outstanding - amt).coerceAtLeast(0.0)
        }
        CenteredAlertDialog(
            onDismissRequest = { partialForId.value = null },
            title = { Text("Partial Payment") },
            text = {
                Column(Modifier.fillMaxWidth()) {
                    val showPicker = remember { mutableStateOf(false) }
                    OutlinedTextField(
                        value = SimpleDateFormat("dd/MM/yyyy").format(Date(partialDateMillis.value)),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Payment Date") },
                        modifier = Modifier.fillMaxWidth(),
                        trailingIcon = { TextButton(onClick = { showPicker.value = true }) { Text("Pick") } }
                    )
                    if (showPicker.value) {
                        val dpState = rememberDatePickerState(initialSelectedDateMillis = partialDateMillis.value)
                        CenteredDatePickerDialog(
                            onDismissRequest = { showPicker.value = false },
                            onConfirm = {
                                partialDateMillis.value = dpState.selectedDateMillis ?: partialDateMillis.value
                                showPicker.value = false
                            }
                        ) { DatePicker(state = dpState) }
                    }

                    OutlinedTextField(
                        value = partialAmount.value,
                        onValueChange = { partialAmount.value = it },
                        label = { Text("Amount") },
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("Interest till date: ${CurrencyFormatter.formatInr(previewInterest.value)}")
                    Text("Remaining after payment: ${CurrencyFormatter.formatInr(previewOutstanding.value)}")
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = partialNote.value,
                        onValueChange = { partialNote.value = it },
                        label = { Text("Note (optional)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val amt = partialAmount.value.toDoubleOrNull() ?: 0.0
                    partialForId.value?.let { vm.applyPartial(it, amt, partialDateMillis.value) }
                    partialForId.value = null
                }) { Text("Apply") }
            },
            dismissButton = { TextButton(onClick = { partialForId.value = null }) { Text("Cancel") } }
        )
    }

    // Payment history dialog
    val paymentsEntryId by vm.paymentsEntryId.collectAsState()
    if (paymentsEntryId != null) {
        val payments by vm.paymentsForViewing.collectAsState()
        CenteredAlertDialog(
            onDismissRequest = { vm.closePayments() },
            title = { Text("Payment History") },
            text = {
                if (payments.isEmpty()) {
                    Text("No payments yet.")
                } else {
                    LazyColumn(Modifier.fillMaxWidth()) {
                        items(payments) { p ->
                            Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                                Text(CurrencyFormatter.formatInr(p.amount), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                                Text(SimpleDateFormat("dd/MM/yyyy").format(Date(p.date)), style = MaterialTheme.typography.labelSmall)
                                if (!p.note.isNullOrBlank()) {
                                    Text(p.note, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { vm.closePayments() }) { Text("Close") } }
        )
    }

    // Confirm delete dialog
    val deleteId = confirmDeleteId.value
    if (deleteId != null) {
        CenteredAlertDialog(
            onDismissRequest = { confirmDeleteId.value = null },
            title = { Text("Delete Entry") },
            text = { Text("Are you sure you want to delete this entry? This will also remove its payments. This action cannot be undone.") },
            confirmButton = {
                TextButton(onClick = { vm.delete(deleteId); confirmDeleteId.value = null }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDeleteId.value = null }) { Text("Cancel") } }
        )
    }

}


@Composable
private fun OverviewCard(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
    container: Color = MaterialTheme.colorScheme.surface,
    content: Color = MaterialTheme.colorScheme.onSurface
) {
    Card(modifier = modifier) {
        Column(Modifier.padding(12.dp)) {
            Text(title, style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.height(4.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(container)
                    .padding(vertical = 6.dp, horizontal = 8.dp)
            ) {
                Text(value, style = MaterialTheme.typography.titleMedium, color = content)
            }
        }
    }
}

@Composable
private fun LabelValue(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(label, style = MaterialTheme.typography.labelSmall)
        Spacer(Modifier.height(2.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CenteredAlertDialog(
    onDismissRequest: () -> Unit,
    title: @Composable (() -> Unit)? = null,
    text: @Composable (() -> Unit)? = null,
    confirmButton: @Composable () -> Unit,
    dismissButton: (@Composable () -> Unit)? = null
) {
    BasicAlertDialog(onDismissRequest = onDismissRequest) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                shape = MaterialTheme.shapes.medium,
                tonalElevation = 6.dp,
                modifier = Modifier.fillMaxWidth(0.9f)
            ) {
                Column(Modifier.padding(24.dp)) {
                    title?.let {
                        it()
                        Spacer(Modifier.height(16.dp))
                    }
                    text?.let {
                        it()
                        Spacer(Modifier.height(24.dp))
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        dismissButton?.let {
                            it()
                            Spacer(Modifier.width(8.dp))
                        }
                        confirmButton()
                    }
                }
            }
        }
    }
}

@Composable
private fun CenteredDatePickerDialog(
    onDismissRequest: () -> Unit,
    onConfirm: () -> Unit,
    content: @Composable () -> Unit
) {
    Dialog(onDismissRequest = onDismissRequest, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                shape = MaterialTheme.shapes.medium,
                tonalElevation = 6.dp,
                modifier = Modifier.fillMaxWidth(0.9f)
            ) {
                Column(Modifier.padding(16.dp)) {
                    content()
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = onDismissRequest) { Text("Cancel") }
                        Spacer(Modifier.width(8.dp))
                        TextButton(onClick = onConfirm) { Text("OK") }
                    }
                }
            }
        }
    }
}

@Composable
private fun LedgerRow(
    vm: LedgerItemVM,
    onClick: () -> Unit,
    onHistory: () -> Unit,
    onEdit: () -> Unit,
    onPartial: () -> Unit,
    onDelete: () -> Unit,
    onShare: () -> Unit,
    showName: Boolean = false,
    showTypeChip: Boolean = true
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            val topBarVisible = showName || showTypeChip
            val openMenu = remember { mutableStateOf(false) }
            if (topBarVisible) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    // Column 1 (matches grid first column)
                    Box(Modifier.weight(1f)) {
                        if (showName) {
                            Text(
                                vm.name,
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                    }

                    // Inter-column gap (matches rows below)
                    Spacer(Modifier.width(16.dp))

                    // Column 2 (matches grid second column): chip at start, menu at end
                    Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                        if (showTypeChip) {
                            val isLend = vm.type == "LEND"
                            val chipBg = if (isLend) Color(0xFFDFF6DD) else Color(0xFFFFE2E0)
                            val chipFg = if (isLend) Color(0xFF0B6A0B) else Color(0xFF9A0007)
                            AssistChip(
                                onClick = {},
                                label = { Text(vm.type, style = MaterialTheme.typography.labelSmall) },
                                colors = AssistChipDefaults.assistChipColors(
                                    containerColor = chipBg,
                                    labelColor = chipFg
                                )
                            )
                        }
                        Spacer(Modifier.weight(1f))
                        Box {
                            IconButton(onClick = { openMenu.value = true }) {
                                Icon(
                                    imageVector = Icons.Default.MoreVert,
                                    contentDescription = "More",
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            DropdownMenu(expanded = openMenu.value, onDismissRequest = { openMenu.value = false }) {
                                DropdownMenuItem(text = { Text("Share Receipt") }, onClick = { openMenu.value = false; onShare() })
                                DropdownMenuItem(text = { Text("Payment History") }, onClick = { openMenu.value = false; onHistory() })
                                DropdownMenuItem(text = { Text("Partial Payment") }, onClick = { openMenu.value = false; onPartial() })
                                DropdownMenuItem(text = { Text("Edit") }, onClick = { openMenu.value = false; onEdit() })
                                DropdownMenuItem(text = { Text("Delete") }, onClick = { openMenu.value = false; onDelete() })
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            val msPerDay = 86_400_000L
            val daysTotal = (((System.currentTimeMillis() - vm.fromDateMillis) / msPerDay).toInt()).coerceAtLeast(0)
            val years = daysTotal / 365
            val remAfterYears = daysTotal % 365
            val months = remAfterYears / 30
            val days = remAfterYears % 30
            val totalTime = buildString {
                if (years > 0) append("${years} Years ")
                if (months > 0) append("${months} Months ")
                append("${days} Days")
            }

            Column(Modifier.fillMaxWidth()) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    LabelValue(label = "Principal", value = formatInrNoDecimals(vm.principal), modifier = Modifier.weight(1f))
                    Box(Modifier.weight(1f)) {
                        LabelValue(label = "Interest Rate", value = "${vm.rate}% ${vm.rateBasis}")
                        if (!topBarVisible) {
                            // Move 3-dots here when header row is hidden (grouping mode)
                            Box(Modifier.fillMaxWidth()) {
                                IconButton(onClick = { openMenu.value = true }, modifier = Modifier.align(Alignment.TopEnd)) {
                                    Icon(
                                        imageVector = Icons.Default.MoreVert,
                                        contentDescription = "More",
                                        tint = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                            DropdownMenu(expanded = openMenu.value, onDismissRequest = { openMenu.value = false }) {
                                DropdownMenuItem(text = { Text("Share Receipt") }, onClick = { openMenu.value = false; onShare() })
                                DropdownMenuItem(text = { Text("Payment History") }, onClick = { openMenu.value = false; onHistory() })
                                DropdownMenuItem(text = { Text("Partial Payment") }, onClick = { openMenu.value = false; onPartial() })
                                DropdownMenuItem(text = { Text("Edit") }, onClick = { openMenu.value = false; onEdit() })
                                DropdownMenuItem(text = { Text("Delete") }, onClick = { openMenu.value = false; onDelete() })
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    LabelValue(label = "From Date", value = vm.dateStr, modifier = Modifier.weight(1f))
                    LabelValue(label = "Total Time", value = totalTime, modifier = Modifier.weight(1f))
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    LabelValue(label = "Interest", value = formatInrNoDecimals(vm.accrued), modifier = Modifier.weight(1f))
                    val isLendChip = vm.type == "LEND"
                    val chipBg2 = if (isLendChip) Color(0xFFDFF6DD) else Color(0xFFFFE2E0)
                    val chipFg2 = if (isLendChip) Color(0xFF0B6A0B) else Color(0xFF9A0007)
                    Column(Modifier.weight(1f)) {
                        Text("Total Amount", style = MaterialTheme.typography.labelSmall)
                        Spacer(Modifier.height(2.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(chipBg2)
                                .padding(vertical = 4.dp, horizontal = 6.dp)
                        ) {
                            Text(
                                formatInrNoDecimals(vm.total),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = chipFg2
                            )
                        }
                    }
                }
            }
        }
    }
}
