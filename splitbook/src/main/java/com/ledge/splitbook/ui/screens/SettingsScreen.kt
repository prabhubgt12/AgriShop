package com.ledge.splitbook.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ListItem
import androidx.hilt.navigation.compose.hiltViewModel
import com.ledge.splitbook.ui.vm.SettingsViewModel
import com.ledge.splitbook.ui.vm.BackupViewModel
import com.ledge.splitbook.ui.vm.BillingViewModel
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.HorizontalDivider
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
    backupViewModel: BackupViewModel = hiltViewModel(),
    billingViewModel: BillingViewModel = hiltViewModel()
) {
    val settings by viewModel.ui.collectAsState()
    var darkMode by remember(settings.darkMode) { mutableStateOf(settings.darkMode) }
    var language by remember(settings.language) { mutableStateOf(settings.language) }
    var currency by remember(settings.currency) { mutableStateOf(settings.currency) }
    val languageOptions = listOf("English", "Hindi")
    val currencyOptions = listOf("INR ₹", "USD $", "EUR €")
    var langExpanded by remember { mutableStateOf(false) }
    var currExpanded by remember { mutableStateOf(false) }
    val backupUi by backupViewModel.ui.collectAsState()
    LaunchedEffect(Unit) { billingViewModel.start() }
    val signInLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        backupViewModel.handleSignInResult(result.data)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = null) } },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = androidx.compose.material3.MaterialTheme.colorScheme.primary,
                    titleContentColor = androidx.compose.material3.MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = androidx.compose.material3.MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = androidx.compose.material3.MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { padding ->
        val contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 8.dp, bottom = 24.dp)
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = contentPadding,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Theme")
                        ListItem(
                            headlineContent = { Text("Dark Mode") },
                            supportingContent = { Text("Use dark theme") },
                            trailingContent = { Switch(checked = darkMode, onCheckedChange = { checked -> darkMode = checked; viewModel.setDarkMode(checked) }) }
                        )
                    }
                }
            }

            item {
                OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Google Backup")
                        val email = backupUi.accountEmail ?: "Not signed in"
                        ListItem(
                            headlineContent = { Text("Account") },
                            supportingContent = { Text(email) },
                            trailingContent = {
                                if (backupUi.signedIn) {
                                    TextButton(onClick = { backupViewModel.signOut() }) { Text("Sign out") }
                                } else {
                                    TextButton(onClick = { signInLauncher.launch(backupViewModel.getSignInIntent()) }) { Text("Sign in") }
                                }
                            }
                        )
                        ListItem(
                            headlineContent = { Text("Last backup") },
                            supportingContent = { Text(backupUi.lastBackupTime ?: "Never") },
                            trailingContent = {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    TextButton(enabled = backupUi.signedIn && !backupUi.isRunning, onClick = { backupViewModel.restoreLatest() }) { Text("Restore") }
                                    TextButton(enabled = backupUi.signedIn && !backupUi.isRunning, onClick = { backupViewModel.backupNow() }) { Text(if (backupUi.isRunning) "Backing up..." else "Backup now") }
                                }
                            }
                        )
                        if (backupUi.error != null) {
                            Text(backupUi.error!!)
                        }
                    }
                }
            }

            item {
                OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text("Remove Ads")
                        ListItem(
                            headlineContent = { Text(if (settings.removeAds) "Ads are removed" else "One-time purchase to remove ads") },
                            trailingContent = {
                                val context = androidx.compose.ui.platform.LocalContext.current
                                if (!settings.removeAds) {
                                    TextButton(onClick = {
                                        val act = (context as? android.app.Activity)
                                        if (act != null) billingViewModel.purchaseRemoveAds(act)
                                    }) { Text("Buy remove ads") }
                                } else {
                                    TextButton(onClick = {}) { Text("Thank you") }
                                }
                            }
                        )

                        Text("Language")
                        ExposedDropdownMenuBox(expanded = langExpanded, onExpandedChange = { langExpanded = !langExpanded }) {
                            OutlinedTextField(
                                value = language,
                                onValueChange = {},
                                readOnly = true,
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("Select language") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = langExpanded) },
                                colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors()
                            )
                            DropdownMenu(expanded = langExpanded, onDismissRequest = { langExpanded = false }) {
                                languageOptions.forEach { opt ->
                                    DropdownMenuItem(text = { Text(opt) }, onClick = {
                                        language = opt
                                        viewModel.setLanguage(opt)
                                        langExpanded = false
                                    })
                                }
                            }
                        }

                        HorizontalDivider()

                        Text("Currency")
                        ExposedDropdownMenuBox(expanded = currExpanded, onExpandedChange = { currExpanded = !currExpanded }) {
                            OutlinedTextField(
                                value = currency,
                                onValueChange = {},
                                readOnly = true,
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("Select currency") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = currExpanded) },
                                colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors()
                            )
                            DropdownMenu(expanded = currExpanded, onDismissRequest = { currExpanded = false }) {
                                currencyOptions.forEach { opt ->
                                    DropdownMenuItem(text = { Text(opt) }, onClick = {
                                        currency = opt
                                        viewModel.setCurrency(opt)
                                        currExpanded = false
                                    })
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
