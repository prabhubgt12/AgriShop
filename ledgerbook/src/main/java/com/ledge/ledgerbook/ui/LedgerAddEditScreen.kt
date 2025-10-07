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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.Alignment
import com.ledge.ledgerbook.data.local.entities.LedgerEntry
import java.text.SimpleDateFormat
import java.util.Date
import androidx.compose.ui.res.stringResource
import com.ledge.ledgerbook.R
import com.ledge.ledgerbook.util.NumberToWords
import com.ledge.ledgerbook.util.CurrencyFormatter
import androidx.compose.ui.platform.LocalContext
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import coil.compose.rememberAsyncImagePainter
import androidx.core.content.FileProvider
import android.net.Uri
import java.io.File
import java.io.FileOutputStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LedgerAddEditScreen(
    onDismiss: () -> Unit,
    onSave: (LedgerEntry) -> Unit,
    existing: LedgerEntry? = null,
    prefillName: String? = null
) {
    val isEdit = existing != null

    var type by remember { mutableStateOf(existing?.type ?: "LEND") }
    var name by remember { mutableStateOf(prefillName ?: existing?.name ?: "") }
    var interestType by remember { mutableStateOf(existing?.interestType ?: "SIMPLE") }
    var period by remember { mutableStateOf(existing?.period ?: "MONTHLY") }
    var compoundPeriod by remember { mutableStateOf(existing?.compoundPeriod ?: "MONTHLY") }
    // Mandatory numeric fields: when adding new, start empty (no default values)
    var principal by remember { mutableStateOf(if (existing != null) CurrencyFormatter.formatNumericUpTo2(existing.principal) else "") }
    var rateRupees by remember { mutableStateOf(if (existing != null) CurrencyFormatter.formatNumericUpTo2(existing.rateRupees) else "") }
    var fromDate by remember { mutableStateOf(existing?.fromDate ?: System.currentTimeMillis()) }
    var notes by remember { mutableStateOf(existing?.notes ?: "") }
    // Parse existing attachment from notes (att: <uri>)
    val existingAtt: Uri? = remember(existing) {
        existing?.notes
            ?.lineSequence()
            ?.firstOrNull { it.trim().startsWith("att:") }
            ?.substringAfter("att:")
            ?.trim()
            ?.let { runCatching { Uri.parse(it) }.getOrNull() }
    }
    var attachmentUri by remember { mutableStateOf<Uri?>(existingAtt) }
    // Optional phone number (digits only). We try to prefill from notes if it contains a 'Phone:' line.
    var phone by remember {
        mutableStateOf(run {
            val n = existing?.notes ?: ""
            // naive extract: look for a line starting with 'Phone:' and take digits
            val line = n.lineSequence().firstOrNull { it.trim().startsWith("Phone:", ignoreCase = true) }
            line?.filter { it.isDigit() } ?: ""
        })
    }

    var showDatePicker by remember { mutableStateOf(false) }

    // Validation for mandatory fields
    val principalValid = principal.toDoubleOrNull() != null
    val rateValid = rateRupees.toDoubleOrNull() != null
    val nameValid = name.isNotBlank()
    val formValid = nameValid && principalValid && rateValid

    CenteredAlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                // Merge phone (if provided) into notes as the first line
                val phoneDigits = phone.filter { it.isDigit() }
                val mergedNotes = buildString {
                    // First, persist attachment if present
                    attachmentUri?.toString()?.let {
                        append("att: "); append(it)
                    }
                    if (phoneDigits.isNotBlank()) {
                        if (isNotEmpty()) append('\n')
                        append("Phone: "); append(phoneDigits)
                    }
                    val base = notes.trim()
                    if (base.isNotEmpty()) {
                        if (isNotEmpty()) append('\n')
                        append(base)
                    }
                }.ifBlank { null }
                val entry = LedgerEntry(
                    id = existing?.id ?: 0,
                    type = type,
                    name = name.trim(),
                    principal = (principal.toDoubleOrNull() ?: 0.0).coerceAtLeast(0.0),
                    interestType = interestType,
                    period = period,
                    compoundPeriod = compoundPeriod,
                    rateRupees = (rateRupees.toDoubleOrNull() ?: 0.0).coerceAtLeast(0.0),
                    fromDate = fromDate,
                    notes = mergedNotes
                )
                onSave(entry)
            }, enabled = formValid) { Text(if (isEdit) stringResource(R.string.update) else stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
        title = { Text(if (isEdit) stringResource(R.string.edit_entry) else stringResource(R.string.add_to_book), style = MaterialTheme.typography.titleLarge) },
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
                        label = { Text(stringResource(R.string.lend)) }
                    )
                    FilterChip(
                        selected = type.equals("BORROW", true),
                        onClick = { type = "BORROW" },
                        label = { Text(stringResource(R.string.borrow)) }
                    )
                }
                Spacer(Modifier.height(12.dp))

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(if (type.equals("LEND", true)) stringResource(R.string.borrower_name) else stringResource(R.string.lender_name)) },
                    modifier = Modifier.fillMaxWidth(),
                    isError = !nameValid && name.isNotEmpty(),
                    supportingText = {
                        if (!nameValid && name.isNotEmpty()) Text(stringResource(R.string.name_required))
                    }
                )
                Spacer(Modifier.height(8.dp))
                // Phone number (optional, digits only)
                OutlinedTextField(
                    value = phone,
                    onValueChange = { input -> phone = input.filter { it.isDigit() } },
                    label = { Text(stringResource(R.string.phone_optional)) },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                Spacer(Modifier.height(8.dp))

                Text(stringResource(R.string.interest_type), style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(4.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    FilterChip(
                        selected = interestType.equals("SIMPLE", true),
                        onClick = { interestType = "SIMPLE" },
                        label = { Text(stringResource(R.string.simple)) }
                    )
                    FilterChip(
                        selected = interestType.equals("COMPOUND", true),
                        onClick = { interestType = "COMPOUND" },
                        label = { Text(stringResource(R.string.compound)) }
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.rate_basis), style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(4.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    FilterChip(selected = period == "MONTHLY", onClick = { period = "MONTHLY" }, label = { Text(stringResource(R.string.monthly)) })
                    FilterChip(selected = period == "YEARLY", onClick = { period = "YEARLY" }, label = { Text(stringResource(R.string.yearly)) })
                }

                if (interestType.equals("COMPOUND", true)) {
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.duration_type), style = MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.height(4.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        FilterChip(selected = compoundPeriod == "MONTHLY", onClick = { compoundPeriod = "MONTHLY" }, label = { Text(stringResource(R.string.monthly)) })
                        FilterChip(selected = compoundPeriod == "YEARLY", onClick = { compoundPeriod = "YEARLY" }, label = { Text(stringResource(R.string.yearly)) })
                    }
                }

                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = principal,
                    onValueChange = { input ->
                        // Allow only digits and a decimal point; disallow negatives
                        principal = input.filter { ch -> ch.isDigit() || ch == '.' }
                    },
                    label = { Text(stringResource(R.string.principal_amount)) },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    isError = principal.isNotEmpty() && !principalValid,
                    supportingText = {
                        if (principal.isNotEmpty() && !principalValid) Text(stringResource(R.string.enter_valid_number))
                    }
                )
                val principalDouble = principal.toDoubleOrNull()
                if (principalDouble != null) {
                    val ctx = LocalContext.current
                    val lang = runCatching { ctx.resources.configuration.locales[0]?.language ?: "en" }.getOrElse { "en" }
                    val words = NumberToWords.inIndianSystem(principalDouble, lang)
                    if (words.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(words, style = MaterialTheme.typography.labelSmall)
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = rateRupees,
                    onValueChange = { input ->
                        // Allow only digits and a decimal point; disallow negatives
                        rateRupees = input.filter { ch -> ch.isDigit() || ch == '.' }
                    },
                    label = { Text(stringResource(R.string.interest_rate_percent)) },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    isError = rateRupees.isNotEmpty() && !rateValid,
                    supportingText = {
                        if (rateRupees.isNotEmpty() && !rateValid) Text(stringResource(R.string.enter_valid_number))
                    }
                )

                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = SimpleDateFormat("dd/MM/yyyy").format(Date(fromDate)),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(stringResource(R.string.from_date)) },
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = {
                        TextButton(onClick = { showDatePicker = true }) { Text(stringResource(R.string.pick)) }
                    }
                )

                if (showDatePicker) {
                    val today = System.currentTimeMillis()
                    val dateState = rememberDatePickerState(
                        initialSelectedDateMillis = fromDate,
                        selectableDates = object : SelectableDates {
                            override fun isSelectableDate(utcTimeMillis: Long): Boolean = utcTimeMillis <= today
                        }
                    )
                    DatePickerDialog(
                        onDismissRequest = { showDatePicker = false },
                        confirmButton = {
                            TextButton(onClick = {
                                val selected = dateState.selectedDateMillis ?: fromDate
                                fromDate = selected.coerceAtMost(today)
                                showDatePicker = false
                            }) { Text(stringResource(R.string.ok)) }
                        },
                        dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text(stringResource(R.string.cancel)) } }
                    ) {
                        DatePicker(state = dateState)
                    }
                }

                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text(stringResource(R.string.notes_optional)) },
                    modifier = Modifier.fillMaxWidth()
                )
                // Attachment row (like Partial Payment)
                Spacer(Modifier.height(8.dp))
                val ctx = LocalContext.current
                val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
                    if (uri != null) {
                        try {
                            ctx.contentResolver.openAssetFileDescriptor(uri, "r")?.use { afd ->
                                val size = afd.length
                                val dir = File(ctx.filesDir, "attachments").apply { mkdirs() }
                                if (size in 1..1_000_000) {
                                    val outFile = File(dir, "att_${'$'}{System.currentTimeMillis()}.jpg")
                                    ctx.contentResolver.openInputStream(uri)?.use { input ->
                                        FileOutputStream(outFile).use { output -> input.copyTo(output) }
                                    }
                                    attachmentUri = FileProvider.getUriForFile(ctx, ctx.packageName + ".fileprovider", outFile)
                                } else if (size <= 0) {
                                    val outFile = File(dir, "att_${'$'}{System.currentTimeMillis()}")
                                    ctx.contentResolver.openInputStream(uri)?.use { input ->
                                        FileOutputStream(outFile).use { output -> input.copyTo(output) }
                                    }
                                    attachmentUri = FileProvider.getUriForFile(ctx, ctx.packageName + ".fileprovider", outFile)
                                } else {
                                    android.widget.Toast.makeText(ctx, ctx.getString(R.string.image_too_large), android.widget.Toast.LENGTH_SHORT).show()
                                }
                            }
                        } catch (_: Exception) {
                            attachmentUri = null
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) { Text(stringResource(R.string.attach)) }
                    Spacer(Modifier.width(8.dp))
                    attachmentUri?.let { att ->
                        val painter = rememberAsyncImagePainter(att)
                        Image(
                            painter = painter,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp).clip(RoundedCornerShape(6.dp)).clickable {
                                try {
                                    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                                        setDataAndType(att, "image/*")
                                        addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    ctx.startActivity(intent)
                                } catch (_: Exception) {}
                            },
                            contentScale = ContentScale.Crop
                        )
                        Spacer(Modifier.width(8.dp))
                        TextButton(onClick = { attachmentUri = null }) { Text(stringResource(R.string.delete)) }
                    }
                }
            }
        }
    )
}

@Composable
private fun CenteredAlertDialog(
    onDismissRequest: () -> Unit,
    title: @Composable (() -> Unit)? = null,
    text: @Composable (() -> Unit)? = null,
    confirmButton: @Composable () -> Unit,
    dismissButton: (@Composable () -> Unit)? = null
) {
    Dialog(onDismissRequest = onDismissRequest, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(shape = MaterialTheme.shapes.medium, tonalElevation = 6.dp) {
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

@Composable
private fun CenteredDatePickerDialog(
    onDismissRequest: () -> Unit,
    onConfirm: () -> Unit,
    content: @Composable () -> Unit
) {
    Dialog(onDismissRequest = onDismissRequest, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Surface(shape = MaterialTheme.shapes.medium, tonalElevation = 6.dp, modifier = Modifier.fillMaxWidth(0.9f)) {
                Column(Modifier.padding(16.dp)) {
                    content()
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = onDismissRequest) { Text(stringResource(R.string.cancel)) }
                        Spacer(Modifier.width(8.dp))
                        TextButton(onClick = onConfirm) { Text(stringResource(R.string.ok)) }
                    }
                }
            }
        }
    }
}
