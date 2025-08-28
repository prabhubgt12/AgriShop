package com.ledge.ledgerbook.ui

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
        var screen by remember { mutableStateOf("list") }
        when (screen) {
            "list" -> LedgerListScreen(onOpenSettings = { screen = "settings" }, onRequestLogout = onRequestLogout)
            "settings" -> SettingsScreen(onBack = { screen = "list" })
        }
    }
}
