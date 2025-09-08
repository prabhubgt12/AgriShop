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

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun SettingsScreen(onBack: () -> Unit, themeViewModel: ThemeViewModel = hiltViewModel()) {
    val scope = rememberCoroutineScope()

    var signedIn by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var backups by remember { mutableStateOf<List<File>>(emptyList()) }
    val context = LocalContext.current
    val activity = context as? Activity

    // Monetization state
    val monetizationVM: MonetizationViewModel = hiltViewModel()
    val hasRemoveAds by monetizationVM.hasRemoveAds.collectAsState()

    val signInLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        val act = activity ?: return@rememberLauncherForActivityResult
        signedIn = DriveClient.handleSignInResult(act, res.data)
        status = if (signedIn) "Signed in" else (DriveClient.lastError() ?: "Sign-in failed")
    }
    LaunchedEffect(Unit) {
        activity?.let { signedIn = DriveClient.tryInitFromLastAccount(it) }
    }

    // Handle system back to go to home/list
    BackHandler(enabled = true) { onBack() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold) }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            item {
                Text(text = if (signedIn) stringResource(R.string.gd_connected) else stringResource(R.string.gd_not_connected))
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = {
                        activity?.let { signInLauncher.launch(DriveClient.getSignInIntent(it)) }
                    }, enabled = !signedIn) { Text(stringResource(R.string.sign_in)) }
                    Button(onClick = {
                        val ctx = activity ?: return@Button
                        DriveClient.signOut(ctx)
                        signedIn = false
                        status = context.getString(R.string.sign_out)
                    }, enabled = signedIn) { Text(stringResource(R.string.sign_out)) }
                }
            }
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
            item { HorizontalDivider() }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = {
                        scope.launch {
                            val ctx = activity ?: return@launch
                            val bytes = BackupManager.createBackupZip(ctx)
                            val ok = DriveClient.uploadAppData("ledgerbook-backup.zip", bytes)
                            status = if (ok) "Backup uploaded" else (DriveClient.lastError() ?: "Backup failed")
                        }
                    }, enabled = signedIn) { Text(stringResource(R.string.backup_to_drive)) }

                    Button(onClick = {
                        scope.launch {
                            backups = DriveClient.listBackups()
                            status = "Found ${backups.size} backups"
                        }
                    }, enabled = signedIn) { Text(stringResource(R.string.list_backups)) }
                }
            }
            items(backups) { f ->
                ElevatedCard(onClick = {
                    scope.launch {
                        val bytes = DriveClient.download(f.id)
                        if (bytes != null) {
                            val ctx = activity
                            val ok = if (ctx != null) BackupManager.restoreBackupZip(ctx, bytes) else false
                            status = if (ok) "Restore complete. Restart app." else "Restore failed"
                        } else status = DriveClient.lastError() ?: "Download failed"
                    }
                }) {
                    Column(Modifier.padding(12.dp)) {
                        Text(f.name ?: "(no name)")
                        Text(f.modifiedTime?.toString() ?: "", style = MaterialTheme.typography.labelSmall)
                        Text(stringResource(R.string.tap_to_restore), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            item {
                status?.let { Text(it, style = MaterialTheme.typography.labelSmall) }
                Spacer(Modifier.height(4.dp))
                // Developer and version info (compact spacing)
                Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.developer_label), style = MaterialTheme.typography.labelSmall)
                    Text(stringResource(R.string.email_label), style = MaterialTheme.typography.labelSmall)
                    Text(stringResource(R.string.app_version_label, BuildConfig.VERSION_NAME), style = MaterialTheme.typography.labelSmall)
                }
            }
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

