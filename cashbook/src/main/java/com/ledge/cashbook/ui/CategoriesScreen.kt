package com.ledge.cashbook.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ledge.cashbook.R
import com.ledge.cashbook.data.local.entities.Category

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoriesScreen(onBack: () -> Unit, vm: CategoriesViewModel = hiltViewModel()) {
    var showDialog by remember { mutableStateOf(false) }
    var editId by remember { mutableStateOf<Long?>(null) }
    var name by remember { mutableStateOf(TextFieldValue("")) }
    var keywords by remember { mutableStateOf(TextFieldValue("")) }
    var menuOpen by remember { mutableStateOf<Long?>(null) } // Track which category menu is open
    var deleteConfirmCategory by remember { mutableStateOf<Category?>(null) } // Track category to delete
    // Read setting to guide users when the feature is off
    val settingsVM: SettingsViewModel = hiltViewModel()
    val showCategory by settingsVM.showCategory.collectAsState(initial = false)

    fun openAdd() {
        editId = null; name = TextFieldValue(""); keywords = TextFieldValue(""); showDialog = true
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.title_categories)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = null) }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { openAdd() }) { Icon(Icons.Default.Add, contentDescription = null) }
        }
    ) { padding ->
        val categories by vm.categories.collectAsState()
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Text(
                    text = stringResource(R.string.categories_info_note_top),
                    style = MaterialTheme.typography.labelSmall
                )
                Spacer(Modifier.height(8.dp))
                if (!showCategory) {
                    Text(
                        text = stringResource(R.string.categories_enable_note),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                }
            }
            items(categories, key = { it.id }) { cat ->
                val kwsFlow = remember(cat.id) { vm.keywordsFor(cat.id) }
                val kws by kwsFlow.collectAsState(initial = emptyList())
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(cat.name, style = MaterialTheme.typography.titleMedium)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = stringResource(R.string.prefix_keywords) + " ",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                val preview = if (kws.isEmpty()) "—" else run {
                                    val list = kws.map { it.keyword }
                                    val head = list.take(2).joinToString(",")
                                    val rem = (list.size - 2).coerceAtLeast(0)
                                    if (rem > 0) "$head,+$rem" else head
                                }
                                Text(
                                    text = preview,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1
                                )
                            }
                        }
                        Box(modifier = Modifier.wrapContentSize(Alignment.TopStart)) {
                            IconButton(
                                onClick = { menuOpen = if (menuOpen == cat.id) null else cat.id },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    Icons.Default.MoreVert,
                                    contentDescription = "Menu",
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            DropdownMenu(
                                expanded = menuOpen == cat.id,
                                onDismissRequest = { menuOpen = null }
                            ) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(id = R.string.edit)) },
                                    onClick = {
                                        editId = cat.id; name = TextFieldValue(cat.name)
                                        keywords = TextFieldValue(kws.joinToString(", ") { it.keyword })
                                        showDialog = true
                                        menuOpen = null
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(id = R.string.delete)) },
                                    onClick = {
                                        deleteConfirmCategory = cat
                                        menuOpen = null
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            confirmButton = {
                TextButton(onClick = {
                    vm.addOrUpdate(name.text, keywords.text, id = editId)
                    showDialog = false
                }) { Text(stringResource(R.string.save)) }
            },
            dismissButton = { TextButton(onClick = { showDialog = false }) { Text(stringResource(R.string.cancel)) } },
            title = { Text(if (editId == null) stringResource(R.string.add_category) else stringResource(R.string.edit_category)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text(stringResource(R.string.category_name)) }, singleLine = true)
                    OutlinedTextField(value = keywords, onValueChange = { keywords = it }, label = { Text(stringResource(R.string.label_keywords)) })
                    Text(
                        text = stringResource(R.string.categories_keywords_help_note),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        )
    }

    // Delete confirmation dialog
    if (deleteConfirmCategory != null) {
        AlertDialog(
            onDismissRequest = { deleteConfirmCategory = null },
            confirmButton = {
                TextButton(onClick = {
                    vm.delete(deleteConfirmCategory!!)
                    deleteConfirmCategory = null
                }) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = {
                TextButton(onClick = { deleteConfirmCategory = null }) {
                    Text(stringResource(R.string.cancel))
                }
            },
            title = { Text(stringResource(R.string.delete)) },
            text = { Text(stringResource(R.string.delete_txn_confirm)) }
        )
    }
}
