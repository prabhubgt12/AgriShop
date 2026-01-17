package com.ledge.splitbook.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Add
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ledge.splitbook.ui.vm.MembersViewModel
import com.ledge.splitbook.ui.components.AddMemberDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MembersScreen(
    groupId: Long,
    onBack: () -> Unit,
    viewModel: MembersViewModel = hiltViewModel()
) {
    LaunchedEffect(groupId) { viewModel.load(groupId) }
    val ui by viewModel.ui.collectAsState()

    var addName by remember { mutableStateOf(TextFieldValue("")) }
    var showAddDialog by remember { mutableStateOf(false) }
    var renameDialog by remember { mutableStateOf<Long?>(null) }
    var renameText by remember { mutableStateOf(TextFieldValue("")) }
    var removeDialog by remember { mutableStateOf<Long?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Members") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") } },
                actions = { IconButton(onClick = { showAddDialog = true }) { Icon(Icons.Default.Add, contentDescription = "Add member") } },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = androidx.compose.material3.MaterialTheme.colorScheme.primary,
                    titleContentColor = androidx.compose.material3.MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = androidx.compose.material3.MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = androidx.compose.material3.MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { padding ->
        Column(modifier = Modifier
            .fillMaxSize()
            .padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            LazyColumn(contentPadding = PaddingValues(4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(ui.members) { m ->
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            MemberAvatar(name = m.name)
                            Text(m.name)
                        }
                        Row {
                            IconButton(onClick = {
                                renameDialog = m.id
                                renameText = TextFieldValue(m.name)
                            }) { Icon(Icons.Default.Edit, contentDescription = "Rename") }
                            IconButton(onClick = { removeDialog = m.id }) { Icon(Icons.Default.Delete, contentDescription = "Remove") }
                        }
                    }
                }
            }
            if (ui.message.isNotBlank()) {
                Text(ui.message)
            }
        }
    }

    if (showAddDialog) {
        AddMemberDialog(
            onDismiss = { showAddDialog = false },
            onAdd = { name, depStr ->
                val trimmed = name.trim()
                val deposit = depStr.toDoubleOrNull() ?: 0.0
                if (trimmed.isNotEmpty()) viewModel.add(trimmed, deposit)
                showAddDialog = false
            }
        )
    }

    if (renameDialog != null) {
        AlertDialog(
            onDismissRequest = { renameDialog = null },
            title = { Text("Rename member") },
            text = { OutlinedTextField(value = renameText, onValueChange = { renameText = it }, label = { Text("New name") }) },
            confirmButton = {
                TextButton(onClick = {
                    val id = renameDialog ?: return@TextButton
                    viewModel.rename(id, renameText.text.trim())
                    renameDialog = null
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { renameDialog = null }) { Text("Cancel") } }
        )
    }

    if (removeDialog != null) {
        AlertDialog(
            onDismissRequest = { removeDialog = null },
            title = { Text("Remove member") },
            text = { Text("Remove this member? Only members not used in expenses can be removed.") },
            confirmButton = {
                TextButton(onClick = {
                    val id = removeDialog ?: return@TextButton
                    viewModel.remove(id)
                    removeDialog = null
                }) { Text("Remove") }
            },
            dismissButton = { TextButton(onClick = { removeDialog = null }) { Text("Cancel") } }
        )
    }

}

@Composable
private fun MemberAvatar(name: String) {
    val initial = name.trim().take(1).uppercase()
    val palette = listOf(
        0xFFE57373, 0xFFF06292, 0xFFBA68C8, 0xFF9575CD, 0xFF64B5F6,
        0xFF4FC3F7, 0xFF4DB6AC, 0xFF81C784, 0xFFFFB74D, 0xFFA1887F
    ).map { Color(it.toInt()) }
    val idx = (kotlin.math.abs(name.hashCode())) % palette.size
    val bg = palette[idx].copy(alpha = 0.25f)
    val fg = palette[idx]
    androidx.compose.foundation.layout.Box(
        modifier = Modifier
            .size(32.dp)
            .background(bg, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(initial, color = fg)
    }
}
