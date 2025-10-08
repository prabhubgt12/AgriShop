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
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.foundation.layout.Box
import com.ledge.cashbook.util.PdfShare
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
    var showAdd by remember { mutableStateOf(false) }
    var accountName by remember { mutableStateOf("") }
    var openBalanceText by remember { mutableStateOf("") }
    val ctx = LocalContext.current
    var confirmDeleteFor by remember { mutableStateOf<Int?>(null) }
    var renameFor by remember { mutableStateOf<Int?>(null) }
    var renameText by remember { mutableStateOf("") }

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
                                        text = { Text(stringResource(R.string.delete_user)) },
                                        onClick = {
                                            menuOpen = false
                                            confirmDeleteFor = acc.id
                                        }
                                    )
                                }
                            }
                        }
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
                                val chipBg = if (pos) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.errorContainer
                                val chipFg = if (pos) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onErrorContainer
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
                    }
                }
                }
                // Bottom spacer so banner doesn't cover list content
                if (!hasRemoveAds) {
                    item { Spacer(Modifier.height(84.dp)) }
                }
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
