package com.tugasakhir.ecgappnative

import android.app.Application
import com.tugasakhir.ecgappnative.data.api.ApiClient
import com.tugasakhir.ecgappnative.data.local.HistoryDatabase
import com.tugasakhir.ecgappnative.data.repository.MainRepository
import com.tugasakhir.ecgappnative.ui.ViewModelFactory
import com.tugasakhir.ecgappnative.data.utils.SessionManager

// Kelas Application kustom untuk inisialisasi Repository
class MainApplication : Application() {

    // 1. Bikin ini DULUAN. Gak pake lazy.
    lateinit var sessionManager: SessionManager

    private val apiService by lazy {
        ApiClient.getInstance(this, sessionManager) // <-- Kita "suntik" sessionManager
    }
    // 3. Database
    private val database by lazy { HistoryDatabase.getDatabase(this) }

    val mainRepository by lazy {
        MainRepository(apiService, database.historyDao(), sessionManager)
    }

    // 5. Factory (Aman)
    val viewModelFactory by lazy {
        ViewModelFactory(mainRepository)
    }

    override fun onCreate() {
        super.onCreate()
        // 1. Inisialisasi sessionManager PERTAMA KALI di sini
        sessionManager = SessionManager(this)
    }
}