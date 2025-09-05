package com.android.autopay.di

import android.content.Context
import com.android.autopay.data.local.db.NotificationDatabase
import com.android.autopay.data.local.db.NotificationHistoryDao
import com.android.autopay.data.local.db.UnsentNotificationDao
import com.android.autopay.data.utils.AppDispatchers
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AppModule {

    companion object {

        @Provides
        @Singleton
        fun provideAppCoroutineDispatchers(): AppDispatchers = AppDispatchers()

        @Provides
        @Singleton
        fun provideUnsentNotificationDatabase(
            @ApplicationContext context: Context
        ): NotificationDatabase {
            return NotificationDatabase.getInstance(context)
        }

        @Provides
        @Singleton
        fun provideUnsentNotificationDao(
            notificationDatabase: NotificationDatabase
        ): UnsentNotificationDao {
            return notificationDatabase.unsentNotificationDao()
        }

        @Provides
        @Singleton
        fun provideNotificationHistoryDao(
            notificationDatabase: NotificationDatabase
        ): NotificationHistoryDao {
            return notificationDatabase.notificationHistoryDao()
        }
    }
}