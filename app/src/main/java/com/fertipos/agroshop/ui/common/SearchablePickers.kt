package com.fertipos.agroshop.ui.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusState
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.window.PopupProperties
import com.fertipos.agroshop.data.local.entities.Customer
import com.fertipos.agroshop.data.local.entities.Product

@Composable
fun CustomerPicker(
    customers: List<Customer>,
    label: String = "Customer",
    initialQuery: String = "",
    modifier: Modifier = Modifier,
    onPicked: (Customer) -> Unit
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
    onPicked: (String) -> Unit
) {
    val options = listOf("Kg", "Pcs", "L")
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
