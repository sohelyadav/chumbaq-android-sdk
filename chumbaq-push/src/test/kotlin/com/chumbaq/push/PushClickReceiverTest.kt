package com.chumbaq.push

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [PushClickReceiver].
 *
 * [ChumbaqPush] is mocked via [mockkObject] so no real network/coroutine
 * work happens; we only verify that the click is reported and that the
 * original target intent is forwarded (or safely skipped).
 */
class PushClickReceiverTest {

    // Spied so goAsync() can be stubbed. The receiver calls it to keep the
    // process alive while the click is reported, but under unit tests there is
    // no real broadcast in flight and the framework hands back null, so the
    // completion callback would NPE on something the test is not about.
    private val receiver = spyk(PushClickReceiver())
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
    fun `onReceive reports click and forwards target intent when campaign id present`() {
        mockkObject(ChumbaqPush)
        every { ChumbaqPush.reportClick(any(), any(), any()) } returns Unit

        val context = mockk<Context>(relaxed = true)
        val targetIntent = mockk<Intent>(relaxed = true)
        val receivedIntent = mockk<Intent>(relaxed = true)
        every { receivedIntent.getStringExtra(PushClickReceiver.EXTRA_CAMPAIGN_ID) } returns "camp_1"
        every {
            receivedIntent.getParcelableExtra<Intent>(PushClickReceiver.EXTRA_TARGET_INTENT)
        } returns targetIntent

        receiver.onReceive(context, receivedIntent)

        // Three arguments, not one: the action id distinguishes a tap on the
        // notification from a tap on one of its buttons, and the callback is
        // what releases the goAsync result once the report is away.
        verify(exactly = 1) { ChumbaqPush.reportClick("camp_1", any(), any()) }
        verify(exactly = 1) { targetIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
        verify(exactly = 1) { context.startActivity(targetIntent) }
    }

    @Test
    fun `onReceive does not report click when campaign id absent`() {
        mockkObject(ChumbaqPush)

        val context = mockk<Context>(relaxed = true)
        val targetIntent = mockk<Intent>(relaxed = true)
        val receivedIntent = mockk<Intent>(relaxed = true)
        every { receivedIntent.getStringExtra(PushClickReceiver.EXTRA_CAMPAIGN_ID) } returns null
        every {
            receivedIntent.getParcelableExtra<Intent>(PushClickReceiver.EXTRA_TARGET_INTENT)
        } returns targetIntent

        receiver.onReceive(context, receivedIntent)

        verify(exactly = 0) { ChumbaqPush.reportClick(any()) }
        verify(exactly = 1) { context.startActivity(targetIntent) }
    }

    @Test
    fun `onReceive does not throw when target intent is missing`() {
        mockkObject(ChumbaqPush)
        every { ChumbaqPush.reportClick(any(), any(), any()) } returns Unit

        val context = mockk<Context>(relaxed = true)
        val receivedIntent = mockk<Intent>(relaxed = true)
        every { receivedIntent.getStringExtra(PushClickReceiver.EXTRA_CAMPAIGN_ID) } returns "camp_1"
        every {
            receivedIntent.getParcelableExtra<Intent>(PushClickReceiver.EXTRA_TARGET_INTENT)
        } returns null

        // Should not throw even without a target to forward to.
        receiver.onReceive(context, receivedIntent)

        // Three arguments, not one: the action id distinguishes a tap on the
        // notification from a tap on one of its buttons, and the callback is
        // what releases the goAsync result once the report is away.
        verify(exactly = 1) { ChumbaqPush.reportClick("camp_1", any(), any()) }
        verify(exactly = 0) { context.startActivity(any()) }
    }
}
