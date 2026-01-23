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
import androidx.compose.ui.res.stringResource
import com.ledge.splitbook.R

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
                title = { Text(stringResource(R.string.category_list_title)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.cd_back)) } },
                actions = {
                    IconButton(onClick = { showAdd = true }) { Icon(Icons.Default.Add, contentDescription = stringResource(R.string.cd_add)) }
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
                Text(stringResource(R.string.no_categories_message))
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
                                            DropdownMenuItem(text = { Text(stringResource(R.string.rename)) }, onClick = {
                                                menuOpen = false
                                                showRename = true
                                            })
                                            DropdownMenuItem(text = { Text(stringResource(R.string.delete_item)) }, onClick = {
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
                                        title = { Text(stringResource(R.string.rename_category_title)) },
                                        text = {
                                            OutlinedTextField(
                                                value = newName,
                                                onValueChange = { newName = it },
                                                label = { Text(stringResource(R.string.category_name_label)) },
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                        },
                                        confirmButton = {
                                            val canSave = newName.trim().isNotEmpty()
                                            TextButton(enabled = canSave, onClick = {
                                                val trimmed = newName.trim()
                                                scope.launch { viewModel.rename(cat.id, trimmed) }
                                                showRename = false
                                            }) { Text(stringResource(R.string.save)) }
                                        },
                                        dismissButton = { TextButton(onClick = { showRename = false }) { Text(stringResource(R.string.cancel)) } }
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
            title = { Text(stringResource(R.string.add_new_category_title)) },
            text = {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    label = { Text(stringResource(R.string.enter_category_name_label)) },
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
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = { TextButton(onClick = { showAdd = false }) { Text(stringResource(R.string.cancel)) } }
        )
    }
}
