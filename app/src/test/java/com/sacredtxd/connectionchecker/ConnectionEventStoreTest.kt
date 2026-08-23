package com.sacredtxd.connectionchecker

import com.sacredtxd.connectionchecker.data.ConnectionEvent
import com.sacredtxd.connectionchecker.data.ConnectionEventStore
import com.sacredtxd.connectionchecker.data.NetworkStatus
import com.sacredtxd.connectionchecker.data.ReachabilityResult
import com.sacredtxd.connectionchecker.data.Transport
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ConnectionEventStoreTest {

    private lateinit var directory: File
    private lateinit var file: File

    @Before
    fun setUp() {
        directory = Files.createTempDirectory("history").toFile()
        file = File(directory, ConnectionEventStore.FILE_NAME)
    }

    @After
    fun tearDown() {
        directory.deleteRecursively()
    }

    private fun event(timestamp: Long) = ConnectionEvent(
        timestampMs = timestamp,
        status = NetworkStatus(
            connected = true,
            validated = true,
            transport = Transport.CELLULAR,
            metered = true,
        ),
        reachability = ReachabilityResult.Success(latencyMs = timestamp, httpStatus = 204),
    )

    @Test
    fun `events round-trip through the file`() = runTest {
        val store = ConnectionEventStore(file)
        store.append(event(1))
        store.append(event(2))

        val reloaded = ConnectionEventStore(file)
        reloaded.load()

        assertEquals(listOf(2L, 1L), reloaded.events.value.map { it.timestampMs })
    }

    @Test
    fun `newest events come first`() = runTest {
        val store = ConnectionEventStore(file)
        store.append(event(1))
        store.append(event(2))
        assertEquals(2L, store.events.value.first().timestampMs)
    }

    @Test
    fun `the log is bounded to maxEvents`() = runTest {
        val store = ConnectionEventStore(file, maxEvents = 3)
        repeat(10) { store.append(event(it.toLong())) }

        assertEquals(3, store.events.value.size)
        assertEquals(listOf(9L, 8L, 7L), store.events.value.map { it.timestampMs })
    }

    @Test
    fun `a corrupt log loads as empty rather than throwing`() = runTest {
        file.parentFile?.mkdirs()
        file.writeText("{ not json")

        val store = ConnectionEventStore(file)
        store.load()

        assertTrue(store.events.value.isEmpty())
    }

    @Test
    fun `clear empties both memory and disk`() = runTest {
        val store = ConnectionEventStore(file)
        store.append(event(1))
        store.clear()

        val reloaded = ConnectionEventStore(file)
        reloaded.load()

        assertTrue(store.events.value.isEmpty())
        assertTrue(reloaded.events.value.isEmpty())
    }
}
