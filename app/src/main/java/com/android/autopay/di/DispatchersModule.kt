package com.android.autopay.di

import com.android.autopay.utils.AppDispatchers
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DispatchersModule {

    @Provides
    @Singleton
    fun provideAppDispatchers(): AppDispatchers = AppDispatchers()
}
