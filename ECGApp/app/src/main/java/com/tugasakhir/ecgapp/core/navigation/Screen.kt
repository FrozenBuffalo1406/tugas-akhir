package com.tugasakhir.ecgapp.core.navigation

sealed class Screen(val route: String) {
    object Login : Screen("login")
    object Register : Screen("register")
    object Dashboard : Screen("dashboard")
    object Profile : Screen("profile")

    // Bikin rute history bisa nerima argumen
    object History : Screen("history") // Rute dasarnya

    // Fungsi bantuan buat bikin rute dinamis (cth: "history/5")
    fun withArgs(vararg args: Any): String {
        return buildString {
            append(route)
            args.forEach { arg ->
                append("/$arg")
            }
        }
    }
}