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
                title = { Text("Settings", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold) }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            item {
                Text(text = if (signedIn) "Google Drive: Connected" else "Google Drive: Not Connected")
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = {
                        activity?.let { signInLauncher.launch(DriveClient.getSignInIntent(it)) }
                    }, enabled = !signedIn) { Text("Sign in") }
                    Button(onClick = {
                        val ctx = activity ?: return@Button
                        DriveClient.signOut(ctx)
                        signedIn = false
                        status = "Signed out"
                    }, enabled = signedIn) { Text("Sign out") }
                }
            }
            item { HorizontalDivider() }
            // Premium
            item { Text("Premium", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(if (hasRemoveAds) "Ads are removed on this device" else "Ads enabled. Purchase to remove permanently.")
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(onClick = { activity?.let { monetizationVM.purchaseRemoveAds(it) } }, enabled = !hasRemoveAds) { Text("Remove ads") }
                        OutlinedButton(onClick = { monetizationVM.restore() }) { Text("Restore Purchase") }
                    }
                }
            }
            item { HorizontalDivider() }
            // Features
            item { Text("Features", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) }
            item {
                val grouping by themeViewModel.groupingEnabled.collectAsState()
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Group by Customer", style = MaterialTheme.typography.bodyLarge)
                        Text("Show expandable parent with child transactions.", style = MaterialTheme.typography.labelSmall)
                    }
                    Switch(checked = grouping, onCheckedChange = { themeViewModel.setGroupingEnabled(it) })
                }
            }
            // Theme section
            item { Text("Theme", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) }
            item {
                val currentMode by themeViewModel.themeMode.collectAsState()
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThemeOptionRow(
                        label = "System",
                        selected = currentMode == ThemeViewModel.MODE_SYSTEM,
                        onSelect = { themeViewModel.setThemeMode(ThemeViewModel.MODE_SYSTEM) }
                    )
                    ThemeOptionRow(
                        label = "Light",
                        selected = currentMode == ThemeViewModel.MODE_LIGHT,
                        onSelect = { themeViewModel.setThemeMode(ThemeViewModel.MODE_LIGHT) }
                    )
                    ThemeOptionRow(
                        label = "Dark",
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
                    }, enabled = signedIn) { Text("Backup to Drive") }

                    Button(onClick = {
                        scope.launch {
                            backups = DriveClient.listBackups()
                            status = "Found ${backups.size} backups"
                        }
                    }, enabled = signedIn) { Text("List Backups") }
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
                        Text("Tap to restore", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            item {
                status?.let { Text(it, style = MaterialTheme.typography.labelSmall) }
                Spacer(Modifier.height(4.dp))
                // Developer and version info (compact spacing)
                Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.fillMaxWidth()) {
                    Text("Developer: SimpleAndro", style = MaterialTheme.typography.labelSmall)
                    Text("Email: prabhurb@gmail.com", style = MaterialTheme.typography.labelSmall)
                    Text("App version: ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.labelSmall)
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

