package com.tugasakhir.ecgapp.data.remote

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Kelas simpel ini nyimpen token di RAM.
 * Aman diakses secara sinkronus dari Interceptor.
 */
@Singleton
class TokenManager @Inject constructor() {
    var authToken: String? = null
}