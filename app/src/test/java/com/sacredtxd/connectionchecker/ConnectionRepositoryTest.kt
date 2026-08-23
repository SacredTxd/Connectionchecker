package com.sacredtxd.connectionchecker

import com.sacredtxd.connectionchecker.data.ConnectionEventStore
import com.sacredtxd.connectionchecker.data.ConnectionRepository
import com.sacredtxd.connectionchecker.data.NetworkStatus
import com.sacredtxd.connectionchecker.data.ReachabilityResult
import com.sacredtxd.connectionchecker.data.Transport
import com.sacredtxd.connectionchecker.monitor.ConnectivitySource
import com.sacredtxd.connectionchecker.monitor.ReachabilityProbe
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ConnectionRepositoryTest {

    private class FakeSource(var status: NetworkStatus) : ConnectivitySource {
        override fun statusFlow(): Flow<NetworkStatus> = flowOf(status)
        override fun currentStatus(): NetworkStatus = status
    }

    private class FakeProbe(var result: ReachabilityResult) : ReachabilityProbe {
        var calls = 0
        override suspend fun check(): ReachabilityResult {
            calls++
            return result
        }
    }

    private val online = NetworkStatus(
        connected = true,
        validated = true,
        transport = Transport.WIFI,
        metered = false,
    )

    private lateinit var directory: File
    private lateinit var store: ConnectionEventStore

    @Before
    fun setUp() {
        directory = Files.createTempDirectory("repo").toFile()
        store = ConnectionEventStore(File(directory, ConnectionEventStore.FILE_NAME))
    }

    @After
    fun tearDown() {
        directory.deleteRecursively()
    }

    private fun repository(
        source: ConnectivitySource,
        probe: ReachabilityProbe,
        // The repository shares state with SharingStarted.WhileSubscribed, whose
        // coroutine never completes; backgroundScope is cancelled when the test ends
        // so runTest does not wait on it forever.
        scope: CoroutineScope,
        clock: () -> Long = { 1_000L },
    ) = ConnectionRepository(
        monitor = source,
        reachabilityChecker = probe,
        store = store,
        scope = scope,
        now = clock,
    )

    @Test
    fun `a successful check is recorded and published`() = runTest {
        val probe = FakeProbe(ReachabilityResult.Success(latencyMs = 30, httpStatus = 204))
        val repository = repository(FakeSource(online), probe, backgroundScope)

        val event = repository.runCheck()

        assertTrue(event.isOnline)
        assertEquals(30L, event.latencyMs)
        assertEquals(1_000L, event.timestampMs)
        assertEquals(listOf(event), repository.history.value)
        assertEquals(event, repository.lastEvent.value)
    }

    @Test
    fun `the endpoint is not probed when there is no network`() = runTest {
        val probe = FakeProbe(ReachabilityResult.Success(latencyMs = 30, httpStatus = 204))
        val repository = repository(FakeSource(NetworkStatus.Offline), probe, backgroundScope)

        val event = repository.runCheck()

        assertEquals(0, probe.calls)
        assertFalse(event.isOnline)
        assertEquals(ReachabilityResult.Failure("No network"), event.reachability)
    }

    @Test
    fun `a failing probe on a connected network is still offline`() = runTest {
        val probe = FakeProbe(ReachabilityResult.Timeout)
        val repository = repository(FakeSource(online), probe, backgroundScope)

        val event = repository.runCheck()

        assertEquals(1, probe.calls)
        assertFalse(event.isOnline)
        assertNull(event.latencyMs)
    }

    @Test
    fun `checkInProgress is cleared after a check`() = runTest {
        val probe = FakeProbe(ReachabilityResult.Success(latencyMs = 5, httpStatus = 204))
        val repository = repository(FakeSource(online), probe, backgroundScope)

        assertFalse(repository.checkInProgress.value)
        repository.runCheck()
        assertFalse(repository.checkInProgress.value)
    }

    @Test
    fun `checkInProgress is cleared when the probe throws`() = runTest {
        val probe = object : ReachabilityProbe {
            override suspend fun check(): ReachabilityResult = error("probe exploded")
        }
        val repository = repository(FakeSource(online), probe, backgroundScope)

        runCatching { repository.runCheck() }

        assertFalse(repository.checkInProgress.value)
    }

    @Test
    fun `clearing history drops the last event too`() = runTest {
        val probe = FakeProbe(ReachabilityResult.Success(latencyMs = 5, httpStatus = 204))
        val repository = repository(FakeSource(online), probe, backgroundScope)

        repository.runCheck()
        repository.clearHistory()

        assertTrue(repository.history.value.isEmpty())
        assertNull(repository.lastEvent.value)
    }

    @Test
    fun `history survives a reload through the store`() = runTest {
        val probe = FakeProbe(ReachabilityResult.Success(latencyMs = 12, httpStatus = 204))
        var clock = 1_000L
        val repository = repository(FakeSource(online), probe, backgroundScope) { clock }

        repository.runCheck()
        clock = 2_000L
        repository.runCheck()

        repository.loadHistory()

        assertEquals(listOf(2_000L, 1_000L), repository.history.value.map { it.timestampMs })
    }
}
