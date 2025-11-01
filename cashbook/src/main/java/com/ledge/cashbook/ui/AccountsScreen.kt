package com.ledge.cashbook.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawStyle
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.foundation.layout.Box
import com.ledge.cashbook.util.PdfShare
import com.ledge.cashbook.util.ExcelShare
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectDragGestures
import kotlin.math.roundToInt
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material.icons.filled.Delete
import androidx.hilt.navigation.compose.hiltViewModel
import com.ledge.cashbook.ads.BannerAd

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
    var showAdd by remember { mutableStateOf(false) }
    var accountName by remember { mutableStateOf("") }
    var openBalanceText by remember { mutableStateOf("") }
    val ctx = LocalContext.current
    var confirmDeleteFor by remember { mutableStateOf<Int?>(null) }
    var renameFor by remember { mutableStateOf<Int?>(null) }
    var renameText by remember { mutableStateOf("") }
    var chartFor by remember { mutableStateOf<Int?>(null) }

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
            // List content
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(accounts, key = { it.id }) { acc ->
                // Collect txns for this account to compute totals live
                val txns by remember(acc.id) { vm.txns(acc.id) }.collectAsState(initial = emptyList())
                val credit = remember(txns) { txns.filter { it.isCredit }.sumOf { it.amount } }
                val debit = remember(txns) { txns.filter { !it.isCredit }.sumOf { it.amount } }
                val balance = remember(credit, debit) { credit - debit }
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenAccount(acc.id) },
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(Modifier.padding(12.dp)) {
                        // First row: account name + menu
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                acc.name,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f)
                            )
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
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.category_split)) },
                                        onClick = {
                                            menuOpen = false
                                            chartFor = acc.id
                                        }
                                    )
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
                // Bottom spacer so banner doesn't cover list content
                if (!hasRemoveAds) {
                    item { Spacer(Modifier.height(84.dp)) }
                }
            }

    // Category split dialog (outside LazyColumn scope)
    val openChartFor = chartFor
    if (openChartFor != null) {
        val txns by remember(openChartFor) { vm.txns(openChartFor) }.collectAsState(initial = emptyList())
        val groups = remember(txns) {
            txns.filter { !it.isCredit }.groupBy { it.category?.takeIf { s -> s.isNotBlank() } ?: "" }
                .mapValues { (_, list) -> list.sumOf { it.amount } }
        }
        val total = remember(groups) { groups.values.sum() }
        AlertDialog(
            onDismissRequest = { chartFor = null },
            title = { Text(stringResource(R.string.category_split)) },
            confirmButton = { TextButton(onClick = { chartFor = null }) { Text(stringResource(R.string.ok)) } },
            text = {
                if (groups.isEmpty() || total <= 0.0) {
                    Text(text = stringResource(R.string.total_debit) + ": 0")
                } else {
                    val entries = groups.toList().sortedByDescending { it.second }
                    // High-contrast, theme-independent palette (works on light/dark)
                    val colors = listOf(
                        Color(0xFF6366F1), // indigo
                        Color(0xFFF59E0B), // amber
                        Color(0xFF10B981), // emerald
                        Color(0xFFEF4444), // red
                        Color(0xFF3B82F6), // blue
                        Color(0xFFEC4899), // pink
                        Color(0xFF8B5CF6), // violet
                        Color(0xFF14B8A6), // teal
                        Color(0xFFA3E635), // lime
                        Color(0xFF06B6D4)  // cyan
                    )
                    val sliceColors = entries.mapIndexed { i, _ -> colors[i % colors.size] }
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        // Pie chart
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            val chartSize = 220.dp
                            val surfaceColor = MaterialTheme.colorScheme.surface
                            Canvas(modifier = Modifier.size(chartSize)) {
                                var startAngle = -90f
                                val gap = 1.5f // degrees between slices for visual separation
                                entries.forEachIndexed { idx, (_, amt) ->
                                    val rawSweep = (amt / total).toFloat() * 360f
                                    val sweep = (rawSweep - gap).coerceAtLeast(0f)
                                    drawArc(
                                        color = sliceColors[idx],
                                        startAngle = startAngle + gap / 2f,
                                        sweepAngle = sweep,
                                        useCenter = true,
                                        style = Fill
                                    )
                                    startAngle += rawSweep
                                }
                                // Donut hole
                                val holeRadius = kotlin.math.min(this.size.width, this.size.height) * 0.45f
                                drawCircle(
                                    color = surfaceColor,
                                    radius = holeRadius
                                )
                            }
                            // Optional center label
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(text = String.format("%.0f", total), style = MaterialTheme.typography.titleMedium)
                                Text(text = stringResource(R.string.total_debit), style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        // Legend
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            entries.forEachIndexed { idx, (cat, amt) ->
                                val pct = (amt / total * 100).toFloat()
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Box(
                                            modifier = Modifier
                                                .size(12.dp)
                                                .clip(RoundedCornerShape(2.dp))
                                                .background(sliceColors[idx])
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
                if (!hasRemoveAds) {
                    BannerAd(modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth())
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
                        modifier = Modifier.fillMaxWidth()
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
