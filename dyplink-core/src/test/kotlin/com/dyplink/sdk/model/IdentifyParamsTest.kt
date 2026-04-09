package com.dyplink.sdk.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IdentifyParamsTest {

    @Test
    fun `builder with all fields creates correct object`() {
        val traits = mapOf("tier" to "gold", "score" to 42)

        val params = IdentifyParams.Builder()
            .distinctId("dist-1")
            .externalUserId("ext-1")
            .firstName("Jane")
            .lastName("Doe")
            .phone("+15551234567")
            .avatar("https://example.com/avatar.png")
            .locale("en_US")
            .language("en")
            .appVersion("1.2.3")
            .appBuild("42")
            .traits(traits)
            .utmSource("google")
            .utmMedium("cpc")
            .utmCampaign("summer")
            .utmContent("banner")
            .utmTerm("shoes")
            .installSource("play_store")
            .installCampaign("launch_promo")
            .emailOptIn(true)
            .smsOptIn(false)
            .pushOptIn(true)
            .gdprConsent(true)
            .doNotTrack(false)
            .build()

        assertEquals("dist-1", params.distinctId)
        assertEquals("ext-1", params.externalUserId)
        assertEquals("Jane", params.firstName)
        assertEquals("Doe", params.lastName)
        assertEquals("+15551234567", params.phone)
        assertEquals("https://example.com/avatar.png", params.avatar)
        assertEquals("en_US", params.locale)
        assertEquals("en", params.language)
        assertEquals("1.2.3", params.appVersion)
        assertEquals("42", params.appBuild)
        assertEquals(traits, params.traits)
        assertEquals("google", params.utmSource)
        assertEquals("cpc", params.utmMedium)
        assertEquals("summer", params.utmCampaign)
        assertEquals("banner", params.utmContent)
        assertEquals("shoes", params.utmTerm)
        assertEquals("play_store", params.installSource)
        assertEquals("launch_promo", params.installCampaign)
        assertTrue(params.emailOptIn!!)
        assertFalse(params.smsOptIn!!)
        assertTrue(params.pushOptIn!!)
        assertTrue(params.gdprConsent!!)
        assertFalse(params.doNotTrack!!)
    }

    @Test
    fun `builder with no fields creates object with all nulls`() {
        val params = IdentifyParams.Builder().build()

        assertNull(params.distinctId)
        assertNull(params.externalUserId)
        assertNull(params.firstName)
        assertNull(params.lastName)
        assertNull(params.phone)
        assertNull(params.avatar)
        assertNull(params.locale)
        assertNull(params.language)
        assertNull(params.appVersion)
        assertNull(params.appBuild)
        assertNull(params.traits)
        assertNull(params.utmSource)
        assertNull(params.utmMedium)
        assertNull(params.utmCampaign)
        assertNull(params.utmContent)
        assertNull(params.utmTerm)
        assertNull(params.installSource)
        assertNull(params.installCampaign)
        assertNull(params.emailOptIn)
        assertNull(params.smsOptIn)
        assertNull(params.pushOptIn)
        assertNull(params.gdprConsent)
        assertNull(params.doNotTrack)
    }

    @Test
    fun `builder with only a few fields leaves others null`() {
        val params = IdentifyParams.Builder()
            .externalUserId("user-99")
            .firstName("Alice")
            .emailOptIn(true)
            .build()

        assertNull(params.distinctId)
        assertEquals("user-99", params.externalUserId)
        assertEquals("Alice", params.firstName)
        assertNull(params.lastName)
        assertNull(params.traits)
        assertTrue(params.emailOptIn!!)
        assertNull(params.smsOptIn)
    }

    @Test
    fun `builder setter methods return the builder for chaining`() {
        val builder = IdentifyParams.Builder()
        val returned = builder.distinctId("id").firstName("A").lastName("B")
        // If chaining works, returned should be the same builder instance.
        assertEquals(builder, returned)
    }
}
