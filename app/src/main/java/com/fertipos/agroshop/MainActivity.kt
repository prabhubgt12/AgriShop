package com.fertipos.agroshop

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import com.fertipos.agroshop.ui.screens.LoginScreen
import com.fertipos.agroshop.ui.screens.RegisterScreen
import com.fertipos.agroshop.ui.theme.AgroShopTheme
import com.fertipos.agroshop.ui.theme.ThemeViewModel
import com.fertipos.agroshop.ui.theme.ThemeMode
import dagger.hilt.android.AndroidEntryPoint
import androidx.hilt.navigation.compose.hiltViewModel
import com.fertipos.agroshop.ui.auth.SessionViewModel

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AgroShopRoot()
        }
    }
}

@Composable
fun AgroShopRoot() {
    val themeVm: ThemeViewModel = hiltViewModel()
    val mode by themeVm.themeMode.collectAsState()
    val sessionVm: SessionViewModel = hiltViewModel()
    val loggedIn by sessionVm.loggedIn.collectAsState()
    val dark = when (mode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    AgroShopTheme(useDarkTheme = dark) {
        Surface(color = MaterialTheme.colorScheme.background) {
            val navController = rememberNavController()
            AppNavHost(navController, startDestination = if (loggedIn) Routes.Dashboard else Routes.Login)
        }
    }
}

object Routes {
    const val Login = "login"
    const val Register = "register"
    const val Dashboard = "dashboard"
}

@Composable
fun AppNavHost(navController: NavHostController, startDestination: String = Routes.Login, modifier: Modifier = Modifier) {
    NavHost(navController = navController, startDestination = startDestination, modifier = modifier) {
        composable(Routes.Login) {
            LoginScreen(
                onLoginSuccess = { navController.navigate(Routes.Dashboard) { popUpTo(Routes.Login) { inclusive = true } } },
                onRegister = { navController.navigate(Routes.Register) }
            )
        }
        composable(Routes.Register) {
            RegisterScreen(onRegistered = { navController.popBackStack() })
        }
        composable(Routes.Dashboard) {
            DashboardScreen(
                onLogout = {
                    navController.navigate(Routes.Login) {
                        popUpTo(Routes.Dashboard) { inclusive = true }
                    }
                }
            )
        }
    }
}
