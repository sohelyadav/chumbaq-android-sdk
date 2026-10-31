package com.chumbaq.push

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream
import java.util.concurrent.TimeUnit

/**
 * Unit tests for [PushImageLoader].
 *
 * `BitmapFactory` is one of the framework classes stubbed out under unit
 * tests, so decoding itself cannot be exercised here. What can — and what
 * decides whether a notification arrives at all — is everything around it:
 * that a bad response, an oversized file or a server that never answers all
 * come back as "no image" instead of an exception or a wait without end.
 */
class PushImageLoaderTest {

    private lateinit var server: MockWebServer

    /**
     * A call timeout shorter than any budget a caller passes, because a
     * coroutine timeout cannot interrupt a socket read OkHttp is blocked in —
     * the client has to be the one that gives up.
     */
    private val client = OkHttpClient.Builder()
        .callTimeout(1, TimeUnit.SECONDS)
        .build()

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `a missing url is not downloaded at all`() = runBlocking {
        assertNull(PushImageLoader.load(null, 5_000, client))
        assertNull(PushImageLoader.load("", 5_000, client))

        assertEquals(0, server.requestCount)
    }

    @Test
    fun `an image is fetched from the url it was given`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("not really a png"))

        // Null: the bytes cannot be decoded without a real BitmapFactory.
        // The request itself is what this asserts.
        PushImageLoader.load(server.url("/hero.png").toString(), 5_000, client)

        assertEquals("/hero.png", server.takeRequest().path)
    }

    @Test
    fun `a server error yields no image and no exception`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(500))

        assertNull(PushImageLoader.load(server.url("/hero.png").toString(), 5_000, client))
    }

    @Test
    fun `an unreachable host yields no image`() = runBlocking {
        val url = server.url("/hero.png").toString()
        server.shutdown()

        assertNull(PushImageLoader.load(url, 5_000, client))
    }

    @Test
    fun `a server that never answers gives up inside the budget`() = runBlocking {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))

        val startedAt = System.currentTimeMillis()
        val image = PushImageLoader.load(server.url("/hero.png").toString(), 5_000, client)
        val elapsed = System.currentTimeMillis() - startedAt

        assertNull(image)
        assertTrue("Gave up after ${elapsed}ms, past its own timeout", elapsed < 5_000)
    }

    @Test
    fun `an oversized image is refused before it is decoded`() {
        val within = PushImageLoader.readAtMost(ByteArrayInputStream(ByteArray(64)), 64)
        val over = PushImageLoader.readAtMost(ByteArrayInputStream(ByteArray(65)), 64)

        assertNotNull("A file at the limit is still usable", within)
        assertEquals(64, within?.size)
        // A notification bitmap crosses a Binder transaction; an image too big
        // for one is dropped rather than carried that far.
        assertNull(over)
    }

    @Test
    fun `a body larger than the cap does not fail the notification`() = runBlocking {
        val oversized = Buffer().apply { write(ByteArray(3 * 1024 * 1024)) }
        server.enqueue(MockResponse().setResponseCode(200).setBody(oversized))

        assertNull(PushImageLoader.load(server.url("/huge.png").toString(), 5_000, client))
    }
}
