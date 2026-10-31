package com.chumbaq.push

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Intent
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.spyk
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [PushDismissReceiver].
 *
 * A swipe is the one notification outcome nothing used to report, so the
 * campaign report's `dismissed` column was permanently zero however many
 * users dismissed it.
 */
class PushDismissReceiverTest {

    // Spied so goAsync() can be stubbed, exactly as in PushClickReceiverTest:
    // there is no real broadcast in flight under unit tests and the framework
    // hands back null, which the completion callback would NPE on.
    private val receiver = spyk(PushDismissReceiver())
    private val pendingResult = mockk<BroadcastReceiver.PendingResult>(relaxed = true)

    @Before
    fun setUp() {
        every { receiver.goAsync() } returns pendingResult
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `onReceive reports the dismissal when a campaign id is present`() {
        mockkObject(ChumbaqPush)
        every { ChumbaqPush.reportDismissed(any(), any()) } returns Unit

        receiver.onReceive(mockk(relaxed = true), dismissIntent("camp_1"))

        verify(exactly = 1) { ChumbaqPush.reportDismissed("camp_1", any()) }
    }

    @Test
    fun `onReceive reports nothing without a campaign id`() {
        mockkObject(ChumbaqPush)

        receiver.onReceive(mockk(relaxed = true), dismissIntent(null))

        verify(exactly = 0) { ChumbaqPush.reportDismissed(any(), any()) }
    }

    /**
     * The broadcast is held open until the report is away. Leaving it open
     * would keep the broadcast pending until the system times it out.
     */
    @Test
    fun `onReceive finishes the broadcast once the report is away`() {
        mockkObject(ChumbaqPush)
        every { ChumbaqPush.reportDismissed(any(), any()) } answers {
            secondArg<() -> Unit>().invoke()
        }

        receiver.onReceive(mockk(relaxed = true), dismissIntent("camp_1"))

        verify(exactly = 1) { pendingResult.finish() }
    }

    /** Nothing is held open when there was never anything to report. */
    @Test
    fun `onReceive without a campaign id holds nothing open`() {
        mockkObject(ChumbaqPush)

        receiver.onReceive(mockk(relaxed = true), dismissIntent(null))

        verify(exactly = 0) { receiver.goAsync() }
    }

    /**
     * The system fires this one, not the app, so it has to be a broadcast
     * [android.app.PendingIntent] into this receiver — the extras it carries
     * cannot be read back, since a unit-test [Intent] stores nothing.
     */
    @Test
    fun `the delete intent is a broadcast into this receiver`() {
        mockkStatic(PendingIntent::class)
        val pending = mockk<PendingIntent>()
        every { PendingIntent.getBroadcast(any(), any(), any(), any()) } returns pending

        val built = PushDismissReceiver.deleteIntent(mockk(relaxed = true), "camp_1", 7)

        assertSame(pending, built)
        verify(exactly = 1) { PendingIntent.getBroadcast(any(), any(), any(), any()) }
    }

    // ── Helpers ─────────────────────────────────────────────────────

    private fun dismissIntent(campaignId: String?) = mockk<Intent>(relaxed = true).apply {
        every { getStringExtra(PushClickReceiver.EXTRA_CAMPAIGN_ID) } returns campaignId
    }
}
