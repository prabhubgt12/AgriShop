package com.fertipos.agroshop.ui.customer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.fertipos.agroshop.data.local.entities.Customer

@Composable
fun CustomerScreen() {
    val vm: CustomerViewModel = hiltViewModel()
    val state = vm.state.collectAsState()

    Surface(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header with Add button
            var showAdd by remember { mutableStateOf(false) }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(text = "Customers", style = MaterialTheme.typography.titleMedium)
                Button(onClick = { showAdd = true }) { Text("Add") }
            }
            Spacer(Modifier.height(8.dp))
            HorizontalDivider()
            Spacer(Modifier.height(8.dp))

            // List only
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(state.value.customers, key = { it.id }) { c ->
                    CustomerRow(
                        c = c,
                        onUpdate = { updated, n, p, a -> vm.update(updated, n, p, a) },
                        onDelete = { vm.delete(c) }
                    )
                    HorizontalDivider()
                }
            }

            if (showAdd) {
                AddCustomerDialog(
                    onConfirm = { n, p, a ->
                        vm.add(n, p, a)
                        showAdd = false
                    },
                    onDismiss = { showAdd = false }
                )
            }
        }
    }
}

@Composable
private fun CustomerRow(
    c: Customer,
    onUpdate: (Customer, String, String?, String?) -> Unit,
    onDelete: () -> Unit
) {
    var showEdit by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).clickable { showEdit = true }) {
        Text(text = c.name, style = MaterialTheme.typography.titleMedium)
        if (!c.phone.isNullOrBlank() || !c.address.isNullOrBlank()) {
            val parts = buildList {
                if (!c.phone.isNullOrBlank()) add("Phone: ${c.phone}")
                if (!c.address.isNullOrBlank()) add("Address: ${c.address}")
            }
            Text(text = parts.joinToString(separator = "  |  "), style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.height(6.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Spacer(modifier = Modifier.weight(1f))
            TextButton(onClick = { showEdit = true }) { Text("Edit") }
            TextButton(onClick = { confirmDelete = true }) { Text("Delete") }
        }
    }
    if (showEdit) {
        EditCustomerDialog(initial = c, onConfirm = { n, p, a ->
            onUpdate(c, n, p, a)
            showEdit = false
        }, onDismiss = { showEdit = false })
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete Customer?") },
            text = { Text("Are you sure you want to delete '${c.name}'? This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = { onDelete(); confirmDelete = false }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun EditCustomerDialog(initial: Customer, onConfirm: (String, String?, String?) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(initial.name) }
    var phone by remember { mutableStateOf(initial.phone ?: "") }
    var address by remember { mutableStateOf(initial.address ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Customer") },
        text = {
            Column {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name*") })
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text("Phone") })
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(value = address, onValueChange = { address = it }, label = { Text("Address") })
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onConfirm(name, phone.ifBlank { null }, address.ifBlank { null })
                onDismiss()
            }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun AddCustomerDialog(onConfirm: (String, String?, String?) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Customer") },
        text = {
            Column {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name*") })
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text("Phone") })
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(value = address, onValueChange = { address = it }, label = { Text("Address") })
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (name.isNotBlank()) {
                    onConfirm(name.trim(), phone.ifBlank { null }, address.ifBlank { null })
                }
            }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
