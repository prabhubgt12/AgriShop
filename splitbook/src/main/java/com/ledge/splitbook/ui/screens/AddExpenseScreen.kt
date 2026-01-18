package com.ledge.splitbook.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Checkbox
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.TextFieldValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import com.ledge.splitbook.ui.vm.AddExpenseViewModel
import com.ledge.splitbook.ui.vm.AddExpenseViewModel.Mode
import com.ledge.splitbook.ui.vm.SettingsViewModel
import com.ledge.splitbook.util.AdsManager
import java.time.LocalDate
import android.app.DatePickerDialog
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddExpenseScreen(
    groupId: Long,
    payerId: Long? = null,
    expenseId: Long? = null,
    onDone: () -> Unit,
    viewModel: AddExpenseViewModel = hiltViewModel()
) {
    LaunchedEffect(groupId) { viewModel.load(groupId) }
    LaunchedEffect(payerId) {
        if (payerId != null) viewModel.selectPayer(payerId)
    }
    LaunchedEffect(expenseId) {
        if (expenseId != null) viewModel.loadForEdit(expenseId)
    }
    val context = LocalContext.current
    val settingsViewModel: SettingsViewModel = hiltViewModel()
    val settings by settingsViewModel.ui.collectAsState()
    LaunchedEffect(Unit) {
        if (!settings.removeAds) AdsManager.ensureInterstitialLoaded(context)
    }

    val ui by viewModel.uiFlow.collectAsState()

    Column(
        modifier = Modifier
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Add Expense")
        // Description at top (mandatory)
        OutlinedTextField(
            value = ui.note,
            onValueChange = { viewModel.updateNote(it) },
            label = { Text("Description") },
            modifier = Modifier.fillMaxWidth(),
        )

        OutlinedTextField(
            value = ui.amount,
            onValueChange = { viewModel.updateAmount(it) },
            label = { Text("Amount") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )

        var expanded by remember { mutableStateOf(false) }
        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
            OutlinedTextField(
                value = ui.paidByName,
                onValueChange = {},
                readOnly = true,
                label = { Text("Paid by") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier
                    .menuAnchor()
                    .fillMaxWidth(),
            )
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                ui.members.forEach { m ->
                    DropdownMenuItem(text = { Text(m.name) }, onClick = {
                        viewModel.selectPayer(m.id)
                        expanded = false
                    })
                }
            }
        }

        OutlinedTextField(
            value = ui.category,
            onValueChange = { viewModel.updateCategory(it) },
            label = { Text("Category") },
            modifier = Modifier.fillMaxWidth(),
        )

        // Date field with picker
        val dateStr = ui.date
        val calendar = remember(dateStr) {
            val parsed = runCatching { LocalDate.parse(dateStr) }.getOrNull() ?: LocalDate.now()
            Calendar.getInstance().apply {
                set(Calendar.YEAR, parsed.year)
                set(Calendar.MONTH, parsed.monthValue - 1)
                set(Calendar.DAY_OF_MONTH, parsed.dayOfMonth)
            }
        }
        val openDatePicker: () -> Unit = {
            val y = calendar.get(Calendar.YEAR)
            val m = calendar.get(Calendar.MONTH)
            val d = calendar.get(Calendar.DAY_OF_MONTH)
            DatePickerDialog(context, { _, yy, mm, dd ->
                val ld = LocalDate.of(yy, mm + 1, dd)
                viewModel.updateDate(ld.toString())
            }, y, m, d).show()
        }
        OutlinedTextField(
            value = ui.date,
            onValueChange = { viewModel.updateDate(it) },
            label = { Text("Date (YYYY-MM-DD)") },
            modifier = Modifier.fillMaxWidth(),
            readOnly = true,
            singleLine = true,
            trailingIcon = {
                IconButton(onClick = openDatePicker) {
                    Icon(Icons.Filled.CalendarMonth, contentDescription = "Pick date")
                }
            }
        )

        // Simplified split controls
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = ui.shareByAll, onCheckedChange = { viewModel.toggleShareByAll(it) })
            Text("Expense share by all")
        }

        if (!ui.shareByAll) {
            // Multi-select dropdown for members
            var selOpen by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(expanded = selOpen, onExpandedChange = { selOpen = !selOpen }) {
                val selectedCount = ui.selectedMemberIds.size
                OutlinedTextField(
                    value = if (selectedCount == 0) "Select members" else "$selectedCount selected",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Select Person") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = selOpen) },
                    modifier = Modifier
                        .menuAnchor()
                        .fillMaxWidth(),
                )
                DropdownMenu(expanded = selOpen, onDismissRequest = { selOpen = false }) {
                    ui.members.forEach { m ->
                        val checked = ui.selectedMemberIds.contains(m.id)
                        DropdownMenuItem(
                            text = {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Checkbox(checked = checked, onCheckedChange = null)
                                    Text(m.name)
                                }
                            },
                            onClick = { viewModel.toggleMemberSelected(m.id, !checked) }
                        )
                    }
                }
            }

            // Rows for selected members with Amount and Percent
            ui.members.filter { ui.selectedMemberIds.contains(it.id) }.forEach { m ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = ui.customAmounts[m.id] ?: "",
                        onValueChange = { viewModel.updateCustomAmount(m.id, it) },
                        label = { Text("${m.name} amount") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    OutlinedTextField(
                        value = ui.percentages[m.id] ?: "",
                        onValueChange = { viewModel.updatePercentage(m.id, it) },
                        label = { Text("%") },
                        modifier = Modifier.weight(0.6f),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                }
            }

            // Helper note
            Text(
                "Note: Enter amounts that total the expense or percentages that sum to 100% to enable Save.",
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall
            )

            // Live summary of totals with validity color
            val selectedIds = ui.selectedMemberIds
            val total = ui.amount.toDoubleOrNull() ?: 0.0
            val sumAmounts = ui.members.filter { selectedIds.contains(it.id) }
                .sumOf { ui.customAmounts[it.id]?.toDoubleOrNull() ?: 0.0 }
            val sumPct = ui.members.filter { selectedIds.contains(it.id) }
                .sumOf { ui.percentages[it.id]?.toDoubleOrNull() ?: 0.0 }
            val totalR = kotlin.math.round(total * 100) / 100.0
            val sumAmtR = kotlin.math.round(sumAmounts * 100) / 100.0
            val sumPctR = kotlin.math.round(sumPct * 100) / 100.0
            val valid = if (sumPctR > 0.0) kotlin.math.abs(sumPctR - 100.0) <= 0.01 else kotlin.math.abs(sumAmtR - totalR) <= 0.01
            val infoColor = if (valid) Color(0xFF16A34A) else Color(0xFFDC2626)
            Text(
                "Amounts: %.2f/%.2f • Percent: %.2f%%".format(sumAmtR, totalR, sumPctR),
                color = infoColor,
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall
            )
        } else {
            Text(
                "Split equally among ${ui.members.size} members",
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = {
                viewModel.save(onSaved = {
                    val activity = context as? android.app.Activity
                    if (activity != null && !settings.removeAds) AdsManager.tryShow(activity)
                    onDone()
                })
            }, enabled = ui.canSave) { Text("Save") }
        }
        Spacer(modifier = Modifier.height(80.dp))

        if (ui.members.isEmpty()) {
            Text("No members yet. Adding two sample members for quick start…")
            Button(onClick = { viewModel.addSampleMembers() }) { Text("Add Sample Members") }
        }
    }
}
