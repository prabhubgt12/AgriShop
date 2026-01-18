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
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
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
import com.ledge.splitbook.util.formatAmount
import com.ledge.splitbook.ui.components.AddMemberDialog
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettleScreen(
    groupId: Long,
    groupName: String,
    onAddExpense: (Long?) -> Unit,
    onOpenTransactions: () -> Unit,
    onOpenSettleDetails: () -> Unit,
    onManageMembers: () -> Unit,
    onBack: () -> Unit,
    viewModel: SettleViewModel = hiltViewModel()
) {
    LaunchedEffect(groupId) { viewModel.load(groupId) }
    val ui by viewModel.ui.collectAsState()
    val context = LocalContext.current
    val settingsViewModel: SettingsViewModel = hiltViewModel()
    val settings by settingsViewModel.ui.collectAsState()
    val currency = settings.currency

    var upiDialog by remember { mutableStateOf<SettlementLogic.Transfer?>(null) }
    var showAddMember by remember { mutableStateOf(false) }
    var detailsMember by remember { mutableStateOf<com.ledge.splitbook.data.entity.MemberEntity?>(null) }
    var editMember by remember { mutableStateOf<com.ledge.splitbook.data.entity.MemberEntity?>(null) }
    var vpa by remember { mutableStateOf(TextFieldValue("")) }
    var payeeName by remember { mutableStateOf(TextFieldValue("")) }
    // Transfers moved to a dedicated screen

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (groupName.isNotBlank()) groupName else "Group") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    var menuOpen by remember { mutableStateOf(false) }
                    IconButton(onClick = { menuOpen = true }) { Icon(Icons.Default.MoreVert, contentDescription = "Menu") }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(text = { Text("Share as Text") }, onClick = {
                            menuOpen = false
                            val groupName = "Group ${ui.groupId}"
                            val summary = ShareExport.buildTextSummary(
                                groupName = groupName,
                                members = ui.members,
                                expenses = ui.expenses,
                                memberSummaries = ui.memberSummaries,
                                currency = currency
                            )
                            ShareExport.shareText(context, summary)
                        })
                        DropdownMenuItem(text = { Text("Export PDF") }, onClick = {
                            menuOpen = false
                            val groupName = "Group ${ui.groupId}"
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
                        DropdownMenuItem(text = { Text("Export Excel") }, onClick = {
                            menuOpen = false
                            val groupName = "Group ${ui.groupId}"
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

    // Error alert for operations like delete not allowed
    if (ui.error != null) {
        AlertDialog(
            onDismissRequest = { viewModel.clearError() },
            title = { Text("Action not allowed") },
            text = { Text(ui.error ?: "") },
            confirmButton = { TextButton(onClick = { viewModel.clearError() }) { Text("OK") } }
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
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = androidx.compose.material3.MaterialTheme.colorScheme.primary,
                    titleContentColor = androidx.compose.material3.MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = androidx.compose.material3.MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = androidx.compose.material3.MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { padding ->
        val listPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = padding.calculateTopPadding() + 12.dp,
            bottom = 24.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + padding.calculateBottomPadding()
        )
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
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
                                "Members",
                                style = androidx.compose.material3.MaterialTheme.typography.titleSmall,
                                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Add member",
                                modifier = Modifier
                                    .size(24.dp)
                                    .clickable { showAddMember = true }
                            )
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
                                        Text(if (m.isAdmin) "${m.name} (Admin)" else m.name, fontWeight = FontWeight.Medium)
                                        val summary = ui.memberSummaries.firstOrNull { it.memberId == m.id }
                                        val paid = summary?.amountPaid ?: 0.0
                                        val shared = summary?.expenseShared ?: 0.0
                                        val dep = if (m.isAdmin) totalDepositsByMembers else m.deposit
                                        val dueBase = if (m.isAdmin) -totalDepositsByMembers else m.deposit
                                        val due = dueBase + paid - shared
                                        val dueColor = if (due >= 0) Color(0xFF16A34A) else Color(0xFFDC2626)
                                        Text(
                                            "Due Amount: ${formatAmount(kotlin.math.abs(due), currency).let { if (due >= 0) "+$it" else "-$it" }}",
                                            color = dueColor,
                                            style = androidx.compose.material3.MaterialTheme.typography.bodySmall
                                        )
                                        val paidStr = formatAmount(paid, currency)
                                        Text("Amount Spent: $paidStr", style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
                                    }
                                }
                                IconButton(onClick = { onAddExpense(m.id) }) { Icon(Icons.Default.Add, contentDescription = "Add expense for member") }
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
                                    "Total",
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

                    // Expense donut chart
                    Card(elevation = CardDefaults.cardElevation(defaultElevation = 1.dp), shape = androidx.compose.foundation.shape.RoundedCornerShape(6.dp)) {
                        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            // Title row with compact Split-by filter at right
                            var splitBy by remember { mutableStateOf("Category") }
                            var ddOpen by remember { mutableStateOf(false) }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Expense Chart")
                                androidx.compose.foundation.layout.Box(modifier = Modifier) {
                                    androidx.compose.material3.TextButton(
                                        onClick = { ddOpen = true },
                                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                                    ) {
                                        Text(splitBy)
                                        androidx.compose.material3.ExposedDropdownMenuDefaults.TrailingIcon(expanded = ddOpen)
                                    }
                                    androidx.compose.material3.DropdownMenu(expanded = ddOpen, onDismissRequest = { ddOpen = false }) {
                                        listOf("Category", "Date", "Member").forEach { option ->
                                            androidx.compose.material3.DropdownMenuItem(
                                                text = { Text(option) },
                                                onClick = { splitBy = option; ddOpen = false }
                                            )
                                        }
                                    }
                                }
                            }

                            val byId = ui.members.associateBy { it.id }
                            val groups = when (splitBy) {
                                "Date" -> ui.expenses.groupBy { it.createdAt ?: "—" }
                                "Member" -> ui.expenses.groupBy { byId[it.paidByMemberId]?.name ?: "—" }
                                else -> ui.expenses.groupBy { it.category.ifBlank { "Uncategorized" } }
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
                                        Text("Total", style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
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
                            val nets = ui.nets.values
                            val receivers = nets.count { it > 0.0 }
                            val payers = nets.count { it < 0.0 }
                            val totalToSettle = nets.filter { it > 0.0 }.sum()
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text("Settle Up")
                                Text(
                                    "Transfers: ${maxOf(receivers, payers)} • Amount: ${formatAmount(totalToSettle, currency)}",
                                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Icon(Icons.Default.ChevronRight, contentDescription = null)
                        }
                    }
                }
            }
        }
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
                            Text("Amount Spent")
                            Text(formatAmount(paid, currency))
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Amount Share By")
                            Text(formatAmount(shared, currency))
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(if (m.isAdmin) "Group Deposit" else "Deposit Amount")
                            Text(formatAmount(dep, currency), color = Color(0xFF2563EB))
                        }
                        val dueColor = if (due >= 0) Color(0xFF16A34A) else Color(0xFFDC2626)
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Due/Refund")
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
                    ) { Text("EDIT", maxLines = 1) }
                    androidx.compose.material3.VerticalDivider(modifier = Modifier.height(24.dp))
                    TextButton(
                        modifier = Modifier.weight(1f),
                        onClick = { detailsMember = null }
                    ) { Text("CLOSE", maxLines = 1) }
                    androidx.compose.material3.VerticalDivider(modifier = Modifier.height(24.dp))
                    TextButton(
                        enabled = !m.isAdmin,
                        modifier = Modifier.weight(1f),
                        onClick = { viewModel.removeMember(m.id); detailsMember = null }
                    ) { Text("DELETE", maxLines = 1) }
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
            confirmLabel = "Save",
            showAdminOption = true,
            adminRequired = m.isAdmin,
            title = "Edit Member",
            initialIsAdmin = m.isAdmin
        )
    }

    if (upiDialog != null) {
        AlertDialog(
            onDismissRequest = { upiDialog = null },
            title = { Text("Pay via UPI") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = vpa, onValueChange = { vpa = it }, label = { Text("UPI ID (VPA)") })
                    OutlinedTextField(value = payeeName, onValueChange = { payeeName = it }, label = { Text("Payee name") })
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val t = upiDialog ?: return@TextButton
                    val uri = buildUpiUri(vpa.text, payeeName.text, t.amount, "Simple Split settlement")
                    val intent = Intent(Intent.ACTION_VIEW, uri)
                    context.startActivity(intent)
                    upiDialog = null
                }) { Text("Open UPI") }
            },
            dismissButton = { TextButton(onClick = { upiDialog = null }) { Text("Cancel") } }
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
        FilledTonalButton(onClick = onMarkPaid, modifier = Modifier.fillMaxWidth()) { Text("Mark as Paid") }
        OutlinedButton(onClick = onPayUpi, modifier = Modifier.fillMaxWidth()) { Text("Pay via UPI") }
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
