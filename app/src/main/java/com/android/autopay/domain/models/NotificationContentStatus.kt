package com.android.autopay.domain.models

enum class NotificationContentStatus(val wireName: String) {
    VISIBLE("visible"),
    HIDDEN_BY_ANDROID_OR_APP("hidden_by_android_or_app");

    companion object {
        fun fromWireName(value: String?): NotificationContentStatus {
            return entries.firstOrNull { it.wireName == value } ?: VISIBLE
        }
    }
}
