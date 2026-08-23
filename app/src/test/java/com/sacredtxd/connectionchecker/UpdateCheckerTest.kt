package com.sacredtxd.connectionchecker

import com.sacredtxd.connectionchecker.data.UpdateComparator
import com.sacredtxd.connectionchecker.data.UpdateManifest
import com.sacredtxd.connectionchecker.util.ManifestResult
import com.sacredtxd.connectionchecker.util.UpdateChecker
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URI
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckerTest {

    private class FakeConnection(
        private val status: Int = 200,
        private val body: String = "",
        private val error: Exception? = null,
    ) : HttpURLConnection(URI("https://example.invalid/version.json").toURL()) {
        var disconnected = false

        override fun getResponseCode(): Int {
            error?.let { throw it }
            return status
        }

        override fun getInputStream(): InputStream = ByteArrayInputStream(body.toByteArray())
        override fun connect() = Unit
        override fun disconnect() { disconnected = true }
        override fun usingProxy(): Boolean = false
    }

    private fun checker(connection: FakeConnection) =
        UpdateChecker(manifestUrl = "https://example.invalid/version.json") { connection }

    private val validBody = """
        {
          "versionCode": 12,
          "versionName": "0.3.12",
          "apkUrl": "https://example.invalid/app-release.apk",
          "notes": "Adds the graph"
        }
    """.trimIndent()

    @Test
    fun `a published manifest is parsed`() = runTest {
        val result = checker(FakeConnection(body = validBody)).fetch()

        val manifest = (result as ManifestResult.Found).manifest
        assertEquals(12, manifest.versionCode)
        assertEquals("0.3.12", manifest.versionName)
        assertEquals("https://example.invalid/app-release.apk", manifest.apkUrl)
    }

    @Test
    fun `unknown fields do not break parsing`() = runTest {
        // The manifest is expected to gain fields over time; an older build must
        // still be able to read a newer one to learn that an update exists.
        val body = """{"versionCode":3,"versionName":"0.3.3","apkUrl":"u","futureField":true}"""
        val result = checker(FakeConnection(body = body)).fetch()
        assertEquals(3, (result as ManifestResult.Found).manifest.versionCode)
    }

    @Test
    fun `a missing manifest is reported rather than thrown`() = runTest {
        val result = checker(FakeConnection(status = 404)).fetch()
        assertEquals(ManifestResult.Failed("HTTP 404"), result)
    }

    @Test
    fun `malformed json is reported rather than thrown`() = runTest {
        val result = checker(FakeConnection(body = "{ not json")).fetch()
        assertTrue(result is ManifestResult.Failed)
    }

    @Test
    fun `a network error carries its message`() = runTest {
        val result = checker(FakeConnection(error = IOException("offline"))).fetch()
        assertEquals(ManifestResult.Failed("offline"), result)
    }

    @Test
    fun `the connection is released on every path`() = runTest {
        val ok = FakeConnection(body = validBody)
        checker(ok).fetch()
        assertTrue(ok.disconnected)

        val bad = FakeConnection(error = IOException("boom"))
        checker(bad).fetch()
        assertTrue(bad.disconnected)
    }

    @Test
    fun `only a higher version code counts as an update`() {
        val manifest = UpdateManifest(versionCode = 5, versionName = "0.3.5", apkUrl = "u")

        assertTrue(UpdateComparator.isNewer(manifest, installedVersionCode = 4))
        assertFalse(UpdateComparator.isNewer(manifest, installedVersionCode = 5))
        assertFalse(UpdateComparator.isNewer(manifest, installedVersionCode = 6))
    }
}
