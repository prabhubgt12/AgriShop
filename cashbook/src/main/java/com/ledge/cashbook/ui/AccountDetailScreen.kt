package com.ledge.cashbook.ui

import android.app.DatePickerDialog
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.draw.scale
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ledge.cashbook.R
import com.ledge.cashbook.util.Currency
import com.ledge.cashbook.data.local.entities.CashTxn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.combinedClickable
import kotlin.math.roundToInt
import java.text.SimpleDateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun AccountDetailScreen(accountId: Int, onBack: () -> Unit, openAdd: Boolean = false, vm: AccountDetailViewModel = hiltViewModel()) {
    LaunchedEffect(accountId) { vm.load(accountId) }

    val name by vm.accountName.collectAsState()
    val txns by vm.txns.collectAsState()
    val balance by vm.balance.collectAsState()

    var showAdd by remember { mutableStateOf(false) }
    var isCredit by remember { mutableStateOf(true) }
    var amount by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var dateMillis by remember { mutableStateOf(System.currentTimeMillis()) }
    var showDatePicker by remember { mutableStateOf(false) }
    var confirmDeleteTxn by remember { mutableStateOf<CashTxn?>(null) }
    // Long-press actions and edit states
    var actionTxn by remember { mutableStateOf<CashTxn?>(null) }
    var editTxn by remember { mutableStateOf<CashTxn?>(null) }
    var editIsCredit by remember { mutableStateOf(true) }
    var editAmount by remember { mutableStateOf("") }
    var editNote by remember { mutableStateOf("") }
    var editDateMillis by remember { mutableStateOf(System.currentTimeMillis()) }
    var showEditDatePicker by remember { mutableStateOf(false) }
    // Filter state
    var filterMenuOpen by remember { mutableStateOf(false) }
    var filterStart by remember { mutableStateOf<Long?>(null) }
    var filterEnd by remember { mutableStateOf<Long?>(null) }
    var showStartPicker by remember { mutableStateOf(false) }
    var showEndPicker by remember { mutableStateOf(false) }

    LaunchedEffect(openAdd) {
        if (openAdd) showAdd = true
    }

    // Long-press actions dialog (Edit/Delete)
    val pendingAction = actionTxn
    if (pendingAction != null) {
        AlertDialog(
            onDismissRequest = { actionTxn = null },
            title = { Text(stringResource(R.string.select_action)) },
            confirmButton = {
                TextButton(onClick = {
                    // Start edit with prefilled fields
                    editTxn = pendingAction
                    editIsCredit = pendingAction.isCredit
                    editAmount = pendingAction.amount.toString()
                    editNote = pendingAction.note ?: ""
                    editDateMillis = pendingAction.date
                    actionTxn = null
                }) { Text(stringResource(R.string.edit)) }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { confirmDeleteTxn = pendingAction; actionTxn = null }) { Text(stringResource(R.string.delete)) }
                    Spacer(Modifier.width(6.dp))
                    TextButton(onClick = { actionTxn = null }) { Text(stringResource(R.string.cancel)) }
                }
            }
        )
    }

    // Edit transaction dialog
    val toEdit = editTxn
    if (toEdit != null) {
        val editAmountValid = remember(editAmount) { editAmount.toDoubleOrNull()?.let { it > 0 } == true }
        val editNoteValid = remember(editNote) { editNote.isNotBlank() }
        AlertDialog(
            onDismissRequest = { editTxn = null },
            confirmButton = {
                TextButton(
                    enabled = editAmountValid && editNoteValid,
                    onClick = {
                        val amt = editAmount.toDoubleOrNull() ?: 0.0
                        if (amt <= 0 || editNote.isBlank()) return@TextButton
                        val updated = toEdit.copy(
                            date = editDateMillis,
                            amount = amt,
                            isCredit = editIsCredit,
                            note = editNote
                        )
                        vm.updateTxn(updated)
                        editTxn = null
                    }
                ) { Text(stringResource(R.string.update)) }
            },
            dismissButton = { TextButton(onClick = { editTxn = null }) { Text(stringResource(R.string.cancel)) } },
            title = { Text(stringResource(R.string.edit)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        FilterChip(selected = editIsCredit, onClick = { editIsCredit = true }, label = { Text(stringResource(R.string.credit)) })
                        FilterChip(selected = !editIsCredit, onClick = { editIsCredit = false }, label = { Text(stringResource(R.string.debit)) })
                    }
                    OutlinedTextField(
                        value = SimpleDateFormat("dd/MM/yy").format(Date(editDateMillis)),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.date)) },
                        trailingIcon = {
                            IconButton(onClick = { showEditDatePicker = true }) { Icon(Icons.Default.DateRange, contentDescription = "Pick date") }
                        }
                    )
                    OutlinedTextField(
                        value = editAmount,
                        onValueChange = { input -> editAmount = input.filter { it.isDigit() || it == '.' } },
                        label = { Text(stringResource(R.string.amount)) }
                    )
                    OutlinedTextField(
                        value = editNote,
                        onValueChange = { editNote = it },
                        label = { Text(stringResource(R.string.particular)) }
                    )
                }
            }
        )

        if (showEditDatePicker) {
            val state = rememberDatePickerState(initialSelectedDateMillis = editDateMillis)
            DatePickerDialog(
                onDismissRequest = { showEditDatePicker = false },
                confirmButton = {
                    TextButton(onClick = {
                        editDateMillis = state.selectedDateMillis ?: editDateMillis
                        showEditDatePicker = false
                    }) { Text(stringResource(R.string.ok)) }
                },
                dismissButton = { TextButton(onClick = { showEditDatePicker = false }) { Text(stringResource(R.string.cancel)) } }
            ) { DatePicker(state = state) }
        }
    }

    // Custom range pickers
    if (showStartPicker) {
        val state = rememberDatePickerState(initialSelectedDateMillis = filterStart ?: System.currentTimeMillis())
        DatePickerDialog(
            onDismissRequest = { showStartPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    filterStart = state.selectedDateMillis
                    showStartPicker = false
                    showEndPicker = true
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = { TextButton(onClick = { showStartPicker = false }) { Text(stringResource(R.string.cancel)) } }
        ) { DatePicker(state = state) }
    }
    if (showEndPicker) {
        val state = rememberDatePickerState(initialSelectedDateMillis = filterEnd ?: System.currentTimeMillis())
        DatePickerDialog(
            onDismissRequest = { showEndPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    // normalize end to end-of-day
                    val sel = state.selectedDateMillis
                    if (sel != null) {
                        val cal = java.util.Calendar.getInstance()
                        cal.timeInMillis = sel
                        cal.set(java.util.Calendar.HOUR_OF_DAY, 23)
                        cal.set(java.util.Calendar.MINUTE, 59)
                        cal.set(java.util.Calendar.SECOND, 59)
                        cal.set(java.util.Calendar.MILLISECOND, 999)
                        filterEnd = cal.timeInMillis
                    }
                    showEndPicker = false
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = { TextButton(onClick = { showEndPicker = false }) { Text(stringResource(R.string.cancel)) } }
        ) { DatePicker(state = state) }
    }

    // Confirm delete transaction dialog
    val txnToDelete = confirmDeleteTxn
    if (txnToDelete != null) {
        AlertDialog(
            onDismissRequest = { confirmDeleteTxn = null },
            title = { Text(stringResource(R.string.delete)) },
            text = { Text(stringResource(R.string.delete_txn_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteTxn(txnToDelete)
                    confirmDeleteTxn = null
                }) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = { TextButton(onClick = { confirmDeleteTxn = null }) { Text(stringResource(R.string.cancel)) } }
        )
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Text(
                            name,
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null) }
                    },
                    actions = {
                        val pos = balance >= 0
                        val chipBg = if (pos) Color(0xFFDFF6DD) else Color(0xFFFFE2E0)
                        val chipFg = if (pos) Color(0xFF0B6A0B) else Color(0xFF9A0007)
                        AssistChip(
                            onClick = {},
                            label = {
                                val labelText = stringResource(R.string.balance) + ": "
                                val amtText = Currency.inr(balance)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(labelText, style = MaterialTheme.typography.labelSmall)
                                    Text(amtText, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                }
                            },
                            colors = AssistChipDefaults.assistChipColors(containerColor = chipBg, labelColor = chipFg)
                        )
                        // Date filter button (no badge/tint)
                        IconButton(onClick = { filterMenuOpen = true }) {
                            Icon(Icons.Default.FilterList, contentDescription = "Filter")
                        }
                        DropdownMenu(expanded = filterMenuOpen, onDismissRequest = { filterMenuOpen = false }) {
                            DropdownMenuItem(text = { Text(stringResource(R.string.filter_today)) }, onClick = {
                                filterMenuOpen = false
                                val now = java.util.Calendar.getInstance()
                                now.set(java.util.Calendar.HOUR_OF_DAY, 0)
                                now.set(java.util.Calendar.MINUTE, 0)
                                now.set(java.util.Calendar.SECOND, 0)
                                now.set(java.util.Calendar.MILLISECOND, 0)
                                filterStart = now.timeInMillis
                                filterEnd = filterStart!! + 24L*60*60*1000 - 1
                            })
                            DropdownMenuItem(text = { Text(stringResource(R.string.filter_last_7_days)) }, onClick = {
                                filterMenuOpen = false
                                val cal = java.util.Calendar.getInstance()
                                cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
                                cal.set(java.util.Calendar.MINUTE, 0)
                                cal.set(java.util.Calendar.SECOND, 0)
                                cal.set(java.util.Calendar.MILLISECOND, 0)
                                filterEnd = cal.timeInMillis + 24L*60*60*1000 - 1
                                cal.add(java.util.Calendar.DAY_OF_YEAR, -6)
                                filterStart = cal.timeInMillis
                            })
                            DropdownMenuItem(text = { Text(stringResource(R.string.filter_this_month)) }, onClick = {
                                filterMenuOpen = false
                                val cal = java.util.Calendar.getInstance()
                                cal.set(java.util.Calendar.DAY_OF_MONTH, 1)
                                cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
                                cal.set(java.util.Calendar.MINUTE, 0)
                                cal.set(java.util.Calendar.SECOND, 0)
                                cal.set(java.util.Calendar.MILLISECOND, 0)
                                filterStart = cal.timeInMillis
                                cal.add(java.util.Calendar.MONTH, 1)
                                cal.add(java.util.Calendar.MILLISECOND, -1)
                                filterEnd = cal.timeInMillis
                            })
                            DropdownMenuItem(text = { Text(stringResource(R.string.filter_all)) }, onClick = {
                                filterMenuOpen = false
                                filterStart = null
                                filterEnd = null
                            })
                            Divider()
                            DropdownMenuItem(text = { Text(stringResource(R.string.filter_custom_range)) }, onClick = {
                                filterMenuOpen = false
                                showStartPicker = true
                            })
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        titleContentColor = MaterialTheme.colorScheme.onPrimary,
                        navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                        actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                    )
                )

                val isFiltered = filterStart != null || filterEnd != null
                if (isFiltered) {
                    val fmt = remember { SimpleDateFormat("dd MMM yyyy", java.util.Locale.getDefault()) }
                    val startStr = filterStart?.let { fmt.format(Date(it)) }
                    val endStr = filterEnd?.let { fmt.format(Date(it)) }
                    Surface(color = MaterialTheme.colorScheme.primary) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = when {
                                    startStr != null && endStr != null -> "$startStr - $endStr"
                                    startStr != null -> startStr
                                    endStr != null -> endStr
                                    else -> ""
                                },
                                color = MaterialTheme.colorScheme.onPrimary,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = { filterStart = null; filterEnd = null }) {
                                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.cancel), tint = MaterialTheme.colorScheme.onPrimary)
                            }
                        }
                    }
                }
            }
        },
        contentWindowInsets = WindowInsets.systemBars.only(WindowInsetsSides.Top)
    ) { padding ->
        BoxWithConstraints(Modifier.fillMaxSize()) {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    // Removed extra bottom padding so footer sits flush with nav bar
            ) {
            // Column weights for alignment
            // Combine Date + Particular into a single wider column keeping the same total width (0.9 + 1.3 = 2.2)
            val wDatePart = 2.2f
            val wAmt = 1.0f
            val headerBg = MaterialTheme.colorScheme.surfaceVariant

            // Apply date filter to transactions
            val filteredTxns = remember(txns, filterStart, filterEnd) {
                txns.filter { t ->
                    val sOk = filterStart?.let { t.date >= it } ?: true
                    val eOk = filterEnd?.let { t.date <= it } ?: true
                    sOk && eOk
                }
            }
            // Precompute running balances on filtered list
            val runningBalances = remember(filteredTxns) {
                var r = 0.0
                filteredTxns.map { t ->
                    r += if (t.isCredit) t.amount else -t.amount
                    r
                }
            }
            // Totals for credit and debit
            val totalCredit = remember(filteredTxns) { filteredTxns.filter { it.isCredit }.sumOf { it.amount } }
            val totalDebit = remember(filteredTxns) { filteredTxns.filter { !it.isCredit }.sumOf { it.amount } }

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                stickyHeader {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .background(headerBg)
                            .padding(vertical = 8.dp, horizontal = 6.dp), // Added vertical padding
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(stringResource(R.string.col_date_particular), fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(wDatePart))
                        Text(stringResource(R.string.col_credit), fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(wAmt), textAlign = TextAlign.End)
                        Text(stringResource(R.string.col_debit), fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(wAmt), textAlign = TextAlign.End)
                        Text(stringResource(R.string.col_balance), fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(wAmt), textAlign = TextAlign.End)
                    }
                    HorizontalDivider()
                }
                itemsIndexed(filteredTxns) { index, t ->
                    val run = runningBalances.getOrNull(index) ?: 0.0
                    // Theme-aware subtle backgrounds per row by type
                    val dark = androidx.compose.foundation.isSystemInDarkTheme()
                    val creditTint = if (!dark) Color(0x330B6A0B) else Color(0x6618A418)
                    val debitTint = if (!dark) Color(0x339A0007) else Color(0x66CF6671)
                    val rowBg = if (t.isCredit) creditTint else debitTint
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .background(rowBg)
                            .combinedClickable(
                                onClick = {},
                                onLongClick = { actionTxn = t }
                            )
                            .padding(vertical = 8.dp, horizontal = 6.dp), // Added horizontal padding
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(wDatePart)) {
                            Text(
                                SimpleDateFormat("dd/MM/yy").format(Date(t.date)),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                t.note ?: "-",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        Text(if (t.isCredit) Currency.inr(t.amount) else "-", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(wAmt), textAlign = TextAlign.End)
                        Text(if (!t.isCredit) Currency.inr(t.amount) else "-", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(wAmt), textAlign = TextAlign.End)
                        Text(Currency.inr(run), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(wAmt), textAlign = TextAlign.End)
                    }
                    HorizontalDivider()
                }
            }
            // Divider above footer for separation
            HorizontalDivider()
            // Sticky totals footer (outside list)
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom))
                    .padding(vertical = 6.dp, horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Total Credit
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.total_credit),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        Currency.inr(totalCredit),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF0B6A0B)
                    )
                }
                // Total Debit
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.total_debit),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        Currency.inr(totalDebit),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF9A0007)
                    )
                }
                // Quick add mini FABs aligned to the right
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 0.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End
                ) {
                    val haptic = LocalHapticFeedback.current
                    val creditIs = remember { MutableInteractionSource() }
                    val creditPressed by creditIs.collectIsPressedAsState()
                    val creditScale by animateFloatAsState(targetValue = if (creditPressed) 0.92f else 1f, label = "creditScale")

                    SmallFloatingActionButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            isCredit = true
                            showAdd = true
                        },
                        containerColor = Color(0xFF0B6A0B),
                        interactionSource = creditIs,
                        modifier = Modifier.scale(creditScale)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = stringResource(R.string.credit))
                    }

                    Spacer(Modifier.width(8.dp))

                    val debitIs = remember { MutableInteractionSource() }
                    val debitPressed by debitIs.collectIsPressedAsState()
                    val debitScale by animateFloatAsState(targetValue = if (debitPressed) 0.92f else 1f, label = "debitScale")

                    SmallFloatingActionButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            isCredit = false
                            showAdd = true
                        },
                        containerColor = Color(0xFF9A0007),
                        interactionSource = debitIs,
                        modifier = Modifier.scale(debitScale)
                    ) {
                        Icon(Icons.Default.Remove, contentDescription = stringResource(R.string.debit))
                    }
                }
            }
            }
        }
    }

    if (showAdd) {
        val amountValid = remember(amount) { amount.toDoubleOrNull()?.let { it > 0 } == true }
        val noteValid = remember(note) { note.isNotBlank() }
        AlertDialog(
            onDismissRequest = { showAdd = false },
            confirmButton = {
                TextButton(
                    enabled = amountValid && noteValid,
                    onClick = {
                        val amt = amount.toDoubleOrNull() ?: 0.0
                        if (amt <= 0 || note.isBlank()) return@TextButton
                        vm.addTxn(dateMillis, amt, isCredit, note)
                        showAdd = false
                        isCredit = true
                        amount = ""
                        note = ""
                        dateMillis = System.currentTimeMillis()
                    }
                ) { Text(stringResource(R.string.save)) }
            },
            dismissButton = { TextButton(onClick = { showAdd = false }) { Text(stringResource(R.string.cancel)) } },
            title = { Text(stringResource(R.string.add_to_book)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        FilterChip(selected = isCredit, onClick = { isCredit = true }, label = { Text(stringResource(R.string.credit)) })
                        FilterChip(selected = !isCredit, onClick = { isCredit = false }, label = { Text(stringResource(R.string.debit)) })
                    }
                    OutlinedTextField(
                        value = SimpleDateFormat("dd/MM/yy").format(Date(dateMillis)),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.date)) },
                        trailingIcon = {
                            IconButton(onClick = { showDatePicker = true }) {
                                Icon(Icons.Default.DateRange, contentDescription = "Pick date")
                            }
                        }
                    )
                    OutlinedTextField(
                        value = amount,
                        onValueChange = { input -> amount = input.filter { it.isDigit() || it == '.' } },
                        label = { Text(stringResource(R.string.amount)) }
                    )
                    OutlinedTextField(
                        value = note,
                        onValueChange = { note = it },
                        label = { Text(stringResource(R.string.particular)) }
                    )
                }
            }
        )

        if (showDatePicker) {
            val state = rememberDatePickerState(initialSelectedDateMillis = dateMillis)
            DatePickerDialog(
                onDismissRequest = { showDatePicker = false },
                confirmButton = {
                    TextButton(onClick = {
                        dateMillis = state.selectedDateMillis ?: dateMillis
                        showDatePicker = false
                    }) { Text(stringResource(R.string.ok)) }
                },
                dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text(stringResource(R.string.cancel)) } }
            ) {
                DatePicker(state = state)
            }
        }
    }
}
