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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.activity.compose.BackHandler

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
    var notes by remember {
        mutableStateOf(
            run {
                val raw = existing?.notes ?: ""
                raw.lineSequence()
                    .filterNot { line ->
                        val t = line.trim()
                        t.startsWith("att:", ignoreCase = true) || t.startsWith("Phone:", ignoreCase = true)
                    }
                    .joinToString("\n").trim()
            }
        )
    }
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
    // Optional phone number (digits only). Prefill from existing notes Phone: line if present.
    var phone by remember {
        mutableStateOf(run {
            val n = existing?.notes ?: ""
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

    val compactFieldModifier = Modifier
        .fillMaxWidth()
        .heightIn(min = 32.dp)

    // Handle system back button
    BackHandler(enabled = true) {
        onDismiss()
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        if (isEdit) stringResource(R.string.edit_entry) else stringResource(R.string.add_to_book),
                        color = MaterialTheme.colorScheme.onPrimary
                    ) 
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                ),
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(
                            Icons.Filled.ArrowBack, 
                            contentDescription = stringResource(R.string.cancel),
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp, vertical = 2.dp)
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    CompactFilterChip(
                        selected = type.equals("LEND", true),
                        onClick = { type = "LEND" },
                        label = { Text(stringResource(R.string.lend)) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            containerColor = MaterialTheme.colorScheme.surface,
                            labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                    CompactFilterChip(
                        selected = type.equals("BORROW", true),
                        onClick = { type = "BORROW" },
                        label = { Text(stringResource(R.string.borrow)) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            containerColor = MaterialTheme.colorScheme.surface,
                            labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }
                Spacer(Modifier.height(4.dp))

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(if (type.equals("LEND", true)) stringResource(R.string.borrower_name) else stringResource(R.string.lender_name)) },
                    modifier = compactFieldModifier,
                    isError = !nameValid && name.isNotEmpty(),
                    singleLine = true,
                    supportingText = {
                        if (!nameValid && name.isNotEmpty()) Text(stringResource(R.string.name_required))
                    }
                )
                Spacer(Modifier.height(2.dp))
                // Phone number (optional, digits only)
                OutlinedTextField(
                    value = phone,
                    onValueChange = { input -> phone = input.filter { it.isDigit() } },
                    label = { Text(stringResource(R.string.phone_optional)) },
                    modifier = compactFieldModifier,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                Spacer(Modifier.height(2.dp))

                Text(stringResource(R.string.interest_type), style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(1.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    CompactFilterChip(
                        selected = interestType.equals("SIMPLE", true),
                        onClick = { interestType = "SIMPLE" },
                        label = { Text(stringResource(R.string.simple)) }
                    )
                    CompactFilterChip(
                        selected = interestType.equals("COMPOUND", true),
                        onClick = { interestType = "COMPOUND" },
                        label = { Text(stringResource(R.string.compound)) }
                    )
                }

                if (interestType.equals("COMPOUND", true)) {
                    Spacer(Modifier.height(4.dp))
                    Text(stringResource(R.string.duration_type), style = MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.height(1.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        CompactFilterChip(
                            selected = compoundPeriod == "MONTHLY",
                            onClick = { compoundPeriod = "MONTHLY" },
                            label = { Text(stringResource(R.string.monthly)) }
                        )
                        CompactFilterChip(
                            selected = compoundPeriod == "YEARLY",
                            onClick = { compoundPeriod = "YEARLY" },
                            label = { Text(stringResource(R.string.yearly)) }
                        )
                    }
                }

                Spacer(Modifier.height(4.dp))
                OutlinedTextField(
                    value = principal,
                    onValueChange = { input ->
                        // Allow only digits and a decimal point; disallow negatives
                        principal = input.filter { ch -> ch.isDigit() || ch == '.' }
                    },
                    label = { Text(stringResource(R.string.principal_amount)) },
                    modifier = compactFieldModifier,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    isError = principal.isNotEmpty() && !principalValid,
                    singleLine = true,
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
                        Spacer(Modifier.height(1.dp))
                        Text(words, style = MaterialTheme.typography.labelSmall)
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(stringResource(R.string.rate_basis), style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(1.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    CompactFilterChip(
                        selected = period == "MONTHLY",
                        onClick = { period = "MONTHLY" },
                        label = { Text(stringResource(R.string.rate_basis_rupee)) }
                    )
                    CompactFilterChip(
                        selected = period == "YEARLY",
                        onClick = { period = "YEARLY" },
                        label = { Text(stringResource(R.string.rate_basis_percentage)) }
                    )
                }
                Spacer(Modifier.height(4.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = rateRupees,
                        onValueChange = { input ->
                            // Allow only digits and a decimal point; disallow negatives
                            rateRupees = input.filter { ch -> ch.isDigit() || ch == '.' }
                        },
                        label = {
                            Text(
                                if (period == "MONTHLY") {
                                    "${stringResource(R.string.interest_rate)} (₹)"
                                } else {
                                    "${stringResource(R.string.interest_rate)} (%)"
                                }
                            )
                        },
                        modifier = Modifier.weight(1f).heightIn(min = 32.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        isError = rateRupees.isNotEmpty() && !rateValid,
                        singleLine = true,
                        supportingText = {
                            if (rateRupees.isNotEmpty() && !rateValid) Text(stringResource(R.string.enter_valid_number))
                        }
                    )
                    OutlinedTextField(
                        value = SimpleDateFormat("dd/MM/yyyy").format(Date(fromDate)),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.from_date)) },
                        modifier = Modifier.weight(1f).heightIn(min = 32.dp),
                        singleLine = true,
                        trailingIcon = {
                            IconButton(onClick = { showDatePicker = true }) {
                                Icon(Icons.Filled.DateRange, contentDescription = stringResource(R.string.pick))
                            }
                        }
                    )
                }

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

                Spacer(Modifier.height(2.dp))
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text(stringResource(R.string.notes_optional)) },
                    modifier = Modifier.fillMaxWidth()
                )
                // Attachment row (like Partial Payment)
                Spacer(Modifier.height(2.dp))
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
            Spacer(Modifier.height(4.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .imePadding()
                    .navigationBarsPadding()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.cancel))
                }
                Button(
                    onClick = {
                        // Build notes as: att: <uri> (if any), Phone: <digits> (if any), then user's notes (without previous meta)
                        val phoneDigits = phone.filter { it.isDigit() }
                        val mergedNotes = buildString {
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
                    },
                    enabled = formValid,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(if (isEdit) stringResource(R.string.update) else stringResource(R.string.save))
                }
            }
        }
    }
}

@Composable
private fun CompactFilterChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    colors: SelectableChipColors = FilterChipDefaults.filterChipColors(),
) {
    InputChip(
        selected = selected,
        onClick = onClick,
        label = {
            ProvideTextStyle(MaterialTheme.typography.labelSmall) {
                label()
            }
        },
        modifier = Modifier.heightIn(min = 28.dp),
        colors = colors
    )
}
