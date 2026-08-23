package com.sacredtxd.connectionchecker.util

import com.sacredtxd.connectionchecker.data.UpdateManifest
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

/** Outcome of fetching the published manifest. */
sealed interface ManifestResult {
    data class Found(val manifest: UpdateManifest) : ManifestResult
    data class Failed(val reason: String) : ManifestResult
}

/**
 * Reads the update manifest published alongside the newest release. The connection
 * factory is injectable so the parsing and error paths are testable off-device.
 */
class UpdateChecker(
    private val manifestUrl: String,
    private val timeoutMs: Int = DEFAULT_TIMEOUT_MS,
    private val openConnection: (String) -> HttpURLConnection = { url ->
        URL(url).openConnection() as HttpURLConnection
    },
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun fetch(): ManifestResult = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        try {
            connection = openConnection(manifestUrl).apply {
                requestMethod = "GET"
                connectTimeout = timeoutMs
                readTimeout = timeoutMs
                // The release download URL redirects to a storage host.
                instanceFollowRedirects = true
                setRequestProperty("Accept", "application/json")
            }
            val status = connection.responseCode
            if (status !in 200..299) {
                return@withContext ManifestResult.Failed("HTTP $status")
            }
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            ManifestResult.Found(json.decodeFromString<UpdateManifest>(body))
        } catch (e: IOException) {
            ManifestResult.Failed(e.message ?: "Could not reach the update server")
        } catch (e: Exception) {
            ManifestResult.Failed("Could not read the update details")
        } finally {
            connection?.disconnect()
        }
    }

    companion object {
        const val DEFAULT_TIMEOUT_MS = 10_000
    }
}
