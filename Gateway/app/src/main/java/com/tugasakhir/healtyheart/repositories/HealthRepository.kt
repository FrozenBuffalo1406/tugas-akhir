package com.tugasakhir.healtyheart.repositories

import com.tugasakhir.healtyheart.models.Device
import com.tugasakhir.healtyheart.models.ECGData
import com.tugasakhir.healtyheart.models.History
import com.tugasakhir.healtyheart.models.User
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

// @Singleton: Hilt akan membuat satu instance repository ini dan menggunakannya di seluruh aplikasi.
@Singleton
class HealthRepository @Inject constructor() {
    // Di dunia nyata, di sini akan ada injeksi untuk API Service (Retrofit) atau DAO (Room Database)

    // --- Data Dummy ---
    private val dummyHistory = listOf(
        History("hist-001", "2024-10-21 08:00", "Normal", 98.5f),
        History("hist-002", "2024-10-21 08:05", "Normal", 99.1f),
        History("hist-003", "2024-10-21 08:10", "Atrial Fibrillation", 102.3f)
    )
    private val dummyDevices = listOf(
        Device("dev-01", "HeartScan Pro X", "Budi Santoso", "Baru saja", dummyHistory),
        Device("dev-02", "ECG Mobile", "Siti Aminah", "5 jam lalu", dummyHistory.shuffled())
    )

    private val dummyUser = User(
        id = "user-123",
        name = "Budi Santoso",
        email = "budi.santoso@example.com",
        role = "Patient",
        profilePictureUrl = "https://i.pravatar.cc/300?img=12" // Contoh URL gambar profil
    )
    // --- End of Data Dummy ---

    suspend fun getUserDevices(): List<Device> {
        delay(1000) // Simulasi loading dari network/database
        return dummyDevices
    }

    suspend fun getUserProfile(): User {
        delay(800) // Simulasi loading
        return dummyUser
    }

    fun getLatestECGData(): Flow<ECGData> = flow {
        var count = 0
        while (true) {
            val randomLabel = if (Math.random() > 0.95) "PVC" else "Normal"
            val data = ECGData(
                id = "ecg-${System.currentTimeMillis()}",
                timestamp = System.currentTimeMillis().toString(),
                deviceName = "HeartScan Pro X",
                value = 70 + Math.random().toFloat() * 10,
                label = if (count % 20 == 0) "Atrial Fibrillation" else randomLabel
            )
            emit(data)
            count++
            delay(1000) // Kirim data baru setiap detik
        }
    }

    suspend fun requestClassificationFromServer(data: ECGData): String {
        delay(500) // Simulasi request ke server
        return "Reviewed: ${data.label} (Confidence: 95%)"
    }
}

