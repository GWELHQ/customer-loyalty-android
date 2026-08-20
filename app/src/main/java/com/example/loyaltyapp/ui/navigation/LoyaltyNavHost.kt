package com.example.loyaltyapp.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.loyaltyapp.ui.auth.LoginScreen
import com.example.loyaltyapp.ui.profile.ProfileScreen
import com.example.loyaltyapp.ui.sale.SaleFlowScreen
import com.example.loyaltyapp.ui.summary.DailySummaryScreen
import com.example.loyaltyapp.ui.syncqueue.SyncQueueScreen
import com.example.loyaltyapp.ui.today.TodayScreen

@Composable
fun LoyaltyNavHost(navController: NavHostController = rememberNavController()) {
    NavHost(navController = navController, startDestination = Routes.LOGIN) {
        composable(Routes.LOGIN) {
            LoginScreen(
                onLoggedIn = {
                    navController.navigate(Routes.NEW_SALE) {
                        popUpTo(Routes.LOGIN) { inclusive = true }
                    }
                }
            )
        }
        composable(Routes.NEW_SALE) {
            SaleFlowScreen(
                onGoToday = { navController.navigate(Routes.TODAY) { launchSingleTop = true } },
                onGoProfile = { navController.navigate(Routes.PROFILE) { launchSingleTop = true } }
            )
        }
        composable(Routes.TODAY) {
            TodayScreen(
                onGoNewSale = { navController.navigate(Routes.NEW_SALE) { launchSingleTop = true } },
                onGoProfile = { navController.navigate(Routes.PROFILE) { launchSingleTop = true } }
            )
        }
        composable(Routes.SYNC_QUEUE) {
            SyncQueueScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.PROFILE) {
            ProfileScreen(
                onGoNewSale = { navController.navigate(Routes.NEW_SALE) { launchSingleTop = true } },
                onGoToday = { navController.navigate(Routes.TODAY) { launchSingleTop = true } },
                onSyncQueue = { navController.navigate(Routes.SYNC_QUEUE) },
                onDailySummary = { navController.navigate(Routes.DAILY_SUMMARY) },
                onSignedOut = {
                    navController.navigate(Routes.LOGIN) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }
        composable(Routes.DAILY_SUMMARY) {
            DailySummaryScreen(onBack = { navController.popBackStack() })
        }
    }
}
