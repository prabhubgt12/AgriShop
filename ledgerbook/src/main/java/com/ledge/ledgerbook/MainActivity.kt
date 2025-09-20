package com.ledge.ledgerbook

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.graphics.Color
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowInsetsControllerCompat
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.graphics.luminance
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.ledge.ledgerbook.ui.LedgerApp
import com.ledge.ledgerbook.ui.theme.LedgerTheme
import com.ledge.ledgerbook.ui.theme.ThemeViewModel
import dagger.hilt.android.AndroidEntryPoint
import com.ledge.ledgerbook.util.LocaleHelper

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private var onUnlock: (() -> Unit)? = null
    private val confirmDeviceCredential =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
            if (res.resultCode == RESULT_OK) {
                onUnlock?.invoke()
            }
        }

    private val themeVm: ThemeViewModel by viewModels()

    override fun attachBaseContext(newBase: Context) {
        // Wrap base context with selected locale (helps Android 11/OEMs)
        super.attachBaseContext(LocaleHelper.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Enable edge-to-edge with backward compatibility (AndroidX 1.9+)
        enableEdgeToEdge()
        setContent {
            val mode = themeVm.themeMode.collectAsState().value
            LedgerTheme(themeMode = mode) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    // Adjust status/navigation bar icon appearance based on theme
                    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
                    LaunchedEffect(isDark) {
                        val controller = WindowInsetsControllerCompat(window, window.decorView)
                        controller.isAppearanceLightStatusBars = !isDark
                        controller.isAppearanceLightNavigationBars = !isDark
                    }
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
