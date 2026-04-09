package com.dyplink.sdk.internal.deeplink

import android.content.Intent
import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class IntentParserTest {

    private lateinit var parser: IntentParser

    private val deepLinkHosts = listOf("links.example.com", "dyplink.io")
    private val customScheme = "myapp"

    @Before
    fun setUp() {
        parser = IntentParser(deepLinkHosts, customScheme)
    }

    // ── 1. App Link URL extracts shortCode ──────────────────────────

    @Test
    fun `app link URL extracts shortCode`() {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://links.example.com/aBcDe7"))

        val result = parser.parse(intent)

        assertNotNull(result)
        assertEquals("aBcDe7", result!!.shortCode)
        assertEquals("https://links.example.com/aBcDe7", result.url)
        assertFalse(result.isDeferred)
        assertNull(result.linkId)
        assertNull(result.params)
    }

    // ── 2. App Link with query params ───────────────────────────────

    @Test
    fun `app link with query params extracts shortCode and params`() {
        val intent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("https://links.example.com/aBcDe7?screen=product&id=123"),
        )

        val result = parser.parse(intent)

        assertNotNull(result)
        assertEquals("aBcDe7", result!!.shortCode)
        assertEquals("product", result.params?.get("screen"))
        assertEquals("123", result.params?.get("id"))
        assertEquals(2, result.params?.size)
        assertFalse(result.isDeferred)
    }

    // ── 3. Custom scheme extracts shortCode ─────────────────────────

    @Test
    fun `custom scheme URL extracts shortCode`() {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("myapp://open/aBcDe7"))

        val result = parser.parse(intent)

        assertNotNull(result)
        assertEquals("aBcDe7", result!!.shortCode)
        assertEquals("myapp://open/aBcDe7", result.url)
        assertFalse(result.isDeferred)
    }

    // ── 4. Multiple path segments — first valid segment wins ────────

    @Test
    fun `URL with multiple path segments extracts first valid shortCode`() {
        val intent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("https://links.example.com/aBcDe7/extra"),
        )

        val result = parser.parse(intent)

        assertNotNull(result)
        assertEquals("aBcDe7", result!!.shortCode)
    }

    // ── 5. Intent with no data returns null ─────────────────────────

    @Test
    fun `intent with no data returns null`() {
        val intent = Intent(Intent.ACTION_VIEW)
        // No data URI set.

        val result = parser.parse(intent)

        assertNull(result)
    }

    // ── 6. Intent with ACTION_MAIN but has data — still parses ──────

    @Test
    fun `intent with ACTION_MAIN but data still parses`() {
        val intent = Intent(Intent.ACTION_MAIN).apply {
            data = Uri.parse("https://links.example.com/aBcDe7")
        }

        val result = parser.parse(intent)

        assertNotNull(result)
        assertEquals("aBcDe7", result!!.shortCode)
    }

    // ── 7. Short code too short (3 chars) returns null shortCode ────

    @Test
    fun `short code too short returns null shortCode`() {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://links.example.com/abc"))

        val result = parser.parse(intent)

        assertNotNull(result)
        assertNull(result!!.shortCode)
    }

    // ── 8. Unrecognised host and scheme returns null ────────────────

    @Test
    fun `URL host not in deepLinkHosts and scheme does not match returns null`() {
        val intent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("https://unknown.example.com/aBcDe7"),
        )

        val result = parser.parse(intent)

        assertNull(result)
    }

    // ── Additional edge-case tests ──────────────────────────────────

    @Test
    fun `short code too long (11 chars) returns null shortCode`() {
        val intent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("https://links.example.com/abcdefghijk"),
        )

        val result = parser.parse(intent)

        assertNotNull(result)
        assertNull(result!!.shortCode)
    }

    @Test
    fun `short code with underscores and hyphens is extracted`() {
        val intent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("https://links.example.com/a_b-C_d7"),
        )

        val result = parser.parse(intent)

        assertNotNull(result)
        assertEquals("a_b-C_d7", result!!.shortCode)
    }

    @Test
    fun `URL with no path segments returns result with null shortCode`() {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://links.example.com"))

        val result = parser.parse(intent)

        assertNotNull(result)
        assertNull(result!!.shortCode)
    }

    @Test
    fun `URL with only query params and no valid short code`() {
        val intent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("https://links.example.com?foo=bar"),
        )

        val result = parser.parse(intent)

        assertNotNull(result)
        assertNull(result!!.shortCode)
        assertEquals("bar", result.params?.get("foo"))
    }

    @Test
    fun `second host in deepLinkHosts is also recognised`() {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://dyplink.io/short1"))

        val result = parser.parse(intent)

        assertNotNull(result)
        assertEquals("short1", result!!.shortCode)
    }

    @Test
    fun `custom scheme with no matching host still works`() {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("myapp://unknown-host/test12"))

        val result = parser.parse(intent)

        assertNotNull(result)
        assertEquals("test12", result!!.shortCode)
    }

    @Test
    fun `parser without custom scheme rejects unknown scheme`() {
        val parserNoScheme = IntentParser(deepLinkHosts, customScheme = null)
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("myapp://open/aBcDe7"))

        val result = parserNoScheme.parse(intent)

        assertNull(result)
    }
}
