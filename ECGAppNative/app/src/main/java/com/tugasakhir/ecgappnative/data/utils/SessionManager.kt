package com.tugasakhir.ecgappnative.data.utils

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_session")

class SessionManager(private val context: Context) {
    companion object {
        val TOKEN_KEY = stringPreferencesKey("jwt_token")
        val REFRESH_TOKEN_KEY = stringPreferencesKey("jwt_refresh_token") // <-- BARU
        val USER_ID_KEY = stringPreferencesKey("user_id")
        val USER_NAME_KEY = stringPreferencesKey("user_name")
    }

    // Flow untuk observe token
    val tokenFlow: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[TOKEN_KEY]
    }

    val refreshTokenFlow: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[REFRESH_TOKEN_KEY]
    }

    val userIdFlow: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[USER_ID_KEY]
    }

    // Menyimpan data session
    suspend fun saveSession(token: String, refreshToken: String, userId: String, name: String) {
        context.dataStore.edit { preferences ->
            preferences[TOKEN_KEY] = token
            preferences[REFRESH_TOKEN_KEY] = refreshToken
            preferences[USER_ID_KEY] = userId
            preferences[USER_NAME_KEY] = name
        }
    }

    suspend fun saveAccessToken(token: String) {
        context.dataStore.edit { preferences ->
            preferences[TOKEN_KEY] = token
        }
    }

    suspend fun clearSession() {
        context.dataStore.edit { preferences ->
            preferences.clear()
        }
    }
}