package com.ledge.ledgerbook.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.ledge.ledgerbook.ui.settings.SettingsScreen

@Composable
fun LedgerApp(onRequestLogout: () -> Unit) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        var screen by remember { mutableStateOf("home") }
        when (screen) {
            "home" -> HomeScreen(
                onOpenLedger = { screen = "list" },
                onOpenSettings = { screen = "settings" },
                onOpenEmi = { screen = "emi" },
                onRequestLogout = onRequestLogout
            )
            "list" -> {
                BackHandler(enabled = true) { screen = "home" }
                LedgerListScreen()
            }
            "settings" -> SettingsScreen(onBack = { screen = "home" })
            "emi" -> {
                BackHandler(enabled = true) { screen = "home" }
                EMICalculatorScreen(onBack = { screen = "home" })
            }
        }
    }
}
