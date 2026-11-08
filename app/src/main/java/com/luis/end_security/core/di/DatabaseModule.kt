package com.luis.end_security.core.di

import android.content.Context
import androidx.room.Room
import com.luis.end_security.core.database.AppDatabase
import com.luis.end_security.core.database.dao.AccountDao
import com.luis.end_security.core.database.dao.BreachCheckDao
import com.luis.end_security.core.database.dao.HealthSnapshotDao
import com.luis.end_security.core.database.dao.VaultItemDao
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
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.NAME).build()

    @Provides
    fun provideAccountDao(database: AppDatabase): AccountDao = database.accountDao()

    @Provides
    fun provideVaultItemDao(database: AppDatabase): VaultItemDao = database.vaultItemDao()

    @Provides
    fun provideBreachCheckDao(database: AppDatabase): BreachCheckDao = database.breachCheckDao()

    @Provides
    fun provideHealthSnapshotDao(database: AppDatabase): HealthSnapshotDao = database.healthSnapshotDao()
}
