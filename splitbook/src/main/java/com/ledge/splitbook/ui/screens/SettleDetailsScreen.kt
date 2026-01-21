package com.ledge.splitbook.ui.screens

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Alignment
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ledge.splitbook.domain.SettlementLogic
import com.ledge.splitbook.util.buildUpiUri
import com.ledge.splitbook.ui.vm.SettleViewModel
import androidx.compose.foundation.shape.RoundedCornerShape

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettleDetailsScreen(
    groupId: Long,
    groupName: String,
    onBack: () -> Unit,
    viewModel: SettleViewModel = hiltViewModel()
) {
    LaunchedEffect(groupId) { viewModel.load(groupId) }
    val ui by viewModel.ui.collectAsState()
    val context = LocalContext.current

    var upiDialog by remember { mutableStateOf<SettlementLogic.Transfer?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (groupName.isNotBlank()) "$groupName • Settle Up" else "Settle Up") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") } },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { padding ->
        if (ui.isLoading) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding() + 12.dp,
                bottom = 24.dp
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (ui.transfers.isEmpty()) {
                item {
                    Column(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("All settled!", style = MaterialTheme.typography.titleMedium)
                        Text("There are no pending transfers for this group.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                items(
                    items = ui.transfers,
                    key = { t -> "${t.fromMemberId}-${t.toMemberId}-${String.format("%.2f", t.amount)}" }
                ) { t ->
                    val from = ui.members.firstOrNull { it.id == t.fromMemberId }?.name ?: "From"
                    val to = ui.members.firstOrNull { it.id == t.toMemberId }?.name ?: "To"
                    Card(elevation = CardDefaults.cardElevation(defaultElevation = 1.dp), shape = RoundedCornerShape(6.dp)) {
                        Column(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("$from → $to: ₹${String.format("%.2f", t.amount)}")
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                // Use a filled button with primary container for strong contrast on cards
                                Button(
                                    onClick = {
                                        viewModel.markTransferPaid(groupId, t.fromMemberId, t.toMemberId, t.amount)
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        contentColor = MaterialTheme.colorScheme.onPrimary
                                    )
                                ) { Text("Mark as Paid") }
                                OutlinedButton(onClick = { upiDialog = t }) { Text("Pay via UPI") }
                            }
                        }
                    }
                }
            }
        }
    }

    if (upiDialog != null) {
        val t = upiDialog!!
        UpiDialog(
            amount = t.amount,
            onDismiss = { upiDialog = null },
            onOpen = { vpa, payee ->
                val intent = Intent(Intent.ACTION_VIEW, buildUpiUri(vpa, payee, t.amount, "Simple Split settlement"))
                context.startActivity(intent)
                onBack()
            }
        )
    }
}

@Composable
private fun UpiDialog(
    amount: Double,
    onDismiss: () -> Unit,
    onOpen: (vpa: String, payee: String) -> Unit
) {
    var vpa by remember { mutableStateOf("") }
    var payee by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Pay via UPI") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = vpa, onValueChange = { vpa = it }, label = { Text("UPI ID (VPA)") })
                OutlinedTextField(value = payee, onValueChange = { payee = it }, label = { Text("Payee name") })
            }
        },
        confirmButton = { TextButton(onClick = { onOpen(vpa, payee) }) { Text("Open UPI") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
