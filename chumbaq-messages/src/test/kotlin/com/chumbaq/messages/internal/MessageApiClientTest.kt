package com.chumbaq.messages.internal

import com.chumbaq.sdk.ChumbaqConfigBridge
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `/api/messages/check` answers `{"data":{"messages":[…]}}`. The client read
 * `messages` off the root, found nothing, and returned an empty list — which
 * is exactly what "nobody is eligible" looks like, so in-app messaging was
 * dead on every device without a single error anywhere.
 */
class MessageApiClientTest {

    private val client = MessageApiClient()

    /** The body the live API returns, copied from a real check. */
    private val enveloped = """
        {"data":{"messages":[{
          "id":"c63c74dc-d52d-44ab-89a3-73e7f929704d",
          "messageType":"banner_bottom",
          "title":"Parity probe",
          "body":"Checking the SDK envelope",
          "imageUrl":null,
          "imagePosition":"top",
          "buttons":[{"id":"b1","text":"Got it","style":"primary","action":"dismiss"}],
          "theme":{"animation":"slide_up","titleColor":"#FFFFFF","borderRadius":16,"backgroundColor":"#101828"},
          "dismissOnTapOutside":true,
          "autoDismissSeconds":6,
          "triggerDelay":0
        }]}}
    """.trimIndent()

    @Test
    fun `reads messages from inside the envelope`() {
        val messages = client.parseResponse(enveloped)

        assertEquals(1, messages.size)
        val message = messages.first()
        assertEquals("c63c74dc-d52d-44ab-89a3-73e7f929704d", message.id)
        assertEquals("banner_bottom", message.messageType)
        assertEquals("Parity probe", message.title)
        assertEquals("Checking the SDK envelope", message.body)
        assertEquals(6, message.autoDismissSeconds)
        assertEquals(1, message.buttons?.size)
        assertEquals("Got it", message.buttons?.first()?.text)
        assertEquals("#101828", message.theme?.backgroundColor)
        assertEquals(16, message.theme?.borderRadius)
    }

    @Test
    fun `reads messages from an unwrapped body`() {
        val messages = client.parseResponse("""{"messages":[{"id":"m1","messageType":"modal","title":"Hi"}]}""")

        assertEquals(listOf("m1"), messages.map { it.id })
    }

    @Test
    fun `no messages is an empty list`() {
        assertTrue(client.parseResponse("""{"data":{"messages":[]}}""").isEmpty())
    }

    @Test
    fun `unreadable bodies yield no messages`() {
        val bodies = listOf(
            null,
            "",
            "not json",
            "[]",
            """{"data":{}}""",
            """{"data":{"messages":"soon"}}""",
        )
        for (body in bodies) {
            assertTrue("expected no messages for $body", client.parseResponse(body).isEmpty())
        }
    }

    @Test
    fun `one unreadable message costs that message, not the batch`() {
        // `id` is the one field with no default, so a message missing it
        // used to throw and take every other eligible message with it.
        val messages = client.parseResponse(
            """{"data":{"messages":[{"messageType":"modal"},{"id":"m2","title":"Still here"}]}}"""
        )

        assertEquals(listOf("m2"), messages.map { it.id })
    }

    @Test
    fun `absent fields take their defaults`() {
        val messages = client.parseResponse("""{"data":{"messages":[{"id":"m3"}]}}""")

        assertEquals(1, messages.size)
        val message = messages.first()
        assertEquals("modal", message.messageType)
        assertEquals("top", message.imagePosition)
        assertTrue(message.dismissOnTapOutside)
        assertNull(message.autoDismissSeconds)
        assertEquals(0, message.triggerDelay)
    }

    // ── Connection hygiene ──────────────────────────────────────────

    /**
     * A non-2xx check used to throw before anything read or closed the body,
     * so OkHttp could not return the connection to the pool and logged a
     * leak.
     *
     * A reused connection is the observable proof: `sequenceNumber` counts
     * requests on the *same* socket, so a second request only reports 1 if
     * the first response was closed.
     */
    @Test
    fun `a failed check leaves its connection reusable`() = runTest {
        val server = MockWebServer()
        server.start()
        ChumbaqConfigBridge.baseUrl = server.url("").toString().trimEnd('/')
        try {
            server.enqueue(MockResponse().setResponseCode(500).setBody("Internal Server Error"))
            server.enqueue(MockResponse().setResponseCode(200).setBody("""{"data":{"messages":[]}}"""))

            runCatching { client.checkMessages("p", "fp", null, MessageCheckContext()) }
            client.checkMessages("p", "fp", null, MessageCheckContext())

            server.takeRequest()
            assertEquals(1, server.takeRequest().sequenceNumber)
        } finally {
            server.shutdown()
            ChumbaqConfigBridge.baseUrl = ""
        }
    }
}
