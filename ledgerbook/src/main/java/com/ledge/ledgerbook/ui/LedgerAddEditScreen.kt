package com.ledge.ledgerbook.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.ledge.ledgerbook.data.local.entities.LedgerEntry
import java.text.SimpleDateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LedgerAddEditScreen(
    onDismiss: () -> Unit,
    onSave: (LedgerEntry) -> Unit,
    existing: LedgerEntry? = null
) {
    val isEdit = existing != null

    var type by remember { mutableStateOf(existing?.type ?: "LEND") }
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var interestType by remember { mutableStateOf(existing?.interestType ?: "SIMPLE") }
    var period by remember { mutableStateOf(existing?.period ?: "MONTHLY") }
    var compoundPeriod by remember { mutableStateOf(existing?.compoundPeriod ?: "MONTHLY") }
    var principal by remember { mutableStateOf((existing?.principal ?: 0.0).toString()) }
    var rateRupees by remember { mutableStateOf((existing?.rateRupees ?: 0.0).toString()) }
    var fromDate by remember { mutableStateOf(existing?.fromDate ?: System.currentTimeMillis()) }
    var notes by remember { mutableStateOf(existing?.notes ?: "") }

    var showDatePicker by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                val entry = LedgerEntry(
                    id = existing?.id ?: 0,
                    type = type,
                    name = name.trim(),
                    principal = principal.toDoubleOrNull() ?: 0.0,
                    interestType = interestType,
                    period = period,
                    compoundPeriod = compoundPeriod,
                    rateRupees = rateRupees.toDoubleOrNull() ?: 0.0,
                    fromDate = fromDate,
                    notes = notes.ifBlank { null }
                )
                onSave(entry)
            }) { Text(if (isEdit) "Update" else "Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        title = { Text(if (isEdit) "Edit Entry" else "Add to Book", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 480.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    FilterChip(
                        selected = type.equals("LEND", true),
                        onClick = { type = "LEND" },
                        label = { Text("Lend") }
                    )
                    FilterChip(
                        selected = type.equals("BORROW", true),
                        onClick = { type = "BORROW" },
                        label = { Text("Borrow") }
                    )
                }
                Spacer(Modifier.height(12.dp))

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(if (type.equals("LEND", true)) "Borrower Name" else "Lender Name") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))

                Text("Interest Type", style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(4.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    FilterChip(
                        selected = interestType.equals("SIMPLE", true),
                        onClick = { interestType = "SIMPLE" },
                        label = { Text("Simple") }
                    )
                    FilterChip(
                        selected = interestType.equals("COMPOUND", true),
                        onClick = { interestType = "COMPOUND" },
                        label = { Text("Compound") }
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text("Rate Basis", style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(4.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    FilterChip(selected = period == "MONTHLY", onClick = { period = "MONTHLY" }, label = { Text("Monthly") })
                    FilterChip(selected = period == "YEARLY", onClick = { period = "YEARLY" }, label = { Text("Yearly") })
                }

                if (interestType.equals("COMPOUND", true)) {
                    Spacer(Modifier.height(8.dp))
                    Text("Duration Type", style = MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.height(4.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        FilterChip(selected = compoundPeriod == "MONTHLY", onClick = { compoundPeriod = "MONTHLY" }, label = { Text("Monthly") })
                        FilterChip(selected = compoundPeriod == "YEARLY", onClick = { compoundPeriod = "YEARLY" }, label = { Text("Yearly") })
                    }
                }

                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = principal,
                    onValueChange = { principal = it },
                    label = { Text("Principal Amount") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = rateRupees,
                    onValueChange = { rateRupees = it },
                    label = { Text("Interest Rate (%)") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )

                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = SimpleDateFormat("dd/MM/yyyy").format(Date(fromDate)),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("From Date") },
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = {
                        TextButton(onClick = { showDatePicker = true }) { Text("Pick") }
                    }
                )

                if (showDatePicker) {
                    val dateState = rememberDatePickerState(initialSelectedDateMillis = fromDate)
                    DatePickerDialog(
                        onDismissRequest = { showDatePicker = false },
                        confirmButton = {
                            TextButton(onClick = {
                                fromDate = dateState.selectedDateMillis ?: fromDate
                                showDatePicker = false
                            }) { Text("OK") }
                        },
                        dismissButton = {
                            TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
                        }
                    ) {
                        DatePicker(state = dateState)
                    }
                }

                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes (Optional)") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    )
}
