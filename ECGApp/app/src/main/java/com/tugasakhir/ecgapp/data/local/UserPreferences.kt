// com/proyeklo/ecgapp/data/local/UserPreferences.kt
package com.tugasakhir.ecgapp.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

// Bikin extension property buat Context biar gampang diakses
val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "ecg_app_prefs")

@Singleton
class UserPreferences @Inject constructor(@ApplicationContext context: Context) {

    private val dataStore = context.dataStore

    // --- Keys ---
    private object PreferencesKeys {
        val AUTH_TOKEN = stringPreferencesKey("auth_token")
        val USER_ID = intPreferencesKey("user_id")
        val USER_ROLE = stringPreferencesKey("user_role")
        val USER_NAME = stringPreferencesKey("user_name")
    }

    // --- Getters (Flow) ---
    val authToken: Flow<String?> = dataStore.data
        .map { preferences -> preferences[PreferencesKeys.AUTH_TOKEN] }

    val userId: Flow<Int?> = dataStore.data
        .map { preferences -> preferences[PreferencesKeys.USER_ID] }

    val userName: Flow<String?> = dataStore.data
        .map { preferences -> preferences[PreferencesKeys.USER_NAME] }

    val userRole: Flow<String?> = dataStore.data
        .map { preferences -> preferences[PreferencesKeys.USER_ROLE] }

    // --- Setters (Suspend) ---
    suspend fun saveLoginData(token: String, userId: Int, role: String, name: String) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.AUTH_TOKEN] = token
            preferences[PreferencesKeys.USER_ID] = userId
            preferences[PreferencesKeys.USER_ROLE] = role
            preferences[PreferencesKeys.USER_NAME] = name
        }
    }

    suspend fun clear() {
        dataStore.edit { it.clear() }
    }
}