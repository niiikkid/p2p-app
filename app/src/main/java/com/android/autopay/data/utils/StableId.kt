package com.android.autopay.data.utils

import java.security.MessageDigest

object StableId {

    fun buildForSms(sender: String, message: String): String {
        val payload: String = listOf("sms", normalize(sender), normalize(message)).joinToString("|")
        return sha256Hex(payload)
    }

    fun buildForPush(packageName: String, combinedMessage: String): String {
        val payload: String = listOf("push", normalize(packageName), normalize(combinedMessage)).joinToString("|")
        return sha256Hex(payload)
    }

    private fun normalize(value: String?): String {
        return value?.lowercase()?.trim()?.replace("\\s+".toRegex(), " ").orEmpty()
    }

    private fun sha256Hex(input: String): String {
        val md: MessageDigest = MessageDigest.getInstance("SHA-256")
        val bytes: ByteArray = md.digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}


