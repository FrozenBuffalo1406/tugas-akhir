// com/proyeklo/ecgapp/core/navigation/Screen.kt
package com.tugasakhir.ecgapp.core.navigation

import androidx.navigation.NavType
import androidx.navigation.navArgument

/**
 * Sealed class buat nyimpen semua rute (layar) di aplikasi.
 * Bikin navigasi jadi type-safe dan bebas typo.
 */
sealed class Screen(val route: String) {
    // Grup Autentikasi
    object Login : Screen("login")
    object Register : Screen("register")

    // Grup Utama
    object Dashboard : Screen("dashboard")
    object Profile : Screen("profile")

    // Layar dengan argumen
    object History : Screen("history/{userId}") {
        // Definisikan argumennya
        val arguments = listOf(
            navArgument("userId") { type = NavType.IntType }
        )
        // Helper buat manggil rute ini
        fun createRoute(userId: Int) = "history/$userId"
    }
}