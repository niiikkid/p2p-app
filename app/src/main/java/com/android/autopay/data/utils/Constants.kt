package com.android.autopay.data.utils

// Endpoint paths (host берётся из BuildConfig.API_HOST)
const val SMS_ENDPOINT_PATH: String = "/api/app/sms"
const val CONNECT_ENDPOINT_PATH: String = "/api/app/device/connect"
const val FOREGROUND_NOTIFICATION_CHANNEL_ID: String = "notification_handler_channel"
const val FOREGROUND_NOTIFICATION_ID: Int = 1
const val PERIODIC_WORK_NAME: String = "notification_retry_worker"
const val NOTIFICATION_TEXT_EXTRAS_KEY: String = "android.text"
const val NOTIFICATION_TITLE_EXTRAS_KEY: String = "android.title"
const val PUSH_MESSAGE_SEPARATOR: String = " | "
const val PING_WORK_NAME: String = "device_ping_worker"
const val PING_ENDPOINT_PATH: String = "/api/app/device/ping"
const val PING_INTERVAL_SECONDS: Long = 5
const val RETRY_INTERVAL_SECONDS: Long = 10