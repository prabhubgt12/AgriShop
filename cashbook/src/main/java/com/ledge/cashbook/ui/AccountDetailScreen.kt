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
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.hilt.navigation.compose.hiltViewModel
import com.ledge.cashbook.R
import com.ledge.cashbook.util.Currency
import com.ledge.cashbook.data.local.entities.CashTxn
import com.ledge.cashbook.data.local.entities.CashAccount
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.TableView
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.LocalMinimumInteractiveComponentEnforcement
import androidx.compose.runtime.CompositionLocalProvider
 
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.platform.LocalContext
import kotlin.math.roundToInt
import java.text.SimpleDateFormat
import java.util.Date
import com.ledge.cashbook.util.PdfShare
import com.ledge.cashbook.util.ExcelShare
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import coil.compose.AsyncImage
import androidx.compose.material.icons.filled.Attachment
import androidx.compose.foundation.clickable
import android.net.Uri
import android.graphics.BitmapFactory
import java.io.File
import java.io.FileOutputStream
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.window.Dialog
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun AccountDetailScreen(accountId: Int, onBack: () -> Unit, openAdd: Boolean = false, vm: AccountDetailViewModel = hiltViewModel()) {
    LaunchedEffect(accountId) { vm.load(accountId) }

    val name by vm.accountName.collectAsState()
    val txns by vm.txns.collectAsState()
    val balance by vm.balance.collectAsState()
    val ctx = LocalContext.current
    

    var showAdd by remember { mutableStateOf(false) }
    var isCredit by remember { mutableStateOf(true) }
    var amount by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var dateMillis by remember { mutableStateOf(System.currentTimeMillis()) }
    var addAttachmentUri by remember { mutableStateOf<String?>(null) }
    var addCategory by remember { mutableStateOf("") }
    var showDatePicker by remember { mutableStateOf(false) }
    var confirmDeleteTxn by remember { mutableStateOf<CashTxn?>(null) }
    // Long-press actions and edit states
    var actionTxn by remember { mutableStateOf<CashTxn?>(null) }
    var editTxn by remember { mutableStateOf<CashTxn?>(null) }
    var editIsCredit by remember { mutableStateOf(true) }
    var editAmount by remember { mutableStateOf("") }
    var editNote by remember { mutableStateOf("") }
    var editDateMillis by remember { mutableStateOf(System.currentTimeMillis()) }
    var editAttachmentUri by remember { mutableStateOf<String?>(null) }
    var editCategory by remember { mutableStateOf("") }
    var showEditDatePicker by remember { mutableStateOf(false) }
    var moveFor by remember { mutableStateOf<CashTxn?>(null) }
    var moveBulk by remember { mutableStateOf(false) }
    // Explicit selection mode toggle for bulk actions
    var selectionMode by remember { mutableStateOf(false) }
    // Pending confirmations
    var confirmMoveSingle by remember { mutableStateOf<Pair<CashTxn, CashAccount>?>(null) }
    var confirmMoveBulk by remember { mutableStateOf<CashAccount?>(null) }
    // Load all accounts for move action (placed before usage)
    val accountsVM: AccountsViewModel = hiltViewModel()
    val allAccounts by accountsVM.accounts.collectAsState()
    // Filter state
    var filterMenuOpen by remember { mutableStateOf(false) }
    var filterStart by remember { mutableStateOf<Long?>(null) }
    var filterEnd by remember { mutableStateOf<Long?>(null) }
    var showStartPicker by remember { mutableStateOf(false) }
    var showEndPicker by remember { mutableStateOf(false) }

    // Derived filter state and filtered transactions for reuse across top bar, list and footer
    val isFiltered = remember(filterStart, filterEnd) { filterStart != null || filterEnd != null }
    val filteredTxns = remember(txns, filterStart, filterEnd) {
        txns.filter { t ->
            val sOk = filterStart?.let { t.date >= it } ?: true
            val eOk = filterEnd?.let { t.date <= it } ?: true
            sOk && eOk
        }
    }
    // Observe bulk selection from VM
    val selectedIds by vm.selection.collectAsState()
    val inSelectionMode = selectionMode || selectedIds.isNotEmpty()

    // Image preview dialog state and in-app preview (reduced height)
    var previewUri by remember { mutableStateOf<String?>(null) }
    val config = LocalConfiguration.current
    val maxPreviewHeight = (config.screenHeightDp * 0.6f).dp
    val toPreview = previewUri
    if (toPreview != null) {
        AlertDialog(
            onDismissRequest = { previewUri = null },
            confirmButton = { TextButton(onClick = { previewUri = null }) { Text(stringResource(R.string.ok)) } },
            title = { Text(stringResource(R.string.attachment)) },
            text = {
                val bmp = remember(toPreview) {
                    try {
                        if (toPreview.startsWith("content:")) {
                            ctx.contentResolver.openInputStream(Uri.parse(toPreview))?.use { BitmapFactory.decodeStream(it) }
                        } else {
                            BitmapFactory.decodeFile(toPreview)
                        }
                    } catch (e: Exception) { null }
                }
                if (bmp != null) {
                    var scale by remember(toPreview) { mutableStateOf(1f) }
                    var offset by remember(toPreview) { mutableStateOf(Offset.Zero) }
                    val transformState = rememberTransformableState { zoomChange, panChange, _ ->
                        val newScale = (scale * zoomChange).coerceIn(1f, 5f)
                        // Adjust pan sensitivity by scale
                        val pan = if (newScale > 1f) panChange else Offset.Zero
                        scale = newScale
                        offset += pan
                    }
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = maxPreviewHeight)
                            .graphicsLayer(
                                scaleX = scale,
                                scaleY = scale,
                                translationX = offset.x,
                                translationY = offset.y
                            )
                            .transformable(transformState),
                        contentScale = ContentScale.Fit
                    )
                } else {
                    Text("Failed to load image.")
                }
            }
        )
    }

    // Bulk move dialog (account picker)
    if (moveBulk) {
        val options = allAccounts.filter { it.id != accountId }
        var bulkSelected by remember { mutableStateOf<CashAccount?>(null) }
        var bulkExpanded by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { moveBulk = false },
            title = { Text(text = stringResource(R.string.move_to_account)) },
            confirmButton = {
                TextButton(onClick = {
                    bulkSelected?.let { acc ->
                        confirmMoveBulk = acc
                        moveBulk = false
                    }
                }, enabled = bulkSelected != null) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = { TextButton(onClick = { moveBulk = false }) { Text(stringResource(R.string.cancel)) } },
            text = {
                if (options.isEmpty()) {
                    Text(stringResource(R.string.no_other_accounts))
                } else {
                    ExposedDropdownMenuBox(expanded = bulkExpanded, onExpandedChange = { bulkExpanded = !bulkExpanded }) {
                        OutlinedTextField(
                            value = bulkSelected?.name ?: stringResource(R.string.select_account_label),
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(stringResource(R.string.move_to_account)) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = bulkExpanded) },
                            modifier = Modifier
                                .menuAnchor()
                                .fillMaxWidth()
                        )
                        ExposedDropdownMenu(expanded = bulkExpanded, onDismissRequest = { bulkExpanded = false }) {
                            options.forEach { acc ->
                                DropdownMenuItem(text = { Text(acc.name) }, onClick = {
                                    bulkSelected = acc
                                    bulkExpanded = false
                                })
                            }
                        }
                    }
                }
            }
        )
    }

    // Move transaction dialog (account picker for single)
    val toMove = moveFor
    if (toMove != null) {
        val options = allAccounts.filter { it.id != toMove.accountId }
        var singleSelected by remember { mutableStateOf<CashAccount?>(null) }
        var singleExpanded by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { moveFor = null },
            title = { Text(text = stringResource(R.string.move_to_account)) },
            confirmButton = {
                TextButton(onClick = {
                    singleSelected?.let { acc ->
                        confirmMoveSingle = toMove to acc
                        moveFor = null
                    }
                }, enabled = singleSelected != null) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = { TextButton(onClick = { moveFor = null }) { Text(stringResource(R.string.cancel)) } },
            text = {
                if (options.isEmpty()) {
                    Text(stringResource(R.string.no_other_accounts))
                } else {
                    ExposedDropdownMenuBox(expanded = singleExpanded, onExpandedChange = { singleExpanded = !singleExpanded }) {
                        OutlinedTextField(
                            value = singleSelected?.name ?: stringResource(R.string.select_account_label),
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(stringResource(R.string.move_to_account)) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = singleExpanded) },
                            modifier = Modifier
                                .menuAnchor()
                                .fillMaxWidth()
                        )
                        ExposedDropdownMenu(expanded = singleExpanded, onDismissRequest = { singleExpanded = false }) {
                            options.forEach { acc ->
                                DropdownMenuItem(text = { Text(acc.name) }, onClick = {
                                    singleSelected = acc
                                    singleExpanded = false
                                })
                            }
                        }
                    }
                }
            }
        )
    }

    // Confirm single move
    val pendingSingle = confirmMoveSingle
    if (pendingSingle != null) {
        val (txn, acc) = pendingSingle
        AlertDialog(
            onDismissRequest = { confirmMoveSingle = null },
            title = { Text(stringResource(R.string.confirm_move_title)) },
            text = { Text(String.format(stringResource(R.string.confirm_move_single), acc.name)) },
            confirmButton = {
                TextButton(onClick = {
                    vm.updateTxn(txn.copy(accountId = acc.id))
                    confirmMoveSingle = null
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = { TextButton(onClick = { confirmMoveSingle = null }) { Text(stringResource(R.string.cancel)) } }
        )
    }

    // Confirm bulk move
    val pendingBulk = confirmMoveBulk
    if (pendingBulk != null) {
        val count = selectedIds.size
        AlertDialog(
            onDismissRequest = { confirmMoveBulk = null },
            title = { Text(stringResource(R.string.confirm_move_title)) },
            text = { Text(String.format(stringResource(R.string.confirm_move_bulk), count, pendingBulk.name)) },
            confirmButton = {
                TextButton(onClick = {
                    vm.moveSelected(pendingBulk.id)
                    confirmMoveBulk = null
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = { TextButton(onClick = { confirmMoveBulk = null }) { Text(stringResource(R.string.cancel)) } }
        )
    }

    // Move transaction dialog (defined later, after accounts list is loaded)
    val filteredBalance = remember(filteredTxns) {
        val credit = filteredTxns.filter { it.isCredit }.sumOf { it.amount }
        val debit = filteredTxns.filter { !it.isCredit }.sumOf { it.amount }
        credit - debit
    }

    LaunchedEffect(openAdd) {
        if (openAdd) showAdd = true
    }

    // Settings for category dropdown
    val settingsVM: SettingsViewModel = hiltViewModel()
    val showCategory by settingsVM.showCategory.collectAsState(initial = false)
    val categoriesCsv by settingsVM.categoriesCsv.collectAsState(initial = "")
    val categories = remember(categoriesCsv) {
        val normalized = categoriesCsv
            .replace('\r', '\n')
            .replace('\uFF0C', ',') // fullwidth comma
            .replace('\u060C', ',') // arabic comma
            .replace('\u061B', ';') // arabic semicolon
        Regex("[,;\n]+")
            .split(normalized)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
    }


    // Helper to copy the picked image into app-private storage and keep only our own file path
    fun copyPickedToApp(uri: Uri): String? {
        return try {
            val input = ctx.contentResolver.openInputStream(uri) ?: return null
            val dir = File(ctx.filesDir, "attachments").apply { mkdirs() }
            val ext = "jpg"
            val outFile = File(dir, "att_${System.currentTimeMillis()}.$ext")
            FileOutputStream(outFile).use { out ->
                input.copyTo(out)
            }
            input.close()
            outFile.absolutePath
        } catch (e: Exception) { null }
    }

    // Long-press actions dialog (Edit/Delete)
    val pendingAction = actionTxn
    if (pendingAction != null) {
        AlertDialog(
            onDismissRequest = { actionTxn = null },
            title = { Text(stringResource(R.string.select_action)) },
            confirmButton = {},
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TextButton(onClick = {
                        // Start edit with prefilled fields
                        editTxn = pendingAction
                        editIsCredit = pendingAction.isCredit
                        editAmount = pendingAction.amount.toString()
                        editNote = pendingAction.note ?: ""
                        editDateMillis = pendingAction.date
                        editAttachmentUri = pendingAction.attachmentUri
                        editCategory = pendingAction.category ?: ""
                        actionTxn = null
                    }) { Text(stringResource(R.string.edit)) }
                    TextButton(onClick = { confirmDeleteTxn = pendingAction; actionTxn = null }) { Text(stringResource(R.string.delete)) }
                    TextButton(onClick = { moveFor = pendingAction; actionTxn = null }) { Text(stringResource(R.string.move)) }
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
                            note = editNote,
                            attachmentUri = editAttachmentUri,
                            category = editCategory.ifBlank { null }
                        )
                        vm.updateTxn(updated)
                        editTxn = null
                    }
                ) { Text(stringResource(R.string.update)) }
            },
            dismissButton = { TextButton(onClick = { editTxn = null }) { Text(stringResource(R.string.cancel)) } },
            title = { Text(stringResource(R.string.edit)) },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .verticalScroll(rememberScrollState())
                        .windowInsetsPadding(WindowInsets.ime)
                ) {
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
                        label = { Text(stringResource(R.string.amount)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = editNote,
                        onValueChange = { editNote = it },
                        label = { Text(stringResource(R.string.particular)) }
                    )
                    if (showCategory) {
                        var expanded by remember { mutableStateOf(false) }
                        Box {
                            OutlinedTextField(
                                value = if (editCategory.isBlank()) stringResource(R.string.select_category) else editCategory,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text(stringResource(R.string.category)) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { expanded = true },
                                trailingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.ArrowDropDown,
                                        contentDescription = null,
                                        modifier = Modifier.clickable { expanded = !expanded }
                                    )
                                }
                            )
                            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                                DropdownMenuItem(text = { Text(stringResource(R.string.uncategorized)) }, onClick = { editCategory = ""; expanded = false })
                                categories.forEach { cat ->
                                    DropdownMenuItem(text = { Text(cat) }, onClick = { editCategory = cat; expanded = false })
                                }
                            }
                        }
                    }
                    val pickEditImageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
                        editAttachmentUri = uri?.let { copyPickedToApp(it) }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { pickEditImageLauncher.launch("image/*") }) {
                            Icon(Icons.Filled.Attachment, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text(text = stringResource(id = R.string.add_attachment))
                        }
                        if (editAttachmentUri != null) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                AsyncImage(
                                    model = File(editAttachmentUri!!),
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(56.dp)
                                        .clip(RoundedCornerShape(6.dp)),
                                    contentScale = ContentScale.Crop
                                )
                                IconButton(onClick = { editAttachmentUri = null }) {
                                    Icon(Icons.Default.Close, contentDescription = stringResource(id = R.string.cancel))
                                }
                            }
                        }
                    }
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
                        if (inSelectionMode) {
                            Text(
                                text = "${selectedIds.size} selected",
                                style = MaterialTheme.typography.titleSmall
                            )
                        } else {
                            Text(
                                name,
                                style = MaterialTheme.typography.titleSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null) }
                    },
                    actions = {
                        if (inSelectionMode) {
                            CompositionLocalProvider(LocalMinimumInteractiveComponentEnforcement provides false) {
                                IconButton(
                                    onClick = { if (selectedIds.isNotEmpty()) moveBulk = true },
                                    enabled = selectedIds.isNotEmpty(),
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(Icons.Filled.SwapHoriz, contentDescription = stringResource(R.string.move), modifier = Modifier.size(20.dp))
                                }
                                Spacer(Modifier.width(8.dp))
                                IconButton(
                                    onClick = { vm.clearSelection(); selectionMode = false },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.cancel), modifier = Modifier.size(20.dp))
                                }
                            }
                        } else {
                            val displayBal = if (isFiltered) filteredBalance else balance
                            val pos = displayBal >= 0
                            val chipBg = if (pos) Color(0xFFDFF6DD) else Color(0xFFFFE2E0)
                            val chipFg = if (pos) Color(0xFF0B6A0B) else Color(0xFF9A0007)
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(chipBg)
                                    .padding(vertical = 1.dp, horizontal = 4.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = stringResource(R.string.balance) + ": ",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = chipFg
                                    )
                                    Text(
                                        text = Currency.inr(displayBal),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = chipFg
                                    )
                                }
                            }
                            // Date filter button (no badge/tint)
                            CompositionLocalProvider(LocalMinimumInteractiveComponentEnforcement provides false) {
                                IconButton(onClick = { filterMenuOpen = true }, modifier = Modifier.size(24.dp)) {
                                    Icon(Icons.Default.FilterList, contentDescription = "Filter", modifier = Modifier.size(20.dp))
                                }
                                Spacer(Modifier.width(8.dp))
                                // Enter bulk selection mode
                                IconButton(onClick = { selectionMode = true }, modifier = Modifier.size(24.dp)) {
                                    Icon(Icons.Filled.SelectAll, contentDescription = "Select", modifier = Modifier.size(20.dp))
                                }
                            }
                            DropdownMenu(expanded = filterMenuOpen, onDismissRequest = { filterMenuOpen = false }) {
                                val ctxMenu = LocalContext.current
                                DropdownMenuItem(text = { Text(stringResource(R.string.export_to_pdf)) }, onClick = {
                                    filterMenuOpen = false
                                    val list = if (isFiltered) filteredTxns else txns
                                    PdfShare.exportAccount(ctxMenu, name, list, startMillis = filterStart, endMillis = filterEnd)
                                })
                                DropdownMenuItem(text = { Text(stringResource(R.string.export_to_excel)) }, onClick = {
                                    filterMenuOpen = false
                                    val list = if (isFiltered) filteredTxns else txns
                                    ExcelShare.exportAccountXlsx(ctxMenu, name, list, startMillis = filterStart, endMillis = filterEnd, showCategory = showCategory)
                                })
                                Divider()
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
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        titleContentColor = MaterialTheme.colorScheme.onPrimary,
                        navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                        actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                    )
                )

                if (isFiltered) {
                    val fmt = remember { SimpleDateFormat("dd MMM yyyy", java.util.Locale.getDefault()) }
                    val startStr = filterStart?.let { fmt.format(Date(it)) }
                    val endStr = filterEnd?.let { fmt.format(Date(it)) }
                    val ctxBar = LocalContext.current
                    Surface(color = MaterialTheme.colorScheme.primary) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 2.dp),
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
                            // Export filtered list to PDF/Excel (second row)
                            IconButton(onClick = { PdfShare.exportAccount(ctxBar, name, filteredTxns, startMillis = filterStart, endMillis = filterEnd) }) {
                                Icon(
                                    imageVector = Icons.Filled.PictureAsPdf,
                                    contentDescription = stringResource(R.string.export_to_pdf),
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            IconButton(onClick = { ExcelShare.exportAccountXlsx(ctxBar, name, filteredTxns, startMillis = filterStart, endMillis = filterEnd, showCategory = showCategory) }) {
                                Icon(
                                    imageVector = Icons.Filled.TableView,
                                    contentDescription = stringResource(R.string.export_to_excel),
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            IconButton(onClick = { filterStart = null; filterEnd = null }) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = stringResource(R.string.cancel),
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(24.dp)
                                )
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

            // filteredTxns already computed above to be shared with top bar and totals
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
                            .padding(vertical = 6.dp, horizontal = 6.dp),
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
                                onClick = {
                                    if (inSelectionMode) vm.toggleSelection(t.id) else Unit
                                },
                                onLongClick = {
                                    if (inSelectionMode) vm.toggleSelection(t.id) else actionTxn = t
                                }
                            )
                            .padding(vertical = 6.dp, horizontal = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (inSelectionMode) {
                            Checkbox(checked = t.id in selectedIds, onCheckedChange = { vm.toggleSelection(t.id) })
                            Spacer(Modifier.width(6.dp))
                        }
                        Column(
                            modifier = Modifier
                                .weight(wDatePart)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    SimpleDateFormat("dd/MM/yy").format(Date(t.date)),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (t.attachmentUri != null) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    CompositionLocalProvider(LocalMinimumInteractiveComponentEnforcement provides false) {
                                        IconButton(
                                            onClick = {
                                                previewUri = t.attachmentUri
                                            },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.Attachment,
                                                contentDescription = "View attachment",
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                            }
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
        val pickImageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            addAttachmentUri = uri?.let { copyPickedToApp(it) }
        }
        AlertDialog(
            onDismissRequest = { showAdd = false },
            confirmButton = {
                TextButton(
                    enabled = amountValid && noteValid,
                    onClick = {
                        val amt = amount.toDoubleOrNull() ?: 0.0
                        if (amt <= 0 || note.isBlank()) return@TextButton
                        vm.addTxn(dateMillis, amt, isCredit, note, addAttachmentUri, addCategory.ifBlank { null })
                        showAdd = false
                        isCredit = true
                        amount = ""
                        note = ""
                        dateMillis = System.currentTimeMillis()
                        addAttachmentUri = null
                        addCategory = ""
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
                        label = { Text(stringResource(R.string.amount)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = note,
                        onValueChange = { note = it },
                        label = { Text(stringResource(R.string.particular)) }
                    )
                    if (showCategory) {
                        var expanded by remember { mutableStateOf(false) }
                        Box {
                            OutlinedTextField(
                                value = if (addCategory.isBlank()) stringResource(R.string.select_category) else addCategory,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text(stringResource(R.string.category)) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { expanded = true },
                                trailingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.ArrowDropDown,
                                        contentDescription = null,
                                        modifier = Modifier.clickable { expanded = !expanded }
                                    )
                                }
                            )
                            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                                DropdownMenuItem(text = { Text(stringResource(R.string.uncategorized)) }, onClick = { addCategory = ""; expanded = false })
                                categories.forEach { cat ->
                                    DropdownMenuItem(text = { Text(cat) }, onClick = { addCategory = cat; expanded = false })
                                }
                            }
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { pickImageLauncher.launch("image/*") }) {
                            Icon(Icons.Filled.Attachment, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text(text = stringResource(id = R.string.add_attachment))
                        }
                        if (addAttachmentUri != null) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                AsyncImage(
                                    model = File(addAttachmentUri!!),
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(56.dp)
                                        .clip(RoundedCornerShape(6.dp)),
                                    contentScale = ContentScale.Crop
                                )
                                IconButton(onClick = { addAttachmentUri = null }) {
                                    Icon(Icons.Default.Close, contentDescription = stringResource(id = R.string.cancel))
                                }
                            }
                        }
                    }
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
