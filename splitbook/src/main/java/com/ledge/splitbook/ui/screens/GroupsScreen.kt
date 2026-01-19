package com.ledge.splitbook.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.platform.LocalContext
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.hilt.navigation.compose.hiltViewModel
import com.ledge.splitbook.ui.vm.GroupsViewModel
import com.ledge.splitbook.ui.vm.SettingsViewModel
import com.ledge.splitbook.util.formatAmount
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.ledge.splitbook.BuildConfig
import kotlinx.coroutines.delay

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

    Scaffold(
        topBar = {
            var menuOpen by remember { mutableStateOf(false) }
            TopAppBar(
                title = { Text("Simple Split") },
                navigationIcon = {
                    Box {
                        IconButton(onClick = { menuOpen = true }) { Icon(Icons.Default.Menu, contentDescription = "Menu") }
                        val context = LocalContext.current
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                leadingIcon = { Icon(Icons.Default.List, contentDescription = null) },
                                text = { Text("Manage Category") }, onClick = {
                                menuOpen = false
                                onOpenCategories()
                            })
                            DropdownMenuItem(
                                leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null) },
                                text = { Text("Settings") }, onClick = {
                                menuOpen = false
                                onOpenSettings()
                            })
                            DropdownMenuItem(
                                leadingIcon = { Icon(Icons.Default.Star, contentDescription = null) },
                                text = { Text("Remove Ads") },
                                enabled = !settings.removeAds,
                                onClick = {
                                    menuOpen = false
                                    // Redirect to Settings where purchase flow lives
                                    onOpenSettings()
                                }
                            )
                            
                            DropdownMenuItem(
                                leadingIcon = { Icon(Icons.Default.Star, contentDescription = null) },
                                text = { Text("Rate It") }, onClick = {
                                menuOpen = false
                                val pkg = context.packageName
                                val market = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$pkg"))
                                market.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                try {
                                    context.startActivity(market)
                                } catch (_: Exception) {
                                    val web = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$pkg"))
                                    web.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    context.startActivity(web)
                                }
                            })
                        }
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
            ExtendedFloatingActionButton(onClick = { showCreate = true }, icon = { Icon(Icons.Default.Add, null) }, text = { Text("New group") })
        },
        bottomBar = {
            if (!settings.removeAds) {
                AndroidView(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding(),
                    factory = { context ->
                        AdView(context).apply {
                            adUnitId = if (BuildConfig.USE_TEST_ADS) "ca-app-pub-3940256099942544/6300978111" else "ca-app-pub-2556604347710668/9615145808"
                            setAdSize(AdSize.BANNER)
                            loadAd(AdRequest.Builder().build())
                        }
                    }
                )
            }
        }
    ) { padding ->
        val contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = padding.calculateTopPadding() + 8.dp, bottom = 96.dp)
        if (showEmpty) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) { Text("No groups yet. Tap + to create.", textAlign = TextAlign.Center) }
        } else {
            LazyColumn(contentPadding = contentPadding, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(items = groups, key = { it.id }) { g ->
                    var menuOpen by remember { mutableStateOf(false) }
                    var showRename by remember { mutableStateOf(false) }
                    var showDelete by remember { mutableStateOf(false) }
                    var newName by remember { mutableStateOf(g.name) }
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
                                        "Total",
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
                                IconButton(onClick = { menuOpen = true }) { Icon(Icons.Default.MoreVert, contentDescription = "More") }
                                DropdownMenu(
                                    expanded = menuOpen,
                                    onDismissRequest = { menuOpen = false }
                                ) {
                                    DropdownMenuItem(text = { Text("Edit group") }, onClick = {
                                        menuOpen = false
                                        newName = g.name
                                        showRename = true
                                    })
                                    DropdownMenuItem(text = { Text("Delete group") }, onClick = {
                                        menuOpen = false
                                        showDelete = true
                                    })
                                }
                            }
                        }
                    }

                    if (showRename) {
                        AlertDialog(
                            onDismissRequest = { showRename = false },
                            title = { Text("Edit Group") },
                            text = {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedTextField(
                                        value = newName,
                                        onValueChange = { newName = it },
                                        label = { Text("Group name") },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            },
                            confirmButton = {
                                TextButton(onClick = {
                                    val trimmed = newName.trim()
                                    if (trimmed.isNotEmpty()) {
                                        viewModel.renameGroup(g.id, trimmed)
                                        showRename = false
                                    }
                                }) { Text("Save") }
                            },
                            dismissButton = { TextButton(onClick = { showRename = false }) { Text("Cancel") } }
                        )
                    }

                    if (showDelete) {
                        AlertDialog(
                            onDismissRequest = { showDelete = false },
                            title = { Text("Delete Group") },
                            text = { Text("This will delete the group and all its data. This action cannot be undone.") },
                            confirmButton = {
                                TextButton(onClick = {
                                    viewModel.deleteGroup(g.id)
                                    showDelete = false
                                }) { Text("Delete") }
                            },
                            dismissButton = { TextButton(onClick = { showDelete = false }) { Text("Cancel") } }
                        )
                    }
                }
            }
        }
    }

    if (showCreate) {
        AlertDialog(
            onDismissRequest = { showCreate = false },
            title = { Text("Create Group") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Group name") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    // simple icon picker as text buttons
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Choose icon:")
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
                }) { Text("Create") }
            },
            dismissButton = { TextButton(onClick = { showCreate = false }) { Text("Cancel") } }
        )
    }
}
