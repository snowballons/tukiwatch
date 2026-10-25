package com.snowballons.tukiwatch.net

/**
 * Port of `app/src/lib/networkErrors.ts`: tells "no internet" apart from server
 * or app errors so screens show the offline UI with a retry affordance.
 *
 * The transport changed (axios/RN-fetch → Chaquopy + Streamlink + raw sockets),
 * so the original `err.code` set is expressed as exception-type names and the
 * message hints are kept verbatim. Walks the cause chain because the
 * Kotlin/Python bridge and Streamlink wrap the real IO failure.
 */

const val OFFLINE_MESSAGE = "No internet connection. Check your connection and try again."

private val NETWORK_MESSAGE_HINTS = listOf(
    "network error",
    "network request failed",
    "failed to fetch",
    "load failed",
    "network connection lost",
)

/**
 * Exception simple-names that mean "the network/OS refused us", covering the
 * codes in networkErrors.ts (ECONNABORTED/ETIMEDOUT → SocketTimeoutException,
 * ENOTFOUND/EAI_AGAIN → UnknownHostException, ECONNREFUSED → ConnectException,
 * EHOSTUNREACH/ENETUNREACH/EPIPE → SocketException, ERR_NETWORK → IOException).
 */
private val NETWORK_EXCEPTION_NAMES = setOf(
    "IOException",
    "SocketException",
    "SocketTimeoutException",
    "InterruptedIOException",
    "UnknownHostException",
    "ConnectException",
    "NoRouteToHostException",
    "PortUnreachableException",
    "SSLException",
    "SSLHandshakeException",
)

fun isNetworkError(error: Throwable?): Boolean {
    var current: Throwable? = error
    val seen = HashSet<Throwable>()
    while (current != null && seen.add(current)) {
        // A coroutine timeout is the AbortError analogue in networkErrors.ts:
        // retryable, and on mobile usually a dropped connection. Checked before
        // the CancellationException guard because it subclasses it.
        if (current::class.simpleName == "TimeoutCancellationException") return true
        if (current is kotlinx.coroutines.CancellationException) return false
        if (current::class.simpleName in NETWORK_EXCEPTION_NAMES) return true
        current.message?.let { msg ->
            val lower = msg.lowercase()
            if (NETWORK_MESSAGE_HINTS.any { lower.contains(it) }) return true
        }
        current = current.cause
    }
    return false
}
