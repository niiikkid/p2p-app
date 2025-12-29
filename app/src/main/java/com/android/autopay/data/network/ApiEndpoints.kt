package com.android.autopay.data.network

import com.android.autopay.BuildConfig

object ApiEndpoints {
    private const val SMS_PATH: String = "/api/app/sms"
    private const val CONNECT_PATH: String = "/api/app/device/connect"
    private const val PING_PATH: String = "/api/app/device/ping"

    private val host: String = BuildConfig.API_HOST.removeSuffix("/")

    fun getSmsUrl(): String {
        return host + SMS_PATH
    }

    fun getConnectUrl(): String {
        return host + CONNECT_PATH
    }

    fun getPingUrl(): String {
        return host + PING_PATH
    }
}

