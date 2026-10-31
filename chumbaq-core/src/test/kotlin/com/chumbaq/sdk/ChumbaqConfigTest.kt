package com.chumbaq.sdk

import com.chumbaq.sdk.internal.util.LogLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChumbaqConfigTest {

    private val validBaseUrl = "https://api.chumbaq.com"
    private val validApiKey = "test-api-key"
    private val validProjectId = "test-project-id"

    // ── Happy path ─────────────────────────────────────────────────

    @Test
    fun `builder with valid params creates config`() {
        val config = ChumbaqConfig.Builder(validBaseUrl, validApiKey, validProjectId)
            .logLevel(LogLevel.DEBUG)
            .flushInterval(60)
            .maxQueueSize(500)
            .maxRetries(5)
            .sessionTimeout(600)
            .enableAutoSessionTracking(false)
            .enableAutoDeviceInfo(false)
            .deepLinkHosts(listOf("links.example.com", "l.example.com"))
            .customScheme("myapp")
            .build()

        assertEquals(validBaseUrl, config.baseUrl)
        assertEquals(validApiKey, config.apiKey)
        assertEquals(validProjectId, config.projectId)
        assertEquals(LogLevel.DEBUG, config.logLevel)
        assertEquals(60, config.flushIntervalSeconds)
        assertEquals(500, config.maxQueueSize)
        assertEquals(5, config.maxRetries)
        assertEquals(600, config.sessionTimeoutSeconds)
        assertEquals(false, config.enableAutoSessionTracking)
        assertEquals(false, config.enableAutoDeviceInfo)
        assertEquals(listOf("links.example.com", "l.example.com"), config.deepLinkHosts)
        assertEquals("myapp", config.customScheme)
    }

    @Test
    fun `builder trims trailing slash from baseUrl`() {
        val config = ChumbaqConfig.Builder(
            "https://api.chumbaq.com/", validApiKey, validProjectId,
        ).build()

        assertEquals("https://api.chumbaq.com", config.baseUrl)
    }

    @Test
    fun `builder trims multiple trailing slashes from baseUrl`() {
        val config = ChumbaqConfig.Builder(
            "https://api.chumbaq.com///", validApiKey, validProjectId,
        ).build()

        // trimEnd('/') removes all trailing slashes
        assertEquals("https://api.chumbaq.com", config.baseUrl)
    }

    // ── Default values ─────────────────────────────────────────────

    @Test
    fun `default values are correct`() {
        val config = ChumbaqConfig.Builder(validBaseUrl, validApiKey, validProjectId).build()

        assertEquals(LogLevel.NONE, config.logLevel)
        assertEquals(30, config.flushIntervalSeconds)
        assertEquals(1000, config.maxQueueSize)
        assertEquals(3, config.maxRetries)
        assertEquals(300, config.sessionTimeoutSeconds)
        assertTrue(config.enableAutoSessionTracking)
        assertTrue(config.enableAutoDeviceInfo)
        assertTrue(config.deepLinkHosts.isEmpty())
        assertNull(config.customScheme)
    }

    // ── Validation: blank fields ───────────────────────────────────

    @Test(expected = IllegalArgumentException::class)
    fun `blank baseUrl throws IllegalArgumentException`() {
        ChumbaqConfig.Builder("", validApiKey, validProjectId).build()
    }

    @Test(expected = IllegalArgumentException::class)
    fun `whitespace-only baseUrl throws IllegalArgumentException`() {
        ChumbaqConfig.Builder("   ", validApiKey, validProjectId).build()
    }

    @Test(expected = IllegalArgumentException::class)
    fun `blank apiKey throws IllegalArgumentException`() {
        ChumbaqConfig.Builder(validBaseUrl, "", validProjectId).build()
    }

    @Test(expected = IllegalArgumentException::class)
    fun `blank projectId throws IllegalArgumentException`() {
        ChumbaqConfig.Builder(validBaseUrl, validApiKey, "").build()
    }

    // ── Validation: numeric fields ─────────────────────────────────

    @Test(expected = IllegalArgumentException::class)
    fun `zero flushInterval throws IllegalArgumentException`() {
        ChumbaqConfig.Builder(validBaseUrl, validApiKey, validProjectId)
            .flushInterval(0)
            .build()
    }

    @Test(expected = IllegalArgumentException::class)
    fun `negative flushInterval throws IllegalArgumentException`() {
        ChumbaqConfig.Builder(validBaseUrl, validApiKey, validProjectId)
            .flushInterval(-1)
            .build()
    }

    @Test(expected = IllegalArgumentException::class)
    fun `zero maxQueueSize throws IllegalArgumentException`() {
        ChumbaqConfig.Builder(validBaseUrl, validApiKey, validProjectId)
            .maxQueueSize(0)
            .build()
    }

    @Test(expected = IllegalArgumentException::class)
    fun `negative maxRetries throws IllegalArgumentException`() {
        ChumbaqConfig.Builder(validBaseUrl, validApiKey, validProjectId)
            .maxRetries(-1)
            .build()
    }

    @Test
    fun `zero maxRetries is allowed`() {
        val config = ChumbaqConfig.Builder(validBaseUrl, validApiKey, validProjectId)
            .maxRetries(0)
            .build()

        assertEquals(0, config.maxRetries)
    }

    // ── Builder method chaining ────────────────────────────────────

    @Test
    fun `all builder methods return the builder for chaining`() {
        val builder = ChumbaqConfig.Builder(validBaseUrl, validApiKey, validProjectId)

        val returned = builder
            .logLevel(LogLevel.VERBOSE)
            .flushInterval(10)
            .maxQueueSize(200)
            .maxRetries(1)
            .sessionTimeout(120)
            .enableAutoSessionTracking(false)
            .enableAutoDeviceInfo(false)
            .deepLinkHosts(listOf("links.example.com"))
            .customScheme("testapp")

        // Chaining should return the same builder instance
        assertEquals(builder, returned)
    }

    // ── Diagnostics ────────────────────────────────────────────────

    /**
     * Off unless asked for. The SDK swallows almost every network failure
     * by design, and this is the only way to see one — but an integration
     * that did not ask for it must stay silent.
     */
    @Test
    fun `diagnostics are off by default`() {
        val config = ChumbaqConfig.Builder(validBaseUrl, validApiKey, validProjectId).build()

        assertFalse(config.diagnosticsEnabled)
    }

    @Test
    fun `diagnostics can be enabled`() {
        val config = ChumbaqConfig.Builder(validBaseUrl, validApiKey, validProjectId)
            .diagnostics(true)
            .build()

        assertTrue(config.diagnosticsEnabled)
    }

    @Test
    fun `diagnostics are independent of logLevel`() {
        val config = ChumbaqConfig.Builder(validBaseUrl, validApiKey, validProjectId)
            .logLevel(LogLevel.VERBOSE)
            .build()

        assertFalse(config.diagnosticsEnabled)
    }

    // ── ApiClientConfig contract ───────────────────────────────────

    @Test
    fun `config implements ApiClientConfig correctly`() {
        val config = ChumbaqConfig.Builder(validBaseUrl, validApiKey, validProjectId)
            .logLevel(LogLevel.INFO)
            .maxRetries(7)
            .build()

        // These are the ApiClientConfig properties
        assertEquals(validBaseUrl, config.baseUrl)
        assertEquals(validApiKey, config.apiKey)
        assertEquals(LogLevel.INFO, config.logLevel)
        assertEquals(7, config.maxRetries)
    }
}
