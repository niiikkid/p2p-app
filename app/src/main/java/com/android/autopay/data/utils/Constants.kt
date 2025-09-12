package com.android.autopay.data.utils

//const val DEFAULT_URL = "http://10.0.2.2:8000/api/app/sms"
//const val CONNECT_URL: String = "http://10.0.2.2:8000/api/app/device/connect"
const val DEFAULT_URL = "https://p2pprocessing.ru/api/app/sms"
const val CONNECT_URL: String = "https://p2pprocessing.ru/api/app/device/connect"
const val FOREGROUND_NOTIFICATION_CHANNEL_ID: String = "notification_handler_channel"
const val FOREGROUND_NOTIFICATION_ID: Int = 1
const val PERIODIC_WORK_NAME: String = "notification_retry_worker"
const val NOTIFICATION_TEXT_EXTRAS_KEY: String = "android.text"
const val NOTIFICATION_TITLE_EXTRAS_KEY: String = "android.title"
const val PUSH_MESSAGE_SEPARATOR: String = " | "
const val PING_WORK_NAME: String = "device_ping_worker"
const val PING_ENDPOINT_PATH: String = "/api/app/device/ping"
const val PING_INTERVAL_SECONDS: Long = 15