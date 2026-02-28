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
import androidx.compose.ui.unit.sp
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
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Today
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
import com.ledge.cashbook.ads.BannerAd
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.google.android.gms.ads.AdSize
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
import androidx.compose.ui.geometry.Size
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.FocusRequester

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun AccountDetailScreen(accountId: Int, onBack: () -> Unit, openAdd: Boolean = false, vm: AccountDetailViewModel = hiltViewModel()) {
    LaunchedEffect(accountId) {
        vm.load(accountId)
        // Generate any overdue recurring transactions when viewing this account
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            vm.generateRecurringTransactions()
        }
    }

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
    // Suggestion state for Edit dialog
    var userSetEditCategory by remember { mutableStateOf(false) }
    var editSuggestion by remember { mutableStateOf<Pair<String, Double>?>(null) }
    var autoSetEditCategory by remember { mutableStateOf(false) }
    // Add dialog suggestion state
    var userSetAddCategory by remember { mutableStateOf(false) }
    var addSuggestion by remember { mutableStateOf<Pair<String, Double>?>(null) }
    var autoSetAddCategory by remember { mutableStateOf(false) }
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
    // DB category list for dropdowns
    val catListVM: CategoryListViewModel = hiltViewModel()
    val dbCategories by catListVM.categories.collectAsState()
    // Filter state
    var filterMenuOpen by remember { mutableStateOf(false) }
    var filterStart by remember { mutableStateOf<Long?>(null) }
    var filterEnd by remember { mutableStateOf<Long?>(null) }
    var showStartPicker by remember { mutableStateOf(false) }
    var showEndPicker by remember { mutableStateOf(false) }

    // Recurring toggle state (add dialog) – monthly, no end date
    var addRecurring by remember { mutableStateOf(false) }
    // For edit dialog, remember whether this txn belongs to an active recurring rule.
    var editRecurring by remember { mutableStateOf(false) }
    // Track if user wants to stop recurring (UI shows stopped immediately, but actual stop happens on Update)
    var stopRecurringIntent by remember { mutableStateOf(false) }

    // Derived filter state and filtered transactions for reuse across top bar, list and footer
    val isFiltered = remember(filterStart, filterEnd) { filterStart != null || filterEnd != null }
    val filteredTxns = remember(txns, filterStart, filterEnd) {
        txns.filter { t ->
            val sOk = filterStart?.let { t.date >= it } ?: true
            val eOk = filterEnd?.let { t.date <= it } ?: true
            sOk && eOk
        }
    }
    // Search state and search-applied list
    var searchOpen by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    val displayedTxns = remember(filteredTxns, searchQuery) {
        val q = searchQuery.trim()
        if (q.isEmpty()) filteredTxns else filteredTxns.filter { t ->
            val noteOk = (t.note ?: "").contains(q, ignoreCase = true)
            val catOk = (t.category ?: "").contains(q, ignoreCase = true)
            val amtOk = t.amount.toString().contains(q, ignoreCase = true)
            noteOk || catOk || amtOk
        }
    }
    // Observe bulk selection from VM
    val selectedIds by vm.selection.collectAsState()
    val inSelectionMode = selectionMode || selectedIds.isNotEmpty()
    // Ads state
    val adsVm: AdsViewModel = hiltViewModel()
    val hasRemoveAds by adsVm.hasRemoveAds.collectAsState(initial = false)
    var bannerLoaded by remember { mutableStateOf(false) }
    // Expense overview chart state
    var showExpenseChart by remember { mutableStateOf(false) }

    // Vibrant palette shared with expense chart for category pills
    val categoryPalette = remember {
        listOf(
            Color(0xFFEF5350), Color(0xFFAB47BC), Color(0xFF5C6BC0), Color(0xFF29B6F6),
            Color(0xFF26A69A), Color(0xFF66BB6A), Color(0xFFFFCA28), Color(0xFFFFA726), Color(0xFF8D6E63)
        )
    }

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
            // Extra space below summary so it's fully visible above the banner
            if (!hasRemoveAds) {
                Spacer(Modifier.height(84.dp))
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

    // Settings toggle still controls visibility; source now from DB
    val settingsVM: SettingsViewModel = hiltViewModel()
    val showCategory by settingsVM.showCategory.collectAsState(initial = false)
    val showCategoryInList by settingsVM.showCategoryInList.collectAsState(initial = true)
    val categories = remember(dbCategories) { dbCategories.map { it.name }.distinct() }
    // Category suggestion engine (only when category is shown)
    val catSuggestVm: CategorySuggestionViewModel? = if (showCategory) hiltViewModel() else null


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
                        editAmount = java.math.BigDecimal.valueOf(pendingAction.amount).stripTrailingZeros().toPlainString()
                        editNote = pendingAction.note ?: ""
                        editDateMillis = pendingAction.date
                        editAttachmentUri = pendingAction.attachmentUri
                        editCategory = pendingAction.category ?: ""
                        editRecurring = pendingAction.recurringId != null
                        stopRecurringIntent = false
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
        // Refresh whether the linked recurring rule is still active whenever this dialog opens
        LaunchedEffect(toEdit.recurringId) {
            val id = toEdit.recurringId
            editRecurring = if (id != null) {
                try {
                    vm.isRecurringActive(id)
                } catch (_: Exception) {
                    false
                }
            } else {
                false
            }
        }
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
                        // Stop recurring if user requested it
                        if (stopRecurringIntent && toEdit.recurringId != null) {
                            vm.stopRecurring(toEdit)
                        }
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
                    // Recurring info: show only inside edit dialog; no list indicators
                    if (toEdit.recurringId != null) {
                        if (editRecurring) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Switch(
                                    checked = editRecurring,
                                    onCheckedChange = { checked ->
                                        if (!checked) {
                                            stopRecurringIntent = true
                                            editRecurring = false
                                        }
                                    }
                                )
                                Text(text = stringResource(R.string.repeat_monthly_toggle), style = MaterialTheme.typography.bodyMedium)
                            }
                        } else {
                            Text(
                                text = stringResource(R.string.recurring_stopped_label),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    val es = editSuggestion
                    if (es != null && editCategory.isBlank()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            AssistChip(onClick = {
                                editCategory = es.first
                                userSetEditCategory = true
                                editSuggestion = null
                            }, label = { Text(stringResource(R.string.label_suggested_with_name, es.first)) })
                        }
                    }
                    if (showCategory) {
                        var expanded by remember { mutableStateOf(false) }
                        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
                            OutlinedTextField(
                                value = if (editCategory.isBlank()) stringResource(R.string.select_category) else editCategory,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text(stringResource(R.string.category)) },
                                modifier = Modifier
                                    .menuAnchor()
                                    .fillMaxWidth(),
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) }
                            )
                            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
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
    // Expense overview dialog - pie chart of filtered debit totals (expenses) by category
    if (showExpenseChart) {
        val expenses = remember(filteredTxns) {
            filteredTxns.filter { !it.isCredit && it.amount > 0 }
        }
        val byCategory = remember(expenses) {
            expenses.groupBy { (it.category ?: "Uncategorized").trim().ifEmpty { "Uncategorized" } }
                .mapValues { entry -> entry.value.sumOf { it.amount } }
                .toList()
                .sortedByDescending { it.second }
        }
        val total = byCategory.sumOf { it.second }.coerceAtLeast(0.0)
        val palette = listOf(
            Color(0xFFEF5350), Color(0xFFAB47BC), Color(0xFF5C6BC0), Color(0xFF29B6F6),
            Color(0xFF26A69A), Color(0xFF66BB6A), Color(0xFFFFCA28), Color(0xFFFFA726), Color(0xFF8D6E63)
        )
        AlertDialog(
            onDismissRequest = { showExpenseChart = false },
            confirmButton = { TextButton(onClick = { showExpenseChart = false }) { Text(stringResource(R.string.ok)) } },
            title = { Text("Expense overview") },
            text = {
                if (total <= 0.0) {
                    Text("No expenses in the selected range.")
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        // Pie chart
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            val chartSize = 220.dp
                            Canvas(modifier = Modifier.size(chartSize)) {
                                var startAngle = -90f
                                byCategory.forEachIndexed { idx, (cat, value) ->
                                    val sweep = ((value / total) * 360.0).toFloat()
                                    drawArc(
                                        color = palette[idx % palette.size],
                                        startAngle = startAngle,
                                        sweepAngle = sweep,
                                        useCenter = true,
                                        style = Fill,
                                        size = Size(this.size.minDimension, this.size.minDimension),
                                        topLeft = androidx.compose.ui.geometry.Offset(
                                            (this.size.width - this.size.minDimension) / 2f,
                                            (this.size.height - this.size.minDimension) / 2f
                                        )
                                    )
                                    startAngle += sweep
                                }
                            }
                        }
                        // Legend
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            byCategory.forEachIndexed { idx, (cat, value) ->
                                val pct = (value / total * 100).toFloat()
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        Modifier.size(12.dp).background(palette[idx % palette.size], RoundedCornerShape(2.dp))
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = "$cat - ${String.format(java.util.Locale.getDefault(), "%.1f", pct)}% (${Currency.inr(value)})",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        }
                    }
                }
            }
        )
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
                            if (searchOpen) {
                                val fr = remember { FocusRequester() }
                                LaunchedEffect(Unit) { fr.requestFocus() }
                                val shape = RoundedCornerShape(20.dp)
                                BasicTextField(
                                    value = searchQuery,
                                    onValueChange = { searchQuery = it },
                                    singleLine = true,
                                    textStyle = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onPrimary),
                                    cursorBrush = SolidColor(MaterialTheme.colorScheme.onPrimary),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .focusRequester(fr),
                                    decorationBox = { innerTextField ->
                                        Row(
                                            Modifier
                                                .fillMaxWidth()
                                                .heightIn(min = 36.dp)
                                                .clip(shape)
                                                .border(1.dp, MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.5f), shape)
                                                .padding(horizontal = 10.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(Icons.Filled.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary)
                                            Spacer(Modifier.width(8.dp))
                                            Box(Modifier.weight(1f)) {
                                                if (searchQuery.isBlank()) {
                                                    Text(
                                                        stringResource(R.string.search),
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
                                                    )
                                                }
                                                innerTextField()
                                            }
                                        }
                                    }
                                )
                            } else {
                                Text(
                                    name,
                                    style = MaterialTheme.typography.titleSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    },
                    navigationIcon = {},
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
                            if (!searchOpen) {
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
                            }
                            // Date filter button (no badge/tint)
                            CompositionLocalProvider(LocalMinimumInteractiveComponentEnforcement provides false) {
                                IconButton(onClick = {
                                    if (searchOpen) { searchOpen = false; searchQuery = "" } else { searchOpen = true }
                                }, modifier = Modifier.size(24.dp)) {
                                    Icon(
                                        imageVector = if (searchOpen) Icons.Default.Close else Icons.Default.Search,
                                        contentDescription = if (searchOpen) stringResource(R.string.cancel) else stringResource(R.string.search),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Spacer(Modifier.width(8.dp))
                                // Actual filter icon
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
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.export_to_pdf)) },
                                    leadingIcon = { Icon(Icons.Default.PictureAsPdf, contentDescription = null) },
                                    onClick = {
                                    filterMenuOpen = false
                                    val list = if (isFiltered) filteredTxns else txns
                                    PdfShare.exportAccount(ctxMenu, name, list, startMillis = filterStart, endMillis = filterEnd)
                                })
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.export_to_excel)) },
                                    leadingIcon = { Icon(Icons.Default.TableView, contentDescription = null) },
                                    onClick = {
                                    filterMenuOpen = false
                                    val list = if (isFiltered) filteredTxns else txns
                                    ExcelShare.exportAccountXlsx(ctxMenu, name, list, startMillis = filterStart, endMillis = filterEnd, showCategory = showCategory)
                                })
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.category_split)) },
                                    leadingIcon = { Icon(Icons.Default.PieChart, contentDescription = null) },
                                    onClick = {
                                    filterMenuOpen = false
                                    showExpenseChart = true
                                })
                                Divider()
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.filter_today)) },
                                    leadingIcon = { Icon(Icons.Default.Today, contentDescription = null) },
                                    onClick = {
                                    filterMenuOpen = false
                                    val now = java.util.Calendar.getInstance()
                                    now.set(java.util.Calendar.HOUR_OF_DAY, 0)
                                    now.set(java.util.Calendar.MINUTE, 0)
                                    now.set(java.util.Calendar.SECOND, 0)
                                    now.set(java.util.Calendar.MILLISECOND, 0)
                                    filterStart = now.timeInMillis
                                    filterEnd = filterStart!! + 24L*60*60*1000 - 1
                                })
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.filter_last_7_days)) },
                                    leadingIcon = { Text("7d", style = LocalTextStyle.current.copy(fontWeight = FontWeight.Bold)) },
                                    onClick = {
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
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.filter_this_month)) },
                                    leadingIcon = { Icon(Icons.Default.DateRange, contentDescription = null) },
                                    onClick = {
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
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.filter_all)) },
                                    leadingIcon = { Icon(Icons.Default.SelectAll, contentDescription = null) },
                                    onClick = {
                                    filterMenuOpen = false
                                    filterStart = null
                                    filterEnd = null
                                })
                                Divider()
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.filter_custom_range)) },
                                    leadingIcon = { Icon(Icons.Default.DateRange, contentDescription = null) },
                                    onClick = {
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

            // Search+date filtered list reused across top bar, list and totals
            // Precompute running balances on displayed list
            val runningBalances = remember(displayedTxns) {
                var r = 0.0
                displayedTxns.map { t ->
                    r += if (t.isCredit) t.amount else -t.amount
                    r
                }
            }
            // Totals for credit and debit
            val totalCredit = remember(displayedTxns) { displayedTxns.filter { it.isCredit }.sumOf { it.amount } }
            val totalDebit = remember(displayedTxns) { displayedTxns.filter { !it.isCredit }.sumOf { it.amount } }

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
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 0.dp),
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f),
                        thickness = 0.6.dp
                    )
                }
                itemsIndexed(displayedTxns) { index, t ->
                    val run = runningBalances.getOrNull(index) ?: 0.0
                    // Theme-aware subtle background (15% opacity)
                    val rowBg = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f)
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
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .background(color = Color(0xFF5C6BC0), shape = RoundedCornerShape(4.dp))
                                            .padding(horizontal = 4.dp, vertical = 0.dp)
                                    ) {
                                        Text(
                                            SimpleDateFormat("dd/MM/yy").format(Date(t.date)),
                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                            color = Color.White
                                        )
                                    }
                                    val catLabelDate = (t.category ?: "").trim()
                                    if (showCategory && showCategoryInList && !t.isCredit && catLabelDate.isNotEmpty()) {
                                        val color = Color(0xFF7E57C2)
                                        Box(
                                            modifier = Modifier
                                                .padding(start = 6.dp)
                                                .background(color = color, shape = RoundedCornerShape(4.dp))
                                                .padding(horizontal = 4.dp, vertical = 0.dp)
                                        ) {
                                            Text(
                                                text = catLabelDate,
                                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                                color = Color.White,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                                if (t.attachmentUri != null) {
                                    CompositionLocalProvider(LocalMinimumInteractiveComponentEnforcement provides false) {
                                        IconButton(
                                            onClick = { previewUri = t.attachmentUri },
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
                                fontWeight = FontWeight.Medium,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Text(if (t.isCredit) Currency.inr(t.amount) else "-", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(wAmt), textAlign = TextAlign.End, color = if (t.isCredit) Color(0xFF2E7D32) else MaterialTheme.colorScheme.onSurface)
                        Text(if (!t.isCredit) Currency.inr(t.amount) else "-", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(wAmt), textAlign = TextAlign.End, color = if (!t.isCredit) Color(0xFFB71C1C) else MaterialTheme.colorScheme.onSurface)
                        Text(Currency.inr(run), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(wAmt), textAlign = TextAlign.End, color = if (run >= 0) Color(0xFF2E7D32) else Color(0xFFB71C1C))
                    }
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 0.dp),
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f),
                        thickness = 0.6.dp
                    )
                }
            }
            // Divider above footer for separation
            HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 0.dp),
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f),
                        thickness = 0.6.dp
                    )
            // Sticky totals footer (outside list)
            val showBanner = !hasRemoveAds && !showAdd
            val bannerHeightDp = remember(showBanner, bannerLoaded) {
                if (!showBanner) 0f else {
                    val dm = ctx.resources.displayMetrics
                    val widthDp = (dm.widthPixels / dm.density).toInt()
                    val size = AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(ctx, widthDp)
                    (size.getHeightInPixels(ctx) / dm.density)
                }
            }
            val bannerHeight = bannerHeightDp.dp
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .then(if (!showBanner) Modifier.windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom)) else Modifier)
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
            if (showBanner) {
                BannerAd(
                    modifier = Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom)),
                    adUnitId = "ca-app-pub-2556604347710668/8804283822",
                    onLoadState = { ok -> bannerLoaded = ok }
                )
            }
        }
    }

    // Reset add dialog inputs whenever the dialog is opened
    LaunchedEffect(showAdd) {
        if (showAdd) {
            amount = ""
            note = ""
            dateMillis = System.currentTimeMillis()
            addAttachmentUri = null
            addCategory = ""
            userSetAddCategory = false
            addSuggestion = null
            autoSetAddCategory = false
            addRecurring = false
        }
    }
    // (moved declarations above)
    // Debounced auto-category selection from note when user hasn't chosen manually
    LaunchedEffect(note, showAdd, showCategory) {
        if (showCategory && catSuggestVm != null && showAdd && (!userSetAddCategory)) {
            kotlinx.coroutines.delay(350)
            val sugg = try { catSuggestVm.suggest(note) } catch (_: Exception) { null }
            if (sugg != null) {
                val (nameS, score) = sugg
                // Remember suggestion for chip if medium
                addSuggestion = if (score in 0.75..0.8999 && addCategory.isBlank()) nameS to score else null
                if (score >= 0.9) {
                    addCategory = nameS
                    autoSetAddCategory = true
                }
            }
        }
    }
    // Suggestion for Edit dialog with debounce
    LaunchedEffect(editNote, editTxn, showCategory) {
        if (showCategory && catSuggestVm != null && editTxn != null && (!userSetEditCategory)) {
            kotlinx.coroutines.delay(350)
            val sugg = try { catSuggestVm.suggest(editNote) } catch (_: Exception) { null }
            if (sugg != null) {
                val (nameS, score) = sugg
                editSuggestion = if (score in 0.75..0.8999 && editCategory.isBlank()) nameS to score else null
                if (score >= 0.9) {
                    editCategory = nameS
                    autoSetEditCategory = true
                }
            }
        }
    }
    // Reset edit suggestion flags when opening edit dialog
    LaunchedEffect(editTxn) {
        if (editTxn != null) {
            userSetEditCategory = false
            editSuggestion = null
            autoSetEditCategory = false
        }
    }
    if (showAdd) {
        val amountValid = remember(amount) { amount.toDoubleOrNull()?.let { it > 0 } == true }
        val noteValid = remember(note) { note.isNotBlank() }
        val pickImageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            addAttachmentUri = uri?.let { copyPickedToApp(it) }
        }
        AlertDialog(
            onDismissRequest = {
                showAdd = false
                amount = ""
                note = ""
                dateMillis = System.currentTimeMillis()
                addAttachmentUri = null
                addCategory = ""
                addRecurring = false
            },
            confirmButton = {
                TextButton(
                    enabled = amountValid && noteValid,
                    onClick = {
                        val amt = amount.toDoubleOrNull() ?: 0.0
                        if (amt <= 0 || note.isBlank()) return@TextButton
                        vm.addTxn(dateMillis, amt, isCredit, note, addAttachmentUri, addCategory.ifBlank { null }, makeRecurring = addRecurring)
                        showAdd = false
                        isCredit = true
                        amount = ""
                        note = ""
                        dateMillis = System.currentTimeMillis()
                        addAttachmentUri = null
                        addCategory = ""
                        addRecurring = false
                    }
                ) { Text(stringResource(R.string.save)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    showAdd = false
                    amount = ""
                    note = ""
                    dateMillis = System.currentTimeMillis()
                    addAttachmentUri = null
                    addCategory = ""
                }) { Text(stringResource(R.string.cancel)) }
            },
            title = { Text(stringResource(R.string.add_to_book)) },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
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
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Switch(checked = addRecurring, onCheckedChange = { addRecurring = it })
                        Text(text = stringResource(R.string.repeat_monthly_toggle), style = MaterialTheme.typography.bodyMedium)
                    }
                    val asugg = addSuggestion
                    if (asugg != null && addCategory.isBlank()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            AssistChip(onClick = {
                                addCategory = asugg.first
                                userSetAddCategory = true
                                autoSetAddCategory = false
                                addSuggestion = null
                            }, label = { Text(stringResource(R.string.label_suggested_with_name, asugg.first)) })
                        }
                    }
                    if (showCategory) {
                        var expanded by remember { mutableStateOf(false) }
                        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
                            OutlinedTextField(
                                value = if (addCategory.isBlank()) stringResource(R.string.select_category) else addCategory,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text(stringResource(R.string.category)) },
                                modifier = Modifier
                                    .menuAnchor()
                                    .fillMaxWidth(),
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) }
                            )
                            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                                DropdownMenuItem(text = { Text(stringResource(R.string.uncategorized)) }, onClick = { addCategory = ""; userSetAddCategory = true; autoSetAddCategory = false; expanded = false })
                                categories.forEach { cat ->
                                    DropdownMenuItem(text = { Text(cat) }, onClick = { addCategory = cat; userSetAddCategory = true; autoSetAddCategory = false; expanded = false })
                                }
                            }
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
                            if (autoSetAddCategory && addCategory.isNotBlank()) {
                                AssistChip(onClick = {}, enabled = false, label = { Text(stringResource(R.string.label_auto_selected)) })
                            } else if (addSuggestion != null && addCategory.isBlank()) {
                                AssistChip(onClick = {}, enabled = false, label = { Text(stringResource(R.string.label_suggested)) })
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

}
