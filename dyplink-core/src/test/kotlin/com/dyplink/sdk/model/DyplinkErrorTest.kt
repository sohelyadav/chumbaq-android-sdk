package com.dyplink.sdk.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DyplinkErrorTest {

    // ── NotInitialized ──────────────────────────────────────────────

    @Test
    fun `NotInitialized has default message`() {
        val error = DyplinkError.NotInitialized()
        assertEquals("Dyplink SDK not initialized", error.message)
    }

    @Test
    fun `NotInitialized accepts custom message`() {
        val error = DyplinkError.NotInitialized("custom")
        assertEquals("custom", error.message)
    }

    @Test
    fun `NotInitialized is a DyplinkError`() {
        val error: DyplinkError = DyplinkError.NotInitialized()
        assertTrue(error is DyplinkError.NotInitialized)
    }

    @Test
    fun `NotInitialized is an Exception`() {
        val error = DyplinkError.NotInitialized()
        assertTrue(error is Exception)
    }

    // ── NetworkError ────────────────────────────────────────────────

    @Test
    fun `NetworkError stores message and status code`() {
        val error = DyplinkError.NetworkError("timeout", statusCode = 503)
        assertEquals("timeout", error.message)
        assertEquals(503, error.statusCode)
    }

    @Test
    fun `NetworkError status code defaults to null`() {
        val error = DyplinkError.NetworkError("no connection")
        assertNull(error.statusCode)
    }

    @Test
    fun `NetworkError preserves cause`() {
        val cause = java.io.IOException("socket closed")
        val error = DyplinkError.NetworkError("failed", cause = cause)
        assertEquals(cause, error.cause)
    }

    @Test
    fun `NetworkError is a DyplinkError`() {
        val error: DyplinkError = DyplinkError.NetworkError("err")
        assertTrue(error is DyplinkError.NetworkError)
    }

    // ── ApiError ────────────────────────────────────────────────────

    @Test
    fun `ApiError stores message status and body`() {
        val error = DyplinkError.ApiError("bad request", 400, """{"error":"invalid"}""")
        assertEquals("bad request", error.message)
        assertEquals(400, error.statusCode)
        assertEquals("""{"error":"invalid"}""", error.responseBody)
    }

    @Test
    fun `ApiError response body defaults to null`() {
        val error = DyplinkError.ApiError("server error", 500)
        assertNull(error.responseBody)
    }

    @Test
    fun `ApiError is a DyplinkError`() {
        val error: DyplinkError = DyplinkError.ApiError("err", 422)
        assertTrue(error is DyplinkError.ApiError)
    }

    // ── InvalidConfig ───────────────────────────────────────────────

    @Test
    fun `InvalidConfig stores message`() {
        val error = DyplinkError.InvalidConfig("missing API key")
        assertEquals("missing API key", error.message)
    }

    @Test
    fun `InvalidConfig is a DyplinkError`() {
        val error: DyplinkError = DyplinkError.InvalidConfig("bad")
        assertTrue(error is DyplinkError.InvalidConfig)
    }

    // ── Exhaustive when ─────────────────────────────────────────────

    @Test
    fun `all subtypes are distinguishable in when expression`() {
        val errors: List<DyplinkError> = listOf(
            DyplinkError.NotInitialized(),
            DyplinkError.NetworkError("net"),
            DyplinkError.ApiError("api", 500),
            DyplinkError.InvalidConfig("cfg"),
        )
        val labels = errors.map { error ->
            when (error) {
                is DyplinkError.NotInitialized -> "not_init"
                is DyplinkError.NetworkError -> "network"
                is DyplinkError.ApiError -> "api"
                is DyplinkError.InvalidConfig -> "config"
            }
        }
        assertEquals(listOf("not_init", "network", "api", "config"), labels)
    }
}
