package com.ledge.cashbook.ui

import android.app.Activity
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
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

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun SettingsScreen(onBack: () -> Unit, themeViewModel: ThemeViewModel = hiltViewModel()) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val activity = context as? Activity

    var signedIn by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var backups by remember { mutableStateOf<List<File>>(emptyList()) }

    val signInLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        val act = activity ?: return@rememberLauncherForActivityResult
        signedIn = DriveClient.handleSignInResult(act, res.data)
        status = if (signedIn) context.getString(R.string.gd_connected) else (DriveClient.lastError() ?: context.getString(R.string.sign_in_failed))
    }
    LaunchedEffect(Unit) { activity?.let { signedIn = DriveClient.tryInitFromLastAccount(it) } }

    BackHandler(enabled = true) { onBack() }

    Scaffold(topBar = {
        TopAppBar(title = { Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold) })
    }) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            // Google Drive status and actions
            item { Text(text = if (signedIn) stringResource(R.string.gd_connected) else stringResource(R.string.gd_not_connected)) }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = { activity?.let { signInLauncher.launch(DriveClient.getSignInIntent(it)) } }, enabled = !signedIn) {
                        Text(stringResource(R.string.sign_in))
                    }
                    Button(onClick = { activity?.let { DriveClient.signOut(it); signedIn = false; status = context.getString(R.string.sign_out) } }, enabled = signedIn) {
                        Text(stringResource(R.string.sign_out))
                    }
                }
            }
            item { HorizontalDivider() }

            // Language
            item { Text(stringResource(R.string.language_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) }
            item {
                val storedTag by themeViewModel.appLocaleTag.collectAsState()
                val normTag = storedTag.lowercase()
                val options = listOf(
                    "" to stringResource(R.string.system_option),
                    "en" to stringResource(R.string.english_label),
                    "hi" to stringResource(R.string.hindi_label),
                    "kn" to stringResource(R.string.kannada_label)
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
                val currentMode by themeViewModel.themeMode.collectAsState()
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThemeOptionRow(label = stringResource(R.string.system_option), selected = currentMode == ThemeViewModel.MODE_SYSTEM) { themeViewModel.setThemeMode(ThemeViewModel.MODE_SYSTEM) }
                    ThemeOptionRow(label = stringResource(R.string.light_option), selected = currentMode == ThemeViewModel.MODE_LIGHT) { themeViewModel.setThemeMode(ThemeViewModel.MODE_LIGHT) }
                    ThemeOptionRow(label = stringResource(R.string.dark_option), selected = currentMode == ThemeViewModel.MODE_DARK) { themeViewModel.setThemeMode(ThemeViewModel.MODE_DARK) }
                }
            }
            item { HorizontalDivider() }

            // Backup/Restore
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = {
                        scope.launch {
                            val act = activity ?: return@launch
                            val bytes = BackupManager.createBackupZip(act)
                            val ok = DriveClient.uploadAppData("cashbook-backup.zip", bytes)
                            status = if (ok) context.getString(R.string.backup_uploaded) else (DriveClient.lastError() ?: context.getString(R.string.backup_failed))
                        }
                    }, enabled = signedIn) { Text(stringResource(R.string.backup_to_drive)) }

                    Button(onClick = {
                        scope.launch {
                            backups = DriveClient.listBackups()
                            status = context.getString(R.string.listed_backups, backups.size)
                        }
                    }, enabled = signedIn) { Text(stringResource(R.string.list_backups)) }
                }
            }
            items(backups) { f ->
                ElevatedCard(onClick = {
                    scope.launch {
                        val bytes = DriveClient.download(f.id)
                        if (bytes != null) {
                            val act = activity
                            val ok = if (act != null) BackupManager.restoreBackupZip(act, bytes) else false
                            status = if (ok) context.getString(R.string.restore_complete) else context.getString(R.string.restore_failed)
                        } else status = DriveClient.lastError() ?: context.getString(R.string.download_failed)
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
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Row { RadioButton(selected = selected, onClick = onSelect) }
    }
}
