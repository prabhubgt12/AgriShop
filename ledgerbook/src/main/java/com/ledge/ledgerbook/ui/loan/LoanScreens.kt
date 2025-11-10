package com.ledge.ledgerbook.ui.loan

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import androidx.hilt.navigation.compose.hiltViewModel
import com.ledge.ledgerbook.R
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import com.ledge.ledgerbook.data.local.entities.LoanProfile
import kotlin.math.pow
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoanListScreen(
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onOpenDetail: (Long) -> Unit,
    vm: LoanViewModel = hiltViewModel()
) {
    BackHandler { onBack() }
    var pendingDeleteId by rememberSaveable { mutableStateOf<Long?>(null) }
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.loan_book)) },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAdd) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.add_loan))
            }
        }
    ) { padding ->
        val loans by vm.loans.collectAsState()
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(loans) { p ->
                LoanCard(
                    profile = p,
                    onClick = { onOpenDetail(p.id) },
                    onDelete = { pendingDeleteId = p.id }
                )
            }
        }
        if (pendingDeleteId != null) {
            AlertDialog(
                onDismissRequest = { pendingDeleteId = null },
                confirmButton = {
                    TextButton(onClick = {
                        pendingDeleteId?.let { vm.delete(it) }
                        pendingDeleteId = null
                    }) { Text(stringResource(R.string.ok)) }
                },
                dismissButton = {
                    TextButton(onClick = { pendingDeleteId = null }) { Text(stringResource(R.string.cancel)) }
                },
                title = { Text(stringResource(R.string.delete)) },
                text = { Text(stringResource(R.string.delete_loan_confirm)) }
            )
        }
    }
}

@Composable
private fun LoanCard(profile: LoanProfile, onClick: () -> Unit, onDelete: () -> Unit) {
    val summary = remember(profile) { computeSummary(profile) }
    val paidToDate = remember(profile) { computePaidToDate(profile) }
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(Modifier.padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Icon(iconForType(profile.type), contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(6.dp))
                Column(Modifier.weight(1f)) {
                    Text(profile.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(profile.type, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("EMI " + format(summary.emi), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(6.dp))
                    IconButton(onClick = { onDelete() }, modifier = Modifier.size(32.dp)) {
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = stringResource(R.string.delete),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
            val paid = paidToDate.total
            val progress = if (summary.total > 0) (paid / summary.total).toFloat().coerceIn(0f, 1f) else 0f
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(format(paid), style = MaterialTheme.typography.labelSmall)
                LinearProgressIndicator(progress = { progress }, modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp))
                Text(format(summary.total), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private fun iconForType(type: String) = when (type.lowercase()) {
    "home" -> Icons.Default.Home
    "personal" -> Icons.Default.Person
    "car", "auto" -> Icons.Default.DirectionsCar
    "gold" -> Icons.Default.AccountBalance
    else -> Icons.Default.AccountBalance
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoanAddScreen(
    onBack: () -> Unit,
    onSaved: (Long) -> Unit,
    loanId: Long? = null,
    vm: LoanViewModel = hiltViewModel()
) {
    BackHandler { onBack() }
    var typeIndex by remember { mutableStateOf(0) }
    val types = listOf("Home", "Car", "Personal", "Gold", "Other")
    var name by remember { mutableStateOf("") }
    var principal by remember { mutableStateOf("") }
    var rate by remember { mutableStateOf("") }
    var tenure by remember { mutableStateOf("") }
    var years by remember { mutableStateOf(true) }
    var firstEmiDate by remember { mutableStateOf<LocalDate?>(null) }
    var showDatePicker by remember { mutableStateOf(false) }

    // Prefill when editing
    LaunchedEffect(loanId) {
        if (loanId != null) {
            vm.getById(loanId)?.let { lp ->
                // type index
                typeIndex = types.indexOfFirst { it.equals(lp.type, ignoreCase = true) }.takeIf { it >= 0 } ?: 0
                name = lp.name
                principal = lp.principal.toString()
                rate = lp.annualRatePercent.toString()
                // decide years/months toggle and value
                if (lp.tenureMonths % 12 == 0) {
                    years = true
                    tenure = (lp.tenureMonths / 12).toString()
                } else {
                    years = false
                    tenure = lp.tenureMonths.toString()
                }
                if (lp.firstEmiDateMillis > 0L) {
                    firstEmiDate = java.time.Instant.ofEpochMilli(lp.firstEmiDateMillis).atZone(ZoneId.systemDefault()).toLocalDate()
                }
            }
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.add_loan)) },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
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
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            stringResource(if (loanId != null) R.string.edit else R.string.add_loan),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        // Type dropdown
                        var typeExpanded by remember { mutableStateOf(false) }
                        ExposedDropdownMenuBox(expanded = typeExpanded, onExpandedChange = { typeExpanded = !typeExpanded }) {
                            OutlinedTextField(
                                readOnly = true,
                                value = types[typeIndex],
                                onValueChange = {},
                                label = { Text(stringResource(R.string.loan_type)) },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = typeExpanded) },
                                modifier = Modifier.menuAnchor().fillMaxWidth()
                            )
                            ExposedDropdownMenu(expanded = typeExpanded, onDismissRequest = { typeExpanded = false }) {
                                types.forEachIndexed { idx, t ->
                                    DropdownMenuItem(text = { Text(t) }, onClick = { typeIndex = idx; typeExpanded = false })
                                }
                            }
                        }
                        val nameError = name.isBlank()
                        val principalError = (principal.toDoubleOrNull() ?: 0.0) <= 0.0
                        val rateError = (rate.toDoubleOrNull() ?: 0.0) <= 0.0
                        val monthsVal = (tenure.toIntOrNull() ?: 0) * if (years) 12 else 1
                        val tenureError = monthsVal <= 0
                        OutlinedTextField(label = { Text(stringResource(R.string.profile_name)) }, value = name, onValueChange = { name = it }, modifier = Modifier.fillMaxWidth(), isError = nameError, supportingText = { if (nameError) Text(stringResource(R.string.name_required)) })
                        OutlinedTextField(label = { Text(stringResource(R.string.principal_amount)) }, value = principal, onValueChange = { principal = it.filter { c -> c.isDigit() || c == '.' } }, modifier = Modifier.fillMaxWidth(), isError = principalError)
                        OutlinedTextField(label = { Text(stringResource(R.string.interest_per_annum)) }, value = rate, onValueChange = { rate = it.filter { c -> c.isDigit() || c == '.' } }, modifier = Modifier.fillMaxWidth(), isError = rateError)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(label = { Text(stringResource(R.string.loan_tenure)) }, value = tenure, onValueChange = { tenure = it.filter { c -> c.isDigit() } }, modifier = Modifier.weight(1f))
                            FilledTonalButton(onClick = { years = !years }) { Text(if (years) stringResource(R.string.years) else stringResource(R.string.months)) }
                        }
                        val dateFmt = remember { DateTimeFormatter.ofPattern("dd/MM/yyyy") }
                        OutlinedTextField(
                            readOnly = true,
                            value = (firstEmiDate?.format(dateFmt) ?: ""),
                            onValueChange = {},
                            label = { Text(stringResource(R.string.first_emi_date)) },
                            trailingIcon = {
                                IconButton(onClick = { showDatePicker = true }) {
                                    Icon(Icons.Filled.DateRange, contentDescription = null)
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = {
                                name = ""; principal = ""; rate = ""; tenure = ""; years = true; firstEmiDate = null
                            }) { Text(stringResource(R.string.reset)) }
                            val valid = !nameError && !principalError && !rateError && !tenureError
                            Button(onClick = {
                                val p = principal.toDoubleOrNull() ?: 0.0
                                val r = rate.toDoubleOrNull() ?: 0.0
                                val t = tenure.toIntOrNull() ?: 0
                                val months = if (years) t * 12 else t
                                if (name.isNotBlank() && p > 0 && r > 0 && months > 0) {
                                    vm.save(
                                        LoanProfile(
                                            id = loanId ?: 0L,
                                            type = types[typeIndex],
                                            name = name.trim(),
                                            principal = p,
                                            annualRatePercent = r,
                                            tenureMonths = months,
                                            firstEmiDateMillis = (firstEmiDate?.atStartOfDay(ZoneId.systemDefault())?.toInstant()?.toEpochMilli() ?: 0L)
                                        )
                                    ) { savedId ->
                                        onSaved(savedId)
                                    }
                                }
                            }, enabled = valid) { Text(stringResource(R.string.save)) }
                        }
                    }
                }
            }
        }

        if (showDatePicker) {
            val ctx = androidx.compose.ui.platform.LocalContext.current
            if (android.os.Build.VERSION.SDK_INT <= 30) {
                androidx.compose.runtime.DisposableEffect(Unit) {
                    val today = java.util.Calendar.getInstance()
                    firstEmiDate?.let {
                        today.set(java.util.Calendar.YEAR, it.year)
                        today.set(java.util.Calendar.MONTH, it.monthValue - 1)
                        today.set(java.util.Calendar.DAY_OF_MONTH, it.dayOfMonth)
                    }
                    val dlg = android.app.DatePickerDialog(
                        ctx,
                        { _, y, m, d ->
                            try { firstEmiDate = LocalDate.of(y, m + 1, d) } catch (_: Exception) {}
                        },
                        today.get(java.util.Calendar.YEAR),
                        today.get(java.util.Calendar.MONTH),
                        today.get(java.util.Calendar.DAY_OF_MONTH)
                    )
                    dlg.setOnDismissListener { showDatePicker = false }
                    dlg.show()
                    onDispose { dlg.dismiss() }
                }
            } else {
                val initial = try {
                    firstEmiDate?.atStartOfDay(ZoneId.systemDefault())?.toInstant()?.toEpochMilli()
                        ?: System.currentTimeMillis()
                } catch (_: Exception) { System.currentTimeMillis() }
                val dateState = rememberDatePickerState(initialSelectedDateMillis = initial)
                DatePickerDialog(
                    onDismissRequest = { showDatePicker = false },
                    confirmButton = {
                        TextButton(onClick = {
                            val selected = dateState.selectedDateMillis ?: initial
                            try {
                                firstEmiDate = LocalDate.ofInstant(java.time.Instant.ofEpochMilli(selected), ZoneId.systemDefault())
                            } catch (_: Exception) {}
                            showDatePicker = false
                        }) { Text(stringResource(R.string.ok)) }
                    },
                    dismissButton = {
                        TextButton(onClick = { showDatePicker = false }) { Text(stringResource(R.string.cancel)) }
                    }
                ) { DatePicker(state = dateState) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoanDetailScreen(
    loanId: Long,
    onBack: () -> Unit,
    onEdit: (Long) -> Unit = {},
    vm: LoanViewModel = hiltViewModel()
) {
    BackHandler { onBack() }
    var profile by remember { mutableStateOf<LoanProfile?>(null) }
    LaunchedEffect(loanId) {
        profile = vm.getById(loanId)
    }
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.loan_details_title)) },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                ),
                actions = {
                    IconButton(onClick = { onEdit(loanId) }) {
                        Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.edit), tint = MaterialTheme.colorScheme.onPrimary)
                    }
                }
            )
        }
    ) { padding ->
        profile?.let { p ->
            val s = remember(p) { computeSummary(p) }
            val paid = remember(p) { computePaidToDate(p) }
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(p.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            InfoRow(label = stringResource(R.string.principal_amount), value = format(p.principal))
                            InfoRow(label = stringResource(R.string.interest_per_annum), value = p.annualRatePercent.toString())
                            InfoRow(label = stringResource(R.string.duration), value = "${p.tenureMonths} " + stringResource(R.string.months))
                            InfoRow(label = "EMI", value = format(s.emi))
                            InfoRow(label = stringResource(R.string.total_interest), value = format(s.totalInterest))
                            InfoRow(label = stringResource(R.string.total_amount), value = format(s.total))
                            if (p.firstEmiDateMillis > 0L) {
                                val first = java.time.Instant.ofEpochMilli(p.firstEmiDateMillis).atZone(ZoneId.systemDefault()).toLocalDate()
                                val dateFmt = DateTimeFormatter.ofPattern("dd/MM/yyyy")
                                InfoRow(label = stringResource(R.string.first_emi_date), value = first.format(dateFmt))
                            }
                            Divider()
                            InfoRow(label = stringResource(R.string.principal_paid), value = format(paid.principal))
                            InfoRow(label = stringResource(R.string.interest_paid), value = format(paid.interest))
                            InfoRow(label = stringResource(R.string.total_paid), value = format(paid.total))
                            InfoRow(label = stringResource(R.string.remaining_amount), value = format((s.total - paid.total).coerceAtLeast(0.0)))
                        }
                    }
                }
                item {
                    // Donut chart card replacing linear/circular progress
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        DonutCard(
                            total = s.total,
                            paid = paid.total,
                            remaining = (s.total - paid.total).coerceAtLeast(0.0)
                        )
                    }
                }
                if (p.tenureMonths > 0) {
                    item {
                        val schedule = remember(p) { buildSchedule(p) }
                        if (schedule.isNotEmpty()) {
                            val groups = remember(schedule) { schedule.groupBy { it.date?.year ?: 0 }.toSortedMap() }
                            val expanded = remember { mutableStateMapOf<Int, Boolean>() }
                            // Default expand first year group once
                            LaunchedEffect(groups.keys) {
                                val firstKey = groups.keys.firstOrNull()
                                if (firstKey != null && expanded.isEmpty()) {
                                    expanded[firstKey] = true
                                }
                            }
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                            ) {
                                Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(
                                        stringResource(R.string.repayment_schedule),
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.fillMaxWidth(),
                                        textAlign = TextAlign.Center
                                    )
                                    groups.forEach { (year, rows) ->
                                        val isOpen = expanded[year] ?: false
                                        ElevatedCard(
                                            onClick = { expanded[year] = !isOpen },
                                            modifier = Modifier.fillMaxWidth(),
                                            colors = CardDefaults.elevatedCardColors(
                                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                                            )
                                        ) {
                                            Column(Modifier.fillMaxWidth().padding(10.dp)) {
                                                Text(
                                                    if (year == 0) stringResource(R.string.repayment_schedule) else year.toString(),
                                                    fontWeight = FontWeight.SemiBold,
                                                    modifier = Modifier.fillMaxWidth(),
                                                    textAlign = TextAlign.Center
                                                )
                                                if (isOpen) {
                                                    Spacer(Modifier.height(6.dp))
                                                    HorizontalDivider()
                                                    Spacer(Modifier.height(6.dp))
                                                    Row(Modifier.fillMaxWidth()) {
                                                        Text(stringResource(R.string.col_month), modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelSmall)
                                                        Text(stringResource(R.string.label_principal), modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelSmall)
                                                        Text(stringResource(R.string.label_interest), modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelSmall)
                                                        Text(stringResource(R.string.balance), modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelSmall)
                                                    }
                                                    HorizontalDivider()
                                                    rows.forEach { row ->
                                                        Row(Modifier.fillMaxWidth()) {
                                                            Text(row.monthLabel, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                                                            Text(format(row.principal), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                                                            Text(format(row.interest), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                                                            Text(format(row.balance), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private data class LoanSummary(val emi: Double, val total: Double, val totalInterest: Double, val paid: Double = 0.0, val progress: Float = 0f)
private data class Paid(val principal: Double, val interest: Double) { val total: Double get() = principal + interest }
private data class RowItem(val principal: Double, val interest: Double, val balance: Double, val date: LocalDate?, val monthLabel: String)

private fun computeSummary(p: LoanProfile): LoanSummary {
    val r = (p.annualRatePercent / 12.0) / 100.0
    val n = p.tenureMonths
    val emi = if (r == 0.0) p.principal / n else p.principal * r * (1 + r).pow(n) / ((1 + r).pow(n) - 1)
    val total = emi * n
    val totalInt = total - p.principal
    return LoanSummary(emi = emi, total = total, totalInterest = totalInt, paid = 0.0, progress = 0f)
}

private fun buildSchedule(p: LoanProfile): List<RowItem> {
    val r = (p.annualRatePercent / 12.0) / 100.0
    val n = p.tenureMonths
    if (n <= 0) return emptyList()
    val emi = if (r == 0.0) p.principal / n else p.principal * r * (1 + r).pow(n) / ((1 + r).pow(n) - 1)
    var balance = p.principal
    val list = ArrayList<RowItem>(n)
    val startDate: LocalDate? = if (p.firstEmiDateMillis > 0L) {
        java.time.Instant.ofEpochMilli(p.firstEmiDateMillis).atZone(ZoneId.systemDefault()).toLocalDate().withDayOfMonth(1)
    } else null
    val fmt = DateTimeFormatter.ofPattern("MMM")
    repeat(n) { idx ->
        val interest = balance * r
        val principalPart = (emi - interest).coerceAtLeast(0.0)
        balance = (balance - principalPart).coerceAtLeast(0.0)
        val d = startDate?.plusMonths(idx.toLong())
        val label = d?.format(fmt) ?: "${idx + 1}"
        list.add(RowItem(principal = principalPart, interest = interest.coerceAtLeast(0.0), balance = balance, date = d, monthLabel = label))
    }
    return list
}

private fun format(v: Double): String = if (v == 0.0 || v.isNaN() || v.isInfinite()) "0" else String.format("%,.2f", v)

private fun computePaidToDate(p: LoanProfile): Paid {
    if (p.firstEmiDateMillis <= 0L) return Paid(0.0, 0.0)
    val first = java.time.Instant.ofEpochMilli(p.firstEmiDateMillis).atZone(ZoneId.systemDefault()).toLocalDate()
    val today = LocalDate.now()
    val base = ChronoUnit.MONTHS.between(first.withDayOfMonth(1), today.withDayOfMonth(1)).toInt()
    var monthsPaid = base
    if (base >= 0 && today.dayOfMonth >= first.dayOfMonth) {
        monthsPaid += 1
    }
    monthsPaid = monthsPaid.coerceIn(0, p.tenureMonths)
    if (monthsPaid <= 0) return Paid(0.0, 0.0)
    val r = (p.annualRatePercent / 12.0) / 100.0
    val emi = if (r == 0.0) p.principal / p.tenureMonths else p.principal * r * (1 + r).pow(p.tenureMonths) / ((1 + r).pow(p.tenureMonths) - 1)
    var balance = p.principal
    var principalPaid = 0.0
    var interestPaid = 0.0
    repeat(monthsPaid) {
        val interest = balance * r
        val principalPart = emi - interest
        interestPaid += interest.coerceAtLeast(0.0)
        principalPaid += principalPart.coerceAtLeast(0.0)
        balance -= principalPart
    }
    return Paid(principal = principalPaid.coerceAtMost(p.principal), interest = interestPaid)
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Left
        )
        Text(
            text = ": $value",
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Left
        )
    }
}

@Composable
private fun DonutCard(total: Double, paid: Double, remaining: Double) {
    val pctPaid = if (total > 0) (paid / total).toFloat().coerceIn(0f, 1f) else 0f
    // Bright colors that look good on light/dark
    // Cashbook-like bright colors that work on light/dark
    val paidColor = Color(0xFF66BB6A)      // Green 400
    val remColor = Color(0xFFFFA726)       // Orange 400
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(Modifier.size(180.dp), contentAlignment = Alignment.Center) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val stroke = Stroke(width = 18f, cap = StrokeCap.Round)
                val sizeMin = size.minDimension
                val topLeft = androidx.compose.ui.geometry.Offset(
                    (size.width - sizeMin) / 2f,
                    (size.height - sizeMin) / 2f
                )
                // Background ring
                drawArc(
                    color = remColor.copy(alpha = 0.25f),
                    startAngle = -90f,
                    sweepAngle = 360f,
                    useCenter = false,
                    style = stroke,
                    size = Size(sizeMin, sizeMin),
                    topLeft = topLeft
                )
                // Paid segment
                drawArc(
                    color = paidColor,
                    startAngle = -90f,
                    sweepAngle = 360f * pctPaid,
                    useCenter = false,
                    style = stroke,
                    size = Size(sizeMin, sizeMin),
                    topLeft = topLeft
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = stringResource(R.string.total_amount), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(text = format(total), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(12.dp).background(paidColor, RoundedCornerShape(2.dp)))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.total_paid) + ": " + format(paid), style = MaterialTheme.typography.bodySmall)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(12.dp).background(remColor, RoundedCornerShape(2.dp)))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.remaining_amount) + ": " + format(remaining), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
