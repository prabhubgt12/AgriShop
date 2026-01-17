package com.ledge.splitbook

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.ledge.splitbook.ui.theme.SimpleSplitTheme
import androidx.hilt.navigation.compose.hiltViewModel
import com.ledge.splitbook.ui.vm.SettingsViewModel
import androidx.compose.runtime.collectAsState
import dagger.hilt.android.AndroidEntryPoint
import android.util.Log
import com.ledge.splitbook.BuildConfig

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    private var onUnlock: (() -> Unit)? = null
    private val confirmDeviceCredential =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
            if (res.resultCode == RESULT_OK) {
                onUnlock?.invoke()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d("SplitBook", "MainActivity onCreate")
        if (BuildConfig.SHOW_COMPOSE_MINIMAL) {
            Log.d("SplitBook", "SHOW_COMPOSE_MINIMAL -> rendering compose minimal screen")
            setContent {
                SimpleSplitTheme {
                    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(text = "Simple Split (Compose Minimal UI)")
                        }
                    }
                }
            }
            return
        } else if (BuildConfig.SHOW_MINIMAL_UI) {
            Log.d("SplitBook", "SHOW_MINIMAL_UI enabled -> rendering native minimal layout")
            val tv = android.widget.TextView(this).apply { text = "Simple Split (Native Minimal UI)"; textSize = 20f; gravity = android.view.Gravity.CENTER }
            val lp = android.widget.LinearLayout(this).apply {
                orientation = android.widget.LinearLayout.VERTICAL
                gravity = android.view.Gravity.CENTER
                addView(tv, android.widget.LinearLayout.LayoutParams(android.widget.LinearLayout.LayoutParams.MATCH_PARENT, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT))
            }
            setContentView(lp)
            return
        } else {
            enableEdgeToEdge()
        }
        setContent {
            Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                if (BuildConfig.DISABLE_LOCK) {
                    // In debug, skip any intermediate/unlock UI and show app directly
                    Log.d("SplitBook", "DISABLE_LOCK true -> showing AppRoot immediately")
                    AppRoot()
                } else {
                    var unlocked by remember { mutableStateOf(false) }
                    LaunchedEffect(Unit) {
                        Log.d("SplitBook", "LaunchedEffect: requesting unlock")
                        onUnlock = { unlocked = true }
                        requestUnlockOrGrant()
                    }
                    if (unlocked) {
                        Log.d("SplitBook", "Unlocked -> showing AppRoot")
                        AppRoot()
                    } else {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Log.d("SplitBook", "Waiting for unlock UI visible")
                            Text(text = getString(R.string.unlock_to_continue))
                        }
                    }
                }
            }
        }
    }

    private fun requestUnlockOrGrant() {
        val km = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        if (km.isKeyguardSecure) {
            Log.d("SplitBook", "Device is secure, launching confirm credentials")
            val intent: Intent? = km.createConfirmDeviceCredentialIntent(
                getString(R.string.app_name),
                getString(R.string.unlock_to_continue)
            )
            if (intent != null) {
                Log.d("SplitBook", "Launching confirmDeviceCredential activity")
                confirmDeviceCredential.launch(intent)
            } else {
                Log.w("SplitBook", "confirmDeviceCredential intent was null; unlocking directly")
                onUnlock?.invoke()
            }
        } else {
            Log.d("SplitBook", "Device not secure; unlocking directly")
            onUnlock?.invoke()
        }
    }
}

@Composable
private fun AppRoot(viewModel: SettingsViewModel = hiltViewModel()) {
    val settings = viewModel.ui.collectAsState()
    SimpleSplitTheme(darkTheme = settings.value.darkMode) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            com.ledge.splitbook.ui.navigation.NavRoot()
        }
    }
}

@Composable
private fun MinimalDebugScreen() {
    SimpleSplitTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(text = "Simple Split (Debug Minimal UI)")
            }
        }
    }
}
