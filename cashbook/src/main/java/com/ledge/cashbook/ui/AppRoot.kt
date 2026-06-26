package com.ledge.cashbook.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import android.widget.Toast
import java.net.URLEncoder
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

@Composable
fun AppRoot() {
    val nav = rememberNavController()
    val ctx = LocalContext.current
    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        NavHost(navController = nav, startDestination = "home") {
            composable("home") {
                HomeScreen(
                    onOpenCashBook = { nav.navigate("accounts") },
                    onOpenSettings = { nav.navigate("settings") },
                    onOpenCategories = { nav.navigate("categories") },
                )
            }
            composable("accounts") {
                AccountsScreen(
                    onOpenAccount = { id -> nav.navigate("account/$id") },
                    onAddToBook = { id -> nav.navigate("account/$id?add=true") },
                    onVoiceAdd = { id, note, amount -> 
                        val encodedNote = URLEncoder.encode(note, StandardCharsets.UTF_8.toString())
                        val encodedAmount = URLEncoder.encode(amount, StandardCharsets.UTF_8.toString())
                        nav.navigate("account/$id?add=true&note=$encodedNote&amount=$encodedAmount")
                    }
                )
            }
            composable(
                route = "account/{id}?add={add}&note={note}&amount={amount}",
                arguments = listOf(
                    navArgument("id") { type = NavType.IntType },
                    navArgument("add") { defaultValue = false; type = NavType.BoolType },
                    navArgument("note") { defaultValue = ""; type = NavType.StringType },
                    navArgument("amount") { defaultValue = ""; type = NavType.StringType }
                )
            ) { backStack ->
                val id = backStack.arguments?.getInt("id") ?: 0
                val openAdd = backStack.arguments?.getBoolean("add") ?: false
                val rawNote = backStack.arguments?.getString("note") ?: ""
                val rawAmount = backStack.arguments?.getString("amount") ?: ""
                val note = URLDecoder.decode(rawNote, StandardCharsets.UTF_8.toString())
                val amount = URLDecoder.decode(rawAmount, StandardCharsets.UTF_8.toString())
                AccountDetailScreen(accountId = id, onBack = { nav.popBackStack() }, openAdd = openAdd, prefillNote = note, prefillAmount = amount)
            }
            composable("settings") {
                SettingsScreen(onBack = { nav.popBackStack() })
            }
            composable("categories") {
                CategoriesScreen(onBack = { nav.popBackStack() })
            }
        }
    }
}
