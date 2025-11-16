// com/proyeklo/ecgapp/data/remote/AuthInterceptor.kt
package com.tugasakhir.ecgapp.data.remote

import com.tugasakhir.ecgapp.data.local.UserPreferences
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject

/**
 * Interceptor ini "mencegat" tiap request API
 * dan nambahin header "Authorization: Bearer <token>"
 * secara otomatis kalo token-nya ada.
 */
class AuthInterceptor @Inject constructor(
    private val userPreferences: UserPreferences
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        // Ambil token dari DataStore.
        // runBlocking dipake karena interceptor sifatnya sinkron.
        val token = runBlocking { userPreferences.authToken.first() }

        val requestBuilder = chain.request().newBuilder()

        // Kalo token-nya ada, tambahin ke header
        token?.let {
            requestBuilder.addHeader("Authorization", "Bearer $it")
        }

        return chain.proceed(requestBuilder.build())
    }
}