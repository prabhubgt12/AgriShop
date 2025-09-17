package com.ledge.cashbook.ui

import android.app.DatePickerDialog
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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

    LaunchedEffect(openAdd) {
        if (openAdd) showAdd = true
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(name) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null) }
                },
                actions = {
                    val pos = balance >= 0
                    val chipBg = if (pos) Color(0xFFDFF6DD) else Color(0xFFFFE2E0)
                    val chipFg = if (pos) Color(0xFF0B6A0B) else Color(0xFF9A0007)
                    AssistChip(
                        onClick = {},
                        label = { Text(stringResource(R.string.balance) + ": " + Currency.inr(balance)) },
                        colors = AssistChipDefaults.assistChipColors(containerColor = chipBg, labelColor = chipFg)
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        },
        contentWindowInsets = WindowInsets.systemBars
    ) { padding ->
        BoxWithConstraints(Modifier.fillMaxSize()) {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(bottom = 8.dp) // Adjusted padding here
            ) {
            // Column weights for alignment
            val wDate = 0.9f
            val wPart = 1.3f
            val wAmt = 1.0f
            val headerBg = MaterialTheme.colorScheme.surfaceVariant

            // Precompute running balances to avoid mutating state during list iteration
            val runningBalances = remember(txns) {
                var r = 0.0
                txns.map { t ->
                    r += if (t.isCredit) t.amount else -t.amount
                    r
                }
            }

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
                        Text(stringResource(R.string.col_date), fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(wDate))
                        Text(stringResource(R.string.col_particular), fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(wPart))
                        Text(stringResource(R.string.col_credit), fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(wAmt), textAlign = TextAlign.End)
                        Text(stringResource(R.string.col_debit), fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(wAmt), textAlign = TextAlign.End)
                        Text(stringResource(R.string.col_balance), fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(wAmt), textAlign = TextAlign.End)
                    }
                    HorizontalDivider()
                }
                itemsIndexed(txns) { index, t ->
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
                            .padding(vertical = 8.dp, horizontal = 6.dp), // Added horizontal padding
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(SimpleDateFormat("dd/MM/yy").format(Date(t.date)), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(wDate))
                        Text(t.note ?: "-", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(wPart))
                        Text(if (t.isCredit) Currency.inr(t.amount) else "-", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(wAmt), textAlign = TextAlign.End)
                        Text(if (!t.isCredit) Currency.inr(t.amount) else "-", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(wAmt), textAlign = TextAlign.End)
                        Text(Currency.inr(run), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(wAmt), textAlign = TextAlign.End)
                    }
                    HorizontalDivider()
                }
            }
            }
            
            // Draggable FAB overlay (same pattern as LedgerBook)
            val density = LocalDensity.current
            val fabSize = 56.dp
            val edge = 16.dp
            val topInsetPx = with(density) { WindowInsets.statusBars.getTop(this).toFloat() }
            val bottomInsetPx = with(density) { WindowInsets.navigationBars.getBottom(this).toFloat() }
            val maxX = with(density) { (this@BoxWithConstraints.maxWidth - fabSize - edge).toPx() }
            val maxY = with(density) { (this@BoxWithConstraints.maxHeight - fabSize - edge).toPx() } - bottomInsetPx
            val minX = with(density) { edge.toPx() }
            val minY = topInsetPx + with(density) { edge.toPx() }
            var offsetX by remember(this@BoxWithConstraints.maxWidth, this@BoxWithConstraints.maxHeight, topInsetPx, bottomInsetPx) { mutableStateOf(maxX.coerceAtLeast(minX)) }
            var offsetY by remember(this@BoxWithConstraints.maxWidth, this@BoxWithConstraints.maxHeight, topInsetPx, bottomInsetPx) { mutableStateOf(maxY.coerceAtLeast(minY)) }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.navigationBars)
            ) {
                FloatingActionButton(
                    onClick = { showAdd = true },
                    modifier = Modifier
                        .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
                        .pointerInput(Unit) {
                            detectDragGestures { change, dragAmount ->
                                change.consume()
                                offsetX = (offsetX + dragAmount.x).coerceIn(minX, maxX)
                                offsetY = (offsetY + dragAmount.y).coerceIn(minY, maxY)
                            }
                        }
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                }
            }
        }
    }

    if (showAdd) {
        AlertDialog(
            onDismissRequest = { showAdd = false },
            confirmButton = {
                TextButton(onClick = {
                    val amt = amount.toDoubleOrNull() ?: 0.0
                    vm.addTxn(dateMillis, amt, isCredit, note.ifBlank { null })
                    showAdd = false
                    isCredit = true
                    amount = ""
                    note = ""
                    dateMillis = System.currentTimeMillis()
                }) { Text(stringResource(R.string.save)) }
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
                    OutlinedTextField(value = amount, onValueChange = { input -> amount = input.filter { it.isDigit() || it == '.' } }, label = { Text(stringResource(R.string.amount)) })
                    OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text(stringResource(R.string.particular)) })
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
