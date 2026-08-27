package com.example.loyaltyapp.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.loyaltyapp.ui.auth.LoginScreen
import com.example.loyaltyapp.ui.profile.ProfileScreen
import com.example.loyaltyapp.ui.sale.SaleFlowScreen
import com.example.loyaltyapp.ui.summary.DailySummaryScreen
import com.example.loyaltyapp.ui.syncqueue.SyncQueueScreen
import com.example.loyaltyapp.ui.today.TodayDetailScreen
import com.example.loyaltyapp.ui.today.TodayScreen

@Composable
fun LoyaltyNavHost(
    navController: NavHostController = rememberNavController(),
    sessionWatcherViewModel: SessionWatcherViewModel = hiltViewModel()
) {
    // A 401 anywhere clears the stored session (see NetworkModule's auth interceptor) but that
    // clear is invisible on its own — every screen just keeps rendering with a now-token-less
    // client, and every subsequent call fails too ("Missing bearer token"), which reads to the
    // attendant as a generic connectivity problem rather than "you were signed out." This routes
    // back to LOGIN the moment that happens, from anywhere in the app, not just the explicit
    // ProfileScreen "Sign out" path.
    val session by sessionWatcherViewModel.session.collectAsState()
    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route
    LaunchedEffect(session, currentRoute) {
        if (session == null && currentRoute != null && currentRoute != Routes.LOGIN) {
            navController.navigate(Routes.LOGIN) {
                popUpTo(0) { inclusive = true }
            }
        }
    }

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
                onGoProfile = { navController.navigate(Routes.PROFILE) { launchSingleTop = true } },
                onOpenDetail = { type, id -> navController.navigate(Routes.todayDetail(type, id)) }
            )
        }
        composable(
            route = Routes.TODAY_DETAIL,
            arguments = listOf(
                navArgument("type") { type = NavType.StringType },
                navArgument("id") { type = NavType.StringType }
            )
        ) {
            TodayDetailScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.SYNC_QUEUE) {
            SyncQueueScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.PROFILE) {
            ProfileScreen(
                onGoNewSale = { navController.navigate(Routes.NEW_SALE) { launchSingleTop = true } },
                onGoToday = { navController.navigate(Routes.TODAY) { launchSingleTop = true } },
                onSyncQueue = { navController.navigate(Routes.SYNC_QUEUE) },
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
