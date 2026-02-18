package com.ledge.cashbook.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ledge.cashbook.R
import com.ledge.cashbook.util.Currency
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawStyle
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.sp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.foundation.layout.Box
import com.ledge.cashbook.util.PdfShare
import com.ledge.cashbook.util.ExcelShare
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import kotlin.math.roundToInt
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material.icons.filled.Delete
import androidx.hilt.navigation.compose.hiltViewModel
import com.ledge.cashbook.ads.BannerAd
import android.graphics.Paint
import com.google.android.gms.ads.AdSize
import android.widget.Toast

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountsScreen(
    onOpenAccount: (Int) -> Unit,
    onAddToBook: (Int) -> Unit = {}
    , vm: AccountsViewModel = hiltViewModel()
) {
    val adsVm: AdsViewModel = hiltViewModel()
    val hasRemoveAds by adsVm.hasRemoveAds.collectAsState(initial = false)
    val accounts by vm.accounts.collectAsState()
    val settingsVM: SettingsViewModel = hiltViewModel()
    val showCategory by settingsVM.showCategory.collectAsState(initial = false)
    val showSummary by settingsVM.showSummary.collectAsState(initial = false)
    val totalCredit by vm.totalCredit.collectAsState()
    val totalDebit by vm.totalDebit.collectAsState()
    val dueCount by vm.dueAccountsCount.collectAsState()
    var showAdd by remember { mutableStateOf(false) }
    var accountName by remember { mutableStateOf("") }
    var openBalanceText by remember { mutableStateOf("") }
    val ctx = LocalContext.current
    var bannerLoaded by remember { mutableStateOf(false) }
    var confirmDeleteFor by remember { mutableStateOf<Int?>(null) }
    var renameFor by remember { mutableStateOf<Int?>(null) }
    var renameText by remember { mutableStateOf("") }
    var chartFor by remember { mutableStateOf<Int?>(null) }
    var showDueOnly by rememberSaveable { mutableStateOf(false) }
    var quickAddFor by remember { mutableStateOf<Int?>(null) }
    var selectedQuickTemplate by remember { mutableStateOf<String?>(null) }
    var quickAddAmount by remember { mutableStateOf("") }
    var quickAddMenuFor by remember { mutableStateOf<Int?>(null) }

    Scaffold(
        topBar = { CenterAlignedTopAppBar(
            title = { Text(stringResource(R.string.title_cash_book)) },
            colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                containerColor = MaterialTheme.colorScheme.primary,
                titleContentColor = MaterialTheme.colorScheme.onPrimary,
                navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                actionIconContentColor = MaterialTheme.colorScheme.onPrimary
            )
        ) },
        contentWindowInsets = WindowInsets.systemBars
    ) { padding ->
        BoxWithConstraints(Modifier.fillMaxSize()) {
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
            // List content
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(
                    start = 12.dp,
                    end = 12.dp,
                    top = 12.dp,
                    bottom = if (showBanner && bannerLoaded) bannerHeight else 12.dp
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (showSummary) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Column(Modifier.padding(12.dp)) {
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    // Credit
                                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(stringResource(R.string.total_credit), style = MaterialTheme.typography.labelSmall)
                                        val chipBg = Color(0xFFDFF6DD)
                                        val chipFg = Color(0xFF0B6A0B)
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(chipBg)
                                                .padding(vertical = 4.dp, horizontal = 6.dp)
                                        ) {
                                            Text(
                                                Currency.inr(totalCredit),
                                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                                color = chipFg,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                    // Debit
                                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(stringResource(R.string.total_debit), style = MaterialTheme.typography.labelSmall)
                                        val chipBg = MaterialTheme.colorScheme.errorContainer
                                        val chipFg = MaterialTheme.colorScheme.onErrorContainer
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(chipBg)
                                                .padding(vertical = 4.dp, horizontal = 6.dp)
                                        ) {
                                            Text(
                                                Currency.inr(totalDebit),
                                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                                color = chipFg,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                    // Due count
                                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(stringResource(R.string.due_accounts), style = MaterialTheme.typography.labelSmall)
                                        val chipBg = MaterialTheme.colorScheme.errorContainer
                                        val chipFg = MaterialTheme.colorScheme.onErrorContainer
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(chipBg)
                                                .padding(vertical = 4.dp, horizontal = 6.dp)
                                                .then(if (dueCount > 0) Modifier.clickable { showDueOnly = !showDueOnly } else Modifier)
                                        ) {
                                            Text(
                                                dueCount.toString(),
                                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                                color = chipFg,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                items(accounts, key = { it.id }) { acc ->
                // Collect txns for this account to compute totals live
                val txns by remember(acc.id) { vm.txns(acc.id) }.collectAsState(initial = emptyList())
                val credit = remember(txns) { txns.filter { it.isCredit }.sumOf { it.amount } }
                val debit = remember(txns) { txns.filter { !it.isCredit }.sumOf { it.amount } }
                val balance = remember(credit, debit) { credit - debit }
                if (showDueOnly && balance >= 0.0) return@items
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenAccount(acc.id) },
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(Modifier.padding(12.dp)) {
                        // First row: account name + chart + menu
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                acc.name,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f)
                            )
                            // Quick Add button
                            Box(modifier = Modifier.wrapContentSize(Alignment.TopStart)) {
                                IconButton(onClick = { quickAddMenuFor = acc.id }) {
                                    Icon(Icons.Filled.Add, contentDescription = "Quick Add")
                                }
                                // Quick Add menu
                                val accountTxns by remember(acc.id) { vm.txns(acc.id) }.collectAsState(initial = emptyList())
                                val historyTemplates = remember(accountTxns) {
                                    accountTxns
                                        .filter { !it.isCredit } // Only debit transactions for common expenses
                                        .filter { it.note?.isNotBlank() == true }
                                        .groupBy { it.note!! } // Group by note
                                        .mapValues { (_, txns) -> txns.first().category } // Take the most recent category for each note
                                        .entries
                                        .sortedByDescending { accountTxns.indexOfFirst { txn -> txn.note == it.key } } // Sort by most recent
                                        .take(5) // Limit to 5 most recent unique notes
                                        .associate { it.key to it.value } // Convert to map of note -> category
                                }

                                DropdownMenu(
                                    expanded = quickAddMenuFor == acc.id,
                                    onDismissRequest = { quickAddMenuFor = null },
                                    modifier = Modifier.width(200.dp)
                                ) {
                                    // Enhanced menu header with bold title
                                    Text(
                                        text = "Quick Add",
                                        style = MaterialTheme.typography.titleSmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 16.sp
                                        ),
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                                    )
                                    // Enhanced divider with better styling
                                    HorizontalDivider(modifier = Modifier.padding(horizontal = 12.dp))
                                    
                                    // Menu items with reduced padding
                                    historyTemplates.entries.forEachIndexed { index, entry ->
                                        val (note, category) = entry
                                        DropdownMenuItem(
                                            text = { Text(text = note) },
                                            onClick = {
                                                selectedQuickTemplate = note
                                                quickAddMenuFor = null
                                                quickAddFor = acc.id // Trigger amount dialog
                                            },
                                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp) // Reduced vertical padding
                                        )
                                    }
                                }
                            }
                            // Chart icon to open monthly comparison
                            IconButton(onClick = { chartFor = acc.id }) {
                                Icon(Icons.Filled.BarChart, contentDescription = "Chart")
                            }
                            var menuOpen by remember(acc.id) { mutableStateOf(false) }
                            Box(modifier = Modifier.wrapContentSize(Alignment.TopStart)) {
                                IconButton(onClick = { menuOpen = true }) {
                                    Icon(Icons.Default.MoreVert, contentDescription = null)
                                }
                                DropdownMenu(
                                    expanded = menuOpen,
                                    onDismissRequest = { menuOpen = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.add_to_book)) },
                                        onClick = { menuOpen = false; onAddToBook(acc.id) }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.edit)) },
                                        onClick = {
                                            menuOpen = false
                                            renameFor = acc.id
                                            renameText = acc.name
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.export_to_pdf)) },
                                        onClick = {
                                            menuOpen = false
                                            PdfShare.exportAccount(ctx, acc.name, txns)
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.export_to_excel)) },
                                        onClick = {
                                            menuOpen = false
                                            ExcelShare.exportAccountXlsx(ctx, acc.name, txns, showCategory = showCategory)
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.delete_user)) },
                                        onClick = {
                                            menuOpen = false
                                            confirmDeleteFor = acc.id
                                        }
                                    )
                                    // Keep other menu items; chart now has its own icon
                                }
                            }
                        }
                        HorizontalDivider()
                        Spacer(Modifier.height(8.dp))
                        // Second row: Credit, Debit, Balance (chip)
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top
                        ) {
                            // Credit block
                            Column(Modifier.weight(1f)) {
                                Text(stringResource(R.string.credit), style = MaterialTheme.typography.labelSmall)
                                Text(Currency.inr(credit), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                            }
                            // Debit block
                            Column(Modifier.weight(1f)) {
                                Text(stringResource(R.string.debit), style = MaterialTheme.typography.labelSmall)
                                Text(Currency.inr(debit), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                            }
                            // Balance block styled like ledgerbook (rounded Box)
                            Column(Modifier.weight(1.4f)) {
                                Text(stringResource(R.string.balance), style = MaterialTheme.typography.labelSmall)
                                val pos = balance >= 0
                                val chipBg = if (pos) Color(0xFFDFF6DD) else MaterialTheme.colorScheme.errorContainer
                                val chipFg = if (pos) Color(0xFF0B6A0B) else MaterialTheme.colorScheme.onErrorContainer
                                Box(
                                    modifier = Modifier
                                        .defaultMinSize(minWidth = 112.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(chipBg)
                                        .padding(vertical = 4.dp, horizontal = 6.dp)
                                ) {
                                    Text(
                                        Currency.inr(balance),
                                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                        color = chipFg,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                        val totalTx = (credit + debit).coerceAtLeast(0.0)
                        if (totalTx > 0.0) {
                            Spacer(Modifier.height(6.dp))
                            val creditFrac = (credit / totalTx).toFloat().coerceIn(0f, 1f)
                            val debitFrac = (debit / totalTx).toFloat().coerceIn(0f, 1f)
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(2.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                Row(Modifier.fillMaxSize()) {
                                    if (creditFrac > 0f) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxHeight()
                                                .weight(creditFrac)
                                                .background(Color(0xFF10B981))
                                        )
                                    }
                                    if (debitFrac > 0f) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxHeight()
                                                .weight(debitFrac)
                                                .background(Color(0xFFEF4444))
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                }
            }

    val openChartFor = chartFor
    if (openChartFor != null) {
        val txns by remember(openChartFor) { vm.txns(openChartFor) }.collectAsState(initial = emptyList())
        data class MonthAgg(val label: String, val credit: Double, val debit: Double)
        val monthData = remember(txns) {
            val cal = java.util.Calendar.getInstance()
            // Move to first day of current month
            cal.set(java.util.Calendar.DAY_OF_MONTH, 1)
            cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
            cal.set(java.util.Calendar.MINUTE, 0)
            cal.set(java.util.Calendar.SECOND, 0)
            cal.set(java.util.Calendar.MILLISECOND, 0)
            val fmt = java.text.SimpleDateFormat("MMM")
            (5 downTo 0).map { back ->
                val c = cal.clone() as java.util.Calendar
                c.add(java.util.Calendar.MONTH, -back)
                val start = c.timeInMillis
                val label = fmt.format(java.util.Date(start))
                val c2 = c.clone() as java.util.Calendar
                c2.add(java.util.Calendar.MONTH, 1)
                val end = c2.timeInMillis
                val monthTx = txns.filter { it.date in start until end }
                val cr = monthTx.filter { it.isCredit }.sumOf { it.amount }
                val dr = monthTx.filter { !it.isCredit }.sumOf { it.amount }
                MonthAgg(label, cr, dr)
            }
        }
        val maxTotal = remember(monthData) { monthData.maxOfOrNull { (it.credit + it.debit).coerceAtLeast(0.0) }?.takeIf { it > 0.0 } ?: 1.0 }
        // Category split (debit-only) aggregates
        var catRange by rememberSaveable { mutableStateOf(0) } // 0=this month,1=3 months,2=6 months
        val catFilteredTxns = remember(txns, catRange) {
            val now = java.util.Calendar.getInstance()
            val startCal = now.clone() as java.util.Calendar
            startCal.set(java.util.Calendar.DAY_OF_MONTH, 1)
            startCal.set(java.util.Calendar.HOUR_OF_DAY, 0)
            startCal.set(java.util.Calendar.MINUTE, 0)
            startCal.set(java.util.Calendar.SECOND, 0)
            startCal.set(java.util.Calendar.MILLISECOND, 0)
            val monthsBack = when (catRange) { 1 -> 2; 2 -> 5; else -> 0 } // inclusive current month
            startCal.add(java.util.Calendar.MONTH, -monthsBack)
            val startMillis = startCal.timeInMillis
            txns.filter { it.date >= startMillis }
        }
        val catGroups = remember(catFilteredTxns) {
            catFilteredTxns.filter { !it.isCredit }.groupBy { it.category?.takeIf { s -> s.isNotBlank() } ?: "" }
                .mapValues { (_, list) -> list.sumOf { it.amount } }
        }
        val catTotal = remember(catGroups) { catGroups.values.sum() }
        val catEntries = remember(catGroups) { catGroups.toList().sortedByDescending { it.second } }
        val catColors = listOf(
            Color(0xFF6366F1), Color(0xFFF59E0B), Color(0xFF10B981), Color(0xFFEF4444), Color(0xFF3B82F6),
            Color(0xFFEC4899), Color(0xFF8B5CF6), Color(0xFF14B8A6), Color(0xFFA3E635), Color(0xFF06B6D4)
        )

        var tab by rememberSaveable { mutableStateOf(0) }
        AlertDialog(
            onDismissRequest = { chartFor = null },
            title = { Text(stringResource(R.string.expense_split)) },
            confirmButton = { TextButton(onClick = { chartFor = null }) { Text(stringResource(R.string.ok)) } },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    TabRow(selectedTabIndex = tab) {
                        Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text(stringResource(R.string.category_split)) })
                        Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text(stringResource(R.string.last_6_months)) })
                    }
                    if (tab == 1) {
                        if (monthData.all { it.credit + it.debit <= 0.0 }) {
                            Text(stringResource(R.string.no_data_last_6_months))
                        } else {
                            val barHeight = 220.dp
                            val green = Color(0xFF10B981)
                            val red = Color(0xFFEF4444)
                            val onSurfaceArgb = MaterialTheme.colorScheme.onSurface.toArgb()
                            val density = LocalDensity.current
                            val leftPad = with(density) { 8.dp.toPx() }
                            val rightPad = with(density) { 8.dp.toPx() }
                            var selectedIndex by rememberSaveable { mutableStateOf<Int?>(null) }
                            Box {
                                val canvasMod = Modifier
                                    .fillMaxWidth()
                                    .height(barHeight)
                                    .pointerInput(monthData, maxTotal) {
                                        detectTapGestures { offset ->
                                            val totalW = size.width
                                            val h = size.height
                                            val w = (totalW - leftPad - rightPad).coerceAtLeast(1f)
                                            val count = monthData.size
                                            val gap = w / (count * 5f)
                                            val barW = w / (count * 2.5f)
                                            var x = leftPad + gap
                                            var hit: Int? = null
                                            for (i in 0 until count) {
                                                if (offset.x in x..(x + barW)) { hit = i; break }
                                                x += barW + gap
                                            }
                                            selectedIndex = hit
                                        }
                                    }
                                Canvas(modifier = canvasMod) {
                                    val count = monthData.size
                                    val totalW = size.width
                                    val h = size.height
                                    val w = (totalW - leftPad - rightPad).coerceAtLeast(1f)
                                    val gap = w / (count * 5f)
                                    val barW = w / (count * 2.5f)
                                    var x = leftPad + gap
                                    monthData.forEachIndexed { idx, m ->
                                        val total = (m.credit + m.debit).toFloat()
                                        val scale = (total / maxTotal.toFloat()).coerceIn(0f, 1f)
                                        val barH = h * scale
                                        val creditH = if (total > 0f) barH * (m.credit.toFloat() / total) else 0f
                                        val debitH = if (total > 0f) barH * (m.debit.toFloat() / total) else 0f
                                        val bottomY = h
                                        drawRect(
                                            color = green,
                                            topLeft = androidx.compose.ui.geometry.Offset(x, bottomY - creditH),
                                            size = Size(barW, creditH)
                                        )
                                        drawRect(
                                            color = red,
                                            topLeft = androidx.compose.ui.geometry.Offset(x, bottomY - creditH - debitH),
                                            size = Size(barW, debitH)
                                        )
                                        if (selectedIndex == idx) {
                                            drawRect(
                                                color = Color(onSurfaceArgb),
                                                topLeft = androidx.compose.ui.geometry.Offset(x - 1f, bottomY - barH - 1f),
                                                size = Size(barW + 2f, barH + 2f),
                                                style = Stroke(width = 2f)
                                            )
                                        }
                                        x += barW + gap
                                    }
                                }
                                // values moved below legend (no overlay on chart)
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                monthData.forEach { m -> Text(m.label, style = MaterialTheme.typography.labelSmall) }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Box(Modifier.size(10.dp).clip(RoundedCornerShape(2.dp)).background(Color(0xFF10B981)))
                                    Text(stringResource(R.string.credit), style = MaterialTheme.typography.labelSmall)
                                }
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Box(Modifier.size(10.dp).clip(RoundedCornerShape(2.dp)).background(Color(0xFFEF4444)))
                                    Text(stringResource(R.string.debit), style = MaterialTheme.typography.labelSmall)
                                }
                            }
                            val sel = selectedIndex
                            if (sel != null) {
                                val m = monthData[sel]
                                Spacer(Modifier.height(6.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Text(stringResource(R.string.credit) + ": " + Currency.inr(m.credit), style = MaterialTheme.typography.labelSmall)
                                    Text(stringResource(R.string.debit) + ": " + Currency.inr(m.debit), style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    } else {
                        // Category Split tab content
                        var ddOpen by rememberSaveable { mutableStateOf(false) }
                        val currentLabel = when (catRange) { 1 -> stringResource(R.string.three_months); 2 -> stringResource(R.string.six_months); else -> stringResource(R.string.this_month) }
                        Box {
                            OutlinedButton(
                                onClick = { ddOpen = true },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                colors = ButtonDefaults.outlinedButtonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.width(140.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                    Text(currentLabel, style = MaterialTheme.typography.labelSmall)
                                    Icon(Icons.Filled.ArrowDropDown, contentDescription = null, modifier = Modifier.size(16.dp))
                                }
                            }
                            DropdownMenu(expanded = ddOpen, onDismissRequest = { ddOpen = false }) {
                                DropdownMenuItem(text = { Text(stringResource(R.string.this_month)) }, onClick = { catRange = 0; ddOpen = false })
                                DropdownMenuItem(text = { Text(stringResource(R.string.three_months)) }, onClick = { catRange = 1; ddOpen = false })
                                DropdownMenuItem(text = { Text(stringResource(R.string.six_months)) }, onClick = { catRange = 2; ddOpen = false })
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        if (catEntries.isEmpty() || catTotal <= 0.0) {
                            Text(text = stringResource(R.string.total_debit) + ": 0")
                        } else {
                            val surfaceColor = MaterialTheme.colorScheme.surface
                            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                val chartSize = 220.dp
                                Canvas(modifier = Modifier.size(chartSize)) {
                                    var startAngle = -90f
                                    val gap = 1.5f
                                    catEntries.forEachIndexed { idx, (_, amt) ->
                                        val rawSweep = (amt / catTotal).toFloat() * 360f
                                        val sweep = (rawSweep - gap).coerceAtLeast(0f)
                                        drawArc(
                                            color = catColors[idx % catColors.size],
                                            startAngle = startAngle + gap / 2f,
                                            sweepAngle = sweep,
                                            useCenter = true,
                                            style = Fill
                                        )
                                        startAngle += rawSweep
                                    }
                                    val holeRadius = kotlin.math.min(this.size.width, this.size.height) * 0.45f
                                    drawCircle(color = surfaceColor, radius = holeRadius)
                                }
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(text = String.format("%.0f", catTotal), style = MaterialTheme.typography.titleMedium)
                                    Text(text = stringResource(R.string.total_debit), style = MaterialTheme.typography.labelSmall)
                                }
                            }
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                catEntries.forEachIndexed { idx, (cat, amt) ->
                                    val pct = (amt / catTotal * 100).toFloat()
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Box(
                                                modifier = Modifier
                                                    .size(12.dp)
                                                    .clip(RoundedCornerShape(2.dp))
                                                    .background(catColors[idx % catColors.size])
                                            )
                                            Text(if (cat.isBlank()) stringResource(R.string.uncategorized) else cat, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        }
                                        Text(
                                            text = "${Currency.inr(amt)} (${String.format("%.0f%%", pct)})",
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        )
    }

    // Quick Add from History amount dialog
    val quickAddAccountId = quickAddFor
    if (selectedQuickTemplate != null && quickAddAccountId != null) {
        val accountTxns by remember(quickAddAccountId) { vm.txns(quickAddAccountId) }.collectAsState(initial = emptyList())
        val historyTemplates = remember(accountTxns) {
            accountTxns
                .filter { !it.isCredit } // Only debit transactions for common expenses
                .filter { it.note?.isNotBlank() == true }
                .groupBy { it.note!! } // Group by note
                .mapValues { (_, txns) -> txns.first().category } // Take the most recent category for each note
                .entries
                .sortedByDescending { accountTxns.indexOfFirst { txn -> txn.note == it.key } } // Sort by most recent
                .take(5) // Limit to 5 most recent unique notes
                .associate { it.key to it.value } // Convert to map of note -> category
        }

        // Show amount input dialog for selected template
        AlertDialog(
            onDismissRequest = {
                selectedQuickTemplate = null
                quickAddAmount = ""
                quickAddFor = null
            },
            title = { Text(selectedQuickTemplate!!) },
            confirmButton = {
                TextButton(
                    enabled = quickAddAmount.toDoubleOrNull()?.let { it > 0 } == true,
                    onClick = {
                        val amount = quickAddAmount.toDoubleOrNull() ?: 0.0
                        if (amount > 0) {
                            vm.addTxn(
                                accountId = quickAddAccountId,
                                date = System.currentTimeMillis(),
                                amount = amount,
                                isCredit = false, // Quick add for expenses
                                note = selectedQuickTemplate,
                                attachmentUri = null,
                                category = historyTemplates[selectedQuickTemplate],
                                makeRecurring = false
                            )
                            Toast.makeText(ctx, "Transaction added successfully", Toast.LENGTH_SHORT).show()
                        }
                        selectedQuickTemplate = null
                        quickAddAmount = ""
                        quickAddFor = null
                    }
                ) { Text(stringResource(R.string.save)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    selectedQuickTemplate = null
                    quickAddAmount = ""
                    quickAddFor = null
                }) { Text(stringResource(R.string.cancel)) }
            },
            text = {
                OutlinedTextField(
                    value = quickAddAmount,
                    onValueChange = { input -> quickAddAmount = input.filter { it.isDigit() || it == '.' } },
                    label = { Text("Amount") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        )
    }

    // Rename account dialog
    val toRename = renameFor
    if (toRename != null) {
        AlertDialog(
            onDismissRequest = { renameFor = null },
            confirmButton = {
                TextButton(onClick = {
                    val name = renameText.trim()
                    if (name.isNotEmpty()) vm.renameAccount(toRename, name)
                    renameFor = null
                }) { Text(stringResource(R.string.update)) }
            },
            dismissButton = { TextButton(onClick = { renameFor = null }) { Text(stringResource(R.string.cancel)) } },
            title = { Text(stringResource(R.string.edit)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = renameText,
                        onValueChange = { renameText = it },
                        label = { Text(stringResource(R.string.account_name)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        )
    }

            // Draggable FAB overlay (mirrors LedgerBook)
            val density = LocalDensity.current
            val fabSize = 56.dp
            val edge = 16.dp
            val topInsetPx = with(density) { WindowInsets.statusBars.getTop(this).toFloat() }
            val bottomInsetPx = with(density) { WindowInsets.navigationBars.getBottom(this).toFloat() }
            val bannerReservePx = with(density) { if (showBanner && bannerLoaded) (bannerHeight + 12.dp).toPx() else 0f }
            val maxX = with(density) { (maxWidth - fabSize - edge).toPx() }
            val maxY = with(density) { (maxHeight - fabSize - edge).toPx() } - bottomInsetPx - bannerReservePx
            val minX = with(density) { edge.toPx() }
            val minY = topInsetPx + with(density) { edge.toPx() }
            var offsetX by remember(maxWidth, maxHeight, topInsetPx, bottomInsetPx) { mutableStateOf(maxX.coerceAtLeast(minX)) }
            var offsetY by remember(maxWidth, maxHeight, topInsetPx, bottomInsetPx) { mutableStateOf(maxY.coerceAtLeast(minY)) }

            LaunchedEffect(minY, maxY) {
                offsetY = offsetY.coerceIn(minY, maxY)
            }

            LaunchedEffect(hasRemoveAds) {
                if (hasRemoveAds) {
                    offsetX = maxX.coerceAtLeast(minX)
                    offsetY = maxY.coerceAtLeast(minY)
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.navigationBars)
            ) {
                if (showBanner) {
                    BannerAd(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth(),
                        onLoadState = { ok -> bannerLoaded = ok }
                    )
                }
                FloatingActionButton(
                    onClick = { showAdd = true },
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
                    Icon(Icons.Default.Add, contentDescription = null)
                }
            }
        }
    }

    if (showAdd) {
        AlertDialog(
            onDismissRequest = { showAdd = false },
            confirmButton = {
                TextButton(onClick = {
                    val name = accountName.trim()
                    val ob = openBalanceText.trim().replace(",", "").toDoubleOrNull()
                    if (name.isNotEmpty()) vm.addAccount(name, ob)
                    showAdd = false
                    accountName = ""
                    openBalanceText = ""
                }) { Text(stringResource(R.string.save)) }
            },
            dismissButton = { TextButton(onClick = { showAdd = false }) { Text(stringResource(R.string.cancel)) } },
            title = { Text(stringResource(R.string.add_account)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = accountName,
                        onValueChange = { accountName = it },
                        label = { Text(stringResource(R.string.account_name)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = openBalanceText,
                        onValueChange = { input ->
                            // allow digits, dot and comma
                            openBalanceText = input.filter { it.isDigit() || it == '.' || it == ',' }
                        },
                        label = { Text(stringResource(R.string.open_balance)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                    )
                }
            }
        )
    }

    // Confirm delete user dialog
    val toDelete = confirmDeleteFor
    if (toDelete != null) {
        AlertDialog(
            onDismissRequest = { confirmDeleteFor = null },
            title = { Text(stringResource(R.string.delete_user)) },
            text = { Text(stringResource(R.string.delete_user_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteAccountDeep(toDelete)
                    confirmDeleteFor = null
                }) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = { TextButton(onClick = { confirmDeleteFor = null }) { Text(stringResource(R.string.cancel)) } }
        )
    }
}
