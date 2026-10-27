package com.snowballons.tukiwatch

import com.snowballons.tukiwatch.net.isNetworkError
import java.io.IOException
import java.net.ConnectException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.CancellationException
import javax.net.ssl.SSLHandshakeException
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NetworkErrorsTest {
    @Test
    fun ioExceptionsClassifyAsOffline() {
        assertTrue(isNetworkError(SocketTimeoutException()))
        assertTrue(isNetworkError(UnknownHostException("no dns")))
        assertTrue(isNetworkError(ConnectException("refused")))
        assertTrue(isNetworkError(SocketException("unreachable")))
        assertTrue(isNetworkError(IOException("closed")))
        assertTrue(isNetworkError(SSLHandshakeException("tls")))
    }

    @Test
    fun wrappedCausesAreWalked() {
        // The Chaquopy bridge and Streamlink wrap the real socket failure.
        assertTrue(isNetworkError(RuntimeException("resolve failed", IOException("Network Error"))))
        assertTrue(
            isNetworkError(IllegalStateException("outer", RuntimeException("mid", SocketTimeoutException()))),
        )
    }

    @Test
    fun messageHintsFromThePortedListMatch() {
        assertTrue(isNetworkError(RuntimeException("request failed: Failed to fetch")))
        assertTrue(isNetworkError(RuntimeException("LOAD FAILED")))
    }

    @Test
    fun pythonTransportErrorsMatch() {
        // A PyException from the Chaquopy bridge carries only Python text; the
        // axios-era hints alone would miss every real DNS/connection failure.
        assertTrue(
            isNetworkError(
                RuntimeException(
                    "Max retries exceeded with url: /live.m3u8 " +
                        "(Caused by NameResolutionError(\"Failed to resolve 'example.invalid'\"))",
                ),
            ),
        )
        assertTrue(isNetworkError(RuntimeException("HTTPSConnectionPool: connection refused")))
        assertTrue(isNetworkError(RuntimeException("Read timed out. (read timeout=30)")))
    }

    @Test
    fun pluginAndAppErrorsAreNotOffline() {
        assertFalse(isNetworkError(IllegalStateException("No plugin matches this URL")))
        assertFalse(isNetworkError(RuntimeException("403 client error: forbidden")))
        assertFalse(isNetworkError(null))
    }

    @Test
    fun cancellationIsNotOffline() {
        assertFalse(isNetworkError(CancellationException("scope gone")))
    }

    @Test
    fun causeLoopsTerminate() {
        val a = RuntimeException("a")
        val b = RuntimeException("b", a)
        // Defensive: a self-referencing chain must not hang the classifier.
        try {
            a.initCause(b)
        } catch (_: IllegalStateException) {
            // JDK forbids re-causing an already-caused throwable; nothing to prove.
        }
        assertFalse(isNetworkError(a))
    }
}
