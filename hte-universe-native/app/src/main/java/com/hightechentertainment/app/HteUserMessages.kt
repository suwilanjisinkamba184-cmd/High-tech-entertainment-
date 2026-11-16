package com.hightechentertainment.app

import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/** Converts technical failures into safe, actionable copy for normal app screens. */
internal fun Throwable.toHteUserMessage(fallback: String): String {
    val causes = generateSequence(this) { it.cause }.toList()
    return when {
        causes.any { it is UnknownHostException || it is SocketTimeoutException || it is ConnectException } ->
            "No internet connection or the service is unreachable. Check your connection and try again."
        causes.any { it is SSLException } ->
            "A secure connection could not be established. Check your connection and device date, then try again."
        causes.any { it is SecurityException } ->
            "You do not have permission to complete this action."
        else -> fallback
    }
}
