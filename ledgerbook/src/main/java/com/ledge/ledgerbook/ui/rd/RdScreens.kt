package com.ledge.ledgerbook.ui.rd

import android.app.DatePickerDialog
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ledge.ledgerbook.R
import com.ledge.ledgerbook.data.local.entities.RdAccount
import com.ledge.ledgerbook.data.local.entities.RdDeposit
import com.ledge.ledgerbook.util.CurrencyFormatter
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import androidx.compose.ui.graphics.Color

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RdListScreen(
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onOpenDetail: (Long) -> Unit,
    vm: RdViewModel = hiltViewModel()
) {
    BackHandler { onBack() }
    val list by vm.accounts.collectAsState()

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.rd_book)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = stringResource(R.string.cancel))
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAdd) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.add_rd))
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(list) { a ->
                Card(
                    onClick = { onOpenDetail(a.id) },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.weight(1f)) {
                                Text(a.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                Text(
                                    stringResource(R.string.rd_start_date_value, a.startDateStr),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                stringResource(R.string.rd_rate_value, "%.2f".format(a.annualRatePercent)),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column {
                                Text(
                                    stringResource(R.string.rd_total_deposited_value, fmtMoneyNo(a.totalDeposited)),
                                    style = MaterialTheme.typography.labelMedium
                                )
                                Text(
                                    stringResource(R.string.rd_accrued_interest_value, fmtMoneyNo(a.accruedInterest)),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Color(0xFF2E7D32)
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    stringResource(R.string.rd_total_value_value, fmtMoneyNo(a.totalValue)),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    stringResource(R.string.rd_installment_value, fmtMoneyNo(a.installmentAmount)),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            item {
                Spacer(Modifier.height(60.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RdAddScreen(
    onBack: () -> Unit,
    onSaved: (Long) -> Unit,
    rdId: Long? = null,
    vm: RdViewModel = hiltViewModel()
) {
    BackHandler { onBack() }
    val ctx = LocalContext.current
    val zone = ZoneId.systemDefault()
    val fmt = remember { DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.ENGLISH) }

    var name by remember { mutableStateOf("") }
    var installment by remember { mutableStateOf("") }
    var rateAnnual by remember { mutableStateOf("") }
    var tenure by remember { mutableStateOf("") }
    var tenureYears by remember { mutableStateOf(true) }
    var startDate by remember { mutableStateOf<LocalDate?>(null) }
    var autoPay by remember { mutableStateOf(false) }

    LaunchedEffect(rdId) {
        if (rdId != null) {
            vm.getById(rdId)?.let { a ->
                name = a.name
                installment = a.installmentAmount.toString()
                rateAnnual = a.annualRatePercent.toString()
                autoPay = a.autoPay
                if (a.tenureMonths % 12 == 0) {
                    tenureYears = true
                    tenure = (a.tenureMonths / 12).toString()
                } else {
                    tenureYears = false
                    tenure = a.tenureMonths.toString()
                }
                startDate = Instant.ofEpochMilli(a.startDateMillis).atZone(zone).toLocalDate()
            }
        }
    }

    val startDateText = startDate?.format(fmt).orEmpty()
    val showEdit = rdId != null

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(if (showEdit) R.string.edit_rd else R.string.add_rd)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = stringResource(R.string.cancel))
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            label = { Text(stringResource(R.string.rd_name)) },
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = installment,
                            onValueChange = { installment = it.filter { ch -> ch.isDigit() || ch == '.' } },
                            label = { Text(stringResource(R.string.rd_installment_amount)) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = rateAnnual,
                            onValueChange = { rateAnnual = it.filter { ch -> ch.isDigit() || ch == '.' } },
                            label = { Text(stringResource(R.string.rd_annual_rate)) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(stringResource(R.string.rd_auto_pay), style = MaterialTheme.typography.labelLarge)
                                Text(
                                    stringResource(R.string.rd_auto_pay_hint),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(checked = autoPay, onCheckedChange = { autoPay = it })
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            FilterChip(
                                selected = tenureYears,
                                onClick = { tenureYears = true },
                                label = { Text(stringResource(R.string.years)) }
                            )
                            FilterChip(
                                selected = !tenureYears,
                                onClick = { tenureYears = false },
                                label = { Text(stringResource(R.string.months)) }
                            )
                        }
                        OutlinedTextField(
                            value = tenure,
                            onValueChange = { tenure = it.filter { ch -> ch.isDigit() } },
                            label = { Text(stringResource(R.string.rd_tenure)) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                        fun openDatePicker() {
                            val base = startDate ?: LocalDate.now()
                            DatePickerDialog(
                                ctx,
                                { _, y, m, d ->
                                    startDate = LocalDate.of(y, m + 1, d)
                                },
                                base.year,
                                base.monthValue - 1,
                                base.dayOfMonth
                            ).show()
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { openDatePicker() }
                        ) {
                            OutlinedTextField(
                                value = startDateText,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text(stringResource(R.string.rd_start_date)) },
                                trailingIcon = {
                                    IconButton(onClick = { openDatePicker() }) {
                                        Icon(
                                            Icons.Filled.CalendarMonth,
                                            contentDescription = stringResource(R.string.rd_start_date)
                                        )
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            TextButton(
                                onClick = {
                                    val inst = installment.toDoubleOrNull() ?: 0.0
                                    val rate = rateAnnual.toDoubleOrNull() ?: 0.0
                                    val tVal = tenure.toIntOrNull() ?: 0
                                    val months = if (tenureYears) tVal * 12 else tVal
                                    val startMillis = (startDate ?: LocalDate.now())
                                        .atStartOfDay(zone)
                                        .toInstant()
                                        .toEpochMilli()

                                    if (name.isNotBlank() && inst > 0.0 && rate > 0.0 && months > 0) {
                                        val account = RdAccount(
                                            id = rdId ?: 0L,
                                            name = name.trim(),
                                            installmentAmount = inst,
                                            annualRatePercent = rate,
                                            startDateMillis = startMillis,
                                            tenureMonths = months,
                                            autoPay = autoPay
                                        )
                                        vm.saveAccount(account) { id -> onSaved(id) }
                                    }
                                }
                            ) {
                                Text(stringResource(R.string.save))
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RdDetailScreen(
    rdId: Long,
    onBack: () -> Unit,
    onEdit: (Long) -> Unit,
    vm: RdViewModel = hiltViewModel()
) {
    BackHandler { onBack() }

    LaunchedEffect(rdId) {
        vm.selectAccount(rdId)
    }

    val account by vm.selectedAccount.collectAsState()
    val deposits by vm.selectedDeposits.collectAsState()
    val summary by vm.selectedSummary.collectAsState()

    val pendingDelete = rememberSaveable { mutableStateOf(false) }

    val a = account
    if (a == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.loading))
        }
        return
    }

    val schedule = remember(a) { buildSchedule(a) }
    val depositByDue = remember(deposits) { deposits.associateBy { it.dueDateMillis } }

    LaunchedEffect(a.id, a.autoPay, deposits.size) {
        if (a.autoPay) {
            val todayMillis = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            schedule
                .asSequence()
                .filter { due -> due <= todayMillis }
                .filter { due -> depositByDue[due]?.paidDateMillis == null }
                .forEach { due ->
                    vm.markInstallmentPaid(a.id, due, a.installmentAmount, paidDateMillis = due)
                }
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(a.name) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = stringResource(R.string.cancel))
                    }
                },
                actions = {
                    IconButton(onClick = { onEdit(a.id) }) {
                        Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.edit))
                    }
                    IconButton(onClick = { pendingDelete.value = true }) {
                        Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.delete))
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                val s = summary
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(stringResource(R.string.rd_summary), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            stringResource(R.string.rd_rate_value, "%.2f".format(a.annualRatePercent)),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            stringResource(R.string.rd_installment_value, fmtMoneyNo(a.installmentAmount)),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (s != null) {
                            Spacer(Modifier.height(2.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Column {
                                    Text(
                                        stringResource(R.string.rd_total_deposited_value, fmtMoneyNo(s.totalDeposited)),
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Text(
                                        stringResource(R.string.rd_accrued_interest_value, fmtMoneyNo(s.accruedInterest)),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Color(0xFF2E7D32),
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        stringResource(R.string.rd_total_value_value, fmtMoneyNo(s.totalValue)),
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item {
                Text(stringResource(R.string.rd_schedule), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }

            items(schedule) { dueMillis ->
                val dep: RdDeposit? = depositByDue[dueMillis]
                val paid = dep?.paidDateMillis != null
                val todayMillis = remember { LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() }
                val isFuture = dueMillis > todayMillis
                val isDueNow = !paid && !isFuture
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.rd_due_date_value, formatDate(dueMillis)))
                            Text(
                                text = when {
                                    paid -> stringResource(R.string.rd_status_paid)
                                    isFuture -> stringResource(R.string.rd_status_upcoming)
                                    else -> stringResource(R.string.rd_status_due)
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = when {
                                    paid -> MaterialTheme.colorScheme.onSurfaceVariant
                                    isDueNow -> Color(0xFFD32F2F)
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                        if (!paid && !isFuture) {
                            TextButton(onClick = { vm.markInstallmentPaid(a.id, dueMillis, a.installmentAmount) }) {
                                Text(stringResource(R.string.rd_mark_paid), color = Color(0xFFD32F2F))
                            }
                        } else if (paid) {
                            Text(stringResource(R.string.rd_paid_amount_value, fmtMoneyNo(dep?.amountPaid ?: 0.0)))
                        }
                    }
                }
            }

            item {
                Spacer(Modifier.height(30.dp))
            }
        }

        if (pendingDelete.value) {
            AlertDialog(
                onDismissRequest = { pendingDelete.value = false },
                confirmButton = {
                    TextButton(onClick = {
                        vm.deleteAccount(a) { onBack() }
                        pendingDelete.value = false
                    }) {
                        Text(stringResource(R.string.ok))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { pendingDelete.value = false }) {
                        Text(stringResource(R.string.cancel))
                    }
                },
                title = { Text(stringResource(R.string.delete)) },
                text = { Text(stringResource(R.string.rd_delete_confirm)) }
            )
        }
    }
}

private fun buildSchedule(account: RdAccount): List<Long> {
    val zone = ZoneId.systemDefault()
    val start = Instant.ofEpochMilli(account.startDateMillis).atZone(zone).toLocalDate()
    return (0 until account.tenureMonths).map { i ->
        start.plusMonths(i.toLong()).atStartOfDay(zone).toInstant().toEpochMilli()
    }
}

private fun formatDate(millis: Long): String {
    val zone = ZoneId.systemDefault()
    val fmt = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.ENGLISH)
    return Instant.ofEpochMilli(millis).atZone(zone).toLocalDate().format(fmt)
}

private fun fmtMoneyNo(v: Double): String {
    return CurrencyFormatter.formatNoDecimals(v)
}
