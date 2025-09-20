package com.fertipos.agroshop

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowInsetsControllerCompat
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.fertipos.agroshop.ui.screens.DashboardScreen
import com.fertipos.agroshop.ui.theme.AgroShopTheme
import com.fertipos.agroshop.ui.theme.ThemeViewModel
import com.fertipos.agroshop.ui.theme.ThemeMode
import dagger.hilt.android.AndroidEntryPoint
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import com.fertipos.agroshop.R
import com.fertipos.agroshop.data.prefs.LocalePrefs
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.fertipos.agroshop.util.LocaleHelper

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private var onUnlock: (() -> Unit)? = null
    private val confirmDeviceCredential =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
            if (res.resultCode == RESULT_OK) {
                onUnlock?.invoke()
            }
        }

    override fun attachBaseContext(newBase: Context) {
        // Wrap context with selected locale before any UI inflation (Android 11/OEMs)
        super.attachBaseContext(LocaleHelper.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Edge-to-edge for Android 15+ with backward compatibility
        enableEdgeToEdge()
        setContent {
            val themeVm: ThemeViewModel = hiltViewModel()
            val mode by themeVm.themeMode.collectAsState()
            val dark = when (mode) {
                ThemeMode.DARK -> true
                ThemeMode.LIGHT -> false
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
            }
            AgroShopTheme(useDarkTheme = dark) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    // Adjust status/navigation bar icon colors for readability
                    LaunchedEffect(dark) {
                        val controller = WindowInsetsControllerCompat(window, window.decorView)
                        controller.isAppearanceLightStatusBars = !dark
                        controller.isAppearanceLightNavigationBars = !dark
                    }
                    var unlocked by remember { mutableStateOf(false) }

                    LaunchedEffect(Unit) {
                        onUnlock = { unlocked = true }
                        this@MainActivity.requestUnlockOrGrant()
                    }

                    if (unlocked) {
                        val navController = rememberNavController()
                        AppNavHost(navController, startDestination = Routes.Dashboard)
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

object Routes {
    const val Dashboard = "dashboard"
}

@Composable
fun AppNavHost(navController: NavHostController, startDestination: String = Routes.Dashboard, modifier: Modifier = Modifier) {
    NavHost(navController = navController, startDestination = startDestination, modifier = modifier) {
        composable(Routes.Dashboard) {
            DashboardScreen()
        }
    }
}
