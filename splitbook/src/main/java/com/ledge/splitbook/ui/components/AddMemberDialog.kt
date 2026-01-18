package com.ledge.splitbook.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType

@Composable
fun AddMemberDialog(
    onDismiss: () -> Unit,
    onAdd: (name: String, deposit: String, isAdmin: Boolean) -> Unit,
    initialName: String = "",
    initialDeposit: String = "",
    nameReadOnly: Boolean = false,
    confirmLabel: String = "Add",
    showAdminOption: Boolean = false,
    adminRequired: Boolean = false,
    title: String = "Add Member",
    initialIsAdmin: Boolean = false
) {
    var name by remember { mutableStateOf(initialName) }
    var deposit by remember { mutableStateOf(initialDeposit) }
    var isAdmin by remember { mutableStateOf(if (adminRequired) true else initialIsAdmin) }
    var showAdminWarn by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Member name") },
                    modifier = Modifier.fillMaxWidth(),
                    readOnly = nameReadOnly
                )
                if (!isAdmin) {
                    OutlinedTextField(
                        value = deposit,
                        onValueChange = { raw ->
                            // allow only digits and a single decimal point
                            val filtered = raw.filter { it.isDigit() || it == '.' }
                            val parts = filtered.split('.')
                            deposit = when {
                                parts.size <= 2 -> filtered
                                else -> parts[0] + "." + parts.drop(1).joinToString("").replace(".", "")
                            }
                        },
                        label = { Text("Deposit amount (optional)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                    )
                }
                if (showAdminOption) {
                    androidx.compose.foundation.layout.Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                    ) {
                        androidx.compose.material3.Checkbox(checked = isAdmin, onCheckedChange = {
                            val next = it || adminRequired
                            if (!isAdmin && next) {
                                // becoming admin: warn that deposits (if any) will be cleared/ignored
                                showAdminWarn = true
                            }
                            isAdmin = next
                        })
                        Text(if (adminRequired) "Set as Admin (required)" else "Set as Admin")
                    }
                    if (isAdmin) {
                        Text("Deposit will be ignored for admin.", color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onAdd(name, deposit, isAdmin) }) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )

    if (showAdminWarn) {
        AlertDialog(
            onDismissRequest = { showAdminWarn = false },
            title = { Text("Make Admin") },
            text = { Text("This member will become admin. Any existing deposit for this member will be removed and ignored. Continue?") },
            confirmButton = { TextButton(onClick = { showAdminWarn = false }) { Text("OK") } },
            dismissButton = {}
        )
    }
}
