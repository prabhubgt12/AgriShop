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
import androidx.compose.material3.RadioButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Alignment
import androidx.hilt.navigation.compose.hiltViewModel
import com.fertipos.agroshop.data.local.entities.CompanyProfile
import coil.compose.AsyncImage
import androidx.compose.ui.platform.LocalContext
import android.content.Intent
import com.fertipos.agroshop.ui.theme.ThemeViewModel
import com.fertipos.agroshop.ui.theme.ThemeMode
import com.fertipos.agroshop.ui.auth.SessionViewModel
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import com.fertipos.agroshop.data.backup.BackupManager
import com.fertipos.agroshop.data.backup.DriveClient
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(onLogout: () -> Unit) {
    val vm: CompanyProfileViewModel = hiltViewModel()
    val current by vm.profile.collectAsState()
    val themeVm: ThemeViewModel = hiltViewModel()
    val themeMode by themeVm.themeMode.collectAsState()
    val sessionVm: SessionViewModel = hiltViewModel()

    varStatefulForm(current, themeMode,
        onThemeChange = { themeVm.setTheme(it) },
        onLogout = {
            sessionVm.logout()
            onLogout()
        }
    ) { updated ->
        vm.save(updated)
    }
}

@Composable
private fun varStatefulForm(
    current: CompanyProfile,
    themeMode: ThemeMode,
    onThemeChange: (ThemeMode) -> Unit,
    onLogout: () -> Unit,
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

    // Google Sign-In launcher
    val signInLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        val ok = DriveClient.handleSignInResult(context, res.data)
    }

    Surface(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
                .imePadding()
                .navigationBarsPadding()
        ) {
            Text("Company Profile")
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
                            logoUri = logo
                        )
                    )
                }) { Text("Save") }
            }

            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(Modifier.height(8.dp))
            Text("Account")
            Spacer(Modifier.height(8.dp))
            Button(onClick = { onLogout() }) { Text("Logout") }

            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(Modifier.height(8.dp))
            Text("Backup & Sync")
            Spacer(Modifier.height(8.dp))

            // Sign in/out row
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                val signedIn = remember { mutableStateOf(DriveClient.isSignedIn(context)) }
                Button(onClick = {
                    if (!signedIn.value) {
                        signInLauncher.launch(DriveClient.getSignInIntent(context))
                        // update state on next composition; user returns here
                        scope.launch { kotlinx.coroutines.delay(300); signedIn.value = DriveClient.isSignedIn(context) }
                    } else {
                        DriveClient.signOut(context)
                        signedIn.value = false
                    }
                }) { Text(if (signedIn.value) "Sign out Google" else "Sign in Google") }
            }

            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    scope.launch {
                        val zip = BackupManager.createBackupZip(context)
                        val ok = DriveClient.uploadAppData("agroshop_backup.zip", zip)
                        // In a production app, show a Snackbar/Toast based on ok
                    }
                }) { Text("Backup now") }

                Button(onClick = {
                    scope.launch {
                        val files = DriveClient.listBackups()
                        val latest = files.firstOrNull()
                        if (latest != null) {
                            val bytes = DriveClient.download(latest.id)
                            if (bytes != null) {
                                BackupManager.restoreBackupZip(context, bytes)
                                // Recommend app restart after restore
                            }
                        }
                    }
                }) { Text("Restore") }
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
