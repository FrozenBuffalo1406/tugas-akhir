// com/proyeklo/ecgapp/MainActivity.kt
package com.tugasakhir.ecgapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.rememberNavController
import com.tugasakhir.ecgapp.core.navigation.NavigationGraph
import com.tugasakhir.ecgapp.ui.theme.ECGAppTheme // Asumsi nama tema lo
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ECGAppTheme {
                Surface {
                    val navController = rememberNavController()

                    // Kita butuh ViewModel di sini buat ngecek "udah login apa belum"
                    // Buat nentuin startDestination
                    val mainViewModel: MainViewModel = hiltViewModel()
                    val startDestination by mainViewModel.startDestination.collectAsState()

                    // Tunjukin loading dulu sampe viewModel-nya siap
                    if (startDestination.isNotEmpty()) {
                        NavigationGraph(
                            navController = navController,
                            startDestination = startDestination
                        )
                    } else {
                        SplashScreen()
                    }
                }
            }
        }
    }
}

@Preview
@Composable
fun SplashScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // Ini placeholder fotonya
            Icon(
                imageVector = Icons.Default.Image, //
                contentDescription = "Placeholder Logo",
                modifier = Modifier.size(150.dp),
                tint = MaterialTheme.colorScheme.primary // Biar warnanya cakep
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                "EcgApp", // Ganti nama app lo
                style = MaterialTheme.typography.headlineSmall
            )
            Spacer(modifier = Modifier.height(24.dp))
            CircularProgressIndicator() // Biar keliatan loading
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Mengecek sesi...",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}