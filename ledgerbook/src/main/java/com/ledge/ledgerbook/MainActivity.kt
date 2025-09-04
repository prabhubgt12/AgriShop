package com.ledge.ledgerbook

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import com.ledge.ledgerbook.ui.LedgerApp
import com.ledge.ledgerbook.ui.theme.LedgerTheme
import com.ledge.ledgerbook.ui.theme.ThemeViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private var onUnlock: (() -> Unit)? = null
    private val confirmDeviceCredential =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
            if (res.resultCode == RESULT_OK) {
                onUnlock?.invoke()
            }
        }

    // (Reverted) No explicit status bar adjustments in onResume

    private val themeVm: ThemeViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val mode = themeVm.themeMode.collectAsState().value
            LedgerTheme(themeMode = mode) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    var unlocked by remember { mutableStateOf(false) }

                    LaunchedEffect(Unit) {
                        onUnlock = { unlocked = true }
                        requestUnlockOrGrant()
                    }

                    if (unlocked) {
                        LedgerApp(onRequestLogout = { finish() })
                    } else {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("Unlocking…")
                        }
                    }
                }
            }
        }
    }

    private fun requestUnlockOrGrant() {
        val km = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        if (km.isKeyguardSecure) {
            val intent: Intent? = km.createConfirmDeviceCredentialIntent(
                getString(R.string.app_name),
                getString(R.string.unlock_to_continue)
            )
            if (intent != null) {
                confirmDeviceCredential.launch(intent)
            }
        } else {
            // No device credential set; allow access immediately
            onUnlock?.invoke()
        }
    }
}
