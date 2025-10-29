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
import com.ledge.ledgerbook.ui.loan.LoanAddScreen
import com.ledge.ledgerbook.ui.loan.LoanDetailScreen
import com.ledge.ledgerbook.ui.loan.LoanListScreen

@Composable
fun LedgerApp(onRequestLogout: () -> Unit) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        var screen by remember { mutableStateOf("home") }
        var selectedLoanId by remember { mutableStateOf<Long?>(null) }
        when (screen) {
            "home" -> HomeScreen(
                onOpenLedger = { screen = "list" },
                onOpenSettings = { screen = "settings" },
                onOpenLoanBook = { screen = "loan" },
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
            "loan" -> {
                BackHandler(enabled = true) { screen = "home" }
                LoanListScreen(
                    onBack = { screen = "home" },
                    onAdd = { selectedLoanId = null; screen = "loan_add" },
                    onOpenDetail = { id -> selectedLoanId = id; screen = "loan_detail" }
                )
            }
            "loan_add" -> {
                BackHandler(enabled = true) { screen = "loan" }
                LoanAddScreen(
                    onBack = { screen = "loan" },
                    onSaved = { id -> selectedLoanId = id; screen = "loan_detail" },
                    loanId = selectedLoanId
                )
            }
            "loan_detail" -> {
                BackHandler(enabled = true) { screen = "loan" }
                LoanDetailScreen(
                    loanId = selectedLoanId ?: -1L,
                    onBack = { screen = "loan" },
                    onEdit = { id -> selectedLoanId = id; screen = "loan_add" }
                )
            }
        }
    }
}
