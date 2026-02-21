package com.ledge.splitbook.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.Spacer
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ledge.splitbook.domain.SettlementLogic
import com.ledge.splitbook.util.ShareExport
import com.ledge.splitbook.ui.vm.SettleViewModel
import androidx.compose.material3.Divider
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import com.ledge.splitbook.ui.vm.SettingsViewModel
import com.ledge.splitbook.ui.vm.TripPlanViewModel
import com.ledge.splitbook.util.formatAmount
import com.ledge.splitbook.ui.components.AddMemberDialog
import kotlin.math.abs
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.ledge.splitbook.BuildConfig

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettleScreen(
    groupId: Long,
    groupName: String,
    onAddExpense: (Long?) -> Unit,
    onOpenTransactions: () -> Unit,
    onOpenSettleDetails: () -> Unit,
    onManageMembers: () -> Unit,
    onOpenTripPlan: () -> Unit,
    onBack: () -> Unit,
    viewModel: SettleViewModel = hiltViewModel()
) {
    LaunchedEffect(groupId) { viewModel.load(groupId) }
    LaunchedEffect(groupId) { viewModel.loadUniqueMemberNames() }
    val ui by viewModel.ui.collectAsState()
    val context = LocalContext.current
    val settingsViewModel = hiltViewModel<SettingsViewModel>()
    val settings by settingsViewModel.ui.collectAsState()
    val currency = settings.currency
    val tripPlanViewModel = hiltViewModel<TripPlanViewModel>()
    val tripPlanUi by tripPlanViewModel.ui.collectAsState()

    LaunchedEffect(groupId) { tripPlanViewModel.loadGroup(groupId) }

    var upiDialog by remember { mutableStateOf<SettlementLogic.Transfer?>(null) }
    var showAddMember by remember { mutableStateOf(false) }
    var showQuickAddMember by remember { mutableStateOf(false) }
    var detailsMember by remember { mutableStateOf<com.ledge.splitbook.data.entity.MemberEntity?>(null) }
    var editMember by remember { mutableStateOf<com.ledge.splitbook.data.entity.MemberEntity?>(null) }
    var vpa by remember { mutableStateOf(TextFieldValue("")) }
    var payeeName by remember { mutableStateOf(TextFieldValue("")) }
    var topMenuOpen by remember { mutableStateOf(false) }
    var chartDdOpen by remember { mutableStateOf(false) }

    val anyPopupOpen = topMenuOpen || chartDdOpen || ui.error != null || showAddMember || showQuickAddMember || detailsMember != null || editMember != null || upiDialog != null
    val bannerVisible = !settings.removeAds && !anyPopupOpen
    // Transfers moved to a dedicated screen

    // Error alert for operations like delete not allowed
    if (ui.error != null) {
        val msg = when (ui.error) {
            "REMOVE_USED" -> stringResource(id = com.ledge.splitbook.R.string.cannot_remove_used)
            else -> ui.error ?: ""
        }
        AlertDialog(
            onDismissRequest = { viewModel.clearError() },
            title = { Text(stringResource(id = com.ledge.splitbook.R.string.action_not_allowed)) },
            text = { Text(msg) },
            confirmButton = { TextButton(onClick = { viewModel.clearError() }) { Text(stringResource(id = com.ledge.splitbook.R.string.ok)) } }
        )
    }

    if (showAddMember) {
        val hasAdmin = ui.members.any { it.isAdmin }
        AddMemberDialog(
            onDismiss = { showAddMember = false },
            onAdd = { name, depStr, isAdmin ->
                val trimmed = name.trim()
                val deposit = depStr.toDoubleOrNull() ?: 0.0
                if (trimmed.isNotEmpty()) viewModel.addMember(trimmed, deposit, isAdmin)
                showAddMember = false
            },
            showAdminOption = !hasAdmin,
            adminRequired = !hasAdmin
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (groupName.isNotBlank()) groupName else stringResource(id = com.ledge.splitbook.R.string.group)) },
                actions = {
                    IconButton(onClick = { topMenuOpen = true }) { Icon(Icons.Default.MoreVert, contentDescription = stringResource(id = com.ledge.splitbook.R.string.more)) }
                    DropdownMenu(expanded = topMenuOpen, onDismissRequest = { topMenuOpen = false }) {
                        DropdownMenuItem(text = { Text(stringResource(id = com.ledge.splitbook.R.string.share_as_text)) }, onClick = {
                            topMenuOpen = false
                            val groupName = context.getString(com.ledge.splitbook.R.string.group_with_id, ui.groupId)
                            val summary = ShareExport.buildTextSummary(
                                context = context,
                                groupName = groupName,
                                members = ui.members,
                                expenses = ui.expenses,
                                memberSummaries = ui.memberSummaries,
                                currency = currency
                            )
                            ShareExport.shareText(context, summary)
                        })
                        DropdownMenuItem(text = { Text(stringResource(id = com.ledge.splitbook.R.string.export_pdf)) }, onClick = {
                            topMenuOpen = false
                            val groupName = groupName
                            val uri = ShareExport.exportPdf(
                                context = context,
                                groupName = groupName,
                                members = ui.members,
                                expenses = ui.expenses,
                                memberSummaries = ui.memberSummaries,
                                currency = currency
                            )
                            ShareExport.shareFile(context, uri, "application/pdf")
                        })
                        DropdownMenuItem(text = { Text(stringResource(id = com.ledge.splitbook.R.string.export_excel)) }, onClick = {
                            topMenuOpen = false
                            val groupName = context.getString(com.ledge.splitbook.R.string.group_with_id, ui.groupId)
                            val uri = ShareExport.exportExcel(
                                context = context,
                                groupName = groupName,
                                members = ui.members,
                                expenses = ui.expenses,
                                memberSummaries = ui.memberSummaries,
                                currency = currency
                            )
                            ShareExport.shareFile(context, uri, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                        })
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = androidx.compose.material3.MaterialTheme.colorScheme.primary,
                    titleContentColor = androidx.compose.material3.MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = androidx.compose.material3.MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = androidx.compose.material3.MaterialTheme.colorScheme.onPrimary
                )
            )
        },
        bottomBar = {
            if (bannerVisible) {
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
        if (ui.isLoading) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                androidx.compose.material3.CircularProgressIndicator()
            }
            return@Scaffold
        }
        val listPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = 12.dp,
            bottom = 24.dp
        )
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = listPadding,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Members card (all members inside one card)
            item {
                Card(elevation = CardDefaults.cardElevation(defaultElevation = 1.dp), shape = androidx.compose.foundation.shape.RoundedCornerShape(6.dp)) {
                    Column(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(color = androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant, shape = androidx.compose.material3.MaterialTheme.shapes.small),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                stringResource(id = com.ledge.splitbook.R.string.members),
                                style = androidx.compose.material3.MaterialTheme.typography.titleSmall,
                                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Download,
                                    contentDescription = stringResource(id = com.ledge.splitbook.R.string.cd_quick_add_members),
                                    modifier = Modifier
                                        .size(22.dp)
                                        .clickable { showQuickAddMember = true }
                                )
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = stringResource(id = com.ledge.splitbook.R.string.add_member),
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clickable { showAddMember = true }
                                )
                            }
                        }
                        androidx.compose.material3.HorizontalDivider()
                        val totalDepositsByMembers = ui.members.filter { !it.isAdmin }.sumOf { it.deposit }
                        ui.members.forEachIndexed { idx, m ->
                            val net = ui.nets[m.id] ?: 0.0
                            androidx.compose.foundation.layout.Row(
                                modifier = Modifier.fillMaxWidth().clickable { detailsMember = m },
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    AvatarCircle(name = m.name)
                                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Text(if (m.isAdmin) m.name + " (" + stringResource(id = com.ledge.splitbook.R.string.admin) + ")" else m.name, fontWeight = FontWeight.Medium)
                                        val summary = ui.memberSummaries.firstOrNull { it.memberId == m.id }
                                        val paid = summary?.amountPaid ?: 0.0
                                        val shared = summary?.expenseShared ?: 0.0
                                        val dep = if (m.isAdmin) totalDepositsByMembers else m.deposit
                                        val dueBase = if (m.isAdmin) -totalDepositsByMembers else m.deposit
                                        val due = dueBase + paid - shared
                                        val dueColor = if (due >= 0) Color(0xFF16A34A) else Color(0xFFDC2626)
                                        Text(
                                            stringResource(id = com.ledge.splitbook.R.string.due_amount) + ": " + (formatAmount(kotlin.math.abs(due), currency).let { if (due >= 0) "+$it" else "-$it" }),
                                            color = dueColor,
                                            style = androidx.compose.material3.MaterialTheme.typography.bodySmall
                                        )
                                        val paidStr = formatAmount(paid, currency)
                                        Text(stringResource(id = com.ledge.splitbook.R.string.amount_spent) + ": " + paidStr, style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
                                    }
                                }
                                IconButton(onClick = { onAddExpense(m.id) }) { Icon(Icons.Default.Add, contentDescription = stringResource(id = com.ledge.splitbook.R.string.add_expense_for_member)) }
                            }
                            if (idx != ui.members.lastIndex) {
                                Divider(modifier = Modifier.padding(vertical = 8.dp))
                            }
                        }
                    }
                }
            }

            // Summary: separate cards with minimal gap to appear stacked
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    // Total
                    Card(
                        modifier = Modifier.clickable { onOpenTransactions() },
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(6.dp)
                    ) {
                        androidx.compose.foundation.layout.Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            val total = ui.expenses.sumOf { it.amount }
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    stringResource(id = com.ledge.splitbook.R.string.total),
                                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    formatAmount(total, currency),
                                    style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            Icon(Icons.Default.ChevronRight, contentDescription = null)
                        }
                    }

                    // Trip Plan
                    Card(
                        modifier = Modifier.clickable { onOpenTripPlan() },
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(6.dp)
                    ) {
                        androidx.compose.foundation.layout.Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            val summary = if (tripPlanUi.days.isEmpty()) {
                                stringResource(id = com.ledge.splitbook.R.string.no_plan_added)
                            } else {
                                val days = tripPlanUi.days.size
                                val places = tripPlanUi.days.sumOf { it.places.size }
                                stringResource(id = com.ledge.splitbook.R.string.days_places, days, places)
                            }
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    stringResource(id = com.ledge.splitbook.R.string.trip_plan),
                                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    summary,
                                    style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            Icon(Icons.Default.ChevronRight, contentDescription = null)
                        }
                    }

                    // Expense donut chart
                    Card(elevation = CardDefaults.cardElevation(defaultElevation = 1.dp), shape = androidx.compose.foundation.shape.RoundedCornerShape(6.dp)) {
                        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            // Title row with compact Split-by filter at right
                            var splitBy by remember { mutableStateOf("Category") }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(stringResource(id = com.ledge.splitbook.R.string.expense_chart))
                                androidx.compose.foundation.layout.Box(modifier = Modifier) {
                                    androidx.compose.material3.TextButton(
                                        onClick = { chartDdOpen = true },
                                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                                    ) {
                                        val splitLabel = when (splitBy) {
                                            "Date" -> stringResource(id = com.ledge.splitbook.R.string.date)
                                            "Member" -> stringResource(id = com.ledge.splitbook.R.string.member)
                                            else -> stringResource(id = com.ledge.splitbook.R.string.category)
                                        }
                                        Text(splitLabel)
                                        androidx.compose.material3.ExposedDropdownMenuDefaults.TrailingIcon(expanded = chartDdOpen)
                                    }
                                    androidx.compose.material3.DropdownMenu(expanded = chartDdOpen, onDismissRequest = { chartDdOpen = false }) {
                                        val options = listOf(
                                            "Category" to stringResource(id = com.ledge.splitbook.R.string.category),
                                            "Date" to stringResource(id = com.ledge.splitbook.R.string.date),
                                            "Member" to stringResource(id = com.ledge.splitbook.R.string.member)
                                        )
                                        options.forEach { (value, label) ->
                                            androidx.compose.material3.DropdownMenuItem(
                                                text = { Text(label) },
                                                onClick = { splitBy = value; chartDdOpen = false }
                                            )
                                        }
                                    }
                                }
                            }

                            val byId = ui.members.associateBy { it.id }
                            val groups = when (splitBy) {
                                "Date" -> ui.expenses.groupBy { it.createdAt ?: "—" }
                                "Member" -> ui.expenses.groupBy { byId[it.paidByMemberId]?.name ?: "—" }
                                else -> ui.expenses.groupBy { it.category.ifBlank { stringResource(id = com.ledge.splitbook.R.string.uncategorized) } }
                            }
                            val totals = groups.mapValues { (_, list) -> list.sumOf { it.amount } }
                            val grand = totals.values.sum()
                            val slices = totals.entries.sortedByDescending { it.value }
                            val colors = listOf(
                                Color(0xFF6366F1), // indigo
                                Color(0xFFF59E0B), // amber
                                Color(0xFF10B981), // emerald
                                Color(0xFFEF4444), // red
                                Color(0xFF8B5CF6), // violet
                                Color(0xFF06B6D4), // cyan
                                Color(0xFFEAB308)  // yellow
                            )
                            val strokeWidth = 22.dp
                            Column(
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                // Donut with centered total
                                Box(modifier = Modifier.size(200.dp), contentAlignment = Alignment.Center) {
                                    Canvas(modifier = Modifier.matchParentSize()) {
                                        val diameter = size.minDimension
                                        val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
                                        var startAngle = -90f
                                        val sw = strokeWidth.toPx()
                                        slices.forEachIndexed { idx, e ->
                                            val sweep = if (grand <= 0.0) 0f else (e.value / grand * 360f).toFloat()
                                            if (sweep > 0f) {
                                                drawArc(
                                                    color = colors[idx % colors.size],
                                                    startAngle = startAngle,
                                                    sweepAngle = sweep,
                                                    useCenter = false,
                                                    topLeft = topLeft,
                                                    size = Size(diameter, diameter),
                                                    style = Stroke(width = sw, cap = StrokeCap.Round)
                                                )
                                                startAngle += sweep
                                            }
                                        }
                                    }
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(formatAmount(grand, currency), fontWeight = FontWeight.Medium)
                                        Text(stringResource(id = com.ledge.splitbook.R.string.total), style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
                                    }
                                }
                                // Legend below chart, compact text
                                Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    slices.forEachIndexed { idx, e ->
                                        val amount = e.value
                                        val pct = if (grand <= 0.0) 0 else (amount / grand * 100).toInt()
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                                androidx.compose.foundation.layout.Box(
                                                    modifier = Modifier.size(10.dp).clip(CircleShape).background(colors[idx % colors.size])
                                                )
                                                Text(e.key, maxLines = 1, style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
                                            }
                                            Text("${formatAmount(amount, currency)} (${pct}%)", style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Settle up
                    Card(
                        modifier = Modifier.clickable { onOpenSettleDetails() },
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(6.dp)
                    ) {
                        androidx.compose.foundation.layout.Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            // Show pending settlements based on computed transfers (tolerant to rounding),
                            // instead of raw nets which may have tiny residuals after 'Settle All'.
                            val pendingTransfers = ui.transfers
                            val transfersCount = pendingTransfers.size
                            val totalToSettle = pendingTransfers.sumOf { it.amount }
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(stringResource(id = com.ledge.splitbook.R.string.settle_up))
                                Text(
                                    stringResource(id = com.ledge.splitbook.R.string.total) + ": " + formatAmount(totalToSettle, currency),
                                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Icon(Icons.Default.ChevronRight, contentDescription = null)
                        }
                    }
                }
            }

            if (bannerVisible) {
                item { Spacer(modifier = Modifier.height(88.dp)) }
            }
        }
    }

    if (showQuickAddMember) {
        val existingNames = remember(ui.members) {
            ui.members.map { it.name.trim() }.filter { it.isNotEmpty() }.toSet()
        }
        val candidates = remember(ui.uniqueMemberNames, existingNames) {
            ui.uniqueMemberNames
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .filter { it !in existingNames }
        }
        val hasAdmin = ui.members.any { it.isAdmin }
        val selected = remember(candidates) { mutableStateMapOf<String, Boolean>() }
        LaunchedEffect(candidates) {
            candidates.forEach { name ->
                if (!selected.containsKey(name)) selected[name] = false
            }
        }

        AlertDialog(
            onDismissRequest = { showQuickAddMember = false },
            title = { Text(stringResource(com.ledge.splitbook.R.string.quick_add_members_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(com.ledge.splitbook.R.string.quick_add_members_note))

                    when {
                        ui.isUniqueNamesLoading -> {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center
                            ) {
                                CircularProgressIndicator()
                            }
                        }

                        candidates.isEmpty() -> {
                            Text(stringResource(com.ledge.splitbook.R.string.quick_add_members_empty))
                        }

                        else -> {
                            LazyColumn(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(candidates) { name ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Checkbox(
                                            checked = selected[name] == true,
                                            onCheckedChange = { checked -> selected[name] = checked }
                                        )
                                        Text(name)
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                val chosen = selected.filterValues { it }.keys.toList()
                TextButton(
                    enabled = chosen.isNotEmpty() && !ui.isUniqueNamesLoading,
                    onClick = {
                        val trimmed = chosen.map { it.trim() }.filter { it.isNotEmpty() }
                        if (trimmed.isNotEmpty()) {
                            if (!hasAdmin) {
                                val first = trimmed.first()
                                viewModel.addMember(first, 0.0, true)
                                trimmed.drop(1).forEach { n -> viewModel.addMember(n, 0.0, false) }
                            } else {
                                trimmed.forEach { n -> viewModel.addMember(n, 0.0, false) }
                            }
                        }
                        showQuickAddMember = false
                    }
                ) {
                    Text(stringResource(com.ledge.splitbook.R.string.add))
                }
            },
            dismissButton = {
                TextButton(onClick = { showQuickAddMember = false }) {
                    Text(stringResource(com.ledge.splitbook.R.string.cancel))
                }
            }
        )
    }

    // Member details dialog (beautified header, colored amounts, action buttons with dividers)
    if (detailsMember != null) {
        val m = detailsMember!!
        val summary = ui.memberSummaries.firstOrNull { it.memberId == m.id }
        val paid = summary?.amountPaid ?: 0.0
        val shared = summary?.expenseShared ?: 0.0
        val totalDepositsByMembers = ui.members.filter { !it.isAdmin }.sumOf { it.deposit }
        val dep = if (m.isAdmin) totalDepositsByMembers else m.deposit
        val dueBase = if (m.isAdmin) -totalDepositsByMembers else m.deposit
        val due = dueBase + paid - shared
        AlertDialog(
            onDismissRequest = { detailsMember = null },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                    // Header bar with avatar and name
                    androidx.compose.foundation.layout.Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                            .background(color = androidx.compose.material3.MaterialTheme.colorScheme.primary)
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            AvatarCircle(name = m.name)
                            Text(if (m.isAdmin) "${m.name} (Admin)" else m.name, color = androidx.compose.material3.MaterialTheme.colorScheme.onPrimary)
                        }
                    }
                    androidx.compose.material3.HorizontalDivider()
                    // Metrics
                    Column(modifier = Modifier.padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(stringResource(id = com.ledge.splitbook.R.string.amount_spent))
                            Text(formatAmount(paid, currency))
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(stringResource(id = com.ledge.splitbook.R.string.amount_share_by))
                            Text(formatAmount(shared, currency))
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            val depLabel = if (m.isAdmin) stringResource(id = com.ledge.splitbook.R.string.group_deposit) else stringResource(id = com.ledge.splitbook.R.string.deposit_amount)
                            Text(depLabel)
                            Text(formatAmount(dep, currency), color = Color(0xFF2563EB))
                        }
                        val dueColor = if (due >= 0) Color(0xFF16A34A) else Color(0xFFDC2626)
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(stringResource(id = com.ledge.splitbook.R.string.due_refund))
                            val dueLabel = formatAmount(kotlin.math.abs(due), currency).let { if (due >= 0) "+$it" else "-$it" }
                            Text(dueLabel, color = dueColor, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        enabled = !m.isAdmin,
                        modifier = Modifier.weight(1f),
                        onClick = { editMember = m; detailsMember = null }
                    ) { Text(stringResource(id = com.ledge.splitbook.R.string.edit), maxLines = 1) }
                    androidx.compose.material3.VerticalDivider(modifier = Modifier.height(24.dp))
                    TextButton(
                        modifier = Modifier.weight(1f),
                        onClick = { detailsMember = null }
                    ) { Text(stringResource(id = com.ledge.splitbook.R.string.close), maxLines = 1) }
                    androidx.compose.material3.VerticalDivider(modifier = Modifier.height(24.dp))
                    TextButton(
                        enabled = !m.isAdmin,
                        modifier = Modifier.weight(1f),
                        onClick = { viewModel.removeMember(m.id); detailsMember = null }
                    ) { Text(stringResource(id = com.ledge.splitbook.R.string.delete), maxLines = 1) }
                }
            }
        )
    }

    // Edit member (name readonly, deposit editable)
    if (editMember != null) {
        val m = editMember!!
        AddMemberDialog(
            onDismiss = { editMember = null },
            onAdd = { _, depStr, isAdmin ->
                // Save edited fields: if setting admin from non-admin, make admin.
                if (isAdmin && !m.isAdmin) {
                    viewModel.makeAdmin(m.id)
                }
                // Update deposit only when member is not admin.
                val newDep = depStr.toDoubleOrNull() ?: 0.0
                if (!isAdmin) {
                    viewModel.updateMemberDeposit(m.id, newDep)
                }
                editMember = null
            },
            initialName = m.name,
            initialDeposit = if (m.deposit == 0.0) "" else String.format("%.2f", m.deposit),
            nameReadOnly = true,
            confirmLabel = stringResource(id = com.ledge.splitbook.R.string.save),
            showAdminOption = true,
            adminRequired = m.isAdmin,
            title = stringResource(id = com.ledge.splitbook.R.string.edit) + " " + stringResource(id = com.ledge.splitbook.R.string.member),
            initialIsAdmin = m.isAdmin
        )
    }

    if (upiDialog != null) {
        AlertDialog(
            onDismissRequest = { upiDialog = null },
            title = { Text(stringResource(id = com.ledge.splitbook.R.string.pay_via_upi)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = vpa, onValueChange = { vpa = it }, label = { Text(stringResource(id = com.ledge.splitbook.R.string.upi_id_vpa)) })
                    OutlinedTextField(value = payeeName, onValueChange = { payeeName = it }, label = { Text(stringResource(id = com.ledge.splitbook.R.string.payee_name)) })
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val t = upiDialog ?: return@TextButton
                    val uri = buildUpiUri(vpa.text, payeeName.text, t.amount, "Simple Split settlement")
                    val intent = Intent(Intent.ACTION_VIEW, uri)
                    context.startActivity(intent)
                    upiDialog = null
                }) { Text(stringResource(id = com.ledge.splitbook.R.string.open_upi)) }
            },
            dismissButton = { TextButton(onClick = { upiDialog = null }) { Text(stringResource(id = com.ledge.splitbook.R.string.cancel)) } }
        )
    }

    // Transactions moved to dedicated screen

}

@Composable
fun AvatarCircle(name: String) {
    val initial = name.trim().take(1).uppercase()
    // Deterministic color from name so different members get different colors
    val palette = listOf(
        0xFFE57373, 0xFFF06292, 0xFFBA68C8, 0xFF9575CD, 0xFF64B5F6,
        0xFF4FC3F7, 0xFF4DB6AC, 0xFF81C784, 0xFFFFB74D, 0xFFA1887F
    ).map { Color(it.toInt()) }
    val idx = (kotlin.math.abs(name.hashCode())) % palette.size
    val bg = palette[idx].copy(alpha = 0.25f)
    val fg = palette[idx].copy(alpha = 1f)
    androidx.compose.foundation.layout.Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(bg),
        contentAlignment = Alignment.Center
    ) {
        Text(initial, color = fg, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun RowActions(onMarkPaid: () -> Unit, onPayUpi: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        FilledTonalButton(onClick = onMarkPaid, modifier = Modifier.fillMaxWidth()) { Text(stringResource(id = com.ledge.splitbook.R.string.mark_as_paid)) }
        OutlinedButton(onClick = onPayUpi, modifier = Modifier.fillMaxWidth()) { Text(stringResource(id = com.ledge.splitbook.R.string.pay_via_upi)) }
    }
}

private fun buildUpiUri(pa: String, pn: String, amount: Double, note: String): Uri {
    val builder = Uri.parse("upi://pay").buildUpon()
        .appendQueryParameter("am", String.format("%.2f", amount))
        .appendQueryParameter("cu", "INR")
        .appendQueryParameter("tn", note)
    if (pa.isNotBlank()) builder.appendQueryParameter("pa", pa)
    if (pn.isNotBlank()) builder.appendQueryParameter("pn", pn)
    return builder.build()
}
