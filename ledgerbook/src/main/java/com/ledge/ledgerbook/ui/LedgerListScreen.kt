package com.ledge.ledgerbook.ui
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Search
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
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
import java.util.Date
import kotlin.math.roundToInt

typealias LedgerItemVM = LedgerViewModel.LedgerItemVM

// Helper to display INR without fractional digits for parent summary
private fun formatInrNoDecimals(value: Double): String {
    val full = CurrencyFormatter.formatInr(value)
    // Strip any decimal portion like .00 or .50; keep sign and currency symbol/grouping
    return full.replace(Regex("\\.[0-9]+"), "")
}

// Normalize labels like "BORROW"/"LEND", "MONTHLY"/"YEARLY", "SIMPLE"/"COMPOUND" to Camel case
private fun toCamel(label: String?): String {
    if (label.isNullOrBlank()) return ""
    val lower = label.lowercase()
    return lower.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
}

// Build a human-readable plain text for an entry, optionally with promo
private fun buildShareText(ctx: android.content.Context, vm: LedgerItemVM, includePromo: Boolean): String {
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
        appendLine(ctx.getString(R.string.label_principal_with_value, CurrencyFormatter.formatInr(vm.principal)))
        appendLine(ctx.getString(R.string.label_rate_with_value, vm.rate.toString(), basisLabel))
        appendLine(ctx.getString(R.string.label_from_with_value, sdf.format(java.util.Date(vm.fromDateMillis))))
        appendLine(ctx.getString(R.string.label_interest_till_now_with_value, CurrencyFormatter.formatInr(vm.accrued)))
        appendLine(ctx.getString(R.string.label_total_with_value, CurrencyFormatter.formatInr(vm.total)))
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
    // Monetization flag
    val monetizationVM: MonetizationViewModel = hiltViewModel()
    val hasRemoveAds by monetizationVM.hasRemoveAds.collectAsState()
    // Search state and filtered items
    var searchQuery by rememberSaveable { mutableStateOf("") }
    val filteredItems = remember(state.items, searchQuery) {
        if (searchQuery.isBlank()) state.items
        else state.items.filter { it.name.contains(searchQuery, ignoreCase = true) }
    }
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
                // Compact single overview card with 2x2 grid + Final Amount row
                Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                        // Grid: two rows, two columns
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .height(IntrinsicSize.Min),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(stringResource(R.string.total_lend), style = MaterialTheme.typography.labelSmall)
                                Spacer(Modifier.height(2.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0xFFDFF6DD))
                                        .padding(vertical = 4.dp, horizontal = 6.dp)
                                ) { Text(formatInrNoDecimals(state.totalLend), style = MaterialTheme.typography.bodyMedium, color = Color(0xFF0B6A0B), fontWeight = FontWeight.SemiBold) }
                            }
                            // Vertical divider between Lend and Borrow columns (lightened for dark theme visibility)
                            Box(
                                Modifier
                                    .width(1.dp)
                                    .fillMaxHeight()
                                    .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                            )
                            Column(Modifier.weight(1f)) {
                                Text(stringResource(R.string.lend_interest), style = MaterialTheme.typography.labelSmall)
                                Spacer(Modifier.height(2.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0xFFDFF6DD))
                                        .padding(vertical = 4.dp, horizontal = 6.dp)
                                ) { Text(formatInrNoDecimals(state.totalLendInterest), style = MaterialTheme.typography.bodyMedium, color = Color(0xFF0B6A0B), fontWeight = FontWeight.SemiBold) }
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .height(IntrinsicSize.Min),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(stringResource(R.string.total_borrow), style = MaterialTheme.typography.labelSmall)
                                Spacer(Modifier.height(2.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0xFFFFE2E0))
                                        .padding(vertical = 4.dp, horizontal = 6.dp)
                                ) { Text(formatInrNoDecimals(state.totalBorrow), style = MaterialTheme.typography.bodyMedium, color = Color(0xFF9A0007), fontWeight = FontWeight.SemiBold) }
                            }
                            // Vertical divider between Lend and Borrow columns (lightened for dark theme visibility)
                            Box(
                                Modifier
                                    .width(1.dp)
                                    .fillMaxHeight()
                                    .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                            )
                            Column(Modifier.weight(1f)) {
                                Text(stringResource(R.string.borrow_interest), style = MaterialTheme.typography.labelSmall)
                                Spacer(Modifier.height(2.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0xFFFFE2E0))
                                        .padding(vertical = 4.dp, horizontal = 6.dp)
                                ) { Text(formatInrNoDecimals(state.totalBorrowInterest), style = MaterialTheme.typography.bodyMedium, color = Color(0xFF9A0007), fontWeight = FontWeight.SemiBold) }
                            }
                        }

                        Spacer(Modifier.height(6.dp))

                        // Final Amount row with reminder chips on the same row
                        val isPositive = state.finalAmount >= 0
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(stringResource(R.string.final_amount), style = MaterialTheme.typography.labelSmall)
                                Spacer(Modifier.height(2.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isPositive) Color(0xFFDFF6DD) else Color(0xFFFFE2E0))
                                        .padding(vertical = 6.dp, horizontal = 8.dp)
                                ) {
                                    Text(
                                        formatInrNoDecimals(state.finalAmount),
                                        style = MaterialTheme.typography.titleSmall,
                                        color = if (isPositive) Color(0xFF0B6A0B) else Color(0xFF9A0007),
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            val msPerDay = 86_400_000L
                            val now = System.currentTimeMillis()
                            val overdueCount = remember(state.items) { state.items.count { (((now - it.fromDateMillis) / msPerDay).toInt()) >= 365 } }
                            val dueSoonCount = remember(state.items) { state.items.count { val d = (((now - it.fromDateMillis) / msPerDay).toInt()); d in 335..364 } }

                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                if (overdueCount > 0) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text("Overdue", style = MaterialTheme.typography.labelSmall, color = Color(0xFFB00020))
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(Color(0xFFFDE0E0))
                                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                        ) { Text(overdueCount.toString(), color = Color(0xFFB00020), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold) }
                                    }
                                }
                                if (dueSoonCount > 0) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text("Due soon", style = MaterialTheme.typography.labelSmall, color = Color(0xFF8C6D1F))
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(Color(0xFFFFF3E0))
                                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                        ) { Text(dueSoonCount.toString(), color = Color(0xFF8C6D1F), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold) }
                                    }
                                }
                            }
                        }
                    }
                }

                // Banner Ad (only when ads are not removed) - below the overview card
                if (!hasRemoveAds) {
                    Spacer(Modifier.height(6.dp))
                    BannerAd(modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(6.dp))
                } else {
                    // Minimal gap before search when no ad is shown
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
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { expanded.value = !expanded.value },
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .height(IntrinsicSize.Min)
                            .padding(12.dp)
                    ) {
                        // Thin status strip (amber = due soon, red = overdue), followed by existing purple parent bar
                        val msPerDay = 86_400_000L
                        val now = System.currentTimeMillis()
                        val daysSince: (LedgerItemVM) -> Int = { (((now - it.fromDateMillis) / msPerDay).toInt()).coerceAtLeast(0) }
                        val (overdueCount, dueSoonCount) = run {
                            var od = 0
                            var ds = 0
                            itemsForUser.forEach { item ->
                                val d = daysSince(item)
                                when {
                                    d >= 365 -> od++
                                    d in 335..364 -> ds++
                                }
                            }
                            od to ds
                        }
                        val parentStatusColor = when {
                            overdueCount > 0 -> Color(0xFFEF5350) // red 400/500
                            dueSoonCount > 0 -> Color(0xFFFFB300) // amber 600
                            else -> Color.Transparent
                        }
                        Box(
                            Modifier
                                .width(2.dp)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(8.dp))
                                .background(parentStatusColor)
                        )
                        Spacer(Modifier.width(4.dp))
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
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(chipBg)
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            text = if (overdueCount > 0) "Overdue ($overdueCount)" else "Due soon ($dueSoonCount)",
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
                                    DropdownMenu(expanded = parentMenuOpen.value, onDismissRequest = { parentMenuOpen.value = false }) {
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
                                    value = formatInrNoDecimals(netPrincipal),
                                    modifier = Modifier.weight(1f)
                                )
                                LabelValue(
                                    label = stringResource(R.string.total_interest),
                                    value = formatInrNoDecimals(netInterest),
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
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(chipBg)
                                            .padding(vertical = 4.dp, horizontal = 6.dp)
                                    ) {
                                        Text(
                                            formatInrNoDecimals(netTotal),
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
                                onHistory = { vm.openPayments(item.id) },
                                onEdit = { vm.beginEdit(item.id) },
                                onPartial = {
                                    if (item.outstanding <= 0.0) {
                                        android.widget.Toast.makeText(context, context.getString(R.string.no_payments_pending), android.widget.Toast.LENGTH_SHORT).show()
                                    } else {
                                        partialForId.value = item.id
                                        partialAmount.value = ""
                                        partialNote.value = ""
                                        partialAttachmentUri.value = null
                                        partialFullPayment.value = false
                                        editingLatestPayment.value = false
                                        partialDateMillis.value = System.currentTimeMillis()
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
                        onHistory = { vm.openPayments(item.id) },
                        onEdit = { vm.beginEdit(item.id) },
                        onPartial = {
                            if (item.outstanding <= 0.0) {
                                android.widget.Toast.makeText(context, context.getString(R.string.no_payments_pending), android.widget.Toast.LENGTH_SHORT).show()
                            } else {
                                partialForId.value = item.id
                                partialAmount.value = ""
                                partialNote.value = ""
                                partialAttachmentUri.value = null
                                partialFullPayment.value = false
                                editingLatestPayment.value = false
                                partialDateMillis.value = System.currentTimeMillis()
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
                        if (!e.notes.isNullOrBlank()) {
                            Spacer(Modifier.height(8.dp))
                            LabelValue(label = stringResource(R.string.notes_optional), value = e.notes)
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { detailsForId.value = null; vm.clearEdit() }) { Text(stringResource(R.string.ok)) } }
            )
        }
    }

    // Partial payment dialog
    val showPartial = partialForId.value != null
    if (showPartial) {
        val entryId = partialForId.value!!
        LaunchedEffect(entryId, partialDateMillis.value, partialAmount.value, partialFullPayment.value) {
            val (accrued, _, outstanding) = vm.computeAt(entryId, partialDateMillis.value)
            previewInterest.value = accrued
            baseOutstanding.value = outstanding
            if (partialFullPayment.value) {
                // Auto-fill full outstanding and show remaining as zero
                partialAmount.value = CurrencyFormatter.formatNumericUpTo2(outstanding)
                previewOutstanding.value = 0.0
            } else {
                val amt = partialAmount.value.toDoubleOrNull() ?: 0.0
                previewOutstanding.value = (outstanding - amt).coerceAtLeast(0.0)
            }
        }

        val ctx = LocalContext.current
        val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri != null) {
                // Validate < 1MB
                try {
                    ctx.contentResolver.openAssetFileDescriptor(uri, "r")?.use { afd ->
                        val size = afd.length
                        if (size in 1..1_000_000) {
                            // Import into app's files/attachments directory
                            val dir = File(ctx.filesDir, "attachments").apply { mkdirs() }
                            val ext = "jpg" // keep simple; source type may vary
                            val outFile = File(dir, "att_${System.currentTimeMillis()}.$ext")
                            ctx.contentResolver.openInputStream(uri)?.use { input ->
                                FileOutputStream(outFile).use { output ->
                                    input.copyTo(output)
                                }
                            }
                            val fileUri = FileProvider.getUriForFile(ctx, ctx.packageName + ".fileprovider", outFile)
                            partialAttachmentUri.value = fileUri
                        } else if (size <= 0) {
                            // Fallback: accept
                            val dir = File(ctx.filesDir, "attachments").apply { mkdirs() }
                            val outFile = File(dir, "att_${System.currentTimeMillis()}")
                            ctx.contentResolver.openInputStream(uri)?.use { input ->
                                FileOutputStream(outFile).use { output ->
                                    input.copyTo(output)
                                }
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
        CenteredAlertDialog(
            onDismissRequest = { partialForId.value = null },
            title = { Text(stringResource(R.string.partial_payment)) },
            text = {
                Column(Modifier.fillMaxWidth()) {
                    val showPicker = remember { mutableStateOf(false) }
                    OutlinedTextField(
                        value = SimpleDateFormat("dd/MM/yyyy").format(Date(partialDateMillis.value)),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.payment_date)) },
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = partialFullPayment.value, onCheckedChange = { partialFullPayment.value = it })
                        Text(text = stringResource(R.string.full_payment))
                    }

                    OutlinedTextField(
                        value = partialAmount.value,
                        onValueChange = { input ->
                            // Allow only digits and a decimal point; disallow negatives
                            if (!partialFullPayment.value) {
                                partialAmount.value = input.filter { ch -> ch.isDigit() || ch == '.' }
                            }
                        },
                        label = { Text(stringResource(R.string.amount)) },
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !partialFullPayment.value
                    )
                    // Amount in words (Indian system)
                    val partialDouble = partialAmount.value.toDoubleOrNull()
                    if (partialDouble != null) {
                        val ctx = LocalContext.current
                        val lang = runCatching { ctx.resources.configuration.locales[0]?.language ?: "en" }.getOrElse { "en" }
                        val words = NumberToWords.inIndianSystem(partialDouble, lang)
                        if (words.isNotBlank()) {
                            Spacer(Modifier.height(4.dp))
                            Text(words, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    if (!editingLatestPayment.value) {
                        Text(stringResource(R.string.interest_till_date) + ": " + CurrencyFormatter.formatInr(previewInterest.value))
                        Text(stringResource(R.string.remaining_after_payment) + ": " + CurrencyFormatter.formatInr(previewOutstanding.value))
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = partialNote.value,
                        onValueChange = { partialNote.value = it },
                        label = { Text(stringResource(R.string.notes_optional)) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = {
                            imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        }) { Text(stringResource(R.string.attach)) }
                        Spacer(Modifier.width(8.dp))
                        val att = partialAttachmentUri.value
                        if (att != null) {
                            val painter = rememberAsyncImagePainter(att)
                            Image(painter = painter, contentDescription = null, modifier = Modifier.size(48.dp).clip(RoundedCornerShape(6.dp)), contentScale = ContentScale.Crop)
                            Spacer(Modifier.width(8.dp))
                            TextButton(onClick = { partialAttachmentUri.value = null }) { Text(stringResource(R.string.delete)) }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val amt = (partialAmount.value.toDoubleOrNull() ?: 0.0).coerceAtLeast(0.0)
                    val attStr = partialAttachmentUri.value?.toString()
                    val noteStr = partialNote.value.ifBlank { null }
                    val today = System.currentTimeMillis()
                    val id = partialForId.value
                    if (id != null) {
                        val entry = state.items.firstOrNull { it.id == id }
                        val from = entry?.fromDateMillis ?: 0L
                        // Date bounds validation
                        if (partialDateMillis.value > today || partialDateMillis.value < from) {
                            android.widget.Toast.makeText(context, context.getString(R.string.pick), android.widget.Toast.LENGTH_SHORT).show()
                            return@TextButton
                        }
                        // Amount validation (<= remaining)
                        val maxAllowed = if (editingLatestPayment.value) baseOutstanding.value + originalEditAmount.value else baseOutstanding.value
                        if (!partialFullPayment.value && amt > maxAllowed + 1e-6) {
                            android.widget.Toast.makeText(context, context.getString(R.string.enter_valid_number), android.widget.Toast.LENGTH_SHORT).show()
                            return@TextButton
                        }
                        if (editingLatestPayment.value) {
                            vm.editLatestPayment(id, amt, partialDateMillis.value, noteStr, attStr) { vm.openPayments(id) }
                        } else {
                            val finalAmt = if (partialFullPayment.value) baseOutstanding.value else amt
                            vm.applyPartialWithMeta(id, finalAmt, partialDateMillis.value, noteStr, attStr)
                        }
                    }
                    partialForId.value = null
                }) { Text(stringResource(R.string.apply_action)) }
            },
            dismissButton = { TextButton(onClick = { partialForId.value = null }) { Text(stringResource(R.string.cancel)) } }
        )
    }

    // Payment history dialog
    val paymentsEntryId by vm.paymentsEntryId.collectAsState()
    val selectedPaymentForView = remember { mutableStateOf<com.ledge.ledgerbook.data.local.entities.LedgerPayment?>(null) }
    if (paymentsEntryId != null) {
        val payments by vm.paymentsForViewing.collectAsState()
        CenteredAlertDialog(
            onDismissRequest = { vm.closePayments() },
            title = { Text(stringResource(R.string.payment_history)) },
            text = {
                if (payments.isEmpty()) {
                    Text(stringResource(R.string.no_payments_yet))
                } else {
                    val last = payments.maxWithOrNull(compareBy<com.ledge.ledgerbook.data.local.entities.LedgerPayment>({ it.date }, { it.id }))
                    LazyColumn(Modifier.fillMaxWidth()) {
                        items(payments) { p ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp)
                                    .clickable { selectedPaymentForView.value = p },
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(CurrencyFormatter.formatInr(p.amount), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                                    Text(SimpleDateFormat("dd/MM/yyyy").format(Date(p.date)), style = MaterialTheme.typography.labelSmall)
                                    val userNote = ((p.note?.substringAfter("note:", "")) ?: "").substringBefore('|')
                                    if (userNote.isNotBlank()) Text(userNote, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                }
                                if (last != null && p.id == last.id) {
                                    var menu by remember { mutableStateOf(false) }
                                    Box { 
                                        IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, contentDescription = null) }
                                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                            DropdownMenuItem(text = { Text(stringResource(R.string.edit)) }, onClick = {
                                                menu = false
                                                partialForId.value = paymentsEntryId
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
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { vm.closePayments() }) { Text(stringResource(R.string.close)) } }
        )
    }

    // Payment details view dialog when a history row is tapped
    val viewPayment = selectedPaymentForView.value
    if (viewPayment != null) {
        val userNote = ((viewPayment.note?.substringAfter("note:", "")) ?: "").substringBefore('|')
        val attStr = viewPayment.note?.substringAfter("att:")?.let { it.substringBefore('|') }
        val attUri = attStr?.let { runCatching { Uri.parse(it) }.getOrNull() }
        CenteredAlertDialog(
            onDismissRequest = { selectedPaymentForView.value = null },
            title = { Text(text = stringResource(R.string.entry_details)) },
            text = {
                // Compute interest and outstanding at the payment date using snapshot from payment metadata when available
                val entry = remember(state.items, paymentsEntryId) { state.items.firstOrNull { it.id == paymentsEntryId } }
                // Parse metadata snapshot persisted at payment time: meta:prevPrincipal=..., prevFromDate=...
                val (metaPrincipal, metaFromDate) = remember(viewPayment.note) {
                    var p: Double? = null
                    var d: Long? = null
                    viewPayment.note?.split('|')?.forEach { token ->
                        when {
                            token.startsWith("meta:prevPrincipal=") -> p = token.substringAfter("meta:prevPrincipal=").toDoubleOrNull()
                            token.startsWith("prevFromDate=") -> d = token.substringAfter("prevFromDate=").toLongOrNull()
                        }
                    }
                    p to d
                }

                var interestAtDate by remember { mutableStateOf(0.0) }
                var outstandingAtDate by remember { mutableStateOf(0.0) }
                LaunchedEffect(paymentsEntryId, viewPayment.date, metaPrincipal, metaFromDate) {
                    val id = paymentsEntryId
                    if (id != null) {
                        val triple = if (metaPrincipal != null && metaFromDate != null) {
                            vm.computeAtFromSnapshot(id, viewPayment.date, metaPrincipal, metaFromDate)
                        } else {
                            vm.computeAt(id, viewPayment.date)
                        }
                        interestAtDate = triple.first
                        outstandingAtDate = triple.third
                    }
                }

                val principal = metaPrincipal ?: entry?.principal ?: 0.0
                val fromDate = metaFromDate ?: entry?.fromDateMillis
                val sdf = remember { SimpleDateFormat("dd/MM/yyyy") }
                val totalAmountAtDate = principal + interestAtDate
                // outstandingAtDate already includes the effect of the current payment
                val remainingAfter = outstandingAtDate.coerceAtLeast(0.0)

                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Main summary
                    Text(text = stringResource(R.string.amount) + ": " + CurrencyFormatter.formatInr(viewPayment.amount), style = MaterialTheme.typography.bodyLarge)
                    // Details
                    if (fromDate != null) {
                        Text(text = stringResource(R.string.from_date) + ": " + sdf.format(Date(fromDate)), style = MaterialTheme.typography.bodyMedium)
                    }
                    Text(text = stringResource(R.string.payment_date) + ": " + sdf.format(Date(viewPayment.date)), style = MaterialTheme.typography.bodyMedium)
                    Text(text = stringResource(R.string.label_principal_generic) + ": " + CurrencyFormatter.formatInr(principal), style = MaterialTheme.typography.bodyMedium)
                    Text(text = stringResource(R.string.interest_till_date) + ": " + CurrencyFormatter.formatInr(interestAtDate), style = MaterialTheme.typography.bodyMedium)
                    Text(text = stringResource(R.string.total_amount) + ": " + CurrencyFormatter.formatInr(totalAmountAtDate), style = MaterialTheme.typography.bodyMedium)
                    Text(text = stringResource(R.string.remaining_after_payment) + ": " + CurrencyFormatter.formatInr(remainingAfter), style = MaterialTheme.typography.bodyMedium)
                    if (userNote.isNotBlank()) {
                        Text(text = stringResource(R.string.notes_optional) + ":")
                        Text(text = userNote, style = MaterialTheme.typography.bodySmall)
                    }
                    if (attUri != null) {
                        val painter = rememberAsyncImagePainter(attUri)
                        Image(
                            painter = painter,
                            contentDescription = null,
                            modifier = Modifier
                                .size(140.dp)
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
            },
            confirmButton = { TextButton(onClick = { selectedPaymentForView.value = null }) { Text(stringResource(R.string.close)) } }
        )
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
private fun LabelValue(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(label, style = MaterialTheme.typography.labelSmall)
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
                            AssistChip(
                                onClick = {},
                                label = { Text(typeLabel, style = MaterialTheme.typography.labelSmall) },
                                colors = AssistChipDefaults.assistChipColors(
                                    containerColor = chipBg,
                                    labelColor = chipFg
                                )
                            )
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
                            DropdownMenu(expanded = openMenu.value, onDismissRequest = { openMenu.value = false }) {
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
                    LabelValue(label = stringResource(R.string.label_principal_generic), value = formatInrNoDecimals(vm.principal), modifier = Modifier.weight(1f))
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
                            DropdownMenu(expanded = openMenu.value, onDismissRequest = { openMenu.value = false }) {
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
                    LabelValue(label = stringResource(R.string.from_date), value = vm.dateStr, modifier = Modifier.weight(1f))
                    // Right column: total time with small colored status dot (due soon / overdue)
                    val statusColor = when {
                        daysTotal >= 365 -> Color(0xFFEF5350) // red
                        daysTotal in 335..364 -> Color(0xFFFFB300) // amber
                        else -> null
                    }
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.total_time), style = MaterialTheme.typography.labelSmall)
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
                    LabelValue(label = stringResource(R.string.label_interest), value = formatInrNoDecimals(vm.accrued), modifier = Modifier.weight(1f))
                    val isLendChip = vm.type == "LEND"
                    val chipBg2 = if (isLendChip) Color(0xFFDFF6DD) else Color(0xFFFFE2E0)
                    val chipFg2 = if (isLendChip) Color(0xFF0B6A0B) else Color(0xFF9A0007)
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.total_amount), style = MaterialTheme.typography.labelSmall)
                        Spacer(Modifier.height(2.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(chipBg2)
                                .padding(vertical = 4.dp, horizontal = 6.dp)
                        ) {
                            Text(
                                formatInrNoDecimals(vm.total),
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
