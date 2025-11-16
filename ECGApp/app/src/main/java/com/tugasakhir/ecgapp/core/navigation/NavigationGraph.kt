package com.tugasakhir.ecgapp.core.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.tugasakhir.ecgapp.ui.screen.dashboard.DashboardScreen
import com.tugasakhir.ecgapp.ui.screen.history.HistoryScreen
import com.tugasakhir.ecgapp.ui.screen.login.LoginScreen
import com.tugasakhir.ecgapp.ui.screen.profile.ProfileScreen
import com.tugasakhir.ecgapp.ui.screen.register.RegisterScreen

@Composable
fun NavigationGraph(
    navController: NavHostController,
    startDestination: String
) {
    NavHost(navController = navController, startDestination = startDestination) {

        composable(Screen.Login.route) {
            LoginScreen(navController = navController)
        }
        composable(Screen.Register.route) {
            RegisterScreen(navController = navController)
        }
        composable(Screen.Dashboard.route) {
            DashboardScreen(navController = navController)
        }

        // --- TAMBAHAN BARU ---

        // Rute buat Profile
        composable(Screen.Profile.route) {
            ProfileScreen(navController = navController)
        }

        // Rute buat History (dengan userId)
        composable(
            route = Screen.History.route + "/{userId}", // Rutenya jadi "history/123"
            arguments = listOf(navArgument("userId") { type = NavType.IntType })
        ) { backStackEntry ->
            val userId = backStackEntry.arguments?.getInt("userId") ?: 0
            HistoryScreen(
                navController = navController,
                userId = userId // Kirim userId ke HistoryScreen
            )
        }
    }
}