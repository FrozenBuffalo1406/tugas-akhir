package com.tugasakhir.ecgappnative.data.api

import android.content.Context
import android.util.Log
import com.tugasakhir.ecgappnative.data.utils.SessionManager // <-- Pake kelas lo
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.*
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object ApiClient {

    private const val BASE_URL = "https://ecg-detection.developedbyme.my.id//api/v1/"

    @Volatile
    private var INSTANCE: ApiService? = null

    // DI-UPDATE: Minta SessionManager
    fun getInstance(context: Context, sessionManager: SessionManager): ApiService {
        return INSTANCE ?: synchronized(this) {
            // Kita "suntik" sessionManager ke createService
            val instance = createService(context, sessionManager)
            INSTANCE = instance
            instance
        }
    }

    // FUNGSI BARU: Bikin service KHUSUS buat refresh token
    // Dia gak pake authenticator, jadi gak bakal looping
    private fun createRefreshService(): ApiService {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
        val client = OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .client(client)
            .build()

        return retrofit.create(ApiService::class.java)
    }


    private fun createService(context: Context, sessionManager: SessionManager): ApiService {

        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        // Bikin service khusus refresh token (pake client polosan)
        val refreshService = createRefreshService()

        // Bikin Authenticator, kasih dia SessionManager & service bersih
        val authenticator = TokenAuthenticator(sessionManager, refreshService)

        // Bikin Interceptor, kasih dia SessionManager
        val authInterceptor = AuthInterceptor(sessionManager)

        // Setup OkHttpClient utama (YANG INI PAKE AUTHENTICATOR)
        val client = OkHttpClient.Builder()
            .addInterceptor(authInterceptor)  // <-- Ini buat nambahin token di tiap request
            .authenticator(authenticator) // <-- Ini buat handle 401 (refresh token)
            .addInterceptor(loggingInterceptor) // <-- Ini buat nge-log di Logcat
            .build()

        // Setup Retrofit utama
        return Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }
}

class TokenAuthenticator(
    private val sessionManager: SessionManager,
    private val apiService: ApiService // Ini ApiService "bersih"
) : Authenticator {

    override fun authenticate(route: Route?, response: Response): Request? {
        Log.w("TokenAuthenticator", "Access Token expired. Trying to refresh...")
        // 1. Ambil REFRESH token
        val refreshToken = runBlocking {
            sessionManager.refreshTokenFlow.first()
        }

        if (refreshToken.isNullOrEmpty()) {
            // Kalo refresh token gak ada, nyerah.
            Log.e("TokenAuthenticator", "No refresh token available. Logging out.")
            runBlocking { sessionManager.clearSession() }
            return null // Batal
        }

        return runBlocking {
            try {
                // 2. Panggil API refresh token PAKE SERVICE "BERSIH"
                val tokenResponse = apiService.refreshToken("Bearer $refreshToken")

                if (tokenResponse.isSuccessful && tokenResponse.body() != null) {
                    val newAccessToken = tokenResponse.body()!!.accessToken
                    Log.i("TokenAuthenticator", "Token refreshed successfully!")
                    Log.e("DEBUG_TOKEN", "Token Baru Diterima: $accessToken")
                    // 3. Simpen token baru ke preferences
                    sessionManager.saveAccessToken(newAccessToken)

                    // 4. Ulangi request yang gagal tadi pake token baru
                    response.request.newBuilder()
                        .header("Authorization", "Bearer $newAccessToken")
                        .build()
                } else {
                    // Gagal refresh, token-nya mati. Nyerah.
                    Log.e("TokenAuthenticator", "Refresh token failed. Logging out.")
                    sessionManager.clearSession() // Hapus token lama
                    null
                }
            } catch (e: Exception) {
                // Kalo ada error (misal offline), nyerah.
                Log.e("TokenAuthenticator", "Exception while refreshing: ${e.message}")
                sessionManager.clearSession()
                null
            }
        }
    }
}

class AuthInterceptor(private val sessionManager: SessionManager) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val requestBuilder = chain.request().newBuilder()
        // Ambil token dari session manager
        val token = runBlocking {
            sessionManager.tokenFlow.first()
        }

        if (!token.isNullOrEmpty()) {
            requestBuilder.addHeader("Authorization", "Bearer $token")
        }

        return chain.proceed(requestBuilder.build())
    }
}