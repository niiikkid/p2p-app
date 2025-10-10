package com.android.autopay.data.utils

import com.android.autopay.BuildConfig

object UrlBuilder {
    fun buildAbsoluteUrl(path: String): String {
        val host: String = BuildConfig.API_HOST.removeSuffix("/")
        val normalizedPath: String = if (path.startsWith("/")) path else "/$path"
        return host + normalizedPath
    }
}


