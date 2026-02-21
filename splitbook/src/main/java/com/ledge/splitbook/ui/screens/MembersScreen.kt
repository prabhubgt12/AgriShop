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
import androidx.compose.ui.res.stringResource
import com.ledge.splitbook.R

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
                title = { Text(stringResource(R.string.members_title)) },
                actions = { IconButton(onClick = { showAddDialog = true }) { Icon(Icons.Default.Add, contentDescription = stringResource(R.string.cd_add_member)) } },
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
                            Text(if (m.isAdmin) stringResource(R.string.members_admin_suffix, m.name) else m.name)
                        }
                        Row {
                            IconButton(onClick = {
                                renameDialog = m.id
                                renameText = TextFieldValue(m.name)
                            }, enabled = !m.isAdmin) { Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.cd_rename)) }
                            IconButton(onClick = { removeDialog = m.id }, enabled = !m.isAdmin) { Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.cd_remove)) }
                        }
                    }
                }
            }
            if (ui.message.isNotBlank()) {
                val msg = when (ui.message) {
                    "REMOVE_USED" -> stringResource(R.string.cannot_remove_used)
                    else -> stringResource(R.string.action_not_allowed)
                }
                AlertDialog(
                    onDismissRequest = { viewModel.clearMessage() },
                    title = { Text(stringResource(R.string.action_not_allowed)) },
                    text = { Text(msg) },
                    confirmButton = { TextButton(onClick = { viewModel.clearMessage() }) { Text(stringResource(R.string.ok)) } }
                )
            }
        }
    }

    if (showAddDialog) {
        val hasAdmin = ui.members.any { it.isAdmin }
        com.ledge.splitbook.ui.components.AddMemberDialog(
            onDismiss = { showAddDialog = false },
            onAdd = { name, depStr, isAdmin ->
                val trimmed = name.trim()
                val deposit = depStr.toDoubleOrNull() ?: 0.0
                if (trimmed.isNotEmpty()) viewModel.add(trimmed, deposit, isAdmin)
                showAddDialog = false
            },
            showAdminOption = !hasAdmin,
            adminRequired = !hasAdmin
        )
    }

    if (renameDialog != null) {
        AlertDialog(
            onDismissRequest = { renameDialog = null },
            title = { Text(stringResource(R.string.members_rename_title)) },
            text = { OutlinedTextField(value = renameText, onValueChange = { renameText = it }, label = { Text(stringResource(R.string.members_new_name_label)) }) },
            confirmButton = {
                TextButton(onClick = {
                    val id = renameDialog ?: return@TextButton
                    viewModel.rename(id, renameText.text.trim())
                    renameDialog = null
                }) { Text(stringResource(R.string.save)) }
            },
            dismissButton = { TextButton(onClick = { renameDialog = null }) { Text(stringResource(R.string.cancel)) } }
        )
    }

    if (removeDialog != null) {
        AlertDialog(
            onDismissRequest = { removeDialog = null },
            title = { Text(stringResource(R.string.remove_member_title)) },
            text = { Text(stringResource(R.string.remove_member_message)) },
            confirmButton = {
                TextButton(onClick = {
                    val id = removeDialog ?: return@TextButton
                    viewModel.remove(id)
                    removeDialog = null
                }) { Text(stringResource(R.string.remove)) }
            },
            dismissButton = { TextButton(onClick = { removeDialog = null }) { Text(stringResource(R.string.cancel)) } }
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
