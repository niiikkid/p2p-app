package com.android.autopay.di

import android.util.Log
import com.android.autopay.BuildConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        val logger: HttpLoggingInterceptor = HttpLoggingInterceptor { message -> Log.d(LOG_TAG, message) }
            .apply {
                // Тела запросов содержат Access-Token и тексты уведомлений (PII). В релизе
                // логирование выключаем, чтобы не утекали чувствительные данные в logcat.
                level = if (BuildConfig.DEBUG || BuildConfig.NOTIFICATION_DEBUG_MODE) {
                    HttpLoggingInterceptor.Level.BODY
                } else {
                    HttpLoggingInterceptor.Level.NONE
                }
            }
        return OkHttpClient.Builder()
            .addInterceptor(logger)
            .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .callTimeout(CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    private const val LOG_TAG: String = "HTTP"
    private const val CONNECT_TIMEOUT_SECONDS: Long = 10
    private const val READ_TIMEOUT_SECONDS: Long = 15
    private const val WRITE_TIMEOUT_SECONDS: Long = 15
    private const val CALL_TIMEOUT_SECONDS: Long = 30
}
