package com.sacredtxd.connectionchecker

import com.sacredtxd.connectionchecker.data.ReachabilityResult
import com.sacredtxd.connectionchecker.monitor.ReachabilityChecker
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URI
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReachabilityCheckerTest {

    /** A stub connection that answers with a canned status or throws. */
    private class FakeConnection(
        private val status: Int = 204,
        private val error: Exception? = null,
    ) : HttpURLConnection(URI("https://example.invalid").toURL()) {
        var disconnected = false

        override fun getResponseCode(): Int {
            error?.let { throw it }
            return status
        }

        override fun connect() = Unit
        override fun disconnect() { disconnected = true }
        override fun usingProxy(): Boolean = false
    }

    private fun checker(
        connection: FakeConnection,
        nanos: Iterator<Long> = listOf(0L, 42_000_000L).iterator(),
    ) = ReachabilityChecker(
        openConnection = { connection },
        elapsedNanos = { nanos.next() },
    )

    @Test
    fun `a 204 is a success with measured latency`() = runTest {
        val connection = FakeConnection(status = 204)
        val result = checker(connection).check()

        assertEquals(ReachabilityResult.Success(latencyMs = 42, httpStatus = 204), result)
        assertTrue(connection.disconnected)
    }

    @Test
    fun `a server error is a failure`() = runTest {
        val result = checker(FakeConnection(status = 503)).check()
        assertEquals(ReachabilityResult.Failure("HTTP 503"), result)
    }

    @Test
    fun `a socket timeout maps to Timeout`() = runTest {
        val result = checker(FakeConnection(error = SocketTimeoutException("timed out"))).check()
        assertEquals(ReachabilityResult.Timeout, result)
    }

    @Test
    fun `an IO error carries its message`() = runTest {
        val result = checker(FakeConnection(error = IOException("unreachable"))).check()
        assertEquals(ReachabilityResult.Failure("unreachable"), result)
    }

    @Test
    fun `the connection is released even when the probe fails`() = runTest {
        val connection = FakeConnection(error = IOException("boom"))
        checker(connection).check()
        assertTrue(connection.disconnected)
    }
}
