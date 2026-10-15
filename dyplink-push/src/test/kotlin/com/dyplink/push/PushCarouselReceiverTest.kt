package com.dyplink.push

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.spyk
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [PushCarouselReceiver].
 *
 * The prev/next buttons are the only way a carousel moves, and everything
 * they need travels as extras on their own intent, so these tests build that
 * intent exactly as the notification does — through [PushContent.writeTo] —
 * and read it back the way the receiver would.
 */
class PushCarouselReceiverTest {

    private val carouselJson = """
        [
          {"imageUrl":"https://cdn.test/1.png"},
          {"imageUrl":"https://cdn.test/2.png"},
          {"imageUrl":"https://cdn.test/3.png"}
        ]
    """.trimIndent()

    // Spied so goAsync() can be stubbed, as in PushClickReceiverTest: there is
    // no real broadcast in flight under unit tests and the framework hands
    // back null.
    private val receiver = spyk(PushCarouselReceiver())
    private val pendingResult = mockk<BroadcastReceiver.PendingResult>(relaxed = true)

    @Before
    fun setUp() {
        every { receiver.goAsync() } returns pendingResult
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    private fun content(carousel: String? = carouselJson) = PushContent(
        title = "Flash sale",
        body = "Half price for an hour",
        deepLinkUrl = "app://sale",
        campaignId = "camp_1",
        imageUrl = null,
        carouselJson = carousel,
        timerJson = null,
        actionsJson = null,
    )

    /**
     * Builds the intent a prev/next press arrives on, recording the extras
     * the real [Intent] would have kept: under unit tests its methods are
     * stubs that store nothing.
     */
    private fun pressIntent(
        content: PushContent,
        notificationId: Int = 42,
        currentIndex: Int = 0,
        delta: Int = 1,
    ): Intent {
        val extras = mutableMapOf<String, String?>()
        val intent = mockk<Intent>(relaxed = true)
        every { intent.putExtra(any<String>(), any<String>()) } answers {
            extras[firstArg()] = secondArg()
            intent
        }
        every { intent.getStringExtra(any()) } answers { extras[firstArg()] }
        every { intent.getIntExtra(PushCarouselReceiver.EXTRA_NOTIFICATION_ID, any()) } returns
            notificationId
        every { intent.getIntExtra(PushCarouselReceiver.EXTRA_SLIDE_INDEX, any()) } returns
            currentIndex
        every { intent.getIntExtra(PushCarouselReceiver.EXTRA_DELTA, any()) } returns delta

        content.writeTo(intent)
        return intent
    }

    // ── advanceIndex ────────────────────────────────────────────────

    @Test
    fun `next and previous wrap around the ends`() {
        assertEquals(1, PushCarouselReceiver.advanceIndex(current = 0, size = 3, delta = 1))
        assertEquals(0, PushCarouselReceiver.advanceIndex(current = 2, size = 3, delta = 1))
        assertEquals(1, PushCarouselReceiver.advanceIndex(current = 2, size = 3, delta = -1))
        assertEquals(2, PushCarouselReceiver.advanceIndex(current = 0, size = 3, delta = -1))
    }

    @Test
    fun `advanceIndex survives a payload that lost its slides`() {
        assertEquals(0, PushCarouselReceiver.advanceIndex(current = 3, size = 0, delta = 1))
    }

    // ── resolveNavigation ───────────────────────────────────────────

    @Test
    fun `a next press resolves to the following slide`() {
        val navigation = PushCarouselReceiver.resolveNavigation(
            pressIntent(content(), notificationId = 42, currentIndex = 0, delta = 1),
        )

        assertEquals(1, navigation?.slideIndex)
        // Same id, so the notification on screen changes instead of stacking.
        assertEquals(42, navigation?.notificationId)
        // The payload survives the round trip, or the next slide would be blank.
        assertEquals("Flash sale", navigation?.content?.title)
        assertEquals("camp_1", navigation?.content?.campaignId)
        assertEquals(carouselJson, navigation?.content?.carouselJson)
    }

    @Test
    fun `a previous press on the first slide resolves to the last`() {
        val navigation = PushCarouselReceiver.resolveNavigation(
            pressIntent(content(), currentIndex = 0, delta = -1),
        )

        assertEquals(2, navigation?.slideIndex)
    }

    @Test
    fun `a press carrying no carousel resolves to nothing`() {
        assertNull(PushCarouselReceiver.resolveNavigation(pressIntent(content(carousel = null))))
        assertNull(
            PushCarouselReceiver.resolveNavigation(pressIntent(content(carousel = "{broken"))),
        )
    }

    // ── onReceive ───────────────────────────────────────────────────

    @Test
    fun `onReceive re-posts the notification at the new slide`() {
        mockkObject(PushNotificationHandler)
        coEvery { PushNotificationHandler.post(any(), any(), any(), any()) } returns true

        val context = mockk<Context>(relaxed = true)
        receiver.onReceive(context, pressIntent(content(), notificationId = 42, currentIndex = 2))

        // Re-posting runs on the IO dispatcher, so wait for it rather than
        // assuming onReceive has already finished the work.
        coVerify(timeout = 2_000) {
            PushNotificationHandler.post(any(), any(), 42, 0)
        }
        verify(exactly = 1) { pendingResult.finish() }
    }

    @Test
    fun `onReceive ignores a press it cannot resolve`() {
        mockkObject(PushNotificationHandler)

        val context = mockk<Context>(relaxed = true)
        receiver.onReceive(context, pressIntent(content(carousel = null)))

        // No goAsync, so no broadcast is left open for a slide that cannot
        // be built.
        verify(exactly = 0) { receiver.goAsync() }
        coVerify(exactly = 0) { PushNotificationHandler.post(any(), any(), any(), any()) }
    }
}
