package com.ledge.splitbook.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.ledge.splitbook.ui.screens.TripPlanScreen
import com.ledge.splitbook.ui.screens.TransactionsScreen
import com.ledge.splitbook.ui.screens.SettingsScreen
import com.ledge.splitbook.ui.screens.SettleDetailsScreen
import com.ledge.splitbook.ui.vm.BillingViewModel
import androidx.hilt.navigation.compose.hiltViewModel
import com.ledge.splitbook.ui.vm.SettleViewModel
import androidx.compose.runtime.remember
import java.net.URLEncoder
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

object Routes {
    const val GROUPS = "groups"
    const val CATEGORIES = "categories"
    const val ADD_EXPENSE = "addExpense/{groupId}?payerId={payerId}&expenseId={expenseId}"
    fun addExpense(groupId: Long, payerId: Long? = null, expenseId: Long? = null): String {
        val payerPart = payerId?.toString() ?: ""
        val expPart = expenseId?.toString() ?: ""
        return "addExpense/$groupId?payerId=$payerPart&expenseId=$expPart"
    }
    const val TRIP_PLAN = "tripPlan/{groupId}?groupName={groupName}"
    fun tripPlan(groupId: Long, groupName: String): String {
        val enc = URLEncoder.encode(groupName, StandardCharsets.UTF_8.toString())
        return "tripPlan/$groupId?groupName=$enc"
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
    // Start Billing client on app start to refresh entitlements (e.g., Remove Ads)
    val billingVM: BillingViewModel = hiltViewModel()
    LaunchedEffect(Unit) { billingVM.start() }
    AppNavHost(navController)
}

@Composable
private fun AppNavHost(navController: NavHostController) {
    NavHost(navController = navController, startDestination = Routes.GROUPS) {
        composable(Routes.GROUPS) {
            GroupsScreen(
                onOpenGroup = { gid, name -> navController.navigate(Routes.settle(gid, name)) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                onOpenCategories = { navController.navigate(Routes.CATEGORIES) }
            )
        }
        composable(
            route = Routes.ADD_EXPENSE,
            arguments = listOf(
                navArgument("groupId") { type = NavType.LongType },
                navArgument("payerId") { type = NavType.LongType; defaultValue = Long.MIN_VALUE },
                navArgument("expenseId") { type = NavType.LongType; defaultValue = Long.MIN_VALUE }
            )
        ) { backStackEntry ->
            val gid = backStackEntry.arguments?.getLong("groupId") ?: 0L
            val rawPayer = backStackEntry.arguments?.getLong("payerId") ?: Long.MIN_VALUE
            val payerId: Long? = if (rawPayer == Long.MIN_VALUE) null else rawPayer
            val rawExp = backStackEntry.arguments?.getLong("expenseId") ?: Long.MIN_VALUE
            val expenseId: Long? = if (rawExp == Long.MIN_VALUE) null else rawExp
            AddExpenseScreen(groupId = gid, payerId = payerId, expenseId = expenseId, onDone = {
                // Signal the SETTLE screen to refresh its subscriptions/snapshot after save
                val parentEntry = navController.getBackStackEntry(Routes.SETTLE)
                parentEntry.savedStateHandle["refresh_settle"] = true
                navController.popBackStack()
            })
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
            // ViewModel scoped to this SETTLE route entry
            val settleVm: SettleViewModel = hiltViewModel(backStackEntry)
            // Listen for refresh flag posted by AddExpense screen and reload VM when set
            val refreshFlow = remember(backStackEntry) { backStackEntry.savedStateHandle.getStateFlow("refresh_settle", false) }
            LaunchedEffect(refreshFlow) {
                refreshFlow.collect { need ->
                    if (need) {
                        settleVm.reload()
                        backStackEntry.savedStateHandle["refresh_settle"] = false
                    }
                }
            }
            SettleScreen(
                groupId = gid,
                groupName = gname,
                onOpenTransactions = { navController.navigate(Routes.transactions(gid, gname)) },
                onOpenSettleDetails = { navController.navigate(Routes.settleDetails(gid, gname)) },
                onAddExpense = { payerId -> navController.navigate(Routes.addExpense(gid, payerId)) },
                onManageMembers = { navController.navigate(Routes.members(gid)) },
                onOpenTripPlan = { navController.navigate(Routes.tripPlan(gid, gname)) },
                onBack = { navController.popBackStack() },
                viewModel = settleVm
            )
        }
        composable(
            route = Routes.TRIP_PLAN,
            arguments = listOf(
                navArgument("groupId") { type = NavType.LongType },
                navArgument("groupName") { type = NavType.StringType; defaultValue = "" }
            )
        ) { backStackEntry ->
            val gid = backStackEntry.arguments?.getLong("groupId") ?: 0L
            val encName = backStackEntry.arguments?.getString("groupName") ?: ""
            val gname = URLDecoder.decode(encName, StandardCharsets.UTF_8.toString())
            TripPlanScreen(
                groupId = gid,
                groupName = gname,
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
            // Share the same SettleViewModel instance as the SETTLE screen by scoping to its back stack entry
            val parentEntry = remember(backStackEntry) { navController.getBackStackEntry(Routes.SETTLE) }
            val sharedVm: SettleViewModel = hiltViewModel(parentEntry)
            SettleDetailsScreen(groupId = gid, groupName = gname, onBack = { navController.popBackStack() }, viewModel = sharedVm)
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
            TransactionsScreen(
                groupId = gid,
                groupName = gname,
                onBack = { navController.popBackStack() },
                onEdit = { expenseId -> navController.navigate(Routes.addExpense(gid, null, expenseId)) },
                onDelete = { /* handled in screen */ }
            )
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
        composable(Routes.CATEGORIES) {
            com.ledge.splitbook.ui.screens.CategoriesScreen(onBack = { navController.popBackStack() })
        }
    }
}
