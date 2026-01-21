package com.ledge.splitbook.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ledge.splitbook.ui.vm.CategoriesViewModel
import kotlinx.coroutines.launch
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.draw.scale
import androidx.compose.foundation.layout.size

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoriesScreen(
    onBack: () -> Unit,
    viewModel: CategoriesViewModel = hiltViewModel()
) {
    val categories by viewModel.categories.collectAsState()
    val scope = rememberCoroutineScope()

    var showAdd by remember { mutableStateOf(false) }
    var input by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Category List") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") } },
                actions = {
                    IconButton(onClick = { showAdd = true }) { Icon(Icons.Default.Add, contentDescription = "Add") }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { padding ->
        if (categories.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("No categories. Tap + to add.")
            }
        } else {
            // Compact stacked card containing the full list, with thin dividers
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                // Widen the card by reducing side padding so it covers more whitespace
                contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = padding.calculateTopPadding() + 12.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                item {
                    Card(
                        shape = RoundedCornerShape(6.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column {
                            categories.forEachIndexed { index, cat ->
                                var menuOpen by remember(cat.id) { mutableStateOf(false) }
                                var showRename by remember(cat.id) { mutableStateOf(false) }
                                var newName by remember(cat.id) { mutableStateOf(cat.name) }

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        // Make rows a bit taller and more comfortable to tap
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(cat.name, style = MaterialTheme.typography.bodyMedium, fontSize = 15.sp)
                                    Box {
                                        CompositionLocalProvider(LocalMinimumInteractiveComponentEnforcement provides false) {
                                            IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(28.dp)) {
                                                Icon(Icons.Default.MoreVert, contentDescription = null, modifier = Modifier.size(20.dp))
                                            }
                                        }
                                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                            DropdownMenuItem(text = { Text("Rename") }, onClick = {
                                                menuOpen = false
                                                showRename = true
                                            })
                                            DropdownMenuItem(text = { Text("Delete") }, onClick = {
                                                menuOpen = false
                                                scope.launch { viewModel.delete(cat.id) }
                                            })
                                        }
                                    }
                                }
                                if (index < categories.lastIndex) Divider(thickness = 0.6.dp)

                                if (showRename) {
                                    AlertDialog(
                                        onDismissRequest = { showRename = false },
                                        title = { Text("Rename Category") },
                                        text = {
                                            OutlinedTextField(
                                                value = newName,
                                                onValueChange = { newName = it },
                                                label = { Text("Category name") },
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                        },
                                        confirmButton = {
                                            val canSave = newName.trim().isNotEmpty()
                                            TextButton(enabled = canSave, onClick = {
                                                val trimmed = newName.trim()
                                                scope.launch { viewModel.rename(cat.id, trimmed) }
                                                showRename = false
                                            }) { Text("Save") }
                                        },
                                        dismissButton = { TextButton(onClick = { showRename = false }) { Text("Cancel") } }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAdd) {
        AlertDialog(
            onDismissRequest = { showAdd = false },
            title = { Text("Add New Category") },
            text = {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    label = { Text("Enter category name") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                val canAdd = input.trim().isNotEmpty()
                TextButton(enabled = canAdd, onClick = {
                    val trimmed = input.trim()
                    scope.launch { viewModel.add(trimmed) }
                    input = ""
                    showAdd = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showAdd = false }) { Text("Cancel") } }
        )
    }
}
