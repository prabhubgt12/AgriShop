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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import android.app.Activity
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Intent
import android.os.SystemClock
import android.widget.Toast
import com.ledge.splitbook.MainActivity
import androidx.compose.ui.Alignment

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
    var showCurrencySymbol by remember(settings.showCurrencySymbol) { mutableStateOf(settings.showCurrencySymbol) }
    val languageOptions = listOf("English", "Hindi", "Kannada", "Tamil", "Telugu")
    // Top 10 commonly used currencies (by code) for quick selection
    val currencyOptions = listOf("INR", "USD", "EUR", "GBP", "JPY", "CNY", "AUD", "CAD", "SGD", "AED")
    var langExpanded by remember { mutableStateOf(false) }
    var currExpanded by remember { mutableStateOf(false) }
    val backupUi by backupViewModel.ui.collectAsState()
    LaunchedEffect(Unit) { billingViewModel.start() }
    val signInLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        backupViewModel.handleSignInResult(result.data)
    }

    var showBackupConfirm by remember { mutableStateOf(false) }
    var showRestoreConfirm by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val activity = context as? Activity
    var pendingRestart by remember { mutableStateOf(false) }
    var pendingBackupToast by remember { mutableStateOf(false) }

    // After a successful restore completes, show a toast and restart the app cleanly
    LaunchedEffect(backupUi.isRunning, backupUi.runningOp, backupUi.error, pendingRestart) {
        if (pendingRestart && !backupUi.isRunning && backupUi.runningOp == null && backupUi.error == null) {
            pendingRestart = false
            Toast.makeText(context, "Restore is complete. The app will restart now to apply changes.", Toast.LENGTH_SHORT).show()
            val act = activity
            if (act != null) {
                val ctx = act.applicationContext
                val restartIntent = Intent(ctx, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                }
                if (restartIntent != null) {
                    // Immediate bring-up as primary path
                    ctx.startActivity(restartIntent)
                    val pi = PendingIntent.getActivity(
                        ctx,
                        0,
                        restartIntent,
                        PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    val am = ctx.getSystemService(AlarmManager::class.java)
                    val triggerAt = SystemClock.elapsedRealtime() + 1200
                    try {
                        am?.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pi)
                    } catch (_: Throwable) {
                        am?.setExact(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pi)
                    }
                    act.finishAffinity()
                    try { Thread.sleep(200) } catch (_: Throwable) {}
                    android.os.Process.killProcess(android.os.Process.myPid())
                }
            }
        }
    }

    // After a successful backup completes, show a toast
    LaunchedEffect(backupUi.isRunning, backupUi.runningOp, backupUi.error, pendingBackupToast) {
        if (pendingBackupToast && !backupUi.isRunning && backupUi.runningOp == null && backupUi.error == null) {
            pendingBackupToast = false
            Toast.makeText(context, context.getString(com.ledge.splitbook.R.string.backup_completed), Toast.LENGTH_SHORT).show()
        }
    }

    // Keep global currency formatter in sync
    LaunchedEffect(currency, showCurrencySymbol) {
        val code = currency.takeWhile { !it.isWhitespace() }.ifBlank { currency }
        com.ledge.splitbook.util.CurrencyFormatter.setConfig(code, showCurrencySymbol)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(id = com.ledge.splitbook.R.string.settings)) },
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
                OutlinedCard(modifier = Modifier.fillMaxWidth(), shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)) {
                    Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(stringResource(id = com.ledge.splitbook.R.string.theme))
                        ListItem(
                            headlineContent = { Text(stringResource(id = com.ledge.splitbook.R.string.dark_mode)) },
                            supportingContent = { Text(stringResource(id = com.ledge.splitbook.R.string.use_dark_theme)) },
                            trailingContent = { Switch(checked = darkMode, onCheckedChange = { checked -> darkMode = checked; viewModel.setDarkMode(checked) }) }
                        )
                    }
                }
            }

            item {
                OutlinedCard(modifier = Modifier.fillMaxWidth(), shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)) {
                    Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(stringResource(id = com.ledge.splitbook.R.string.google_backup))
                        val email = backupUi.accountEmail ?: stringResource(id = com.ledge.splitbook.R.string.not_signed_in)
                        ListItem(
                            headlineContent = { Text(stringResource(id = com.ledge.splitbook.R.string.account)) },
                            supportingContent = { Text(email) },
                            trailingContent = {
                                if (backupUi.signedIn) {
                                    TextButton(onClick = { backupViewModel.signOut() }) { Text(stringResource(id = com.ledge.splitbook.R.string.sign_out)) }
                                } else {
                                    TextButton(onClick = { signInLauncher.launch(backupViewModel.getSignInIntent()) }) { Text(stringResource(id = com.ledge.splitbook.R.string.sign_in)) }
                                }
                            }
                        )
                        Column(modifier = Modifier.fillMaxWidth()) {
                            androidx.compose.foundation.layout.Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(stringResource(id = com.ledge.splitbook.R.string.last_backup))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    TextButton(
                                        enabled = backupUi.signedIn && !backupUi.isRunning,
                                        onClick = { showRestoreConfirm = true }
                                    ) { Text(if (backupUi.isRunning && backupUi.runningOp == "restore") stringResource(id = com.ledge.splitbook.R.string.restoring) else stringResource(id = com.ledge.splitbook.R.string.restore)) }
                                    TextButton(
                                        enabled = backupUi.signedIn && !backupUi.isRunning,
                                        onClick = { showBackupConfirm = true }
                                    ) { Text(if (backupUi.isRunning && backupUi.runningOp == "backup") stringResource(id = com.ledge.splitbook.R.string.backing_up) else stringResource(id = com.ledge.splitbook.R.string.backup_now)) }
                                }
                            }
                            Text(
                                text = backupUi.lastBackupTime ?: stringResource(id = com.ledge.splitbook.R.string.never),
                                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                        if (backupUi.error != null) {
                            Text(backupUi.error!!)
                        }
                    }
                }
            }

            item {
                OutlinedCard(modifier = Modifier.fillMaxWidth(), shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)) {
                    Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(id = com.ledge.splitbook.R.string.premium_title), style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
                        Text(
                            if (settings.removeAds)
                                stringResource(id = com.ledge.splitbook.R.string.ads_removed)
                            else
                                stringResource(id = com.ledge.splitbook.R.string.ads_enabled_desc),
                            style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                            maxLines = 2,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Start,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val context = androidx.compose.ui.platform.LocalContext.current
                            if (!settings.removeAds) {
                                androidx.compose.material3.FilledTonalButton(onClick = {
                                    val act = (context as? android.app.Activity)
                                    if (act != null) billingViewModel.purchaseRemoveAds(act)
                                }) { Text(stringResource(id = com.ledge.splitbook.R.string.remove_ads)) }
                            } else {
                                androidx.compose.material3.AssistChip(
                                    onClick = {},
                                    enabled = false,
                                    label = { Text(stringResource(id = com.ledge.splitbook.R.string.thank_you)) }
                                )
                            }
                        }

                        Text(stringResource(id = com.ledge.splitbook.R.string.language))
                        ExposedDropdownMenuBox(expanded = langExpanded, onExpandedChange = { langExpanded = !langExpanded }) {
                            OutlinedTextField(
                                value = language,
                                onValueChange = {},
                                readOnly = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .menuAnchor(),
                                label = { Text(stringResource(id = com.ledge.splitbook.R.string.select_language)) },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = langExpanded) },
                                colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors()
                            )
                            ExposedDropdownMenu(expanded = langExpanded, onDismissRequest = { langExpanded = false }) {
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

                        Text(stringResource(id = com.ledge.splitbook.R.string.currency))
                        ExposedDropdownMenuBox(expanded = currExpanded, onExpandedChange = { currExpanded = !currExpanded }) {
                            OutlinedTextField(
                                value = currency,
                                onValueChange = {},
                                readOnly = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .menuAnchor(),
                                label = { Text(stringResource(id = com.ledge.splitbook.R.string.select_currency)) },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = currExpanded) },
                                colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors()
                            )
                            ExposedDropdownMenu(expanded = currExpanded, onDismissRequest = { currExpanded = false }) {
                                currencyOptions.forEach { code ->
                                    val label = when (code) {
                                        "INR" -> "INR (₹)"
                                        "USD" -> "USD ($)"
                                        "EUR" -> "EUR (€)"
                                        "GBP" -> "GBP (£)"
                                        "JPY" -> "JPY (¥)"
                                        "CNY" -> "CNY (¥)"
                                        "AUD" -> "AUD (A$)"
                                        "CAD" -> "CAD (C$)"
                                        "SGD" -> "SGD (S$)"
                                        "AED" -> "AED"
                                        else -> code
                                    }
                                    DropdownMenuItem(text = { Text(label) }, onClick = {
                                        currency = code
                                        viewModel.setCurrency(code)
                                        currExpanded = false
                                    })
                                }
                            }
                        }
                        // Show currency symbol toggle
                        androidx.compose.material3.ListItem(
                            headlineContent = { Text(stringResource(id = com.ledge.splitbook.R.string.show_currency_symbol)) },
                            trailingContent = { androidx.compose.material3.Switch(checked = showCurrencySymbol, onCheckedChange = { enabled ->
                                showCurrencySymbol = enabled
                                viewModel.setShowCurrencySymbol(enabled)
                            }) }
                        )
                    }
                }
            }
        }
    }

    // Backup confirm dialog (messages matched to Cashbook)
    if (showBackupConfirm) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showBackupConfirm = false },
            title = { Text(stringResource(id = com.ledge.splitbook.R.string.confirm_backup)) },
            text = { Text(stringResource(id = com.ledge.splitbook.R.string.confirm_backup_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showBackupConfirm = false
                    pendingBackupToast = true
                    backupViewModel.backupNow()
                }) { Text(stringResource(id = com.ledge.splitbook.R.string.ok)) }
            },
            dismissButton = { TextButton(onClick = { showBackupConfirm = false }) { Text(stringResource(id = com.ledge.splitbook.R.string.cancel)) } }
        )
    }

    // Restore confirm dialog (messages matched to Cashbook)
    if (showRestoreConfirm) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showRestoreConfirm = false },
            title = { Text(stringResource(id = com.ledge.splitbook.R.string.confirm_restore)) },
            text = { Text(stringResource(id = com.ledge.splitbook.R.string.confirm_restore_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showRestoreConfirm = false
                    pendingRestart = true
                    backupViewModel.restoreLatest()
                }) { Text(stringResource(id = com.ledge.splitbook.R.string.ok)) }
            },
            dismissButton = { TextButton(onClick = { showRestoreConfirm = false }) { Text(stringResource(id = com.ledge.splitbook.R.string.cancel)) } }
        )
    }

    // No dialog on completion; toast and auto-restart handled above
}
