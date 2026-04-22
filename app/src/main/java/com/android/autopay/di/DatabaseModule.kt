package com.android.autopay.di

import android.content.Context
import com.android.autopay.data.datasource.local.db.NotificationDatabase
import com.android.autopay.data.datasource.local.db.NotificationHistoryDao
import com.android.autopay.data.datasource.local.db.UnsentNotificationDao
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
    fun provideNotificationDatabase(@ApplicationContext context: Context): NotificationDatabase {
        return NotificationDatabase.getInstance(context)
    }

    @Provides
    @Singleton
    fun provideUnsentNotificationDao(database: NotificationDatabase): UnsentNotificationDao {
        return database.unsentNotificationDao()
    }

    @Provides
    @Singleton
    fun provideNotificationHistoryDao(database: NotificationDatabase): NotificationHistoryDao {
        return database.notificationHistoryDao()
    }
}
