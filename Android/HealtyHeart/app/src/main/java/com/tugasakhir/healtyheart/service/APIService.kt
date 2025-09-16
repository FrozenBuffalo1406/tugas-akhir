package com.tugasakhir.healtyheart.service

import android.util.Log
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import com.tugasakhir.data.ECGData

// Class untuk mengelola semua panggilan API
class ApiService {
    private val client = HttpClient(CIO) {
        // Konfigurasi HTTP client jika diperlukan
    }

    // URL endpoint server Flask lokal kamu
    private val BASE_URL = "http://192.168.1.102:5000/ecg/data" // Ganti dengan IP lokal kamu

    suspend fun sendEcgData(data: ECGData): Boolean {
        return try {
            val response = client.post(BASE_URL) {
                contentType(ContentType.Application.Json)
                setBody(data)
            }
            Log.d("ApiService", "Data sent successfully. Status: ${response.status}")
            response.status.value == 200 // Mengembalikan true jika status 200 (OK)
        } catch (e: Exception) {
            Log.e("ApiService", "Failed to send data: ${e.message}")
            false
        }
    }
}

