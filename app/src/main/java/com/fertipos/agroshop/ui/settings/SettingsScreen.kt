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

@Composable
fun SettingsScreen() {
    val vm: CompanyProfileViewModel = hiltViewModel()
    val current by vm.profile.collectAsState()
    val themeVm: ThemeViewModel = hiltViewModel()
    val themeMode by themeVm.themeMode.collectAsState()

    varStatefulForm(current, themeMode,
        onThemeChange = { themeVm.setTheme(it) },
    ) { updated ->
        vm.save(updated)
    }
}

@Composable
private fun varStatefulForm(
    current: CompanyProfile,
    themeMode: ThemeMode,
    onThemeChange: (ThemeMode) -> Unit,
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

    // Google Sign-In launcher
    val signInLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        val ok = DriveClient.handleSignInResult(context, res.data)
        signedIn.value = ok && DriveClient.isSignedIn(context)
        scope.launch {
            snackbarHostState.showSnackbar(
                if (signedIn.value) "Signed in to Google" else "Sign-in failed: ${DriveClient.lastError() ?: "Unknown error"}"
            )
        }
    }

    // Try silently initializing Drive from last account (if user already granted scope)
    LaunchedEffect(Unit) {
        if (!signedIn.value) {
            val ok = DriveClient.tryInitFromLastAccount(context)
            if (ok) signedIn.value = true
        }
    }

    Surface(Modifier.fillMaxSize()) {
        Scaffold(snackbarHost = { SnackbarHost(hostState = snackbarHostState) }) { padding ->
        Column(
            Modifier
                .fillMaxWidth()
                .padding(padding).padding(horizontal = 8.dp, vertical = 8.dp)
                .verticalScroll(rememberScrollState())
                .imePadding()
                .navigationBarsPadding()
        ) {
            Text("Company Profile", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
            Spacer(Modifier.height(6.dp))

            OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(value = addr1, onValueChange = { addr1 = it }, label = { Text("Address Line 1") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(value = addr2, onValueChange = { addr2 = it }, label = { Text("Address Line 2") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(6.dp))

            Row(Modifier.fillMaxWidth()) {
                OutlinedTextField(value = city, onValueChange = { city = it }, label = { Text("City") }, singleLine = true, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                OutlinedTextField(value = state, onValueChange = { state = it }, label = { Text("State") }, singleLine = true, modifier = Modifier.weight(1f))
            }
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth()) {
                OutlinedTextField(value = pin, onValueChange = { pin = it }, label = { Text("Pincode") }, singleLine = true, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text("Phone") }, singleLine = true, modifier = Modifier.weight(1f))
            }
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth()) {
                OutlinedTextField(value = gst, onValueChange = { gst = it }, label = { Text("GSTIN") }, singleLine = true, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("Email") }, singleLine = true, modifier = Modifier.weight(1f))
            }

            Spacer(Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(Modifier.height(8.dp))
            Text("Inventory")
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(
                value = lowStockThreshold,
                onValueChange = { raw ->
                    val digits = raw.filter { it.isDigit() }
                    lowStockThreshold = digits
                },
                label = { Text("Low stock threshold") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(Modifier.height(8.dp))
            Text("Theme")
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                ThemeChoice(label = "System", selected = themeMode == ThemeMode.SYSTEM) { onThemeChange(ThemeMode.SYSTEM) }
                ThemeChoice(label = "Light", selected = themeMode == ThemeMode.LIGHT) { onThemeChange(ThemeMode.LIGHT) }
                ThemeChoice(label = "Dark", selected = themeMode == ThemeMode.DARK) { onThemeChange(ThemeMode.DARK) }
            }

            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth()) {
                Button(onClick = { pickLogo.launch(arrayOf("image/*")) }) { Text(if (logo.isBlank()) "Pick Logo" else "Change Logo") }
                if (logo.isNotBlank()) {
                    Spacer(Modifier.width(8.dp))
                    AsyncImage(
                        model = logo,
                        contentDescription = "Logo preview",
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
                    scope.launch { snackbarHostState.showSnackbar("Settings saved") }
                }) { Text("Save") }
            }

            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(Modifier.height(8.dp))
            Text("Product Types (CSV)")
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(
                value = productTypesCsv,
                onValueChange = { productTypesCsv = it },
                label = { Text("Types (comma-separated)") },
                singleLine = false,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(Modifier.height(8.dp))
            Text("Units (CSV)")
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(
                value = unitsCsv,
                onValueChange = { unitsCsv = it },
                label = { Text("Units (comma-separated)") },
                singleLine = false,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(Modifier.height(8.dp))
            Text("Backup & Sync")
            Spacer(Modifier.height(8.dp))

            // Sign in/out row
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(enabled = !isWorking, onClick = {
                    if (!signedIn.value) {
                        signInLauncher.launch(DriveClient.getSignInIntent(context))
                    } else {
                        DriveClient.signOut(context)
                        signedIn.value = false
                        scope.launch { snackbarHostState.showSnackbar("Signed out") }
                    }
                }) { Text(if (signedIn.value) "Sign out Google" else "Sign in Google") }
                Text(if (signedIn.value) "Signed in" else "Not signed in")
            }

            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(enabled = signedIn.value && !isWorking, onClick = {
                    scope.launch {
                        isWorking = true
                        val zip = BackupManager.createBackupZip(context)
                        val ok = DriveClient.uploadAppData("agroshop_backup.zip", zip)
                        isWorking = false
                        snackbarHostState.showSnackbar(
                            if (ok) "Backup uploaded" else "Backup failed: ${DriveClient.lastError() ?: "Unknown error"}"
                        )
                    }
                }) { Text("Backup now") }

                Button(enabled = signedIn.value && !isWorking, onClick = {
                    scope.launch {
                        isWorking = true
                        val files = DriveClient.listBackups()
                        val latest = files.firstOrNull()
                        val msg = if (latest != null) {
                            val bytes = DriveClient.download(latest.id)
                            if (bytes != null && BackupManager.restoreBackupZip(context, bytes)) "Restore complete (restart app)" else "Restore failed: ${DriveClient.lastError() ?: "Unknown error"}"
                        } else {
                            "No backups found"
                        }
                        isWorking = false
                        snackbarHostState.showSnackbar(msg)
                    }
                }) { Text("Restore") }

                if (isWorking) {
                    CircularProgressIndicator(modifier = Modifier.height(24.dp))
                }
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
