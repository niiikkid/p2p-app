package com.android.autopay.di

import android.util.Log
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        val logger: HttpLoggingInterceptor = HttpLoggingInterceptor { message -> Log.d(LOG_TAG, message) }
            .apply { level = HttpLoggingInterceptor.Level.BODY }
        return OkHttpClient.Builder()
            .addInterceptor(logger)
            .build()
    }

    private const val LOG_TAG: String = "HTTP"
}
