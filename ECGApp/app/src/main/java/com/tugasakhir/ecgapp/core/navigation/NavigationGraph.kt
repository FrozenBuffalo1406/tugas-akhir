// com/proyeklo/ecgapp/core/navigation/NavigationGraph.kt
package com.tugasakhir.ecgapp.core.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.tugasakhir.ecgapp.ui.screen.dashboard.DashboardScreen
import com.tugasakhir.ecgapp.ui.screen.history.HistoryScreen
import com.tugasakhir.ecgapp.ui.screen.login.LoginScreen
import com.tugasakhir.ecgapp.ui.screen.profile.ProfileScreen
import com.tugasakhir.ecgapp.ui.screen.register.RegisterScreen

/**
 * Ini adalah "Peta" aplikasi.
 * NavHost mendaftarkan semua Composable (layar) ke rute-nya masing-masing.
 */
@Composable
fun NavigationGraph(navController: NavHostController, startDestination: String) {

    NavHost(
        navController = navController,
        startDestination = startDestination // Ditentukan pas app start
    ) {

        // --- Graf Autentikasi ---
        composable(route = Screen.Login.route) {
            LoginScreen(navController = navController)
        }
        composable(route = Screen.Register.route) {
            RegisterScreen(navController = navController)
        }

        // --- Graf Utama (App) ---
        composable(route = Screen.Dashboard.route) {
            DashboardScreen(navController = navController)
        }
        composable(route = Screen.Profile.route) {
            ProfileScreen(navController = navController)
        }

        composable(
            route = Screen.History.route,
            arguments = Screen.History.arguments
        ) { backStackEntry ->
            val userId = backStackEntry.arguments?.getInt("userId") ?: 0
            HistoryScreen(
                navController = navController,
                userId = userId
            )
        }
    }
}