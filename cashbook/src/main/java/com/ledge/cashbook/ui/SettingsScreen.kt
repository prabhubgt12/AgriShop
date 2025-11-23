package com.ledge.cashbook.ui

import android.app.Activity
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import android.content.Intent
import com.ledge.cashbook.MainActivity
import com.ledge.cashbook.BuildConfig
import com.ledge.cashbook.R
import com.ledge.cashbook.data.backup.BackupManager
import com.ledge.cashbook.data.backup.DriveClient
import com.ledge.cashbook.data.prefs.LocalePrefs
import com.ledge.cashbook.ui.theme.ThemeViewModel
import com.google.api.services.drive.model.File
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import com.ledge.cashbook.billing.MonetizationViewModel

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun SettingsScreen(onBack: () -> Unit, themeViewModel: ThemeViewModel = hiltViewModel()) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val activity = context as? Activity

    var signedIn by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    var isWorking by remember { mutableStateOf(false) }
    var lastBackupDisplay by remember { mutableStateOf<String?>(null) }
    var showBackupConfirm by remember { mutableStateOf(false) }
    var showRestoreConfirm by remember { mutableStateOf(false) }

    // Monetization: remove ads purchase state
    val monetizationVM: MonetizationViewModel = hiltViewModel()
    val hasRemoveAds by monetizationVM.hasRemoveAds.collectAsState(initial = false)
    // Category settings
    val settingsVM: SettingsViewModel = hiltViewModel()
    val showCategory by settingsVM.showCategory.collectAsState(initial = false)

    // Removed legacy CSV categories field and persistence

    val signInLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        val act = activity ?: return@rememberLauncherForActivityResult
        signedIn = DriveClient.handleSignInResult(act, res.data)
        status = if (signedIn) context.getString(R.string.gd_connected) else (DriveClient.lastError() ?: context.getString(R.string.sign_in_failed))
        scope.launch { snackbarHostState.showSnackbar(status ?: "") }
        if (signedIn) {
            scope.launch { lastBackupDisplay = fetchLastBackupTime() }
        } else {
            lastBackupDisplay = null
        }
    }
    LaunchedEffect(Unit) {
        activity?.let {
            signedIn = DriveClient.tryInitFromLastAccount(it)
            if (signedIn) lastBackupDisplay = fetchLastBackupTime()
        }
    }

    BackHandler(enabled = true) {
        onBack()
    }

    Scaffold(topBar = {
        TopAppBar(title = { Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold) })
    }, snackbarHost = { SnackbarHost(snackbarHostState) }) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {

            // Language
            item { Text(stringResource(R.string.language_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) }
            item {
                val storedTag by themeViewModel.appLocaleTag.collectAsState()
                val normTag = storedTag.lowercase()
                val options = listOf(
                    "" to stringResource(R.string.system_option),
                    "en" to stringResource(R.string.english_label),
                    "hi" to stringResource(R.string.hindi_label),
                    "kn" to stringResource(R.string.kannada_label),
                    "ta" to stringResource(R.string.tamil_label),
                    "te" to stringResource(R.string.telugu_label)
                )
                var expanded by remember { mutableStateOf(false) }
                val current = options.firstOrNull { (tag, _) -> if (tag.isBlank()) normTag.isBlank() else normTag == tag || normTag.startsWith("$tag-") } ?: options.first()

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box {
                        OutlinedTextField(
                            modifier = Modifier.fillMaxWidth(),
                            value = current.second,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(stringResource(R.string.language_title)) },
                            trailingIcon = {
                                IconButton(onClick = { expanded = !expanded }) {
                                    Icon(imageVector = Icons.Default.ArrowDropDown, contentDescription = null)
                                }
                            },
                            enabled = true
                        )
                        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                            options.forEach { (tag, label) ->
                                DropdownMenuItem(
                                    text = { Text(label) },
                                    onClick = {
                                        expanded = false
                                        scope.launch {
                                            themeViewModel.setAppLocale(tag)
                                            LocalePrefs.applyLocale(context, tag)
                                            Toast.makeText(
                                                context,
                                                when (tag) {
                                                    "en" -> "Language: English"
                                                    "hi" -> "भाषा: हिन्दी"
                                                    "kn" -> "Language: Kannada"
                                                    "ta" -> "மொழி: தமிழ்"
                                                    "te" -> "భాష: తెలుగు"
                                                    else -> "Language: System"
                                                },
                                                Toast.LENGTH_SHORT
                                            ).show()
                                            activity?.let { act ->
                                                act.finishAffinity()
                                                act.startActivity(Intent(act, MainActivity::class.java))
                                            }
                                        }
                                    }
                                )
                            }
                        }
                    }
                    Text(text = stringResource(R.string.changes_apply_note), style = MaterialTheme.typography.labelSmall)
                }
            }
            item { HorizontalDivider() }
            // Theme
            item { Text(stringResource(R.string.theme_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) }
            item {
                val mode by themeViewModel.themeMode.collectAsState()
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    ThemeOptionRow(
                        label = stringResource(R.string.system_option),
                        selected = mode == ThemeViewModel.MODE_SYSTEM,
                        onSelect = { themeViewModel.setThemeMode(ThemeViewModel.MODE_SYSTEM) }
                    )
                    ThemeOptionRow(
                        label = stringResource(R.string.light_option),
                        selected = mode == ThemeViewModel.MODE_LIGHT,
                        onSelect = { themeViewModel.setThemeMode(ThemeViewModel.MODE_LIGHT) }
                    )
                    ThemeOptionRow(
                        label = stringResource(R.string.dark_option),
                        selected = mode == ThemeViewModel.MODE_DARK,
                        onSelect = { themeViewModel.setThemeMode(ThemeViewModel.MODE_DARK) }
                    )
                }
            }
            item { HorizontalDivider() }

            // Categories settings
            item { Text(stringResource(R.string.category), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.show_category_field))
                        Switch(checked = showCategory, onCheckedChange = { settingsVM.setShowCategory(it) })
                    }
                    Text(
                        text = stringResource(R.string.settings_category_toggle_note),
                        style = MaterialTheme.typography.labelSmall
                    )
                    // Show category pill in transaction list
                    val showCategoryInList by settingsVM.showCategoryInList.collectAsState(initial = true)
                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.show_category_in_list_label))
                        Switch(checked = showCategoryInList, onCheckedChange = { settingsVM.setShowCategoryInList(it) })
                    }
                    // Legacy CSV field removed. Category management is now via the Categories screen.
                }
            }
            item { HorizontalDivider() }

            // Accounts summary
            item {
                val settingsVM: SettingsViewModel = hiltViewModel()
                val showSummary by settingsVM.showSummary.collectAsState(initial = false)
                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.show_summary_card_label))
                    Switch(checked = showSummary, onCheckedChange = { settingsVM.setShowSummary(it) })
                }
            }
            item { HorizontalDivider() }

            // Currency settings
            item { Text(stringResource(R.string.currency_settings), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) }
            item {
                val currencyVm: com.ledge.cashbook.ui.CurrencyViewModel = hiltViewModel()
                val code by currencyVm.currencyCode.collectAsState()
                val showSymbol by currencyVm.showSymbol.collectAsState()
                LaunchedEffect(code, showSymbol) {
                    com.ledge.cashbook.util.CurrencyFormatter.setConfig(code, showSymbol)
                }
                var expanded by remember { mutableStateOf(false) }
                val options = listOf("INR", "USD", "EUR", "GBP")
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box {
                        OutlinedTextField(
                            modifier = Modifier.fillMaxWidth(),
                            value = code,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(stringResource(R.string.currency_code_label)) },
                            trailingIcon = {
                                IconButton(onClick = { expanded = !expanded }) { Icon(imageVector = Icons.Default.ArrowDropDown, contentDescription = null) }
                            }
                        )
                        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                            options.forEach { opt ->
                                DropdownMenuItem(text = { Text(opt) }, onClick = {
                                    expanded = false
                                    currencyVm.setCurrencyCode(opt)
                                })
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.show_currency_symbol))
                        Switch(checked = showSymbol, onCheckedChange = { currencyVm.setShowSymbol(it) })
                    }
                }
            }
            item { HorizontalDivider() }

            // Premium
            item { Text(stringResource(R.string.premium_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(if (hasRemoveAds) stringResource(R.string.ads_removed) else stringResource(R.string.ads_enabled_msg))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(
                            enabled = !hasRemoveAds,
                            onClick = {
                                val act = activity ?: return@Button
                                scope.launch {
                                    val started = monetizationVM.purchaseRemoveAds(act)
                                    if (!started) {
                                        snackbarHostState.showSnackbar("Preparing purchase, please try again in a moment")
                                    }
                                }
                            }
                        ) { Text(stringResource(R.string.remove_ads)) }

                        OutlinedButton(onClick = { monetizationVM.restore() }) {
                            Text(stringResource(R.string.restore_purchase))
                        }
                    }
                }
            }
            item { HorizontalDivider() }

            // Sign in/out row with status
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Button(onClick = {
                        val act = activity ?: return@Button
                        if (signedIn) {
                            DriveClient.signOut(act)
                            signedIn = false
                            status = context.getString(R.string.sign_out_done)
                            scope.launch { snackbarHostState.showSnackbar(status!!) }
                        } else {
                            signInLauncher.launch(DriveClient.getSignInIntent(act))
                        }
                    }) { Text(if (signedIn) stringResource(R.string.sign_out) else stringResource(R.string.sign_in)) }
                    Text(text = if (signedIn) stringResource(R.string.gd_connected) else stringResource(R.string.gd_not_connected))
                }
            }
            item { HorizontalDivider() }
            // Last backup info
            item {
                if (signedIn) {
                    val label = lastBackupDisplay ?: stringResource(R.string.never_label)
                    Text(text = stringResource(R.string.last_backup_label, label), style = MaterialTheme.typography.bodySmall)
                }
            }
            // Backup & Restore (simplified)
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Button(enabled = signedIn && !isWorking, onClick = { showBackupConfirm = true }) { Text(stringResource(R.string.backup_to_drive)) }
                    Button(enabled = signedIn && !isWorking, onClick = { showRestoreConfirm = true }) { Text(stringResource(R.string.restore)) }
                    if (isWorking) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    }
                }
            }
            item {
                status?.let { Text(it, style = MaterialTheme.typography.labelSmall) }
                Spacer(Modifier.height(4.dp))
                Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.developer_label), style = MaterialTheme.typography.labelSmall)
                    Text(stringResource(R.string.email_label), style = MaterialTheme.typography.labelSmall)
                    Text(stringResource(R.string.app_version_label, BuildConfig.VERSION_NAME), style = MaterialTheme.typography.labelSmall)
                }
            }
        }

        // Backup confirmation dialog
        if (showBackupConfirm) {
            AlertDialog(
                onDismissRequest = { showBackupConfirm = false },
                title = { Text(stringResource(R.string.backup_confirm_title)) },
                text = { Text(stringResource(R.string.backup_confirm_message)) },
                confirmButton = {
                    TextButton(onClick = {
                        showBackupConfirm = false
                        scope.launch {
                            val act = activity ?: return@launch
                            isWorking = true
                            val bytes = BackupManager.createBackupZip(act)
                            val ok = DriveClient.uploadAppData("cashbook-backup.zip", bytes)
                            isWorking = false
                            val msg = if (ok) context.getString(R.string.backup_uploaded) else (DriveClient.lastError() ?: context.getString(R.string.backup_failed))
                            snackbarHostState.showSnackbar(msg)
                            if (ok) {
                                // Update last backup time to now
                                lastBackupDisplay = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault()).format(Instant.now())
                            }
                        }
                    }) { Text(stringResource(R.string.ok)) }
                },
                dismissButton = {
                    TextButton(onClick = { showBackupConfirm = false }) { Text(stringResource(R.string.cancel)) }
                }
            )
        }

        // Restore confirmation dialog
        if (showRestoreConfirm) {
            AlertDialog(
                onDismissRequest = { showRestoreConfirm = false },
                title = { Text(stringResource(R.string.restore_confirm_title)) },
                text = { Text(stringResource(R.string.restore_confirm_message)) },
                confirmButton = {
                    TextButton(onClick = {
                        showRestoreConfirm = false
                        scope.launch {
                            isWorking = true
                            val latest = DriveClient.listBackups().firstOrNull()
                            var ok = false
                            val msg = if (latest != null) {
                                val bytes = DriveClient.download(latest.id)
                                ok = bytes != null && activity != null && BackupManager.restoreBackupZip(activity, bytes)
                                if (ok) context.getString(R.string.restore_complete) else (DriveClient.lastError() ?: context.getString(R.string.restore_failed))
                            } else context.getString(R.string.download_failed)
                            isWorking = false
                            snackbarHostState.showSnackbar(msg)
                            // After restore, refresh last backup info from Drive list
                            lastBackupDisplay = fetchLastBackupTime()
                            // Critical: restart app so Room reopens the restored DB and prefs are reloaded
                            if (ok) {
                                val act = activity
                                if (act != null) {
                                    act.finishAffinity()
                                    act.startActivity(Intent(act, MainActivity::class.java))
                                }
                            }
                        }
                    }) { Text(stringResource(R.string.ok)) }
                },
                dismissButton = {
                    TextButton(onClick = { showRestoreConfirm = false }) { Text(stringResource(R.string.cancel)) }
                }
            )
        }
    }
}

@Composable
private fun ThemeOptionRow(label: String, selected: Boolean, onSelect: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Row { RadioButton(selected = selected, onClick = onSelect) }
    }
}

// Helper: fetch latest backup modified time and return a displayable string, or null if none
private suspend fun fetchLastBackupTime(): String? {
    return try {
        val backups = DriveClient.listBackups()
        val latest = backups.firstOrNull()
        latest?.modifiedTime?.toString()
    } catch (t: Throwable) {
        null
    }
}
