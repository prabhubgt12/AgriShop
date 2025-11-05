package com.fertipos.agroshop.ui.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusState
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties
import com.fertipos.agroshop.data.local.entities.Customer
import com.fertipos.agroshop.data.local.entities.Product
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.ui.graphics.Color

@Composable
fun CustomerPicker(
    customers: List<Customer>,
    label: String = "Customer",
    initialQuery: String = "",
    modifier: Modifier = Modifier,
    onPicked: (Customer) -> Unit,
    addNewLabel: String? = null,
    onAddNew: (() -> Unit)? = null
) {
    var expanded by remember { mutableStateOf(false) }
    // Re-initialize internal query whenever the external initialQuery changes (e.g., when editing loads)
    var query by remember(initialQuery) { mutableStateOf(initialQuery) }

    Box(modifier = modifier.fillMaxWidth()) {
        TextField(
            value = query,
            onValueChange = {
                query = it
                if (!expanded) expanded = true
            },
            label = { Text(label) },
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { fs: FocusState -> if (fs.isFocused) expanded = true },
            colors = TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface
            ),
            trailingIcon = {
                IconButton(onClick = { expanded = !expanded }) {
                    Text(if (expanded) "▲" else "▼", style = MaterialTheme.typography.labelLarge)
                }
            },
            singleLine = true
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            properties = PopupProperties(focusable = false)
        ) {
            if (onAddNew != null) {
                DropdownMenuItem(
                    text = {
                        Row {
                            Icon(Icons.Filled.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(6.dp))
                            Text((addNewLabel ?: "Add new"), color = MaterialTheme.colorScheme.primary)
                        }
                    },
                    onClick = {
                        expanded = false
                        onAddNew()
                    }
                )
            }
            customers
                .asSequence()
                .filter { it.name.contains(query, ignoreCase = true) }
                .take(20)
                .forEach { c ->
                    DropdownMenuItem(
                        text = { Text("${c.name} • ${c.phone ?: ""}") },
                        onClick = {
                            onPicked(c)
                            query = c.name
                            expanded = false
                        }
                    )
                }
        }
    }
}

@Composable
fun UnitPicker(
    label: String = "Unit",
    initial: String = "",
    modifier: Modifier = Modifier,
    onPicked: (String) -> Unit,
    options: List<String> = listOf("Kg", "Pcs", "L")
) {
    var expanded by remember { mutableStateOf(false) }
    // Re-initialize internal selection whenever the external initial changes
    var selection by remember(initial) { mutableStateOf(initial) }

    Box(modifier = modifier.fillMaxWidth()) {
        TextField(
            value = selection,
            onValueChange = { /* read-only via picker */ },
            label = { Text(label) },
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { fs: FocusState -> if (fs.isFocused) expanded = true },
            colors = TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface
            ),
            trailingIcon = {
                IconButton(onClick = { expanded = !expanded }) {
                    Text(if (expanded) "▲" else "▼", style = MaterialTheme.typography.labelLarge)
                }
            },
            singleLine = true
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            properties = PopupProperties(focusable = false)
        ) {
            options.forEach { opt ->
                DropdownMenuItem(
                    text = { Text(opt) },
                    onClick = {
                        selection = opt
                        onPicked(opt)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
fun TypePicker(
    label: String = "Type",
    initial: String = "",
    modifier: Modifier = Modifier,
    onPicked: (String) -> Unit,
    options: List<String> = listOf("Fertilizer", "Pecticide", "Fungi", "GP", "Other")
) {
    var expanded by remember { mutableStateOf(false) }
    // Re-initialize internal selection whenever the external initial changes
    var selection by remember(initial) { mutableStateOf(initial) }

    Box(modifier = modifier.fillMaxWidth()) {
        TextField(
            value = selection,
            onValueChange = { /* read-only via picker */ },
            label = { Text(label) },
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { fs: FocusState -> if (fs.isFocused) expanded = true },
            colors = TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface
            ),
            trailingIcon = {
                IconButton(onClick = { expanded = !expanded }) {
                    Text(if (expanded) "▲" else "▼", style = MaterialTheme.typography.labelLarge)
                }
            },
            singleLine = true
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            properties = PopupProperties(focusable = false)
        ) {
            options.forEach { opt ->
                DropdownMenuItem(
                    text = { Text(opt) },
                    onClick = {
                        selection = opt
                        onPicked(opt)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
fun ProductPicker(
    products: List<Product>,
    label: String = "Product",
    initialQuery: String = "",
    modifier: Modifier = Modifier,
    onPicked: (Product) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    // Re-initialize internal query whenever the external initialQuery changes
    var query by remember(initialQuery) { mutableStateOf(initialQuery) }

    Box(modifier = modifier.fillMaxWidth()) {
        TextField(
            value = query,
            onValueChange = {
                query = it
                if (!expanded) expanded = true
            },
            label = { Text(label) },
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { fs: FocusState -> if (fs.isFocused) expanded = true },
            colors = TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface
            ),
            trailingIcon = {
                IconButton(onClick = { expanded = !expanded }) {
                    Text(if (expanded) "▲" else "▼", style = MaterialTheme.typography.labelLarge)
                }
            },
            singleLine = true
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            properties = PopupProperties(focusable = false)
        ) {
            products
                .asSequence()
                .filter { it.name.contains(query, ignoreCase = true) }
                .take(30)
                .forEach { p ->
                    DropdownMenuItem(
                        text = { Text("${p.name} • ${p.unit} • Stock: ${p.stockQuantity}") },
                        onClick = {
                            onPicked(p)
                            query = p.name
                            expanded = false
                        }
                    )
                }
        }
    }
}
