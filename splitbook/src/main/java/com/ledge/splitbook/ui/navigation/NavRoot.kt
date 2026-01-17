package com.ledge.splitbook.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.NavType
import com.ledge.splitbook.ui.screens.AddExpenseScreen
import com.ledge.splitbook.ui.screens.GroupsScreen
import com.ledge.splitbook.ui.screens.SettleScreen
import com.ledge.splitbook.ui.screens.MembersScreen
import com.ledge.splitbook.ui.screens.TransactionsScreen
import com.ledge.splitbook.ui.screens.SettingsScreen
import com.ledge.splitbook.ui.screens.SettleDetailsScreen
import java.net.URLEncoder
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

object Routes {
    const val GROUPS = "groups"
    const val ADD_EXPENSE = "addExpense/{groupId}?payerId={payerId}"
    fun addExpense(groupId: Long, payerId: Long? = null): String {
        return if (payerId != null) "addExpense/$groupId?payerId=$payerId" else "addExpense/$groupId?payerId="
    }
    const val SETTLE = "settle/{groupId}?groupName={groupName}"
    fun settle(groupId: Long, groupName: String): String {
        val enc = URLEncoder.encode(groupName, StandardCharsets.UTF_8.toString())
        return "settle/$groupId?groupName=$enc"
    }
    const val SETTLE_DETAILS = "settleDetails/{groupId}?groupName={groupName}"
    fun settleDetails(groupId: Long, groupName: String): String {
        val enc = URLEncoder.encode(groupName, StandardCharsets.UTF_8.toString())
        return "settleDetails/$groupId?groupName=$enc"
    }
    const val MEMBERS = "members/{groupId}"
    fun members(groupId: Long) = "members/$groupId"
    const val TRANSACTIONS = "transactions/{groupId}?groupName={groupName}"
    fun transactions(groupId: Long, groupName: String): String {
        val enc = URLEncoder.encode(groupName, StandardCharsets.UTF_8.toString())
        return "transactions/$groupId?groupName=$enc"
    }
    const val SETTINGS = "settings"
}

@Composable
fun NavRoot() {
    val navController = rememberNavController()
    AppNavHost(navController)
}

@Composable
private fun AppNavHost(navController: NavHostController) {
    NavHost(navController = navController, startDestination = Routes.GROUPS) {
        composable(Routes.GROUPS) {
            GroupsScreen(
                onOpenGroup = { gid, name -> navController.navigate(Routes.settle(gid, name)) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) }
            )
        }
        composable(
            route = Routes.ADD_EXPENSE,
            arguments = listOf(
                navArgument("groupId") { type = NavType.LongType },
                navArgument("payerId") { type = NavType.LongType; defaultValue = Long.MIN_VALUE }
            )
        ) { backStackEntry ->
            val gid = backStackEntry.arguments?.getLong("groupId") ?: 0L
            val rawPayer = backStackEntry.arguments?.getLong("payerId") ?: Long.MIN_VALUE
            val payerId: Long? = if (rawPayer == Long.MIN_VALUE) null else rawPayer
            AddExpenseScreen(groupId = gid, payerId = payerId, onDone = { navController.popBackStack() })
        }
        composable(
            route = Routes.SETTLE,
            arguments = listOf(
                navArgument("groupId") { type = NavType.LongType },
                navArgument("groupName") { type = NavType.StringType; defaultValue = "" }
            )
        ) { backStackEntry ->
            val gid = backStackEntry.arguments?.getLong("groupId") ?: 0L
            val encName = backStackEntry.arguments?.getString("groupName") ?: ""
            val gname = URLDecoder.decode(encName, StandardCharsets.UTF_8.toString())
            SettleScreen(
                groupId = gid,
                groupName = gname,
                onOpenTransactions = { navController.navigate(Routes.transactions(gid, gname)) },
                onOpenSettleDetails = { navController.navigate(Routes.settleDetails(gid, gname)) },
                onAddExpense = { payerId -> navController.navigate(Routes.addExpense(gid, payerId)) },
                onManageMembers = { navController.navigate(Routes.members(gid)) },
                onBack = { navController.popBackStack() }
            )
        }
        composable(
            route = Routes.SETTLE_DETAILS,
            arguments = listOf(
                navArgument("groupId") { type = NavType.LongType },
                navArgument("groupName") { type = NavType.StringType; defaultValue = "" }
            )
        ) { backStackEntry ->
            val gid = backStackEntry.arguments?.getLong("groupId") ?: 0L
            val encName = backStackEntry.arguments?.getString("groupName") ?: ""
            val gname = URLDecoder.decode(encName, StandardCharsets.UTF_8.toString())
            SettleDetailsScreen(groupId = gid, groupName = gname, onBack = { navController.popBackStack() })
        }
        composable(
            route = Routes.TRANSACTIONS,
            arguments = listOf(
                navArgument("groupId") { type = NavType.LongType },
                navArgument("groupName") { type = NavType.StringType; defaultValue = "" }
            )
        ) { backStackEntry ->
            val gid = backStackEntry.arguments?.getLong("groupId") ?: 0L
            val encName = backStackEntry.arguments?.getString("groupName") ?: ""
            val gname = URLDecoder.decode(encName, StandardCharsets.UTF_8.toString())
            TransactionsScreen(groupId = gid, groupName = gname, onBack = { navController.popBackStack() }, onEdit = { /* TODO */ }, onDelete = { /* handled in screen */ })
        }
        composable(
            route = Routes.MEMBERS,
            arguments = listOf(navArgument("groupId") { type = NavType.LongType })
        ) { backStackEntry ->
            val gid = backStackEntry.arguments?.getLong("groupId") ?: 0L
            MembersScreen(groupId = gid, onBack = { navController.popBackStack() })
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(onBack = { navController.popBackStack() })
        }
    }
}
