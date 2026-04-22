package com.android.autopay.di

import com.android.autopay.data.repository.NotificationRepositoryImpl
import com.android.autopay.data.repository.SettingsRepositoryImpl
import com.android.autopay.domain.repository.NotificationRepository
import com.android.autopay.domain.repository.SettingsRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindNotificationRepository(impl: NotificationRepositoryImpl): NotificationRepository

    @Binds
    @Singleton
    abstract fun bindSettingsRepository(impl: SettingsRepositoryImpl): SettingsRepository
}
