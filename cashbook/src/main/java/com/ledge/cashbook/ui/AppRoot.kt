package com.ledge.cashbook.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument

@Composable
fun AppRoot() {
    val nav = rememberNavController()
    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        NavHost(navController = nav, startDestination = "home") {
            composable("home") {
                HomeScreen(
                    onOpenCashBook = { nav.navigate("accounts") },
                    onOpenSettings = { nav.navigate("settings") },
                )
            }
            composable("accounts") {
                AccountsScreen(
                    onOpenAccount = { id -> nav.navigate("account/$id") },
                    onAddToBook = { id -> nav.navigate("account/$id?add=true") }
                )
            }
            composable(
                route = "account/{id}?add={add}",
                arguments = listOf(
                    navArgument("id") { type = NavType.IntType },
                    navArgument("add") { defaultValue = false; type = NavType.BoolType }
                )
            ) { backStack ->
                val id = backStack.arguments?.getInt("id") ?: 0
                val openAdd = backStack.arguments?.getBoolean("add") ?: false
                AccountDetailScreen(accountId = id, onBack = { nav.popBackStack() }, openAdd = openAdd)
            }
            composable("settings") {
                SettingsScreen(onBack = { nav.popBackStack() })
            }
        }
    }
}
