// com/tugasakhir/ecgapp/di/AppModule.kt
package com.tugasakhir.ecgapp.di

import com.tugasakhir.ecgapp.data.local.EcgDatabase
import com.tugasakhir.ecgapp.data.local.UserPreferences
import com.tugasakhir.ecgapp.data.remote.ApiService
import com.tugasakhir.ecgapp.data.repository.AuthRepository
import com.tugasakhir.ecgapp.data.repository.AuthRepositoryImpl
import com.tugasakhir.ecgapp.data.repository.UserRepository
import com.tugasakhir.ecgapp.data.repository.UserRepositoryImpl
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideAuthRepository(api: ApiService, prefs: UserPreferences): AuthRepository {
        return AuthRepositoryImpl(api, prefs)
    }

    @Provides
    @Singleton
    fun provideUserRepository(
        api: ApiService,
        prefs: UserPreferences,
        db: EcgDatabase // <-- PENTING: Injek DB
    ): UserRepository {
        return UserRepositoryImpl(api, prefs, db)
    }
}