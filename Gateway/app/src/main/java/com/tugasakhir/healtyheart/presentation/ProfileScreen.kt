package com.tugasakhir.healtyheart.presentation

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.tugasakhir.healtyheart.models.User
import com.tugasakhir.healtyheart.ui.theme.Dimens
import com.tugasakhir.healtyheart.ui.theme.HealtyHeartTheme
import com.tugasakhir.healtyheart.viewmodels.UserViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    navController: NavController,
    userViewModel: UserViewModel = hiltViewModel() // Inject UserViewModel
) {
    val user by userViewModel.userProfile.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Profile") },
                actions = {
                    IconButton(onClick = { navController.navigate("settings") }) {
                        Icon(Icons.Filled.Settings, contentDescription = "Settings")
                    }
                }
            )
        }
    ) { paddingValues ->
        // Pake data user yang sudah di-provide, atau tampilkan loading
        user?.let {
            ProfileContent(paddingValues = paddingValues, user = it)
        } ?: run {
            LoadingIndicator(paddingValues = paddingValues)
        }
    }
}

@Composable
fun ProfileContent(paddingValues: PaddingValues, user: User) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .padding(Dimens.PaddingLarge),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Dimens.PaddingMedium)
    ) {
        // Gunakan AsyncImage dari Coil untuk menampilkan gambar dari URL
        SubcomposeAsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(user.profilePictureUrl)
                .crossfade(true)
                .build(),
            contentDescription = "Profile Picture",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(Dimens.ProfilePictureSize)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            // Optional: placeholder atau error jika gambar gagal dimuat
            loading = {
                CircularProgressIndicator(modifier = Modifier.size(Dimens.ProfilePictureSize / 2))
            },
            error = {
                // Gambar placeholder jika URL tidak valid atau gagal dimuat
                Box(
                    modifier = Modifier
                        .size(Dimens.ProfilePictureSize)
                        .clip(CircleShape)
                        .background(Color.Gray), // Background abu-abu
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = user.name.firstOrNull()?.toString()?.uppercase() ?: "??",
                        style = MaterialTheme.typography.headlineLarge,
                        color = Color.White
                    )
                }
            }
        )

        Spacer(modifier = Modifier.height(Dimens.PaddingMedium))

        Text(user.name, style = MaterialTheme.typography.headlineSmall)
        Text(user.email, style = MaterialTheme.typography.bodyLarge)
        Text("Role: ${user.role}", style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun LoadingIndicator(paddingValues: PaddingValues) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator()
    }
}

// --- Preview untuk ProfileScreen ---
@Preview(showBackground = true, name = "Profile Screen Preview")
@Composable
fun ProfileScreenPreview() {
    HealtyHeartTheme {
        val dummyNavController = rememberNavController()
        ProfileScreen(navController = dummyNavController)
    }
}

@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES, name = "Profile Dark Preview")
@Composable
fun ProfileScreenDarkPreview() {
    HealtyHeartTheme {
        val dummyNavController = rememberNavController()
        ProfileScreen(navController = dummyNavController)
    }
}

