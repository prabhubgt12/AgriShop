@file:OptIn(ExperimentalFoundationApi::class)
package com.ledge.ledgerbook.ui
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.launch
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import com.ledge.ledgerbook.R
import com.ledge.ledgerbook.billing.MonetizationViewModel
import com.ledge.ledgerbook.ads.BannerAd
import com.ledge.ledgerbook.ui.theme.ThemeViewModel
import com.ledge.ledgerbook.util.CurrencyFormatter
import com.ledge.ledgerbook.util.PdfShareUtils
import com.ledge.ledgerbook.util.NumberToWords
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.style.TextOverflow
import androidx.core.graphics.drawable.toBitmap
import coil.compose.rememberAsyncImagePainter
import java.text.SimpleDateFormat
import com.ledge.ledgerbook.ui.settings.CurrencyViewModel
import java.util.Date
import kotlin.math.roundToInt

typealias LedgerItemVM = LedgerViewModel.LedgerItemVM

// Helper removed: we now bind formatting to current currency state explicitly

// Normalize labels like "BORROW"/"LEND", "MONTHLY"/"YEARLY", "SIMPLE"/"COMPOUND" to Camel case
private fun toCamel(label: String?): String {
    if (label.isNullOrBlank()) return ""
    val lower = label.lowercase()
    return lower.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
}

// Build a human-readable plain text for an entry, optionally with promo
private fun buildShareText(
    ctx: android.content.Context,
    vm: LedgerItemVM,
    includePromo: Boolean
): String {
    val sdf = java.text.SimpleDateFormat("dd/MM/yyyy")
    val typeLabel = when (vm.type.uppercase()) {
        "LEND" -> ctx.getString(R.string.lend)
        "BORROW" -> ctx.getString(R.string.borrow)
        else -> toCamel(vm.type)
    }
    val basisLabel = when (vm.rateBasis.uppercase()) {
        "MONTHLY" -> ctx.getString(R.string.monthly)
        "YEARLY" -> ctx.getString(R.string.yearly)
        else -> toCamel(vm.rateBasis)
    }
    return buildString {
        appendLine(ctx.getString(R.string.ledger_entry_title))
        appendLine(ctx.getString(R.string.label_name_with_value, vm.name))
        appendLine(ctx.getString(R.string.label_type_with_value, typeLabel))
        appendLine(ctx.getString(R.string.label_principal_with_value, CurrencyFormatter.format(vm.principal)))
        appendLine(ctx.getString(R.string.label_rate_with_value, vm.rate.toString(), basisLabel))
        appendLine(ctx.getString(R.string.label_from_with_value, sdf.format(java.util.Date(vm.fromDateMillis))))
        appendLine(ctx.getString(R.string.label_interest_till_now_with_value, CurrencyFormatter.format(vm.accrued)))
        appendLine(ctx.getString(R.string.label_total_with_value, CurrencyFormatter.format(vm.total)))
        if (includePromo) {
            appendLine()
            append(ctx.getString(R.string.generated_by_footer_with_link, "https://play.google.com/store/apps/details?id=com.ledge.ledgerbook"))
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun LedgerListScreen(vm: LedgerViewModel = hiltViewModel(), themeViewModel: ThemeViewModel = hiltViewModel()) {
    val state by vm.state.collectAsState()
    // Settings flag
    val groupingEnabled by themeViewModel.groupingEnabled.collectAsState()
    val themeMode by themeViewModel.themeMode.collectAsState()
    val isDarkActive = when (themeMode) {
        ThemeViewModel.MODE_DARK -> true
        ThemeViewModel.MODE_LIGHT -> false
        else -> androidx.compose.foundation.isSystemInDarkTheme()
    }
    // Monetization flag
    val monetizationVM: MonetizationViewModel = hiltViewModel()
    val hasRemoveAds by monetizationVM.hasRemoveAds.collectAsState()
    // Currency settings
    val currencyVM: CurrencyViewModel = hiltViewModel()
    val currencyCode by currencyVM.currencyCode.collectAsState()
    val showCurrencySymbol by currencyVM.showSymbol.collectAsState()
    LaunchedEffect(currencyCode, showCurrencySymbol) {
        CurrencyFormatter.setConfig(currencyCode, showCurrencySymbol)
    }
    // Local helpers to format with the current settings on the very first frame
    fun fmt(v: Double): String = CurrencyFormatter.format(v, currencyCode, showCurrencySymbol)
    fun fmtNo(v: Double): String = CurrencyFormatter.formatNoDecimals(v, currencyCode, showCurrencySymbol)
    // Search state and filtered items
    var searchQuery by rememberSaveable { mutableStateOf("") }
    val filteredItems = remember(state.items, searchQuery) {
        if (searchQuery.isBlank()) state.items
        else state.items.filter { it.name.contains(searchQuery, ignoreCase = true) }
    }
    // Thresholds from settings
    val overdueDays by themeViewModel.overdueDays.collectAsState()
    val dueSoonWindow by themeViewModel.dueSoonWindowDays.collectAsState()
    // Precompute groups in composable scope (cannot call remember inside LazyListScope)
    val groups = remember(filteredItems) { filteredItems.groupBy { it.name } }
    val sortedGroups = remember(groups) { groups.entries.sortedBy { it.key.lowercase() } }

    // Dialog-local states
    val showAdd = remember { mutableStateOf(false) }
    val addPrefillName = remember { mutableStateOf<String?>(null) }
    val partialForId = remember { mutableStateOf<Int?>(null) }
    val partialAmount = remember { mutableStateOf("") }
    val partialDateMillis = remember { mutableStateOf(System.currentTimeMillis()) }
    val partialNote = remember { mutableStateOf("") }
    val partialAttachmentUri = remember { mutableStateOf<Uri?>(null) }
    val partialFullPayment = remember { mutableStateOf(false) }
    val editingLatestPayment = remember { mutableStateOf(false) }
    val previewInterest = remember { mutableStateOf(0.0) }
    val previewOutstanding = remember { mutableStateOf(0.0) }
    // Base outstanding at selected date before applying entered amount. Used for validation
    val baseOutstanding = remember { mutableStateOf(0.0) }
    // When editing a payment, remember original amount to allow replacing up to outstanding + original
    val originalEditAmount = remember { mutableStateOf(0.0) }
    val detailsForId = remember { mutableStateOf<Int?>(null) }
    val confirmDeleteId = remember { mutableStateOf<Int?>(null) }
    val context = LocalContext.current
    // Payment dialog tab: 0 = Add Payment, 1 = View Transactions
    val paymentsTab = rememberSaveable { mutableStateOf(1) }

    Scaffold(contentWindowInsets = WindowInsets.safeDrawing) { padding ->
        BoxWithConstraints(Modifier.fillMaxSize()) {
            // Content list with scrolling header
            LazyColumn(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.title_khata_book),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(Modifier.height(2.dp))
            }
            item {
                // Old design: three separate summary cards
                val container = MaterialTheme.colorScheme.surfaceVariant

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            // Lend card
                            Card(
                                colors = CardDefaults.cardColors(containerColor = container),
                                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(Modifier.padding(12.dp)) {
                                    Text(stringResource(R.string.total_lend), style = MaterialTheme.typography.labelSmall)
                                    Spacer(Modifier.height(6.dp))
                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = Color(0xFFDFF6DD)),
                                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Box(Modifier.padding(vertical = 4.dp, horizontal = 8.dp)) {
                                            Text(
                                                fmtNo(state.totalLend),
                                                style = MaterialTheme.typography.titleSmall,
                                                color = Color(0xFF0B6A0B),
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        text = buildAnnotatedString {
                                            append(stringResource(R.string.label_interest) + ": ")
                                            withStyle(SpanStyle(color = Color(0xFF66BB6A), fontWeight = FontWeight.Bold)) {
                                                append(fmtNo(state.totalLendInterest))
                                            }
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        modifier = Modifier.padding(start = 8.dp)
                                    )
                                }
                            }
                            // Borrow card
                            Card(
                                colors = CardDefaults.cardColors(containerColor = container),
                                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(Modifier.padding(12.dp)) {
                                    Text(stringResource(R.string.total_borrow), style = MaterialTheme.typography.labelSmall)
                                    Spacer(Modifier.height(6.dp))
                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFE2E0)),
                                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Box(Modifier.padding(vertical = 4.dp, horizontal = 8.dp)) {
                                            Text(
                                                fmtNo(state.totalBorrow),
                                                style = MaterialTheme.typography.titleSmall,
                                                color = Color(0xFF9A0007),
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        text = buildAnnotatedString {
                                            append(stringResource(R.string.label_interest) + ": ")
                                            withStyle(SpanStyle(color = Color(0xFFEF5350), fontWeight = FontWeight.Bold)) {
                                                append(fmtNo(state.totalBorrowInterest))
                                            }
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        modifier = Modifier.padding(start = 8.dp)
                                    )
                                }
                            }
                    }

                    // Final Amount card
                    val isPositive = state.finalAmount >= 0
                    val msPerDay = 86_400_000L
                    val now = System.currentTimeMillis()
                    val od = overdueDays.coerceAtLeast(1)
                    val win = dueSoonWindow.coerceAtLeast(1)
                    val dueFrom = (od - win).coerceAtLeast(0)
                    val overdueCount = state.items.count { (((now - it.fromDateMillis) / msPerDay).toInt()) >= od }
                    val dueSoonCount = state.items.count {
                        val d = (((now - it.fromDateMillis) / msPerDay).toInt())
                        d in dueFrom until od
                    }
                    Card(
                        colors = CardDefaults.cardColors(containerColor = container),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Text(stringResource(R.string.final_amount), style = MaterialTheme.typography.labelSmall)
                            Spacer(Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = if (isPositive) Color(0xFFDFF6DD) else Color(0xFFFFE2E0)),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Box(Modifier.padding(vertical = 6.dp, horizontal = 10.dp)) {
                                        Text(
                                            fmtNo(state.finalAmount),
                                            style = MaterialTheme.typography.titleMedium,
                                            color = if (isPositive) Color(0xFF0B6A0B) else Color(0xFF9A0007),
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFDE0E0)),
                                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Box(Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                                            Text(
                                                text = stringResource(R.string.overdue_with_count, overdueCount),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = Color(0xFFB00020),
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                    }
                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E0)),
                                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Box(Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                                            Text(
                                                text = stringResource(R.string.due_soon_with_count, dueSoonCount),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = Color(0xFF8C6D1F),
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Banner Ad (only when ads are not removed) - below the summary cards
                if (!hasRemoveAds) {
                    Spacer(Modifier.height(6.dp))
                    BannerAd(modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(6.dp))
                } else {
                    Spacer(Modifier.height(4.dp))
                }
            }
            // Search bar below summary card
            item {
                CompactSearchBar(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = "Search with name",
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (groupingEnabled) {
                // Group by user name and show expandable parent + children (sorted A–Z)
                items(sortedGroups, key = { it.key }) { entry ->
                val name = entry.key
                val itemsForUser = entry.value
                val expanded = rememberSaveable(name) { mutableStateOf(false) }

                // Parent card with totals
                val neutralParent = MaterialTheme.colorScheme.surfaceVariant
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { expanded.value = !expanded.value },
                    colors = CardDefaults.cardColors(
                        containerColor = neutralParent
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .height(IntrinsicSize.Min)
                            .padding(12.dp)
                    ) {
                        // Parent row leading accents
                        val msPerDay = 86_400_000L
                        val now = System.currentTimeMillis()
                        val daysSince: (LedgerItemVM) -> Int = { (((now - it.fromDateMillis) / msPerDay).toInt()).coerceAtLeast(0) }
                        val (overdueCount, dueSoonCount) = run {
                            val odThresh = overdueDays.coerceAtLeast(1)
                            val win = dueSoonWindow.coerceAtLeast(1)
                            val from = (odThresh - win).coerceAtLeast(0)
                            var odC = 0
                            var dsC = 0
                            itemsForUser.forEach { item ->
                                val d = daysSince(item)
                                when {
                                    d >= odThresh -> odC++
                                    d in from until odThresh -> dsC++
                                }
                            }
                            odC to dsC
                        }
                        Box(
                            Modifier
                                .width(4.dp)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.primary)
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text(name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                                // Parent status chip with counts (Overdue preferred over Due soon)
                                if (overdueCount > 0 || dueSoonCount > 0) {
                                    val chipBg = if (overdueCount > 0) Color(0xFFFDE0E0) else Color(0xFFFFF3E0)
                                    val chipFg = if (overdueCount > 0) Color(0xFFB00020) else Color(0xFF8C6D1F)
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(chipBg)
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            text = if (overdueCount > 0)
                                                stringResource(R.string.overdue_with_count_paren, overdueCount)
                                            else
                                                stringResource(R.string.due_soon_with_count_paren, dueSoonCount),
                                            color = chipFg,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                    Spacer(Modifier.width(8.dp))
                                }
                                Icon(
                                    imageVector = if (expanded.value) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                                    contentDescription = if (expanded.value) "Collapse" else "Expand"
                                )
                                Spacer(Modifier.width(4.dp))
                                val parentMenuOpen = remember(name) { mutableStateOf(false) }
                                Box {
                                    IconButton(onClick = { parentMenuOpen.value = true }) {
                                        Icon(
                                            imageVector = Icons.Default.MoreVert,
                                            contentDescription = "More",
                                            tint = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                    DropdownMenu(
                                        expanded = parentMenuOpen.value,
                                        onDismissRequest = { parentMenuOpen.value = false }
                                    ) {
                                        // Call User (parent): use first available phone among children
                                        val firstPhone = remember(itemsForUser) { itemsForUser.firstOrNull { !it.phone.isNullOrBlank() }?.phone }
                                        if (!firstPhone.isNullOrBlank()) {
                                            DropdownMenuItem(
                                                text = { Text(stringResource(R.string.call_user)) },
                                                onClick = {
                                                    parentMenuOpen.value = false
                                                    try {
                                                        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + firstPhone))
                                                        context.startActivity(intent)
                                                    } catch (_: Exception) {}
                                                }
                                            )
                                        }
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.share_receipt)) },
                                            onClick = {
                                                parentMenuOpen.value = false
                                                PdfShareUtils.shareGroup(context, name, itemsForUser, includePromo = !hasRemoveAds)
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.add_to_book)) },
                                            onClick = {
                                                parentMenuOpen.value = false
                                                addPrefillName.value = name
                                                showAdd.value = true
                                            }
                                        )
                                    }
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            // Compute net values (LEND - BORROW) per user
                            val lendPrincipal = itemsForUser.filter { it.type == "LEND" }.sumOf { it.principal }
                            val borrowPrincipal = itemsForUser.filter { it.type == "BORROW" }.sumOf { it.principal }
                            val netPrincipal = lendPrincipal - borrowPrincipal

                            val lendInterest = itemsForUser.filter { it.type == "LEND" }.sumOf { it.accrued }
                            val borrowInterest = itemsForUser.filter { it.type == "BORROW" }.sumOf { it.accrued }
                            val netInterest = lendInterest - borrowInterest

                            val lendTotal = itemsForUser.filter { it.type == "LEND" }.sumOf { it.total }
                            val borrowTotal = itemsForUser.filter { it.type == "BORROW" }.sumOf { it.total }
                            val netTotal = lendTotal - borrowTotal

                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                LabelValue(
                                    label = stringResource(R.string.total_principal),
                                    value = fmtNo(netPrincipal),
                                    modifier = Modifier.weight(1f)
                                )
                                LabelValue(
                                    label = stringResource(R.string.total_interest),
                                    value = fmtNo(netInterest),
                                    modifier = Modifier.weight(1f)
                                )
                                // Total chip (compact)
                                val pos = netTotal >= 0
                                val chipBg = if (pos) Color(0xFFDFF6DD) else Color(0xFFFFE2E0)
                                val chipFg = if (pos) Color(0xFF0B6A0B) else Color(0xFF9A0007)
                                Column(Modifier.weight(1f)) {
                                    Text(stringResource(R.string.total_amount), style = MaterialTheme.typography.labelSmall)
                                    Spacer(Modifier.height(2.dp))
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(chipBg)
                                            .padding(vertical = 2.dp, horizontal = 4.dp)
                                    ) {
                                        Text(
                                            fmtNo(netTotal),
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = chipFg
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Children list (existing cards) when expanded
                if (expanded.value) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(start = 8.dp, top = 6.dp, bottom = 2.dp),
                    ) {
                        itemsForUser.forEach { item ->
                            Spacer(Modifier.height(8.dp))
                            LedgerRow(
                                vm = item,
                                onClick = {
                                    detailsForId.value = item.id
                                    vm.beginEdit(item.id)
                                },
                                onHistory = { paymentsTab.value = 1; vm.openPayments(item.id) },
                                onEdit = { vm.beginEdit(item.id) },
                                onPartial = {
                                    if (item.outstanding <= 0.0) {
                                        android.widget.Toast.makeText(context, context.getString(R.string.no_payments_pending), android.widget.Toast.LENGTH_SHORT).show()
                                    } else {
                                        paymentsTab.value = 0
                                        // Reset add payment form (fresh page)
                                        editingLatestPayment.value = false
                                        partialAmount.value = ""
                                        partialNote.value = ""
                                        partialAttachmentUri.value = null
                                        partialFullPayment.value = false
                                        partialDateMillis.value = System.currentTimeMillis()
                                        vm.openPayments(item.id)
                                    }
                                },
                                onDelete = { confirmDeleteId.value = item.id },
                                onShare = { PdfShareUtils.shareEntry(context, item, includePromo = !hasRemoveAds) },
                                showTypeChip = false,
                                includePromo = !hasRemoveAds
                            )
                        }
                    }
                }
            }
            } else {
                // Flat list (original child cards only)
                items(state.items, key = { it.id }) { item ->
                    LedgerRow(
                        vm = item,
                        onClick = {
                            detailsForId.value = item.id
                            vm.beginEdit(item.id)
                        },
                        onHistory = { paymentsTab.value = 1; vm.openPayments(item.id) },
                        onEdit = { vm.beginEdit(item.id) },
                        onPartial = {
                            if (item.outstanding <= 0.0) {
                                android.widget.Toast.makeText(context, context.getString(R.string.no_payments_pending), android.widget.Toast.LENGTH_SHORT).show()
                            } else {
                                paymentsTab.value = 0
                                // Reset add payment form (fresh page)
                                editingLatestPayment.value = false
                                partialAmount.value = ""
                                partialNote.value = ""
                                partialAttachmentUri.value = null
                                partialFullPayment.value = false
                                partialDateMillis.value = System.currentTimeMillis()
                                vm.openPayments(item.id)
                            }
                        },
                        onDelete = { confirmDeleteId.value = item.id },
                        onShare = { PdfShareUtils.shareEntry(context, item, includePromo = !hasRemoveAds) },
                        showName = true,
                        includePromo = !hasRemoveAds
                    )
                    Spacer(Modifier.height(8.dp))
                }
            }
            }

            // Draggable FAB overlay
            val density = LocalDensity.current
            val fabSize = 56.dp
            val edge = 16.dp
            // Calculate system bar insets in px
            val topInsetPx = with(density) { WindowInsets.statusBars.getTop(this).toFloat() }
            val bottomInsetPx = with(density) { WindowInsets.navigationBars.getBottom(this).toFloat() }
            val maxX = with(density) { (maxWidth - fabSize - edge).toPx() }
            val maxY = with(density) { (maxHeight - fabSize - edge).toPx() } - bottomInsetPx
            val minX = with(density) { edge.toPx() }
            val minY = topInsetPx + with(density) { edge.toPx() }
            var offsetX by remember(maxWidth, maxHeight, topInsetPx, bottomInsetPx) { mutableStateOf(maxX.coerceAtLeast(minX)) }
            var offsetY by remember(maxWidth, maxHeight, topInsetPx, bottomInsetPx) { mutableStateOf(maxY.coerceAtLeast(minY)) }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.navigationBars)
            ) {
                FloatingActionButton(
                    onClick = { addPrefillName.value = null; showAdd.value = true },
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
                    Icon(Icons.Default.Add, contentDescription = "Add")
                }
            }
        }
        // Close Scaffold content lambda
    }

    // Add/Edit dialog
    val editing by vm.editingEntry.collectAsState()
    if (showAdd.value) {
        LedgerAddEditScreen(
            onDismiss = { showAdd.value = false },
            onSave = { entry -> vm.saveNew(entry); showAdd.value = false },
            prefillName = addPrefillName.value
        )
    }
    if (editing != null && (detailsForId.value == null)) {
        LedgerAddEditScreen(
            existing = editing,
            onDismiss = { vm.clearEdit() },
            onSave = { entry -> vm.saveUpdate(entry); vm.clearEdit() }
        )
    }

    // Details dialog (read-only)
    val detailsId = detailsForId.value
    if (detailsId != null) {
        val e = editing
        if (e != null && e.id == detailsId) {
            CenteredAlertDialog(
                onDismissRequest = { detailsForId.value = null; vm.clearEdit() },
                title = { Text(stringResource(R.string.entry_details)) },
                text = {
                    Column(Modifier.fillMaxWidth()) {
                        val isLend = e.type == "LEND"
                        val chipBg = if (isLend) Color(0xFFDFF6DD) else Color(0xFFFFE2E0)
                        val chipFg = if (isLend) Color(0xFF0B6A0B) else Color(0xFF9A0007)
                        val typeLbl = when (e.type.uppercase()) {
                            "LEND" -> stringResource(R.string.lend)
                            "BORROW" -> stringResource(R.string.borrow)
                            else -> toCamel(e.type)
                        }
                        AssistChip(onClick = {}, label = { Text(typeLbl) }, colors = AssistChipDefaults.assistChipColors(containerColor = chipBg, labelColor = chipFg))
                        Spacer(Modifier.height(8.dp))

                        LabelValue(label = stringResource(R.string.name_label), value = e.name)
                        Spacer(Modifier.height(8.dp))
                        val interestTypeValue = when (e.interestType.uppercase()) {
                            "SIMPLE" -> stringResource(R.string.simple)
                            "COMPOUND" -> stringResource(R.string.compound)
                            else -> toCamel(e.interestType)
                        }
                        LabelValue(label = stringResource(R.string.interest_type), value = interestTypeValue)
                        Spacer(Modifier.height(8.dp))
                        val basis = (e.period ?: "MONTHLY").uppercase()
                        val basisValue = when (basis) {
                            "MONTHLY" -> stringResource(R.string.monthly)
                            "YEARLY" -> stringResource(R.string.yearly)
                            else -> toCamel(basis)
                        }
                        LabelValue(label = stringResource(R.string.rate_basis), value = basisValue)
                        if (e.interestType.equals("COMPOUND", true)) {
                            Spacer(Modifier.height(8.dp))
                            val durValue = when (e.compoundPeriod.uppercase()) {
                                "MONTHLY" -> stringResource(R.string.monthly)
                                "YEARLY" -> stringResource(R.string.yearly)
                                else -> toCamel(e.compoundPeriod)
                            }
                            LabelValue(label = stringResource(R.string.duration_type), value = durValue)
                        }
                        Spacer(Modifier.height(8.dp))
                        LabelValue(label = stringResource(R.string.label_principal_generic), value = CurrencyFormatter.formatInr(e.principal))
                        Spacer(Modifier.height(8.dp))
                        LabelValue(label = stringResource(R.string.interest_rate_percent), value = "${e.rateRupees}%")
                        Spacer(Modifier.height(8.dp))
                        LabelValue(label = stringResource(R.string.from_date), value = SimpleDateFormat("dd/MM/yyyy").format(Date(e.fromDate)))
                        // Extract phone from notes (line starting with 'Phone:') and show as tappable
                        val phoneDigits = remember(e.notes) {
                            val n = e.notes ?: ""
                            val line = n.lineSequence().firstOrNull { it.trim().startsWith("Phone:", ignoreCase = true) }
                            line?.filter { it.isDigit() } ?: ""
                        }
                        if (phoneDigits.isNotBlank()) {
                            Spacer(Modifier.height(8.dp))
                            val ctxPhone = LocalContext.current
                            LabelValue(
                                label = stringResource(R.string.phone),
                                value = phoneDigits,
                                modifier = Modifier.clickable {
                                    try {
                                        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + phoneDigits))
                                        ctxPhone.startActivity(intent)
                                    } catch (_: Exception) {}
                                }
                            )
                        }
                        if (!e.notes.isNullOrBlank()) {
                            val filteredNotes = remember(e.notes) {
                                e.notes
                                    ?.lineSequence()
                                    ?.filterNot { it.trim().startsWith("att:") }
                                    ?.joinToString("\n")
                                    ?: ""
                            }
                            if (filteredNotes.isNotBlank()) {
                                Spacer(Modifier.height(8.dp))
                                LabelValue(label = stringResource(R.string.notes_optional), value = filteredNotes)
                            }
                        }
                        // Show attachment preview if att: <uri> exists
                        val attUri = remember(e.notes) {
                            e.notes
                                ?.lineSequence()
                                ?.firstOrNull { it.trim().startsWith("att:") }
                                ?.substringAfter("att:")
                                ?.trim()
                                ?.let { runCatching { Uri.parse(it) }.getOrNull() }
                        }
                        if (attUri != null) {
                            Spacer(Modifier.height(8.dp))
                            val painter = rememberAsyncImagePainter(attUri)
                            Image(
                                painter = painter,
                                contentDescription = null,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(160.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        try {
                                            val intent = Intent(Intent.ACTION_VIEW).apply {
                                                setDataAndType(attUri, "image/*")
                                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                            }
                                            context.startActivity(intent)
                                        } catch (_: Exception) {}
                                    },
                                contentScale = ContentScale.Crop
                            )
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { detailsForId.value = null; vm.clearEdit() }) { Text(stringResource(R.string.ok)) } }
            )
        }
    }

    // Legacy partial payment dialog removed (migrated into full-screen Payments dialog tab 0)

    // Payment history: full-screen dialog with detailed cards (no extra taps)
    val paymentsEntryId by vm.paymentsEntryId.collectAsState()
    if (paymentsEntryId != null) {
        val payments by vm.paymentsForViewing.collectAsState()
        Dialog(onDismissRequest = { vm.closePayments() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Surface(modifier = Modifier.fillMaxSize()) {
                Column(Modifier.fillMaxSize()) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = { vm.closePayments() }) { Text(stringResource(R.string.close)) }
                        Text(
                            text = stringResource(R.string.payments_title),
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.weight(1f),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Spacer(Modifier.width(64.dp))
                    }
                    // Tabs: 0 = Add Payment, 1 = Payment History
                    val pagerState = rememberPagerState(initialPage = paymentsTab.value, pageCount = { 2 })
                    val scope = rememberCoroutineScope()
                    LaunchedEffect(paymentsTab.value) {
                        if (pagerState.currentPage != paymentsTab.value) pagerState.scrollToPage(paymentsTab.value)
                    }
                    LaunchedEffect(pagerState) {
                        snapshotFlow { pagerState.currentPage }.collect { page -> paymentsTab.value = page }
                    }
                    TabRow(selectedTabIndex = paymentsTab.value, modifier = Modifier.height(44.dp)) {
                        Tab(
                            selected = paymentsTab.value == 0,
                            onClick = { paymentsTab.value = 0; scope.launch { pagerState.animateScrollToPage(0) } },
                            modifier = Modifier.height(44.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .padding(horizontal = 16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(stringResource(R.string.add_payment))
                            }
                        }
                        Tab(
                            selected = paymentsTab.value == 1,
                            onClick = { paymentsTab.value = 1; scope.launch { pagerState.animateScrollToPage(1) } },
                            modifier = Modifier.height(44.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .padding(horizontal = 16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(stringResource(R.string.payment_history))
                            }
                        }
                    }
                    // Always reset Add Payment tab fields when opening tab 0 (fresh page)
                    LaunchedEffect(paymentsEntryId, paymentsTab.value) {
                        if (paymentsEntryId != null && paymentsTab.value == 0 && !editingLatestPayment.value) {
                            partialAmount.value = ""
                            partialNote.value = ""
                            partialAttachmentUri.value = null
                            partialFullPayment.value = false
                            partialDateMillis.value = System.currentTimeMillis()
                        }
                    }
                    // Success feedback dialog
                    val successMessage = remember { mutableStateOf<String?>(null) }
                    if (successMessage.value != null) {
                        CenteredAlertDialog(
                            onDismissRequest = { successMessage.value = null; vm.closePayments() },
                            title = { Text(successMessage.value!!) },
                            confirmButton = {
                                TextButton(onClick = { successMessage.value = null; vm.closePayments() }) {
                                    Text(stringResource(R.string.ok))
                                }
                            }
                        )
                    }
                    HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                        if (page == 0) {
                            // Add Payment tab content (with summary and remaining preview)
                            val entryId = paymentsEntryId
                            if (entryId != null) {
                                LaunchedEffect(entryId, partialDateMillis.value, partialAmount.value, partialFullPayment.value) {
                                    val (accrued, _, outstanding) = vm.computeAt(entryId, partialDateMillis.value)
                                    previewInterest.value = accrued
                                    baseOutstanding.value = outstanding
                                    if (partialFullPayment.value) {
                                        partialAmount.value = CurrencyFormatter.formatNumericUpTo2(outstanding)
                                    }
                                }
                                LaunchedEffect(partialAmount.value, baseOutstanding.value, partialFullPayment.value) {
                                    val amt = partialAmount.value.toDoubleOrNull() ?: 0.0
                                    previewOutstanding.value = if (partialFullPayment.value) 0.0 else (baseOutstanding.value - amt).coerceAtLeast(0.0)
                                }

                                val ctx = LocalContext.current
                                val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
                                    if (uri != null) {
                                        try {
                                            ctx.contentResolver.openAssetFileDescriptor(uri, "r")?.use { afd ->
                                                val size = afd.length
                                                if (size in 1..1_000_000) {
                                                    val dir = File(ctx.filesDir, "attachments").apply { mkdirs() }
                                                    val ext = "jpg"
                                                    val outFile = File(dir, "att_${System.currentTimeMillis()}.$ext")
                                                    ctx.contentResolver.openInputStream(uri)?.use { input ->
                                                        FileOutputStream(outFile).use { output -> input.copyTo(output) }
                                                    }
                                                    val fileUri = FileProvider.getUriForFile(ctx, ctx.packageName + ".fileprovider", outFile)
                                                    partialAttachmentUri.value = fileUri
                                                } else if (size <= 0) {
                                                    val dir = File(ctx.filesDir, "attachments").apply { mkdirs() }
                                                    val outFile = File(dir, "att_${System.currentTimeMillis()}")
                                                    ctx.contentResolver.openInputStream(uri)?.use { input ->
                                                        FileOutputStream(outFile).use { output -> input.copyTo(output) }
                                                    }
                                                    val fileUri = FileProvider.getUriForFile(ctx, ctx.packageName + ".fileprovider", outFile)
                                                    partialAttachmentUri.value = fileUri
                                                } else {
                                                    android.widget.Toast.makeText(ctx, ctx.getString(R.string.image_too_large), android.widget.Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        } catch (_: Exception) {
                                            partialAttachmentUri.value = null
                                        }
                                    }
                                }

                                LazyColumn(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .imePadding()
                                        .navigationBarsPadding(),
                                    contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 96.dp)
                                ) {
                                    item {
                                        Card(
                                            modifier = Modifier.fillMaxWidth(),
                                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                            shape = RoundedCornerShape(12.dp),
                                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                                        ) {
                                            Column(
                                                Modifier
                                                    .fillMaxWidth()
                                                    .padding(12.dp),
                                                verticalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                    // Entry summary
                                    val entry = remember(state.items, entryId) { state.items.firstOrNull { it.id == entryId } }
                                    if (entry != null) {
                                        Text(
                                            buildAnnotatedString {
                                                append(stringResource(R.string.user_label) + ": ")
                                                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(entry.name) }
                                            },
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                        Text(
                                            buildAnnotatedString {
                                                append(stringResource(R.string.label_principal_generic) + ": ")
                                                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(CurrencyFormatter.formatInr(entry.principal)) }
                                            },
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                        Text(
                                            buildAnnotatedString {
                                                append(stringResource(R.string.from_date) + ": ")
                                                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(SimpleDateFormat("dd/MM/yyyy").format(Date(entry.fromDateMillis))) }
                                            },
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                    // Date picker
                                    val showPicker = remember { mutableStateOf(false) }
                                    OutlinedTextField(
                                        value = SimpleDateFormat("dd/MM/yyyy").format(Date(partialDateMillis.value)),
                                        onValueChange = {},
                                        readOnly = true,
                                        label = { Text(stringResource(R.string.payment_date), style = MaterialTheme.typography.bodySmall) },
                                        modifier = Modifier.fillMaxWidth(),
                                        trailingIcon = { TextButton(onClick = { showPicker.value = true }) { Text(stringResource(R.string.pick)) } }
                                    )
                                    if (showPicker.value) {
                                        val entryFrom = state.items.firstOrNull { it.id == entryId }?.fromDateMillis ?: 0L
                                        val today = System.currentTimeMillis()
                                        val dpState = rememberDatePickerState(
                                            initialSelectedDateMillis = partialDateMillis.value,
                                            selectableDates = object : SelectableDates {
                                                override fun isSelectableDate(utcTimeMillis: Long): Boolean = utcTimeMillis in entryFrom..today
                                            }
                                        )
                                        DatePickerDialog(
                                            onDismissRequest = { showPicker.value = false },
                                            confirmButton = {
                                                TextButton(onClick = {
                                                    val sel = dpState.selectedDateMillis ?: partialDateMillis.value
                                                    val clamped = sel.coerceIn(entryFrom, today)
                                                    partialDateMillis.value = clamped
                                                    showPicker.value = false
                                                }) { Text(stringResource(R.string.ok)) }
                                            },
                                            dismissButton = { TextButton(onClick = { showPicker.value = false }) { Text(stringResource(R.string.cancel)) } }
                                        ) { DatePicker(state = dpState) }
                                    }
                                    // Summary under payment date (based on selected payment date)
                                    if (!editingLatestPayment.value) {
                                        val e = state.items.firstOrNull { it.id == entryId }
                                        val fromMillis = e?.fromDateMillis ?: 0L
                                        val durDaysAt = (((partialDateMillis.value - fromMillis).coerceAtLeast(0)) / 86_400_000L)
                                        Text(
                                            buildAnnotatedString {
                                                append(stringResource(R.string.duration) + ": ")
                                                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("${durDaysAt} days") }
                                            },
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                        Text(
                                            buildAnnotatedString {
                                                append(stringResource(R.string.interest_till_date) + ": ")
                                                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(CurrencyFormatter.formatInr(previewInterest.value)) }
                                            },
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                        val totalAt = (e?.principal ?: 0.0) + previewInterest.value
                                        Text(
                                            buildAnnotatedString {
                                                append(stringResource(R.string.total_amount) + ": ")
                                                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(CurrencyFormatter.formatInr(totalAt)) }
                                            },
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Checkbox(checked = partialFullPayment.value, onCheckedChange = { partialFullPayment.value = it })
                                        Text(text = stringResource(R.string.full_payment), style = MaterialTheme.typography.bodySmall)
                                    }
                                    OutlinedTextField(
                                        value = partialAmount.value,
                                        onValueChange = { input ->
                                            if (!partialFullPayment.value) {
                                                partialAmount.value = input.filter { ch -> ch.isDigit() || ch == '.' }
                                            }
                                        },
                                        label = { Text(stringResource(R.string.payment_amount), style = MaterialTheme.typography.bodySmall) },
                                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal
                                        ),
                                        modifier = Modifier.fillMaxWidth(),
                                        enabled = !partialFullPayment.value
                                    )
                                    // Amount in words
                                    val partialDouble = partialAmount.value.toDoubleOrNull()
                                    if (partialDouble != null) {
                                        val ctx2 = LocalContext.current
                                        val lang = runCatching { ctx2.resources.configuration.locales[0]?.language ?: "en" }.getOrElse { "en" }
                                        val words = NumberToWords.inIndianSystem(partialDouble, lang)
                                        if (words.isNotBlank()) {
                                            Text(words, style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                    if (!editingLatestPayment.value) {
                                        // Interest is already displayed under Payment Date
                                        Text(
                                            buildAnnotatedString {
                                                append(stringResource(R.string.remaining_after_payment) + ": ")
                                                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(CurrencyFormatter.formatInr(previewOutstanding.value)) }
                                            },
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                    OutlinedTextField(
                                        value = partialNote.value,
                                        onValueChange = { partialNote.value = it },
                                        label = { Text(stringResource(R.string.notes_optional), style = MaterialTheme.typography.bodySmall) },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        TextButton(onClick = { imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) { Text(stringResource(R.string.attach)) }
                                        Spacer(Modifier.width(8.dp))
                                        partialAttachmentUri.value?.let { att ->
                                            val painter = rememberAsyncImagePainter(att)
                                            Image(painter = painter, contentDescription = null, modifier = Modifier.size(48.dp).clip(RoundedCornerShape(6.dp)), contentScale = ContentScale.Crop)
                                            Spacer(Modifier.width(8.dp))
                                            TextButton(onClick = { partialAttachmentUri.value = null }) { Text(stringResource(R.string.delete)) }
                                        }
                                    }
                                    Spacer(Modifier.height(8.dp))
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                        TextButton(onClick = { vm.closePayments() }) { Text(stringResource(R.string.cancel)) }
                                        Spacer(Modifier.width(8.dp))
                                        Button(onClick = {
                                            val id = entryId
                                            val amt = (partialAmount.value.toDoubleOrNull() ?: 0.0).coerceAtLeast(0.0)
                                            val attStr = partialAttachmentUri.value?.toString()
                                            val noteStr = partialNote.value.ifBlank { null }
                                            val today = System.currentTimeMillis()
                                            if (id != null) {
                                                val entry = state.items.firstOrNull { it.id == id }
                                                val from = entry?.fromDateMillis ?: 0L
                                                if (partialDateMillis.value > today || partialDateMillis.value < from) {
                                                    android.widget.Toast.makeText(context, context.getString(R.string.pick), android.widget.Toast.LENGTH_SHORT).show(); return@Button
                                                }
                                                // Amount must be > 0 for non-full payments
                                                if (!partialFullPayment.value && amt <= 0.0) {
                                                    android.widget.Toast.makeText(context, context.getString(R.string.enter_valid_number), android.widget.Toast.LENGTH_SHORT).show(); return@Button
                                                }
                                                val maxAllowed = if (editingLatestPayment.value) baseOutstanding.value + originalEditAmount.value else baseOutstanding.value
                                                if (!partialFullPayment.value && amt > maxAllowed + 1e-6) {
                                                    android.widget.Toast.makeText(context, context.getString(R.string.enter_valid_number), android.widget.Toast.LENGTH_SHORT).show(); return@Button
                                                }
                                                if (editingLatestPayment.value) {
                                                    vm.editLatestPayment(id, amt, partialDateMillis.value, noteStr, attStr) { vm.openPayments(id) }
                                                } else {
                                                    val finalAmt = if (partialFullPayment.value) baseOutstanding.value else amt
                                                    vm.applyPartialWithMeta(id, finalAmt, partialDateMillis.value, noteStr, attStr)
                                                }
                                            }
                                            // After apply, show feedback and close
                                            editingLatestPayment.value = false
                                            successMessage.value = "Payment updated successfully"
                                        }) { Text(stringResource(R.string.apply_action)) }
                                    }
                                            }
                                        }
                                    }
                                }
                            }
                        } else {
                            // Payment history page
                            if (payments.isEmpty()) {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Text(stringResource(R.string.no_payments_yet))
                                }
                            } else {
                                val sdf = remember { SimpleDateFormat("dd/MM/yyyy") }
                                val entry = remember(state.items, paymentsEntryId) { state.items.firstOrNull { it.id == paymentsEntryId } }
                                val lastPayment = remember(payments) {
                                    payments.maxWithOrNull(compareBy<com.ledge.ledgerbook.data.local.entities.LedgerPayment>({ it.date }, { it.id }))
                                }
                                LazyColumn(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    items(payments) { p ->
                                        val (metaPrincipal, metaFromDate) = remember(p.note) {
                                            var mp: Double? = null
                                            var md: Long? = null
                                            p.note?.split('|')?.forEach { token ->
                                                when {
                                                    token.startsWith("meta:prevPrincipal=") -> mp = token.substringAfter("meta:prevPrincipal=").toDoubleOrNull()
                                                    token.startsWith("prevFromDate=") -> md = token.substringAfter("prevFromDate=").toLongOrNull()
                                                }
                                            }
                                            mp to md
                                        }
                                        var interestAtDate by remember(p.id) { mutableStateOf(0.0) }
                                        var outstandingAtDate by remember(p.id) { mutableStateOf(0.0) }
                                        LaunchedEffect(paymentsEntryId, p.id) {
                                            val id = paymentsEntryId
                                            if (id != null) {
                                                val triple = if (metaPrincipal != null && metaFromDate != null) {
                                                    vm.computeAtFromSnapshot(id, p.date, metaPrincipal, metaFromDate)
                                                } else {
                                                    vm.computeAt(id, p.date)
                                                }
                                                interestAtDate = triple.first
                                                outstandingAtDate = triple.third
                                            }
                                        }
                                        val principalAtDate = metaPrincipal ?: entry?.principal ?: 0.0
                                        val fromDateAt = metaFromDate ?: entry?.fromDateMillis
                                        val totalAtDate = principalAtDate + interestAtDate
                                        val remainingAfter = outstandingAtDate.coerceAtLeast(0.0)
                                        val userNote = ((p.note?.substringAfter("note:", "")) ?: "").substringBefore('|')
                                        val attUri = p.note
                                            ?.substringAfter("att:", "")
                                            ?.let { if (it.isNotEmpty()) it.substringBefore('|') else null }
                                            ?.let { runCatching { Uri.parse(it) }.getOrNull() }

                                        Card(
                                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                                            shape = RoundedCornerShape(12.dp)
                                        ) {
                                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                                    Text(
                                                        CurrencyFormatter.format(principalAtDate + interestAtDate - remainingAfter),
                                                        style = MaterialTheme.typography.titleMedium,
                                                        fontWeight = FontWeight.SemiBold,
                                                        modifier = Modifier.weight(1f)
                                                    )
                                                    Text(sdf.format(Date(p.date)), style = MaterialTheme.typography.labelSmall)
                                                    if (lastPayment != null && p.id == lastPayment.id) {
                                                        var menu by remember(p.id) { mutableStateOf(false) }
                                                        Box { 
                                                            IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, contentDescription = null) }
                                                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                                                DropdownMenuItem(text = { Text(stringResource(R.string.edit)) }, onClick = {
                                                                    menu = false
                                                                    paymentsTab.value = 0
                                                                    partialForId.value = null
                                                                    partialAmount.value = CurrencyFormatter.formatNumericUpTo2(p.amount)
                                                                    originalEditAmount.value = p.amount
                                                                    partialDateMillis.value = p.date
                                                                    partialNote.value = ((p.note?.substringAfter("note:", "")) ?: "").substringBefore('|')
                                                                    partialAttachmentUri.value = p.note?.substringAfter("att:")?.let { it.substringBefore('|') }?.let { Uri.parse(it) }
                                                                    partialFullPayment.value = false
                                                                    editingLatestPayment.value = true
                                                                })
                                                                DropdownMenuItem(text = { Text(stringResource(R.string.delete)) }, onClick = {
                                                                    menu = false
                                                                    val id = paymentsEntryId
                                                                    if (id != null) vm.deleteLatestPayment(id) { vm.openPayments(id) }
                                                                })
                                                            }
                                                        }
                                                    }
                                                }
                                                Divider()
                                                if (fromDateAt != null) {
                                                    Text(
                                                        buildAnnotatedString {
                                                            append(stringResource(R.string.from_date) + ": ")
                                                            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(sdf.format(Date(fromDateAt))) }
                                                        },
                                                        style = MaterialTheme.typography.bodySmall
                                                    )
                                                    val durDays = (((p.date - fromDateAt).coerceAtLeast(0)) / 86_400_000L)
                                                    Text(
                                                        buildAnnotatedString {
                                                            append(stringResource(R.string.duration) + ": ")
                                                            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("${durDays} " + stringResource(R.string.days)) }
                                                        },
                                                        style = MaterialTheme.typography.bodySmall
                                                    )
                                                }
                                                Text(
                                                    buildAnnotatedString {
                                                        append(stringResource(R.string.label_principal_generic) + ": ")
                                                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(CurrencyFormatter.format(principalAtDate)) }
                                                    },
                                                    style = MaterialTheme.typography.bodySmall
                                                )
                                                Text(
                                                    buildAnnotatedString {
                                                        append(stringResource(R.string.interest_till_date) + ": ")
                                                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(CurrencyFormatter.format(interestAtDate)) }
                                                    },
                                                    style = MaterialTheme.typography.bodySmall
                                                )
                                                Text(
                                                    buildAnnotatedString {
                                                        append(stringResource(R.string.total_amount) + ": ")
                                                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(CurrencyFormatter.format(totalAtDate)) }
                                                    },
                                                    style = MaterialTheme.typography.bodySmall
                                                )
                                                Text(
                                                    buildAnnotatedString {
                                                        append(stringResource(R.string.remaining_after_payment) + ": ")
                                                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(CurrencyFormatter.format(remainingAfter)) }
                                                    },
                                                    style = MaterialTheme.typography.bodySmall
                                                )
                                                if (userNote.isNotBlank()) {
                                                    Text(
                                                        buildAnnotatedString {
                                                            append(stringResource(R.string.notes_optional) + ": ")
                                                            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(userNote) }
                                                        },
                                                        style = MaterialTheme.typography.bodySmall
                                                    )
                                                }
                                                if (attUri != null) {
                                                    val painter = rememberAsyncImagePainter(attUri)
                                                    Spacer(Modifier.height(6.dp))
                                                    Image(
                                                        painter = painter,
                                                        contentDescription = null,
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .height(140.dp)
                                                            .clip(RoundedCornerShape(8.dp))
                                                            .clickable {
                                                                try {
                                                                    val intent = Intent(Intent.ACTION_VIEW).apply {
                                                                        setDataAndType(attUri, "image/*")
                                                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                                    }
                                                                    context.startActivity(intent)
                                                                } catch (_: Exception) { }
                                                            },
                                                        contentScale = ContentScale.Crop
                                                    )
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

    // Confirm delete dialog
    val deleteId = confirmDeleteId.value
    if (deleteId != null) {
        CenteredAlertDialog(
            onDismissRequest = { confirmDeleteId.value = null },
            title = { Text(stringResource(R.string.delete_entry)) },
            text = { Text(stringResource(R.string.delete_entry_confirm)) },
            confirmButton = {
                TextButton(onClick = { vm.delete(deleteId); confirmDeleteId.value = null }) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = { TextButton(onClick = { confirmDeleteId.value = null }) { Text(stringResource(R.string.cancel)) } }
        )
    }

}


@Composable
private fun OverviewCard(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
    container: Color = MaterialTheme.colorScheme.surface,
    content: Color = MaterialTheme.colorScheme.onSurface
) {
    Card(modifier = modifier) {
        Column(Modifier.padding(12.dp)) {
            Text(title, style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.height(4.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(container)
                    .padding(vertical = 6.dp, horizontal = 8.dp)
            ) {
                Text(value, style = MaterialTheme.typography.titleMedium, color = content)
            }
        }
    }
}

@Composable
private fun LabelValue(label: String, value: String, modifier: Modifier = Modifier, leadingIcon: ImageVector? = null) {
    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (leadingIcon != null) {
                Icon(
                    imageVector = leadingIcon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(Modifier.width(4.dp))
            }
            Text(label, style = MaterialTheme.typography.labelSmall)
        }
        Spacer(Modifier.height(2.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun CompactSearchBar(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(12.dp)
    val interaction = remember { MutableInteractionSource() }
    val focusRequester = remember { FocusRequester() }
    Row(
        modifier = modifier
            .clip(shape)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
            .background(MaterialTheme.colorScheme.surface)
            .heightIn(min = 40.dp)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Outlined.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Box(Modifier.weight(1f)) {
            if (value.isBlank()) {
                Text(placeholder, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CenteredAlertDialog(
    onDismissRequest: () -> Unit,
    title: @Composable (() -> Unit)? = null,
    text: @Composable (() -> Unit)? = null,
    confirmButton: @Composable () -> Unit,
    dismissButton: (@Composable () -> Unit)? = null
) {
    BasicAlertDialog(onDismissRequest = onDismissRequest) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                shape = MaterialTheme.shapes.medium,
                tonalElevation = 6.dp,
                modifier = Modifier.fillMaxWidth(0.9f)
            ) {
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
}

@Composable
private fun CenteredDatePickerDialog(
    onDismissRequest: () -> Unit,
    onConfirm: () -> Unit,
    content: @Composable () -> Unit
) {
    Dialog(onDismissRequest = onDismissRequest, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                shape = MaterialTheme.shapes.medium,
                tonalElevation = 6.dp,
                modifier = Modifier.fillMaxWidth(0.9f)
            ) {
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

@Composable
private fun LedgerRow(
    vm: LedgerItemVM,
    onClick: () -> Unit,
    onHistory: () -> Unit,
    onEdit: () -> Unit,
    onPartial: () -> Unit,
    onDelete: () -> Unit,
    onShare: () -> Unit,
    showName: Boolean = false,
    showTypeChip: Boolean = true,
    includePromo: Boolean = true
) {
    val ctx = LocalContext.current
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            val topBarVisible = showName || showTypeChip
            val openMenu = remember { mutableStateOf(false) }
            if (topBarVisible) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    // Column 1 (matches grid first column)
                    Box(Modifier.weight(1f)) {
                        if (showName) {
                            Text(
                                vm.name,
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                    }

                    // Inter-column gap (matches rows below)
                    Spacer(Modifier.width(16.dp))

                    // Column 2 (matches grid second column): chip at start, menu at end
                    Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                        if (showTypeChip) {
                            val isLend = vm.type == "LEND"
                            val chipBg = if (isLend) Color(0xFFDFF6DD) else Color(0xFFFFE2E0)
                            val chipFg = if (isLend) Color(0xFF0B6A0B) else Color(0xFF9A0007)
                            val typeLabel = when (vm.type.uppercase()) {
                                "LEND" -> stringResource(R.string.lend)
                                "BORROW" -> stringResource(R.string.borrow)
                                else -> toCamel(vm.type)
                            }
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(chipBg)
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = typeLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = chipFg
                                )
                            }
                        }
                        Spacer(Modifier.weight(1f))
                        Box {
                            IconButton(onClick = { openMenu.value = true }) {
                                Icon(
                                    imageVector = Icons.Default.MoreVert,
                                    contentDescription = "More",
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            DropdownMenu(
                                expanded = openMenu.value,
                                onDismissRequest = { openMenu.value = false }
                            ) {
                                if (showName && !vm.phone.isNullOrBlank()) {
                                    DropdownMenuItem(text = { Text(stringResource(R.string.call_user)) }, onClick = {
                                        openMenu.value = false
                                        try {
                                            val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + vm.phone))
                                            ctx.startActivity(intent)
                                        } catch (_: Exception) {}
                                    })
                                }
                                DropdownMenuItem(text = { Text(stringResource(R.string.share_receipt)) }, onClick = { openMenu.value = false; onShare() })
                                DropdownMenuItem(text = { Text(stringResource(R.string.share)) }, onClick = {
                                    openMenu.value = false
                                    val text = buildShareText(ctx, vm, includePromo)
                                    val intent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_TEXT, text)
                                    }
                                    ctx.startActivity(Intent.createChooser(intent, ctx.getString(R.string.share_via)))
                                })
                                DropdownMenuItem(text = { Text(stringResource(R.string.payment_history)) }, onClick = { openMenu.value = false; onHistory() })
                                DropdownMenuItem(text = { Text(stringResource(R.string.partial_payment)) }, onClick = { openMenu.value = false; onPartial() })
                                DropdownMenuItem(text = { Text(stringResource(R.string.edit)) }, onClick = { openMenu.value = false; onEdit() })
                                DropdownMenuItem(text = { Text(stringResource(R.string.delete)) }, onClick = { openMenu.value = false; onDelete() })
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            val msPerDay = 86_400_000L
            val daysTotal = (((System.currentTimeMillis() - vm.fromDateMillis) / msPerDay).toInt()).coerceAtLeast(0)
            val years = daysTotal / 365
            val remAfterYears = daysTotal % 365
            val months = remAfterYears / 30
            val days = remAfterYears % 30
            val totalTime = buildString {
                val yr = stringResource(R.string.year_singular)
                val mo = stringResource(R.string.month_singular)
                val dy = stringResource(R.string.day_singular)
                if (years > 0) append("${years} ${yr} ")
                if (months > 0) append("${months} ${mo} ")
                append("${days} ${dy}")
            }

            Column(Modifier.fillMaxWidth()) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    LabelValue(
                        label = stringResource(R.string.label_principal_generic),
                        value = CurrencyFormatter.formatNoDecimals(vm.principal),
                        modifier = Modifier.weight(1f),
                        leadingIcon = Icons.Outlined.Payments
                    )
                    Box(Modifier.weight(1f)) {
                        val basisLabel = when (vm.rateBasis.uppercase()) {
                            "MONTHLY" -> stringResource(R.string.monthly)
                            "YEARLY" -> stringResource(R.string.yearly)
                            else -> toCamel(vm.rateBasis)
                        }
                        LabelValue(label = stringResource(R.string.interest_rate), value = "${vm.rate}% ${basisLabel}")
                        if (!topBarVisible) {
                            // Move 3-dots here when header row is hidden (grouping mode)
                            Box(Modifier.fillMaxWidth()) {
                                IconButton(onClick = { openMenu.value = true }, modifier = Modifier.align(Alignment.TopEnd)) {
                                    Icon(
                                        imageVector = Icons.Default.MoreVert,
                                        contentDescription = stringResource(R.string.more),
                                        tint = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                            DropdownMenu(
                                expanded = openMenu.value,
                                onDismissRequest = { openMenu.value = false }
                            ) {
                                if (showName && !vm.phone.isNullOrBlank()) {
                                    DropdownMenuItem(text = { Text(stringResource(R.string.call_user)) }, onClick = {
							openMenu.value = false
                                        try {
                                            val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + vm.phone))
                                            ctx.startActivity(intent)
                                        } catch (_: Exception) {}
                                    })
                                }
                                DropdownMenuItem(text = { Text(stringResource(R.string.share_receipt)) }, onClick = { openMenu.value = false; onShare() })
                                DropdownMenuItem(text = { Text(stringResource(R.string.share)) }, onClick = {
                                    openMenu.value = false
                                    val text = buildShareText(ctx, vm, includePromo)
                                    val intent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_TEXT, text)
                                    }
                                    ctx.startActivity(Intent.createChooser(intent, ctx.getString(R.string.share_via)))
                                })
                                DropdownMenuItem(text = { Text(stringResource(R.string.payment_history)) }, onClick = { openMenu.value = false; onHistory() })
                                DropdownMenuItem(text = { Text(stringResource(R.string.partial_payment)) }, onClick = { openMenu.value = false; onPartial() })
                                DropdownMenuItem(text = { Text(stringResource(R.string.edit)) }, onClick = { openMenu.value = false; onEdit() })
                                DropdownMenuItem(text = { Text(stringResource(R.string.delete)) }, onClick = { openMenu.value = false; onDelete() })
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    LabelValue(label = stringResource(R.string.from_date), value = vm.dateStr, modifier = Modifier.weight(1f), leadingIcon = Icons.Outlined.Event)
                    // Right column: total time with small colored status dot (due soon / overdue)
                    // Status dot colors: thresholds from settings
                    val themeVM: ThemeViewModel = hiltViewModel()
                    val odChild by themeVM.overdueDays.collectAsState()
                    val winChild by themeVM.dueSoonWindowDays.collectAsState()
                    val odT = odChild.coerceAtLeast(1)
                    val fromT = (odT - winChild.coerceAtLeast(1)).coerceAtLeast(0)
                    val statusColor = when {
                        daysTotal >= odT -> Color(0xFFEF5350) // red
                        daysTotal in fromT until odT -> Color(0xFFFFB300) // amber
                        else -> null
                    }
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Outlined.AccessTime,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(R.string.total_time), style = MaterialTheme.typography.labelSmall)
                        }
                        Spacer(Modifier.height(2.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            statusColor?.let {
                                Box(
                                    Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(it)
                                )
                                Spacer(Modifier.width(6.dp))
                            }
                            Text(
                                totalTime,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (statusColor == null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    LabelValue(label = stringResource(R.string.label_interest), value = CurrencyFormatter.formatNoDecimals(vm.accrued), modifier = Modifier.weight(1f))
                    val isLendChip = vm.type == "LEND"
                    val chipBg2 = if (isLendChip) Color(0xFFDFF6DD) else Color(0xFFFFE2E0)
                    val chipFg2 = if (isLendChip) Color(0xFF0B6A0B) else Color(0xFF9A0007)
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.total_amount), style = MaterialTheme.typography.labelSmall)
                        Spacer(Modifier.height(2.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(chipBg2)
                                .padding(vertical = 4.dp, horizontal = 6.dp)
                        ) {
                            Text(
                                CurrencyFormatter.formatNoDecimals(vm.total),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = chipFg2
                            )
                        }
                    }
                }
            }
        }
    }
}
