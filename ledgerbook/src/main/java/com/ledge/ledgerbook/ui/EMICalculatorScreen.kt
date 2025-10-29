package com.ledge.ledgerbook.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.ledge.ledgerbook.R
import kotlin.math.pow
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.text.input.KeyboardType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EMICalculatorScreen(onBack: () -> Unit) {
    BackHandler(enabled = true) { onBack() }

    var principalText by remember { mutableStateOf("") }
    var annualRateText by remember { mutableStateOf("") }
    var tenureText by remember { mutableStateOf("") }
    var tenureInYears by remember { mutableStateOf(false) }

    var emi by remember { mutableStateOf(0.0) }
    var totalPayment by remember { mutableStateOf(0.0) }
    var totalInterest by remember { mutableStateOf(0.0) }
    var totalPrincipal by remember { mutableStateOf(0.0) }

    var showSchedule by remember { mutableStateOf(false) }
    var schedule by remember { mutableStateOf(listOf<EmiRow>()) }

    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val dismissKeyboard: () -> Unit = {
        focusManager.clearFocus()
        keyboard?.hide()
    }

    fun resetAll() {
        principalText = ""
        annualRateText = ""
        tenureText = ""
        emi = 0.0
        totalPayment = 0.0
        totalInterest = 0.0
        totalPrincipal = 0.0
        showSchedule = false
        schedule = emptyList()
    }

    fun calculate() {
        val P = principalText.toDoubleOrNull() ?: 0.0
        val rYear = annualRateText.toDoubleOrNull() ?: 0.0
        val t = tenureText.toIntOrNull() ?: 0
        val n = if (tenureInYears) t * 12 else t
        if (P <= 0 || rYear <= 0 || n <= 0) {
            emi = 0.0; totalPayment = 0.0; totalInterest = 0.0; totalPrincipal = 0.0; schedule = emptyList(); return
        }
        val r = rYear / 12.0 / 100.0
        val factor = (1 + r).pow(n)
        val monthlyEmi = P * r * factor / (factor - 1)
        emi = monthlyEmi
        totalPayment = monthlyEmi * n
        totalPrincipal = P
        totalInterest = totalPayment - totalPrincipal
        // Prepare schedule
        var balance = P
        val rows = ArrayList<EmiRow>(n)
        for (m in 1..n) {
            val interest = balance * r
            val principalPart = monthlyEmi - interest
            balance -= principalPart
            rows.add(EmiRow(month = m, principal = principalPart.coerceAtLeast(0.0), interest = interest.coerceAtLeast(0.0), balance = balance.coerceAtLeast(0.0)))
        }
        schedule = rows
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.emi_calculator)) },
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
            // Card: Inputs + Actions + Summary
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = principalText,
                            onValueChange = { principalText = it.filter { c -> c.isDigit() || c == '.' } },
                            label = { Text(stringResource(R.string.label_principal_generic)) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { dismissKeyboard() })
                        )
                        OutlinedTextField(
                            value = annualRateText,
                            onValueChange = { annualRateText = it.filter { c -> c.isDigit() || c == '.' } },
                            label = { Text(stringResource(R.string.interest_per_annum)) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { dismissKeyboard() })
                        )
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = tenureText,
                                onValueChange = { tenureText = it.filter { c -> c.isDigit() } },
                                label = { Text(stringResource(R.string.loan_tenure)) },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(onDone = { dismissKeyboard() })
                            )
                            FilledTonalButton(onClick = { tenureInYears = !tenureInYears }) {
                                Text(if (tenureInYears) stringResource(R.string.years) else stringResource(R.string.months))
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedButton(
                                onClick = { dismissKeyboard(); resetAll() },
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                            ) { Text(stringResource(R.string.reset), style = MaterialTheme.typography.labelMedium) }
                            Button(
                                onClick = { dismissKeyboard(); calculate(); showSchedule = false },
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                            ) { Text(stringResource(R.string.calculate), style = MaterialTheme.typography.labelMedium) }
                            Button(
                                onClick = { dismissKeyboard(); if (schedule.isEmpty()) { calculate() }; showSchedule = true },
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                            ) { Text(stringResource(R.string.emi_stats), style = MaterialTheme.typography.labelMedium) }
                        }
                    }
                }
            }
            // Summary Card
            if (emi > 0) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(stringResource(R.string.summary), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                            SummaryRow(stringResource(R.string.total_principal), totalPrincipal)
                            SummaryRow(stringResource(R.string.total_interest), totalInterest)
                            SummaryRow(stringResource(R.string.total_amount), totalPayment)
                            SummaryRow(stringResource(R.string.emi_monthly), emi)
                        }
                    }
                }
            }
            // Schedule
            if (showSchedule && schedule.isNotEmpty()) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Text(stringResource(R.string.repayment_schedule), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(8.dp))
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp)
                            ) {
                                HeaderCell(stringResource(R.string.col_month), Modifier.weight(0.15f))
                                HeaderCell(stringResource(R.string.col_principal), Modifier.weight(0.28f))
                                HeaderCell(stringResource(R.string.col_interest), Modifier.weight(0.28f))
                                HeaderCell(stringResource(R.string.balance), Modifier.weight(0.29f))
                            }
                            Divider()
                            schedule.forEach { row ->
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 6.dp)
                                ) {
                                    Cell(row.month.toString(), Modifier.weight(0.15f))
                                    Cell(format(row.principal), Modifier.weight(0.28f))
                                    Cell(format(row.interest), Modifier.weight(0.28f))
                                    Cell(format(row.balance), Modifier.weight(0.29f))
                                }
                                Divider()
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable private fun SummaryRow(label: String, value: Double) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = format(value),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable private fun HeaderCell(text: String, modifier: Modifier) {
    Text(text, modifier = modifier, textAlign = TextAlign.Start, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelSmall)
}

@Composable private fun Cell(text: String, modifier: Modifier) {
    Text(text, modifier = modifier, textAlign = TextAlign.Start, style = MaterialTheme.typography.bodySmall)
}

data class EmiRow(val month: Int, val principal: Double, val interest: Double, val balance: Double)

private fun format(v: Double): String {
    return if (v == 0.0 || v.isNaN() || v.isInfinite()) "0" else String.format("%,.2f", v)
}
