package com.ledge.splitbook.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Menu
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ledge.splitbook.ui.vm.GroupsViewModel
import com.ledge.splitbook.ui.vm.SettingsViewModel
import com.ledge.splitbook.util.formatAmount
import com.ledge.splitbook.BuildConfig
import kotlinx.coroutines.delay
import androidx.compose.ui.res.stringResource
import com.ledge.splitbook.R
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupsScreen(
    onOpenGroup: (Long, String) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenCategories: () -> Unit,
    viewModel: GroupsViewModel = hiltViewModel()
) {
    val groups by viewModel.groups.collectAsState(initial = emptyList())
    var showEmpty by remember { mutableStateOf(false) }
    LaunchedEffect(groups) {
        if (groups.isEmpty()) {
            kotlinx.coroutines.delay(250)
            showEmpty = groups.isEmpty()
        } else {
            showEmpty = false
        }
    }
    val settingsViewModel: SettingsViewModel = hiltViewModel()
    val settings by settingsViewModel.ui.collectAsState()
    var showCreate by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var icon by remember { mutableStateOf("🏖️") }
    val iconOptions = listOf("🏖️","🏠","👥","💼","🚌","🍽️","🛏️","🎉")

    var overflowMenuForId by remember { mutableStateOf<Long?>(null) }
    var renameForId by remember { mutableStateOf<Long?>(null) }
    var deleteForId by remember { mutableStateOf<Long?>(null) }
    var renameDraft by remember { mutableStateOf("") }

    val scope = rememberCoroutineScope()
    var menuEnabled by remember { mutableStateOf(true) }

    val anyPopupOpen = showCreate || overflowMenuForId != null || renameForId != null || deleteForId != null
    val bannerVisible = !settings.removeAds && !anyPopupOpen

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.groups_title)) },
                navigationIcon = {
                    IconButton(
                        enabled = menuEnabled,
                        onClick = {
                            if (!menuEnabled) return@IconButton
                            menuEnabled = false
                            onOpenSettings()
                            scope.launch {
                                delay(600)
                                menuEnabled = true
                            }
                        }
                    ) {
                        Icon(Icons.Default.Menu, contentDescription = stringResource(R.string.cd_menu))
                    }
                },
                actions = {},
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showCreate = true }) { Icon(Icons.Default.Add, stringResource(R.string.fab_new_group)) }
        },
        bottomBar = {
            if (bannerVisible) {
                AndroidView(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding(),
                    factory = { ctx ->
                        AdView(ctx).apply {
                            adUnitId = if (BuildConfig.USE_TEST_ADS) {
                                "ca-app-pub-3940256099942544/6300978111"
                            } else {
                                "ca-app-pub-2556604347710668/2522894276"
                            }
                            setAdSize(AdSize.BANNER)
                            loadAd(AdRequest.Builder().build())
                        }
                    }
                )
            }
        }
    ) { padding ->
        val contentPadding = PaddingValues(
            start = 8.dp,
            end = 8.dp,
            top = 8.dp,
            bottom = 104.dp
        )
        if (showEmpty) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) { Text(stringResource(R.string.no_groups_message), textAlign = TextAlign.Center) }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = contentPadding,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                itemsIndexed(items = groups, key = { _, it -> it.id }) { index, g ->
                    val total by viewModel.totalForGroup(g.id).collectAsState()
                    Card(
                        onClick = { onOpenGroup(g.id, g.name) },
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        androidx.compose.foundation.layout.Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(text = "${g.icon ?: "👥"}  ${g.name}", style = MaterialTheme.typography.titleMedium)
                                androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        stringResource(R.string.total),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        formatAmount(total, settings.currency),
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                            Box(modifier = Modifier) {
                                IconButton(onClick = { overflowMenuForId = g.id }) { Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.cd_more)) }
                                DropdownMenu(
                                    expanded = overflowMenuForId == g.id,
                                    onDismissRequest = { if (overflowMenuForId == g.id) overflowMenuForId = null }
                                ) {
                                    DropdownMenuItem(text = { Text(stringResource(R.string.edit_group)) }, onClick = {
                                        overflowMenuForId = null
                                        renameDraft = g.name
                                        renameForId = g.id
                                    })
                                    DropdownMenuItem(text = { Text(stringResource(R.string.delete_group)) }, onClick = {
                                        overflowMenuForId = null
                                        deleteForId = g.id
                                    })
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    val renId = renameForId
    if (renId != null) {
        AlertDialog(
            onDismissRequest = { renameForId = null },
            title = { Text(stringResource(R.string.edit_group_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = renameDraft,
                        onValueChange = { renameDraft = it },
                        label = { Text(stringResource(R.string.group_name_label)) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val trimmed = renameDraft.trim()
                    if (trimmed.isNotEmpty()) {
                        viewModel.renameGroup(renId, trimmed)
                        renameForId = null
                    }
                }) { Text(stringResource(R.string.save)) }
            },
            dismissButton = { TextButton(onClick = { renameForId = null }) { Text(stringResource(R.string.cancel)) } }
        )
    }

    val delId = deleteForId
    if (delId != null) {
        AlertDialog(
            onDismissRequest = { deleteForId = null },
            title = { Text(stringResource(R.string.delete_group_title)) },
            text = { Text(stringResource(R.string.delete_group_message)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteGroup(delId)
                    deleteForId = null
                }) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = { TextButton(onClick = { deleteForId = null }) { Text(stringResource(R.string.cancel)) } }
        )
    }

    if (showCreate) {
        AlertDialog(
            onDismissRequest = { showCreate = false },
            title = { Text(stringResource(R.string.create_group_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text(stringResource(R.string.group_name_label)) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    // simple icon picker as text buttons
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(stringResource(R.string.choose_icon))
                        androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            iconOptions.forEach { opt ->
                                TextButton(onClick = { icon = opt }) { Text(if (icon == opt) "[$opt]" else opt) }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val trimmed = name.trim()
                    if (trimmed.isNotEmpty()) {
                        viewModel.createGroup(trimmed, icon) { gid ->
                            showCreate = false
                            name = ""
                            onOpenGroup(gid, trimmed)
                        }
                    }
                }) { Text(stringResource(R.string.create)) }
            },
            dismissButton = { TextButton(onClick = { showCreate = false }) { Text(stringResource(R.string.cancel)) } }
        )
    }
}
