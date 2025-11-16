// com/tugasakhir/ecgapp/di/DatabaseModule.kt
package com.tugasakhir.ecgapp.di

import android.content.Context
import androidx.room.Room
import com.tugasakhir.ecgapp.data.local.EcgDatabase
import com.tugasakhir.ecgapp.data.local.EcgHistoryDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideEcgDatabase(@ApplicationContext context: Context): EcgDatabase {
        return Room.databaseBuilder(
                context,
                EcgDatabase::class.java,
                "ecg_app.db"
            ).fallbackToDestructiveMigration(false).build()
    }

    @Provides
    @Singleton
    fun provideEcgHistoryDao(database: EcgDatabase): EcgHistoryDao {
        return database.ecgHistoryDao()
    }
}