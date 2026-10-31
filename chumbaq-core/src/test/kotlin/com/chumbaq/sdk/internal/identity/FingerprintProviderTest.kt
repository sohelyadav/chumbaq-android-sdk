package com.chumbaq.sdk.internal.identity

import com.chumbaq.sdk.internal.storage.ChumbaqPreferences
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.slot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class FingerprintProviderTest {

    private lateinit var preferences: ChumbaqPreferences
    private lateinit var provider: FingerprintProvider

    @Before
    fun setUp() {
        preferences = mockk(relaxed = true)
        provider = FingerprintProvider(preferences)
    }

    // ── Fingerprint ─────────────────────────────────────────────────

    @Test
    fun `fingerprint returns existing value from preferences`() {
        every { preferences.deviceFingerprint } returns "existing-fp"

        assertEquals("existing-fp", provider.fingerprint)
    }

    @Test
    fun `fingerprint generates and saves UUID when not in preferences`() {
        every { preferences.deviceFingerprint } returns null

        val slot = slot<String>()
        every { preferences.deviceFingerprint = capture(slot) } answers { }
        // Re-stub getter to return the captured value after set.
        every { preferences.deviceFingerprint } answers {
            if (slot.isCaptured) slot.captured else null
        }

        val fp = provider.fingerprint
        assertNotNull(fp)
        verify { preferences.deviceFingerprint = fp }
    }

    // ── Anonymous ID ────────────────────────────────────────────────

    @Test
    fun `anonymousId returns existing value from preferences`() {
        every { preferences.anonymousId } returns "anon-123"

        assertEquals("anon-123", provider.anonymousId)
    }

    @Test
    fun `anonymousId generates and saves UUID when not in preferences`() {
        every { preferences.anonymousId } returns null

        val slot = slot<String>()
        every { preferences.anonymousId = capture(slot) } answers { }
        every { preferences.anonymousId } answers {
            if (slot.isCaptured) slot.captured else null
        }

        val anonId = provider.anonymousId
        assertNotNull(anonId)
        verify { preferences.anonymousId = anonId }
    }

    @Test
    fun `anonymousId setter delegates to preferences`() {
        provider.anonymousId = "custom-anon"
        verify { preferences.anonymousId = "custom-anon" }
    }

    // ── Identified ID ───────────────────────────────────────────────

    @Test
    fun `identifiedId returns value from preferences`() {
        every { preferences.identifiedId } returns "ident-456"

        assertEquals("ident-456", provider.identifiedId)
    }

    @Test
    fun `identifiedId returns null when not set`() {
        every { preferences.identifiedId } returns null

        assertNull(provider.identifiedId)
    }

    @Test
    fun `identifiedId setter delegates to preferences`() {
        provider.identifiedId = "new-ident"
        verify { preferences.identifiedId = "new-ident" }
    }

    // ── Distinct ID ─────────────────────────────────────────────────

    @Test
    fun `distinctId returns identifiedId when set`() {
        every { preferences.identifiedId } returns "ident-789"
        every { preferences.anonymousId } returns "anon-000"

        assertEquals("ident-789", provider.distinctId)
    }

    @Test
    fun `distinctId falls back to anonymousId when identifiedId is null`() {
        every { preferences.identifiedId } returns null
        every { preferences.anonymousId } returns "anon-fallback"

        assertEquals("anon-fallback", provider.distinctId)
    }

    // ── Reset ───────────────────────────────────────────────────────

    @Test
    fun `reset clears identifiedId and generates new anonymousId`() {
        val oldAnon = "old-anon"
        every { preferences.anonymousId } returns oldAnon

        provider.reset()

        verify { preferences.identifiedId = null }
        // A new anonymousId is set (any non-null String).
        val slot = slot<String>()
        verify { preferences.anonymousId = capture(slot) }
        assertNotNull(slot.captured)
        assertNotEquals(oldAnon, slot.captured)
    }
}
