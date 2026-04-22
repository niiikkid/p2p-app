package com.android.autopay.domain.models

enum class NotificationType(val wireName: String) {
    SMS("sms"),
    PUSH("push");

    companion object {
        fun fromWireName(value: String): NotificationType =
            entries.firstOrNull { it.wireName == value } ?: SMS
    }
}
