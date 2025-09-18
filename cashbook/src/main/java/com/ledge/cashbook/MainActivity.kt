package com.ledge.cashbook

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import com.ledge.cashbook.ui.AppRoot
import com.ledge.cashbook.ui.theme.CashBookTheme
import com.ledge.cashbook.ui.theme.ThemeViewModel
import com.ledge.cashbook.data.prefs.LocalePrefs
import com.ledge.cashbook.util.LocaleHelper
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase))
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val themeVM: ThemeViewModel = hiltViewModel()
            val mode by themeVM.themeMode.collectAsState()
            val localeTag by themeVM.appLocaleTag.collectAsState()

            // Apply locale when tag changes (initial + subsequent changes)
            LaunchedEffect(localeTag) {
                LocalePrefs.applyLocale(this@MainActivity, localeTag)
            }

            val dark = when (mode) {
                ThemeViewModel.MODE_DARK -> true
                ThemeViewModel.MODE_LIGHT -> false
                else -> isSystemInDarkTheme()
            }

            CashBookTheme(darkTheme = dark) {
                Surface(color = MaterialTheme.colorScheme.background) { AppRoot() }
            }
        }
    }
}
