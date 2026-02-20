package com.ledge.splitbook.ui.screens
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.zIndex
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.background
import androidx.compose.material3.LocalMinimumTouchTargetEnforcement
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.ExperimentalFoundationApi
import kotlinx.coroutines.launch
import com.ledge.splitbook.ui.vm.TransactionsViewModel
import com.ledge.splitbook.ui.vm.SettingsViewModel
import com.ledge.splitbook.util.formatAmount
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.ledge.splitbook.BuildConfig

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun TransactionsScreen(
    groupId: Long,
    groupName: String,
    onBack: () -> Unit,
    onEdit: (Long) -> Unit,
    onDelete: (Long) -> Unit,
    viewModel: TransactionsViewModel = hiltViewModel()
) {
    LaunchedEffect(groupId) { viewModel.load(groupId) }
    val ui by viewModel.ui.collectAsState()
    val settingsVm: SettingsViewModel = hiltViewModel()
    val settings by settingsVm.ui.collectAsState()
    val currency = settings.currency
    var detailsForId by remember { mutableStateOf<Long?>(null) }
    var confirmDeleteForId by remember { mutableStateOf<Long?>(null) }
    // Split-by filter shared between AppBar and body (default to All). Internal values: All/Category/Date/Member
    var splitBy by remember { mutableStateOf("All") }
    var splitDdOpen by remember { mutableStateOf(false) }
    var overflowMenuForId by remember { mutableStateOf<Long?>(null) }

    val anyPopupOpen = splitDdOpen || overflowMenuForId != null || detailsForId != null || confirmDeleteForId != null
    val shouldShowAds = !settings.removeAds && !anyPopupOpen

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("$groupName") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = null) } },
                actions = {
                    // Compact Split-by dropdown in AppBar (right)
                    Box(modifier = Modifier.zIndex(1f)) {
                        androidx.compose.material3.TextButton(
                            onClick = { splitDdOpen = true },
                            colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
                                contentColor = androidx.compose.material3.MaterialTheme.colorScheme.onPrimary
                            )
                        ) {
                            val splitLabel = when (splitBy) {
                                "Date" -> stringResource(id = com.ledge.splitbook.R.string.date)
                                "Member" -> stringResource(id = com.ledge.splitbook.R.string.member)
                                "Category" -> stringResource(id = com.ledge.splitbook.R.string.category)
                                else -> stringResource(id = com.ledge.splitbook.R.string.all)
                            }
                            Text(splitLabel, color = androidx.compose.material3.MaterialTheme.colorScheme.onPrimary)
                            androidx.compose.material3.ExposedDropdownMenuDefaults.TrailingIcon(expanded = splitDdOpen)
                        }
                        DropdownMenu(expanded = splitDdOpen, onDismissRequest = { splitDdOpen = false }) {
                            listOf("All", "Category", "Date", "Member").forEach { option ->
                                val label = when (option) {
                                    "Date" -> stringResource(id = com.ledge.splitbook.R.string.date)
                                    "Member" -> stringResource(id = com.ledge.splitbook.R.string.member)
                                    "Category" -> stringResource(id = com.ledge.splitbook.R.string.category)
                                    else -> stringResource(id = com.ledge.splitbook.R.string.all)
                                }
                                DropdownMenuItem(text = { Text(label) }, onClick = { splitDdOpen = false; splitBy = option })
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = androidx.compose.material3.MaterialTheme.colorScheme.primary,
                    titleContentColor = androidx.compose.material3.MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = androidx.compose.material3.MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = androidx.compose.material3.MaterialTheme.colorScheme.onPrimary
                )
            )
        }
        ,
        bottomBar = {
            if (shouldShowAds) {
                AndroidView(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding(),
                    factory = { ctx ->
                        AdView(ctx).apply {
                            adUnitId = if (BuildConfig.USE_TEST_ADS) "ca-app-pub-3940256099942544/6300978111" else "ca-app-pub-2556604347710668/9615145808"
                            setAdSize(AdSize.BANNER)
                            loadAd(AdRequest.Builder().build())
                        }
                    }
                )
            }
        }
    ) { padding ->
        // Group map and keys based on Split-by selection
        val byId = ui.members.associateBy { it.id }
        val grouped: Map<String, List<com.ledge.splitbook.data.entity.ExpenseEntity>> = when (splitBy) {
            "All" -> emptyMap()
            "Date" -> ui.expenses.groupBy { it.createdAt ?: "—" }
            "Member" -> ui.expenses.groupBy { byId[it.paidByMemberId]?.name ?: "—" }
            else -> ui.expenses.groupBy { it.category.ifBlank { "Uncategorized" } }
        }
        val computedKeys = grouped.keys.sorted()
        val groupKeys = if (splitBy == "All") emptyList() else if (computedKeys.isEmpty()) listOf("All") else computedKeys
        var selectedIndex by remember(groupKeys) { mutableStateOf(0) }
        val pagerState = rememberPagerState(initialPage = 0, pageCount = { if (groupKeys.isEmpty()) 1 else groupKeys.size })
        val scope = rememberCoroutineScope()
        LaunchedEffect(groupKeys) { selectedIndex = 0; scope.launch { pagerState.scrollToPage(0) } }
        LaunchedEffect(pagerState.currentPage) { if (groupKeys.isNotEmpty()) selectedIndex = pagerState.currentPage }

        Column(
            modifier = Modifier
                .fillMaxSize()
                // Apply only vertical insets so the header strip can span edge-to-edge horizontally
                .padding(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding())
        ) {
            // Centered single tab title with partial previous/next titles visible
            if (groupKeys.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(color = androidx.compose.material3.MaterialTheme.colorScheme.primary)
                        .padding(vertical = 6.dp)
                ) {
                    val prev = if (selectedIndex > 0) groupKeys[selectedIndex - 1] else null
                    val curr = groupKeys[selectedIndex]
                    val next = if (selectedIndex < groupKeys.lastIndex) groupKeys[selectedIndex + 1] else null

                    // Left: previous title at extreme start, tap to go prev
                    if (prev != null) {
                        val prevLabel = if (prev == "All") stringResource(id = com.ledge.splitbook.R.string.all) else prev
                        Text(
                            prevLabel,
                            color = androidx.compose.material3.MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f),
                            maxLines = 1,
                            textAlign = TextAlign.Start,
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .clickable { scope.launch { pagerState.animateScrollToPage(selectedIndex - 1) } }
                        )
                    }

                    // Center: current title with white underline
                    Column(modifier = Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                        val currLabel = if (curr == "All") stringResource(id = com.ledge.splitbook.R.string.all) else curr
                        Text(
                            currLabel,
                            fontWeight = FontWeight.SemiBold,
                            color = androidx.compose.material3.MaterialTheme.colorScheme.onPrimary,
                            maxLines = 1,
                            textAlign = TextAlign.Center
                        )
                        androidx.compose.material3.Divider(
                            modifier = Modifier.padding(top = 4.dp).fillMaxWidth(0.2f),
                            color = androidx.compose.material3.MaterialTheme.colorScheme.onPrimary,
                            thickness = 2.dp
                        )
                    }
                    // Right: next title at extreme end, tap to go next
                    if (next != null) {
                        val nextLabel = if (next == "All") stringResource(id = com.ledge.splitbook.R.string.all) else next
                        Text(
                            nextLabel,
                            color = androidx.compose.material3.MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f),
                            maxLines = 1,
                            textAlign = TextAlign.End,
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .clickable { scope.launch { pagerState.animateScrollToPage(selectedIndex + 1) } }
                        )
                    }
                }
            }
            // Pager for full-screen horizontal paging of grouped content
            HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                val key = if (groupKeys.isNotEmpty()) groupKeys[page] else null
                val showing = when {
                    key == null -> ui.expenses
                    key == "All" -> ui.expenses
                    else -> grouped[key].orEmpty()
                }
                val groupTotal = showing.sumOf { it.amount }
                LazyColumn(
                    modifier = Modifier.fillMaxSize()
                ) {
                    item {
                        Card(elevation = CardDefaults.cardElevation(defaultElevation = 1.dp), shape = RectangleShape) {
                            Column {
                                // Top total row
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(stringResource(id = com.ledge.splitbook.R.string.total_amount), fontWeight = FontWeight.SemiBold, color = androidx.compose.material3.MaterialTheme.colorScheme.primary)
                                    Text(formatAmount(groupTotal, currency), fontWeight = FontWeight.SemiBold, color = androidx.compose.material3.MaterialTheme.colorScheme.primary)
                                }
                                // Divider between total and list to appear as one card
                                HorizontalDivider()
                                // Stacked transactions with dividers
                                showing.forEachIndexed { index, e ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { detailsForId = e.id }
                                            .padding(start = 14.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(
                                            modifier = Modifier.weight(1f).padding(end = 12.dp),
                                            verticalArrangement = Arrangement.spacedBy(2.dp)
                                        ) {
                                            val desc = e.note?.takeIf { it.isNotBlank() } ?: e.category
                                            Text(
                                                desc,
                                                fontWeight = FontWeight.SemiBold,
                                                style = androidx.compose.material3.MaterialTheme.typography.bodyMedium
                                            )
                                            // Date and Category line(s)
                                            e.createdAt?.let {
                                                val pretty = com.ledge.splitbook.util.DateFormats.formatExpenseDate(it)
                                                Text(pretty, style = androidx.compose.material3.MaterialTheme.typography.bodySmall, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                            Text(
                                                stringResource(id = com.ledge.splitbook.R.string.category) + ": " + (e.category.ifBlank { stringResource(id = com.ledge.splitbook.R.string.uncategorized) }),
                                                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                                                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }

                                        Column(horizontalAlignment = Alignment.End) {
                                            Text(
                                                formatAmount(e.amount, currency),
                                                modifier = Modifier.padding(bottom = 2.dp),
                                                style = androidx.compose.material3.MaterialTheme.typography.bodyMedium
                                            )
                                            CompositionLocalProvider(LocalMinimumTouchTargetEnforcement provides false) {
                                                IconButton(
                                                    onClick = { overflowMenuForId = e.id },
                                                    modifier = Modifier.size(24.dp)
                                                ) {
                                                    Icon(
                                                        Icons.Default.MoreVert,
                                                        contentDescription = stringResource(id = com.ledge.splitbook.R.string.more),
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }
                                            }
                                            DropdownMenu(
                                                expanded = overflowMenuForId == e.id,
                                                onDismissRequest = { if (overflowMenuForId == e.id) overflowMenuForId = null }
                                            ) {
                                                DropdownMenuItem(
                                                    text = { Text(stringResource(id = com.ledge.splitbook.R.string.edit)) },
                                                    onClick = {
                                                        overflowMenuForId = null
                                                        onEdit(e.id)
                                                    }
                                                )
                                                DropdownMenuItem(
                                                    text = { Text(stringResource(id = com.ledge.splitbook.R.string.delete)) },
                                                    onClick = {
                                                        overflowMenuForId = null
                                                        confirmDeleteForId = e.id
                                                    }
                                                )
                                            }
                                        }
                                    }

                                    if (index != showing.lastIndex) HorizontalDivider()
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    val detailsId = detailsForId
    if (detailsId != null) {
        val e = ui.expenses.firstOrNull { it.id == detailsId }
        if (e != null) {
            val payer = ui.members.firstOrNull { it.id == e.paidByMemberId }?.name ?: "—"
            var sharedBy by remember { mutableStateOf("—") }
            LaunchedEffect(detailsId) {
                sharedBy = viewModel.getSharedByText(detailsId)
            }
            AlertDialog(
                onDismissRequest = { detailsForId = null },
                title = { Text(e.note ?: e.category) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(id = com.ledge.splitbook.R.string.amount))
                            Text(formatAmount(e.amount, currency))
                        }
                        e.createdAt?.let { Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(stringResource(id = com.ledge.splitbook.R.string.date)); Text(it) } }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(stringResource(id = com.ledge.splitbook.R.string.category)); Text(e.category) }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(stringResource(id = com.ledge.splitbook.R.string.expense_by)); Text(payer) }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(stringResource(id = com.ledge.splitbook.R.string.shared_by)); Text(sharedBy.lowercase().replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }) }
                    }
                },
                confirmButton = { TextButton(onClick = { detailsForId = null }) { Text(stringResource(id = com.ledge.splitbook.R.string.close)) } }
            )
        } else {
            detailsForId = null
        }
    }

    // Confirm delete dialog
    val delId = confirmDeleteForId
    if (delId != null) {
        AlertDialog(
            onDismissRequest = { confirmDeleteForId = null },
            title = { Text(stringResource(id = com.ledge.splitbook.R.string.delete_transaction_q)) },
            text = { Text(stringResource(id = com.ledge.splitbook.R.string.action_cannot_be_undone)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteExpense(delId)
                    confirmDeleteForId = null
                    onDelete(delId)
                }) { Text(stringResource(id = com.ledge.splitbook.R.string.delete)) }
            },
            dismissButton = { TextButton(onClick = { confirmDeleteForId = null }) { Text(stringResource(id = com.ledge.splitbook.R.string.cancel)) } }
        )
    }
}
