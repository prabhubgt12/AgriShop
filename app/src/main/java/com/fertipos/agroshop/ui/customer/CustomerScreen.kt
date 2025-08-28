package com.fertipos.agroshop.ui.customer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MoreVert
import com.fertipos.agroshop.data.local.entities.Customer

@Composable
fun CustomerScreen() {
    val vm: CustomerViewModel = hiltViewModel()
    val state = vm.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    Surface(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Column(modifier = Modifier.fillMaxSize()) {
            SnackbarHost(hostState = snackbarHostState)
            // Header with Add button
            var showAdd by remember { mutableStateOf(false) }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(text = "Customers", style = MaterialTheme.typography.titleMedium)
                Button(onClick = { showAdd = true }) { Text("Add") }
            }
            Spacer(Modifier.height(8.dp))
            HorizontalDivider()
            Spacer(Modifier.height(8.dp))

            // Search bar
            var searchQuery by remember { mutableStateOf("") }
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Search by customer name") }
            )
            Spacer(Modifier.height(8.dp))

            // Filtered list
            val filtered = remember(state.value.customers, searchQuery) {
                if (searchQuery.isBlank()) state.value.customers else state.value.customers.filter { it.name.contains(searchQuery, ignoreCase = true) }
            }
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(filtered, key = { it.id }) { c ->
                    CustomerRow(
                        c = c,
                        onUpdate = { updated, n, p, a -> vm.update(updated, n, p, a) },
                        onDelete = { vm.delete(c) }
                    )
                    Spacer(Modifier.height(8.dp))
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
            // Error handling
            val err = state.value.error
            LaunchedEffect(err) {
                if (err != null) {
                    snackbarHostState.showSnackbar(err)
                    vm.clearError()
                }
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
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { showEdit = true }
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            var menuExpanded by remember { mutableStateOf(false) }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(text = c.name, fontWeight = FontWeight.SemiBold)
                Box {
                    IconButton(onClick = { menuExpanded = true }) { Icon(Icons.Outlined.MoreVert, contentDescription = "More") }
                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        DropdownMenuItem(text = { Text("Delete") }, onClick = { menuExpanded = false; confirmDelete = true })
                    }
                }
            }
            if (!c.phone.isNullOrBlank() || !c.address.isNullOrBlank()) {
                Spacer(Modifier.height(2.dp))
                val parts = buildList {
                    if (!c.phone.isNullOrBlank()) add("Phone: ${c.phone}")
                    if (!c.address.isNullOrBlank()) add("Address: ${c.address}")
                }
                Text(text = parts.joinToString(separator = "  |  "), style = MaterialTheme.typography.bodySmall)
            }
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
