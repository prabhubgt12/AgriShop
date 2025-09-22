package com.fertipos.agroshop.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.clickable
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Scaffold
import androidx.compose.material3.RadioButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.hilt.navigation.compose.hiltViewModel
import com.fertipos.agroshop.data.local.entities.CompanyProfile
import coil.compose.AsyncImage
import androidx.compose.ui.platform.LocalContext
import android.content.Intent
import com.fertipos.agroshop.ui.theme.ThemeViewModel
import com.fertipos.agroshop.ui.theme.ThemeMode
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import com.fertipos.agroshop.data.backup.BackupManager
import com.fertipos.agroshop.data.backup.DriveClient
import kotlinx.coroutines.launch
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.res.stringResource
import com.fertipos.agroshop.R
import com.fertipos.agroshop.data.prefs.LocalePrefs
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SettingsScreen() {
    val vm: CompanyProfileViewModel = hiltViewModel()
    val current by vm.profile.collectAsState()
    val themeVm: ThemeViewModel = hiltViewModel()
    val themeMode by themeVm.themeMode.collectAsState()
    // Monetization (remove-ads) state and actions
    val monetVm: com.fertipos.agroshop.billing.MonetizationViewModel = hiltViewModel()
    val hasRemoveAds by monetVm.hasRemoveAds.collectAsState()

    varStatefulForm(current, themeMode,
        onThemeChange = { themeVm.setTheme(it) },
        hasRemoveAds = hasRemoveAds,
        onBuyRemoveAds = { act -> monetVm.purchaseRemoveAds(act) },
        onRestorePurchases = { monetVm.restore() }
    ) { updated ->
        vm.save(updated)
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun varStatefulForm(
    current: CompanyProfile,
    themeMode: ThemeMode,
    onThemeChange: (ThemeMode) -> Unit,
    hasRemoveAds: Boolean,
    onBuyRemoveAds: (android.app.Activity) -> Unit,
    onRestorePurchases: () -> Unit,
    onSave: (CompanyProfile) -> Unit
) {
    var name by remember(current) { mutableStateOf(current.name) }
    var addr1 by remember(current) { mutableStateOf(current.addressLine1) }
    var addr2 by remember(current) { mutableStateOf(current.addressLine2) }
    var city by remember(current) { mutableStateOf(current.city) }
    var state by remember(current) { mutableStateOf(current.state) }
    var pin by remember(current) { mutableStateOf(current.pincode) }
    var gst by remember(current) { mutableStateOf(current.gstin) }
    var phone by remember(current) { mutableStateOf(current.phone) }
    var email by remember(current) { mutableStateOf(current.email) }
    var logo by remember(current) { mutableStateOf(current.logoUri) }
    var productTypesCsv by remember(current) { mutableStateOf(current.productTypesCsv) }
    var unitsCsv by remember(current) { mutableStateOf(current.unitsCsv) }
    var lowStockThreshold by remember(current) { mutableStateOf(current.lowStockThreshold.toString()) }

    // System document picker with persistable permission for logo
    val context = LocalContext.current
    val pickLogo = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            // Persist permission so we can read the image later for PDF
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) { /* ignore if not grantable */ }
            logo = uri.toString()
        }
    }

    val scope = rememberCoroutineScope()

    // UI state for backup/restore feedback
    val snackbarHostState = remember { SnackbarHostState() }
    var isWorking by remember { mutableStateOf(false) }
    val signedIn = remember { mutableStateOf(DriveClient.isSignedIn(context)) }
    var showBackupConfirm by remember { mutableStateOf(false) }
    var showRestoreConfirm by remember { mutableStateOf(false) }
    var lastBackupDisplay by remember { mutableStateOf<String?>(null) }

    // Google Sign-In launcher
    val signInLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        val ok = DriveClient.handleSignInResult(context, res.data)
        signedIn.value = ok && DriveClient.isSignedIn(context)
        scope.launch {
            val msg = if (signedIn.value) context.getString(R.string.signed_in_to_google) else context.getString(
                R.string.sign_in_failed_with_error,
                DriveClient.lastError() ?: context.getString(R.string.unknown_error)
            )
            snackbarHostState.showSnackbar(msg)
            if (signedIn.value) {
                // Fetch latest backup time
                val files = DriveClient.listBackups()
                val ts = files.firstOrNull()?.modifiedTime?.value
                lastBackupDisplay = ts?.let { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(it)) }
            } else {
                lastBackupDisplay = null
            }
        }
    }

    // Try silently initializing Drive from last account (if user already granted scope)
    LaunchedEffect(Unit) {
        if (!signedIn.value) {
            val ok = DriveClient.tryInitFromLastAccount(context)
            if (ok) {
                signedIn.value = true
                val files = DriveClient.listBackups()
                val ts = files.firstOrNull()?.modifiedTime?.value
                lastBackupDisplay = ts?.let { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(it)) }
            }
        } else {
            val files = DriveClient.listBackups()
            val ts = files.firstOrNull()?.modifiedTime?.value
            lastBackupDisplay = ts?.let { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(it)) }
        }
    }

    Surface(Modifier.fillMaxSize()) {
        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
        ) { padding ->
        Column(
            Modifier
                .fillMaxWidth()
                .padding(padding).padding(horizontal = 8.dp, vertical = 8.dp)
                .verticalScroll(rememberScrollState())
                .imePadding()
                .navigationBarsPadding()
        ) {
            Text(stringResource(R.string.company_profile), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
            Spacer(Modifier.height(6.dp))

            OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text(stringResource(R.string.name_label)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(value = addr1, onValueChange = { addr1 = it }, label = { Text(stringResource(R.string.address_line1_label)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(value = addr2, onValueChange = { addr2 = it }, label = { Text(stringResource(R.string.address_line2_label)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(6.dp))

            Row(Modifier.fillMaxWidth()) {
                OutlinedTextField(value = city, onValueChange = { city = it }, label = { Text(stringResource(R.string.city_label)) }, singleLine = true, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                OutlinedTextField(value = state, onValueChange = { state = it }, label = { Text(stringResource(R.string.state_label)) }, singleLine = true, modifier = Modifier.weight(1f))
            }
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth()) {
                OutlinedTextField(value = pin, onValueChange = { pin = it }, label = { Text(stringResource(R.string.pincode_label)) }, singleLine = true, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text(stringResource(R.string.phone_label)) }, singleLine = true, modifier = Modifier.weight(1f))
            }
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth()) {
                OutlinedTextField(value = gst, onValueChange = { gst = it }, label = { Text(stringResource(R.string.gstin_label)) }, singleLine = true, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text(stringResource(R.string.email_label)) }, singleLine = true, modifier = Modifier.weight(1f))
            }

            Spacer(Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.inventory))
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(
                value = lowStockThreshold,
                onValueChange = { raw ->
                    val digits = raw.filter { it.isDigit() }
                    lowStockThreshold = digits
                },
                label = { Text(stringResource(R.string.low_stock_threshold)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.theme))
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                ThemeChoice(label = stringResource(R.string.theme_system), selected = themeMode == ThemeMode.SYSTEM) { onThemeChange(ThemeMode.SYSTEM) }
                ThemeChoice(label = stringResource(R.string.theme_light), selected = themeMode == ThemeMode.LIGHT) { onThemeChange(ThemeMode.LIGHT) }
                ThemeChoice(label = stringResource(R.string.theme_dark), selected = themeMode == ThemeMode.DARK) { onThemeChange(ThemeMode.DARK) }
            }

            Spacer(Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(Modifier.height(8.dp))
            // Language
            Text(stringResource(R.string.language_title))
            Spacer(Modifier.height(6.dp))
            run {
                var expanded by remember { mutableStateOf(false) }
                var tag by remember { mutableStateOf(LocalePrefs.getAppLocale(context)) }
                val options = listOf(
                    "" to stringResource(R.string.system_option),
                    "en" to "English",
                    "hi" to "हिन्दी",
                    "kn" to "ಕನ್ನಡ"
                )
                val normTag = tag.lowercase()
                val currentOption = options.firstOrNull { (t, _) ->
                    if (t.isBlank()) normTag.isBlank() else normTag == t || normTag.startsWith("$t-")
                } ?: options.first()
                androidx.compose.foundation.layout.Box(Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        modifier = Modifier.fillMaxWidth(),
                        value = currentOption.second,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.language_title)) }
                    )
                    // Click overlay to open menu
                    androidx.compose.foundation.layout.Box(
                        modifier = Modifier
                            .matchParentSize()
                            .clickable { expanded = true }
                    )
                    androidx.compose.material3.DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        options.forEach { (t, label) ->
                            androidx.compose.material3.DropdownMenuItem(
                                text = { Text(label) },
                                onClick = {
                                    expanded = false
                                    if (tag != t) {
                                        tag = t
                                        LocalePrefs.setAppLocale(context, t)
                                        LocalePrefs.applyLocale(context, t)
                                        // Recreate activity to ensure immediate UI refresh
                                        (context as? android.app.Activity)?.recreate()
                                        scope.launch { snackbarHostState.showSnackbar(context.getString(R.string.changes_apply_note)) }
                                    }
                                }
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth()) {
                Button(onClick = { pickLogo.launch(arrayOf("image/*")) }) { Text(if (logo.isBlank()) stringResource(R.string.pick_logo) else stringResource(R.string.change_logo)) }
                if (logo.isNotBlank()) {
                    Spacer(Modifier.width(8.dp))
                    AsyncImage(
                        model = logo,
                        contentDescription = stringResource(R.string.logo_preview),
                        modifier = Modifier.size(40.dp)
                    )
                }
                Spacer(Modifier.weight(1f))
                Button(onClick = {
                    onSave(
                        CompanyProfile(
                            id = 1,
                            name = name,
                            addressLine1 = addr1,
                            addressLine2 = addr2,
                            city = city,
                            state = state,
                            pincode = pin,
                            gstin = gst,
                            phone = phone,
                            email = email,
                            logoUri = logo,
                            productTypesCsv = productTypesCsv,
                            unitsCsv = unitsCsv,
                            lowStockThreshold = lowStockThreshold.toIntOrNull() ?: 10
                        )
                    )
                    scope.launch { snackbarHostState.showSnackbar(context.getString(R.string.settings_saved)) }
                }) { Text(stringResource(R.string.save)) }
            }

            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.premium))
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val act = (context as? android.app.Activity)
                Button(
                    onClick = { act?.let { onBuyRemoveAds(it) } },
                    enabled = act != null && !hasRemoveAds
                ) { Text(stringResource(R.string.remove_ads)) }

                Button(onClick = { onRestorePurchases() }) { Text(stringResource(R.string.restore_purchases)) }
            }

            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.product_types_csv))
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(
                value = productTypesCsv,
                onValueChange = { productTypesCsv = it },
                label = { Text(stringResource(R.string.types_comma_separated)) },
                singleLine = false,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.units_csv))
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(
                value = unitsCsv,
                onValueChange = { unitsCsv = it },
                label = { Text(stringResource(R.string.units_comma_separated)) },
                singleLine = false,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.backup_and_sync))
            Spacer(Modifier.height(8.dp))
            // Google Drive sign-in status and single action button (toggle sign in/out)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Start,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(onClick = {
                    if (signedIn.value) {
                        DriveClient.signOut(context)
                        signedIn.value = false
                        scope.launch { snackbarHostState.showSnackbar(context.getString(R.string.signed_out)) }
                    } else {
                        signInLauncher.launch(DriveClient.getSignInIntent(context))
                    }
                }) {
                    Text(if (signedIn.value) stringResource(R.string.sign_out_google) else stringResource(R.string.sign_in_google))
                }
                Spacer(Modifier.width(12.dp))
                Text(if (signedIn.value) stringResource(R.string.signed_in) else stringResource(R.string.not_signed_in))
            }
            Spacer(Modifier.height(8.dp))

            // Backup/Restore row
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(
                    enabled = !isWorking,
                    onClick = { showBackupConfirm = true }
                ) { Text(stringResource(R.string.backup_now)) }

                Button(enabled = signedIn.value && !isWorking, onClick = { showRestoreConfirm = true }) { Text(stringResource(R.string.restore)) }

                if (isWorking) {
                    CircularProgressIndicator(modifier = Modifier.height(24.dp))
                }
            }

            // Last backup info line (small, like LedgerBook)
            if (signedIn.value) {
                Spacer(Modifier.height(8.dp))
                val label = lastBackupDisplay ?: stringResource(R.string.never_label)
                Text(
                    text = stringResource(R.string.last_backup_label, label),
                    style = MaterialTheme.typography.bodySmall
                )
            }

            // Developer and version info (compact, like LedgerBook)
            Spacer(Modifier.height(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.developer_info_label), style = MaterialTheme.typography.labelSmall)
                Text(stringResource(R.string.developer_email_label), style = MaterialTheme.typography.labelSmall)
                Text(stringResource(R.string.app_version_label_dev, com.fertipos.agroshop.BuildConfig.VERSION_NAME), style = MaterialTheme.typography.labelSmall)
            }

            // Confirmation dialogs
            if (showBackupConfirm) {
                androidx.compose.material3.AlertDialog(
                    onDismissRequest = { showBackupConfirm = false },
                    title = { Text(stringResource(R.string.backup_confirm_title)) },
                    text = { Text(stringResource(R.string.backup_confirm_message)) },
                    confirmButton = {
                        androidx.compose.material3.TextButton(onClick = {
                            showBackupConfirm = false
                            scope.launch {
                                isWorking = true
                                val zip = BackupManager.createBackupZip(context)
                                val ok = DriveClient.uploadAppData("agroshop_backup.zip", zip)
                                isWorking = false
                                val msg = if (ok) context.getString(R.string.backup_uploaded) else context.getString(
                                    R.string.backup_failed_with_error,
                                    DriveClient.lastError() ?: context.getString(R.string.unknown_error)
                                )
                                snackbarHostState.showSnackbar(msg)
                                if (ok) {
                                    lastBackupDisplay = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date())
                                }
                            }
                        }) { Text(stringResource(R.string.ok)) }
                    },
                    dismissButton = { androidx.compose.material3.TextButton(onClick = { showBackupConfirm = false }) { Text(stringResource(R.string.cancel)) } }
                )
            }

            if (showRestoreConfirm) {
                androidx.compose.material3.AlertDialog(
                    onDismissRequest = { showRestoreConfirm = false },
                    title = { Text(stringResource(R.string.restore_confirm_title)) },
                    text = { Text(stringResource(R.string.restore_confirm_message)) },
                    confirmButton = {
                        androidx.compose.material3.TextButton(onClick = {
                            showRestoreConfirm = false
                            scope.launch {
                                isWorking = true
                                val files = DriveClient.listBackups()
                                val latest = files.firstOrNull()
                                val msg = if (latest != null) {
                                    val bytes = DriveClient.download(latest.id)
                                    if (bytes != null && BackupManager.restoreBackupZip(context, bytes)) context.getString(R.string.restore_complete_restart) else context.getString(
                                        R.string.restore_failed_with_error,
                                        DriveClient.lastError() ?: context.getString(R.string.unknown_error)
                                    )
                                } else {
                                    context.getString(R.string.no_backups_found)
                                }
                                isWorking = false
                                snackbarHostState.showSnackbar(msg)
                                // Refresh last backup time after restore
                                val refreshed = DriveClient.listBackups().firstOrNull()?.modifiedTime?.value
                                lastBackupDisplay = refreshed?.let { java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date(it)) }
                            }
                        }) { Text(stringResource(R.string.ok)) }
                    },
                    dismissButton = { androidx.compose.material3.TextButton(onClick = { showRestoreConfirm = false }) { Text(stringResource(R.string.cancel)) } }
                )
            }
        }
        }
    }
}

@Composable
private fun ThemeChoice(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.clickable { onClick() }
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Spacer(Modifier.width(4.dp))
        Text(label)
    }
}
