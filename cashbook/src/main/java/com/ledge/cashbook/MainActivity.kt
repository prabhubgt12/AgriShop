package com.ledge.cashbook

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Alignment
import com.ledge.cashbook.ui.AppRoot
import com.ledge.cashbook.ui.theme.CashBookTheme
import com.ledge.cashbook.ui.theme.ThemeViewModel
import com.ledge.cashbook.data.prefs.LocalePrefs
import com.ledge.cashbook.util.LocaleHelper
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    private var onUnlock: (() -> Unit)? = null
    private val confirmDeviceCredential =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
            if (res.resultCode == RESULT_OK) {
                onUnlock?.invoke()
            }
        }
    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase))
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Enable edge-to-edge with backward compatibility (AndroidX Activity)
        enableEdgeToEdge()
        setContent {
            val themeVM: ThemeViewModel = hiltViewModel()
            val mode by themeVM.themeMode.collectAsState()
            val localeTag by themeVM.appLocaleTag.collectAsState()

            // Apply locale when tag changes (initial + subsequent changes)
            LaunchedEffect(localeTag) {
                LocalePrefs.applyLocale(this@MainActivity, localeTag)
            }

            var unlocked by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) {
                onUnlock = { unlocked = true }
                requestUnlockOrGrant()
            }

            val dark = when (mode) {
                ThemeViewModel.MODE_DARK -> true
                ThemeViewModel.MODE_LIGHT -> false
                else -> isSystemInDarkTheme()
            }

            CashBookTheme(darkTheme = dark) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    if (unlocked) {
                        AppRoot()
                    } else {
                        Box(modifier = androidx.compose.ui.Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
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
