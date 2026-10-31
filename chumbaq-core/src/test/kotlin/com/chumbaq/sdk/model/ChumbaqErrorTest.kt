package com.chumbaq.sdk.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChumbaqErrorTest {

    // ── NotInitialized ──────────────────────────────────────────────

    @Test
    fun `NotInitialized has default message`() {
        val error = ChumbaqError.NotInitialized()
        assertEquals("Chumbaq SDK not initialized", error.message)
    }

    @Test
    fun `NotInitialized accepts custom message`() {
        val error = ChumbaqError.NotInitialized("custom")
        assertEquals("custom", error.message)
    }

    @Test
    fun `NotInitialized is a ChumbaqError`() {
        val error: ChumbaqError = ChumbaqError.NotInitialized()
        assertTrue(error is ChumbaqError.NotInitialized)
    }

    @Test
    fun `NotInitialized is an Exception`() {
        val error = ChumbaqError.NotInitialized()
        assertTrue(error is Exception)
    }

    // ── NetworkError ────────────────────────────────────────────────

    @Test
    fun `NetworkError stores message and status code`() {
        val error = ChumbaqError.NetworkError("timeout", statusCode = 503)
        assertEquals("timeout", error.message)
        assertEquals(503, error.statusCode)
    }

    @Test
    fun `NetworkError status code defaults to null`() {
        val error = ChumbaqError.NetworkError("no connection")
        assertNull(error.statusCode)
    }

    @Test
    fun `NetworkError preserves cause`() {
        val cause = java.io.IOException("socket closed")
        val error = ChumbaqError.NetworkError("failed", cause = cause)
        assertEquals(cause, error.cause)
    }

    @Test
    fun `NetworkError is a ChumbaqError`() {
        val error: ChumbaqError = ChumbaqError.NetworkError("err")
        assertTrue(error is ChumbaqError.NetworkError)
    }

    // ── ApiError ────────────────────────────────────────────────────

    @Test
    fun `ApiError stores message status and body`() {
        val error = ChumbaqError.ApiError("bad request", 400, """{"error":"invalid"}""")
        assertEquals("bad request", error.message)
        assertEquals(400, error.statusCode)
        assertEquals("""{"error":"invalid"}""", error.responseBody)
    }

    @Test
    fun `ApiError response body defaults to null`() {
        val error = ChumbaqError.ApiError("server error", 500)
        assertNull(error.responseBody)
    }

    @Test
    fun `ApiError is a ChumbaqError`() {
        val error: ChumbaqError = ChumbaqError.ApiError("err", 422)
        assertTrue(error is ChumbaqError.ApiError)
    }

    // ── InvalidConfig ───────────────────────────────────────────────

    @Test
    fun `InvalidConfig stores message`() {
        val error = ChumbaqError.InvalidConfig("missing API key")
        assertEquals("missing API key", error.message)
    }

    @Test
    fun `InvalidConfig is a ChumbaqError`() {
        val error: ChumbaqError = ChumbaqError.InvalidConfig("bad")
        assertTrue(error is ChumbaqError.InvalidConfig)
    }

    // ── Exhaustive when ─────────────────────────────────────────────

    @Test
    fun `all subtypes are distinguishable in when expression`() {
        val errors: List<ChumbaqError> = listOf(
            ChumbaqError.NotInitialized(),
            ChumbaqError.NetworkError("net"),
            ChumbaqError.ApiError("api", 500),
            ChumbaqError.InvalidConfig("cfg"),
        )
        val labels = errors.map { error ->
            when (error) {
                is ChumbaqError.NotInitialized -> "not_init"
                is ChumbaqError.NetworkError -> "network"
                is ChumbaqError.ApiError -> "api"
                is ChumbaqError.InvalidConfig -> "config"
            }
        }
        assertEquals(listOf("not_init", "network", "api", "config"), labels)
    }
}
