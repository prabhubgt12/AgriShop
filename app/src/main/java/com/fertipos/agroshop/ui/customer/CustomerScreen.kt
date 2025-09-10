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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.background
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MoreVert
import com.fertipos.agroshop.data.local.entities.Customer
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.material3.Checkbox
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import com.fertipos.agroshop.R

@Composable
fun CustomerScreen() {
    val vm: CustomerViewModel = hiltViewModel()
    val state = vm.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 8.dp)) {
            SnackbarHost(hostState = snackbarHostState)
            // Header with Add button
            var showAdd by remember { mutableStateOf(false) }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(text = stringResource(R.string.customers_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Button(onClick = { showAdd = true }) { Text(stringResource(R.string.add)) }
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
                label = { Text(stringResource(R.string.search_by_customer)) }
            )
            Spacer(Modifier.height(8.dp))

            // Filtered list
            val filtered = remember(state.value.customers, searchQuery) {
                if (searchQuery.isBlank()) state.value.customers else state.value.customers.filter { it.name.contains(searchQuery, ignoreCase = true) }
            }
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 8.dp)
            ) {
                items(filtered, key = { it.id }) { c ->
                    CustomerRow(
                        c = c,
                        onUpdate = { updated, n, p, a, isSupp -> vm.update(updated, n, p, a, isSupp) },
                        onDelete = { vm.delete(c) }
                    )
                }
            }

            if (showAdd) {
                AddCustomerDialog(
                    onConfirm = { n, p, a, isSupp ->
                        vm.add(n, p, a, isSupp)
                        showAdd = false
                    },
                    onDismiss = { showAdd = false }
                )
            }
            // Error handling
            val err = state.value.error
            LaunchedEffect(err) {
                if (err != null) {
                    val msg = when (err) {
                        "ERR_CUSTOMER_REFERENCED" -> context.getString(R.string.err_customer_referenced)
                        else -> err
                    }
                    snackbarHostState.showSnackbar(msg)
                    vm.clearError()
                }
            }
        }
    }
}

@Composable
private fun CustomerRow(
    c: Customer,
    onUpdate: (Customer, String, String?, String?, Boolean) -> Unit,
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = c.name, fontWeight = FontWeight.SemiBold)
                    if (!c.phone.isNullOrBlank()) {
                        Text(text = stringResource(R.string.phone_colon, c.phone ?: ""), style = MaterialTheme.typography.bodySmall)
                    }
                    if (!c.address.isNullOrBlank()) {
                        Spacer(Modifier.height(2.dp))
                        Text(text = stringResource(R.string.address_colon, c.address ?: ""), style = MaterialTheme.typography.bodySmall)
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (c.isSupplier) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFFDFF6DD))
                                .padding(vertical = 2.dp, horizontal = 6.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.supplier_chip),
                                color = Color(0xFF0B6A0B),
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                        Spacer(Modifier.height(0.dp))
                    }
                    Box {
                        IconButton(onClick = { menuExpanded = true }) { Icon(Icons.Outlined.MoreVert, contentDescription = stringResource(R.string.more_cd)) }
                        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                            DropdownMenuItem(text = { Text(stringResource(R.string.delete)) }, onClick = { menuExpanded = false; confirmDelete = true })
                        }
                    }
                }
            }
        }
    }
    if (showEdit) {
        EditCustomerDialog(initial = c, onConfirm = { n, p, a, isSupp ->
            onUpdate(c, n, p, a, isSupp)
            showEdit = false
        }, onDismiss = { showEdit = false })
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.delete_customer_title)) },
            text = { Text(stringResource(R.string.delete_customer_message, c.name)) },
            confirmButton = {
                TextButton(onClick = { onDelete(); confirmDelete = false }) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }
}

@Composable
private fun EditCustomerDialog(initial: Customer, onConfirm: (String, String?, String?, Boolean) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(initial.name) }
    var phone by remember { mutableStateOf(initial.phone ?: "") }
    var address by remember { mutableStateOf(initial.address ?: "") }
    var isSupplier by remember { mutableStateOf(initial.isSupplier) }
    val vm: CustomerViewModel = hiltViewModel()
    var referenced by remember { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(initial.id) {
        referenced = vm.isReferenced(initial.id)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.edit_customer)) },
        text = {
            Column {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text(stringResource(R.string.name_required)) })
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = phone,
                    onValueChange = { raw -> phone = raw.filter { it.isDigit() } },
                    label = { Text(stringResource(R.string.phone)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(value = address, onValueChange = { address = it }, label = { Text(stringResource(R.string.address)) })
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = isSupplier, onCheckedChange = { checked -> if (referenced != true) isSupplier = checked }, enabled = referenced != true)
                    Text(stringResource(R.string.supplier), modifier = Modifier.padding(start = 4.dp))
                }
                if (referenced == true) {
                    Spacer(Modifier.height(4.dp))
                    Text(text = stringResource(R.string.supplier_locked_info), style = MaterialTheme.typography.labelSmall, color = Color(0xFF6B6B6B))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onConfirm(name, phone.ifBlank { null }, address.ifBlank { null }, isSupplier)
                onDismiss()
            }) { Text(stringResource(R.string.save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}

@Composable
private fun AddCustomerDialog(onConfirm: (String, String?, String?, Boolean) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var isSupplier by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.add_customer)) },
        text = {
            Column {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text(stringResource(R.string.name_required)) })
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = phone,
                    onValueChange = { raw -> phone = raw.filter { it.isDigit() } },
                    label = { Text(stringResource(R.string.phone)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(value = address, onValueChange = { address = it }, label = { Text(stringResource(R.string.address)) })
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = isSupplier, onCheckedChange = { isSupplier = it })
                    Text(stringResource(R.string.supplier), modifier = Modifier.padding(start = 4.dp))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (name.isNotBlank()) {
                    onConfirm(name.trim(), phone.ifBlank { null }, address.ifBlank { null }, isSupplier)
                }
            }) { Text(stringResource(R.string.save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}
