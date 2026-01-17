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

@Composable
fun AddMemberDialog(
    onDismiss: () -> Unit,
    onAdd: (name: String, deposit: String) -> Unit,
    initialName: String = "",
    initialDeposit: String = "",
    nameReadOnly: Boolean = false,
    confirmLabel: String = "Add"
) {
    var name by remember { mutableStateOf(initialName) }
    var deposit by remember { mutableStateOf(initialDeposit) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Member") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Member name") },
                    modifier = Modifier.fillMaxWidth(),
                    readOnly = nameReadOnly
                )
                OutlinedTextField(
                    value = deposit,
                    onValueChange = { deposit = it },
                    label = { Text("Deposit amount (optional)") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = { TextButton(onClick = { onAdd(name, deposit) }) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
