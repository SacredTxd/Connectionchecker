package com.sacredtxd.connectionchecker.monitor

import com.sacredtxd.connectionchecker.data.ReachabilityResult
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Probes an endpoint over HTTP and reports how long it took. The default endpoint is
 * Google's generate_204, which answers with an empty 204 and is the same probe the
 * platform's own captive-portal check uses.
 */
class ReachabilityChecker(
    private val endpoint: String = DEFAULT_ENDPOINT,
    private val timeoutMs: Int = DEFAULT_TIMEOUT_MS,
    private val openConnection: (String) -> HttpURLConnection = { url ->
        URL(url).openConnection() as HttpURLConnection
    },
    private val elapsedNanos: () -> Long = System::nanoTime,
) : ReachabilityProbe {

    override suspend fun check(): ReachabilityResult = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        val startedAt = elapsedNanos()
        try {
            connection = openConnection(endpoint).apply {
                requestMethod = "GET"
                connectTimeout = timeoutMs
                readTimeout = timeoutMs
                useCaches = false
                instanceFollowRedirects = false
                setRequestProperty("Connection", "close")
            }
            val status = connection.responseCode
            val latencyMs = (elapsedNanos() - startedAt) / 1_000_000
            // A captive portal answers 200-with-body where we expect 204, so anything
            // outside 2xx/3xx counts as "reachable but not the internet we wanted".
            if (status in 200..399) {
                ReachabilityResult.Success(latencyMs = latencyMs, httpStatus = status)
            } else {
                ReachabilityResult.Failure("HTTP $status")
            }
        } catch (e: SocketTimeoutException) {
            ReachabilityResult.Timeout
        } catch (e: IOException) {
            ReachabilityResult.Failure(e.message ?: e::class.java.simpleName)
        } finally {
            connection?.disconnect()
        }
    }

    companion object {
        const val DEFAULT_ENDPOINT = "https://clients3.google.com/generate_204"
        const val DEFAULT_TIMEOUT_MS = 5_000
    }
}
