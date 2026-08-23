package com.sacredtxd.connectionchecker.data

import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * A bounded, newest-first history of samples, persisted as JSON so it survives the
 * process being killed. The whole log is rewritten on each append, which is cheap at
 * [maxEvents] entries and avoids a partial-write recovery path.
 */
class ConnectionEventStore(
    private val file: File,
    private val maxEvents: Int = DEFAULT_MAX_EVENTS,
) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    private val writeLock = Mutex()

    private val _events = MutableStateFlow<List<ConnectionEvent>>(emptyList())
    val events: StateFlow<List<ConnectionEvent>> = _events.asStateFlow()

    suspend fun load() = withContext(Dispatchers.IO) {
        val loaded = runCatching {
            if (!file.exists()) return@runCatching emptyList()
            json.decodeFromString<List<ConnectionEvent>>(file.readText())
        }.getOrElse {
            // A corrupt log is not worth failing startup over — start a fresh one.
            emptyList()
        }
        _events.value = loaded.take(maxEvents)
    }

    suspend fun append(event: ConnectionEvent) = withContext(Dispatchers.IO) {
        writeLock.withLock {
            val updated = (listOf(event) + _events.value).take(maxEvents)
            _events.value = updated
            persist(updated)
        }
    }

    suspend fun clear() = withContext(Dispatchers.IO) {
        writeLock.withLock {
            _events.value = emptyList()
            persist(emptyList())
        }
    }

    private fun persist(events: List<ConnectionEvent>) {
        runCatching {
            file.parentFile?.mkdirs()
            val temp = File(file.parentFile, file.name + ".tmp")
            temp.writeText(json.encodeToString(events))
            if (!temp.renameTo(file)) {
                file.writeText(temp.readText())
                temp.delete()
            }
        }
    }

    companion object {
        const val DEFAULT_MAX_EVENTS = 500
        const val FILE_NAME = "connection-history.json"
    }
}
