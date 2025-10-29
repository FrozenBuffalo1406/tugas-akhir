package com.tugasakhir.healtyheart

import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.tugasakhir.healtyheart.presentation.DashboardScreen
import com.tugasakhir.healtyheart.presentation.HistoryScreen
import com.tugasakhir.healtyheart.presentation.ProfileScreen
import com.tugasakhir.healtyheart.presentation.SettingsScreen
import com.tugasakhir.healtyheart.ui.theme.HealtyHeartTheme
import dagger.hilt.android.AndroidEntryPoint

// Anotasi @AndroidEntryPoint untuk mengaktifkan injeksi Hilt di Activity ini
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            HealtyHeartTheme {
                // A surface container using the 'background' color from the theme
                Surface(color = MaterialTheme.colorScheme.background) {
                    HealtyHeartAppContent()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HealtyHeartAppContent() {
    val navController = rememberNavController()
    val navItems = listOf(
        NavItem("dashboard", "Dashboard", Icons.Default.Home),
        NavItem("history", "History", Icons.Default.History),
        NavItem("profile", "Profile", Icons.Default.Person)
    )

    Scaffold(
        bottomBar = {
            NavigationBar {
                val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route
                navItems.forEach { item ->
                    NavigationBarItem(
                        icon = { Icon(item.icon, contentDescription = item.label) },
                        label = { Text(item.label) },
                        selected = currentRoute == item.route,
                        onClick = {
                            if (currentRoute != item.route) {
                                navController.navigate(item.route) {
                                    popUpTo(navController.graph.startDestinationId) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        }
                    )
                }
            }
        }
    ) { paddingValues ->
        NavHost(
            navController = navController,
            startDestination = "dashboard",
            modifier = Modifier.padding(paddingValues)
        ) {
            composable("dashboard") { DashboardScreen(navController) }
            composable("history") { HistoryScreen(navController) }
            composable("profile") { ProfileScreen(navController) }
            composable("settings") { SettingsScreen(navController) } // Rute untuk SettingsScreen
        }
    }
}

data class NavItem(val route: String, val label: String, val icon: ImageVector)

@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    HealtyHeartTheme {
        HealtyHeartAppContent()
    }
}

// Preview untuk mode gelap
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun DarkModePreview() {
    HealtyHeartTheme {
        HealtyHeartAppContent()
    }
}

