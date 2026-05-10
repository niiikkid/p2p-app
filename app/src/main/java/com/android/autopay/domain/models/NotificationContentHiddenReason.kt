package com.android.autopay.domain.models

enum class NotificationContentHiddenReason(val wireName: String) {
    VISIBILITY_PRIVATE("visibility_private"),
    VISIBILITY_SECRET("visibility_secret"),
    PUBLIC_VERSION_ONLY("public_version_only"),
    EMPTY_EXTRAS("empty_extras"),
    UNKNOWN("unknown");

    companion object {
        fun fromWireName(value: String?): NotificationContentHiddenReason? {
            return entries.firstOrNull { it.wireName == value }
        }
    }
}
