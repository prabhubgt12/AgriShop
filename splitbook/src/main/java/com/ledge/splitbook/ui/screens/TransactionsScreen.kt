package com.ledge.splitbook.ui.screens
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ledge.splitbook.ui.vm.TransactionsViewModel
import com.ledge.splitbook.ui.vm.SettingsViewModel
import com.ledge.splitbook.util.formatAmount

@OptIn(ExperimentalMaterial3Api::class)
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
    val total = ui.expenses.sumOf { it.amount }
    var detailsForId by remember { mutableStateOf<Long?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("$groupName") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = null) } },
                actions = {
                    AssistChip(
                        onClick = {},
                        label = { Text("Total: " + formatAmount(total, currency)) },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = androidx.compose.material3.MaterialTheme.colorScheme.secondaryContainer,
                            labelColor = androidx.compose.material3.MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    )
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
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = padding,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            itemsIndexed(ui.expenses) { index, e ->
                var menuOpen by remember { mutableStateOf(false) }
                Card(
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    shape = RectangleShape
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { detailsForId = e.id }
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(
                            modifier = Modifier.weight(1f).padding(end = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(formatAmount(e.amount, currency), fontWeight = FontWeight.SemiBold)
                            Text(e.category)
                            e.note?.let { Text(it) }
                        }
                        Row {
                            var overflowOpen by remember { mutableStateOf(false) }
                            IconButton(onClick = { overflowOpen = true }) { Icon(Icons.Default.MoreVert, contentDescription = "More") }
                            DropdownMenu(expanded = overflowOpen, onDismissRequest = { overflowOpen = false }) {
                                DropdownMenuItem(text = { Text("Edit") }, onClick = { overflowOpen = false; onEdit(e.id) })
                                DropdownMenuItem(text = { Text("Delete") }, onClick = { overflowOpen = false; viewModel.deleteExpense(e.id); onDelete(e.id) })
                            }
                        }
                    }
                }
                if (index != ui.expenses.lastIndex) HorizontalDivider()
            }
        }
    }

    // Details dialog
    val detailsId = detailsForId
    if (detailsId != null) {
        val e = ui.expenses.firstOrNull { it.id == detailsId }
        if (e != null) {
            val payer = ui.members.firstOrNull { it.id == e.paidByMemberId }?.name ?: "—"
            AlertDialog(
                onDismissRequest = { detailsForId = null },
                title = { Text(e.note ?: e.category) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            Text("Amount")
                            Text(formatAmount(e.amount, currency))
                        }
                        e.createdAt?.let { Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("Date"); Text(it) } }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("Category"); Text(e.category) }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("Expense by"); Text(payer) }
                    }
                },
                confirmButton = { TextButton(onClick = { detailsForId = null }) { Text("Close") } }
            )
        } else {
            detailsForId = null
        }
    }
}
