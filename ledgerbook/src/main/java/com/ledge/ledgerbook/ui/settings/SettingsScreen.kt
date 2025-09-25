package com.ledge.ledgerbook.ui.settings

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ledge.ledgerbook.billing.MonetizationViewModel
import com.ledge.ledgerbook.BuildConfig
import com.google.api.services.drive.model.File
import com.ledge.ledgerbook.data.backup.BackupManager
import com.ledge.ledgerbook.data.backup.DriveClient
import com.ledge.ledgerbook.ui.theme.ThemeViewModel
import kotlinx.coroutines.launch
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.compose.foundation.selection.selectable
import android.widget.Toast
import com.ledge.ledgerbook.data.prefs.LocalePrefs
import androidx.lifecycle.lifecycleScope
import androidx.compose.ui.res.stringResource
import com.ledge.ledgerbook.R
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun SettingsScreen(onBack: () -> Unit, themeViewModel: ThemeViewModel = hiltViewModel()) {
    val scope = rememberCoroutineScope()

    var status by remember { mutableStateOf<String?>(null) }
    // AgroShop-style state for Drive
    val snackbarHostState = remember { SnackbarHostState() }
    var isWorking by remember { mutableStateOf(false) }
    var signedInState by remember { mutableStateOf(false) }
    var lastBackupDisplay by remember { mutableStateOf<String?>(null) }
    var showBackupConfirm by remember { mutableStateOf(false) }
    var showRestoreConfirm by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val activity = context as? Activity

    // Monetization state
    val monetizationVM: MonetizationViewModel = hiltViewModel()
    val hasRemoveAds by monetizationVM.hasRemoveAds.collectAsState()

    val signInLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        val act = activity ?: return@rememberLauncherForActivityResult
        val ok = DriveClient.handleSignInResult(act, res.data)
        signedInState = ok && DriveClient.isSignedIn(act)
        status = if (signedInState) act.getString(R.string.gd_connected) else (DriveClient.lastError() ?: act.getString(R.string.sign_in_failed))
        if (signedInState) {
            scope.launch { lastBackupDisplay = fetchLbLastBackupTime() }
        } else {
            lastBackupDisplay = null
        }
    }
    // Try silent init like AgroShop
    LaunchedEffect(Unit) {
        val act = activity
        if (act != null && !signedInState) {
            val ok = DriveClient.tryInitFromLastAccount(act)
            if (ok) {
                signedInState = true
                lastBackupDisplay = fetchLbLastBackupTime()
            }
        }
    }

    // Handle system back to go to home/list
    BackHandler(enabled = true) { onBack() }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold) }
            )
        },
        contentWindowInsets = WindowInsets.safeDrawing
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            // (Sign-in UI moved to bottom with Backup & Restore)
            item { HorizontalDivider() }
            // Language
            item { Text(stringResource(R.string.language_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) }
            item {
                val storedTag by LocalePrefs.appLocaleFlow(context).collectAsState(initial = "")
                val normTag = storedTag.lowercase()
                val options = listOf(
                    "" to stringResource(R.string.system_option),
                    "en" to stringResource(R.string.english_label),
                    "hi" to stringResource(R.string.hindi_label),
                    "kn" to stringResource(R.string.kannada_label)
                )
                var expanded by remember { mutableStateOf(false) }
                val current = options.firstOrNull { (tag, _) ->
                    if (tag.isBlank()) normTag.isBlank() else normTag == tag || normTag.startsWith("$tag-")
                } ?: options.first()

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                        OutlinedTextField(
                            modifier = Modifier.menuAnchor().fillMaxWidth(),
                            value = current.second,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(stringResource(R.string.language_title)) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) }
                        )
                        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                            options.forEach { (tag, label) ->
                                DropdownMenuItem(
                                    text = { Text(label) },
                                    onClick = {
                                        expanded = false
                                        scope.launch {
                                            LocalePrefs.setAppLocale(context, tag)
                                            LocalePrefs.applyLocale(context, tag)
                                            Toast.makeText(
                                                context,
                                                when (tag) {
                                                    "en" -> "Language: English"
                                                    "hi" -> "भाषा: हिन्दी"
                                                    "kn" -> "Language: Kannada"
                                                    else -> "Language: System"
                                                },
                                                Toast.LENGTH_SHORT
                                            ).show()
                                            activity?.recreate()
                                        }
                                    },
                                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                                )
                            }
                        }
                    }
                    Text(
                        text = stringResource(R.string.changes_apply_note),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
            // Reminder thresholds
            item {
                val overdue by themeViewModel.overdueDays.collectAsState()
                val window by themeViewModel.dueSoonWindowDays.collectAsState()
                var overdueText by remember(overdue) { mutableStateOf(overdue.toString()) }
                var windowText by remember(window) { mutableStateOf(window.toString()) }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.reminders_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    OutlinedTextField(
                        value = overdueText,
                        onValueChange = { v ->
                            val f = v.filter { it.isDigit() }
                            overdueText = f
                            f.toIntOrNull()?.let { d -> themeViewModel.setOverdueDays(d.coerceIn(1, 2000)) }
                        },
                        label = { Text(stringResource(R.string.overdue_days_label)) },
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = windowText,
                        onValueChange = { v ->
                            val f = v.filter { it.isDigit() }
                            windowText = f
                            f.toIntOrNull()?.let { d -> themeViewModel.setDueSoonWindowDays(d.coerceIn(1, 365)) }
                        },
                        label = { Text(stringResource(R.string.due_soon_window_days_label)) },
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(stringResource(R.string.due_soon_hint), style = MaterialTheme.typography.labelSmall)
                }
            }
            item { HorizontalDivider() }
            // Premium
            item { Text(stringResource(R.string.premium_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(if (hasRemoveAds) stringResource(R.string.ads_removed) else stringResource(R.string.ads_enabled_msg))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(onClick = { activity?.let { monetizationVM.purchaseRemoveAds(it) } }, enabled = !hasRemoveAds) { Text(stringResource(R.string.remove_ads)) }
                        OutlinedButton(onClick = { monetizationVM.restore() }) { Text(stringResource(R.string.restore_purchase)) }
                    }
                }
            }
            item { HorizontalDivider() }
            // Features
            item { Text(stringResource(R.string.features_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) }
            item {
                val grouping by themeViewModel.groupingEnabled.collectAsState()
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.group_by_customer), style = MaterialTheme.typography.bodyLarge)
                        Text(stringResource(R.string.group_by_desc), style = MaterialTheme.typography.labelSmall)
                    }
                    Switch(checked = grouping, onCheckedChange = { themeViewModel.setGroupingEnabled(it) })
                }
            }
            // Theme section
            item { Text(stringResource(R.string.theme_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) }
            item {
                val currentMode by themeViewModel.themeMode.collectAsState()
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThemeOptionRow(
                        label = stringResource(R.string.system_option),
                        selected = currentMode == ThemeViewModel.MODE_SYSTEM,
                        onSelect = { themeViewModel.setThemeMode(ThemeViewModel.MODE_SYSTEM) }
                    )
                    ThemeOptionRow(
                        label = stringResource(R.string.light_option),
                        selected = currentMode == ThemeViewModel.MODE_LIGHT,
                        onSelect = { themeViewModel.setThemeMode(ThemeViewModel.MODE_LIGHT) }
                    )
                    ThemeOptionRow(
                        label = stringResource(R.string.dark_option),
                        selected = currentMode == ThemeViewModel.MODE_DARK,
                        onSelect = { themeViewModel.setThemeMode(ThemeViewModel.MODE_DARK) }
                    )
                }
            }
            // Backup & Restore (moved to bottom, simplified like AgroShop)
            item { HorizontalDivider() }
            item { Text(stringResource(R.string.backup_and_sync), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) }
            // Single toggle Sign in/Sign out with status
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Button(onClick = {
                        val act = activity ?: return@Button
                        if (signedInState) {
                            DriveClient.signOut(act)
                            signedInState = false
                            lastBackupDisplay = null
                            scope.launch { snackbarHostState.showSnackbar(context.getString(R.string.sign_out)) }
                        } else {
                            signInLauncher.launch(DriveClient.getSignInIntent(act))
                        }
                    }) { Text(if (signedInState) stringResource(R.string.sign_out) else stringResource(R.string.sign_in)) }
                    Text(if (signedInState) stringResource(R.string.gd_connected) else stringResource(R.string.gd_not_connected))
                }
            }
            // Last backup info
            item {
                if (signedInState) {
                    val label = lastBackupDisplay ?: stringResource(R.string.never_label)
                    Text(text = stringResource(R.string.last_backup_label, label), style = MaterialTheme.typography.bodySmall)
                }
            }
            // Backup/Restore buttons with progress, confirm dialogs and snackbar
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Button(enabled = signedInState && !isWorking, onClick = { showBackupConfirm = true }) { Text(stringResource(R.string.backup_to_drive)) }
                    Button(enabled = signedInState && !isWorking, onClick = { showRestoreConfirm = true }) { Text(stringResource(R.string.restore)) }
                    if (isWorking) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    }
                }
            }
            item {
                // Developer and version info (compact spacing)
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
                            val ctx = activity ?: return@launch
                            isWorking = true
                            val bytes = BackupManager.createBackupZip(ctx)
                            val ok = DriveClient.uploadAppData("ledgerbook-backup.zip", bytes)
                            isWorking = false
                            val msg = if (ok) context.getString(R.string.backup_uploaded) else (DriveClient.lastError() ?: context.getString(R.string.backup_failed))
                            snackbarHostState.showSnackbar(msg)
                            if (ok) {
                                lastBackupDisplay = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault()).format(Instant.now())
                            }
                        }
                    }) { Text(stringResource(R.string.ok)) }
                },
                dismissButton = { TextButton(onClick = { showBackupConfirm = false }) { Text(stringResource(R.string.cancel)) } }
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
                            val msg = if (latest != null) {
                                val bytes = DriveClient.download(latest.id)
                                if (bytes != null && activity != null && BackupManager.restoreBackupZip(activity, bytes)) context.getString(R.string.restore_complete) else (DriveClient.lastError() ?: context.getString(R.string.restore_failed))
                            } else context.getString(R.string.no_backups_found)
                            isWorking = false
                            snackbarHostState.showSnackbar(msg)
                            lastBackupDisplay = fetchLbLastBackupTime()
                        }
                    }) { Text(stringResource(R.string.ok)) }
                },
                dismissButton = { TextButton(onClick = { showRestoreConfirm = false }) { Text(stringResource(R.string.cancel)) } }
            )
        }
    }
}

@Composable
private fun ThemeOptionRow(label: String, selected: Boolean, onSelect: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Row {
            RadioButton(selected = selected, onClick = onSelect)
        }
    }
}

// Helper to fetch latest backup modified time for LedgerBook
private suspend fun fetchLbLastBackupTime(): String? {
    return try {
        val list = DriveClient.listBackups()
        val latest = list.firstOrNull()
        latest?.modifiedTime?.toString()
    } catch (_: Throwable) {
        null
    }
}

@Composable
private fun LanguageOptionRow(label: String, selected: Boolean, onSelect: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onSelect),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Row {
            RadioButton(selected = selected, onClick = onSelect)
        }
    }
}

