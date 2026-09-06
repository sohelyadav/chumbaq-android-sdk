package com.dyplink.push

import android.content.Context
import android.content.Intent
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.After
import org.junit.Test

/**
 * Unit tests for [PushClickReceiver].
 *
 * [DyplinkPush] is mocked via [mockkObject] so no real network/coroutine
 * work happens; we only verify that the click is reported and that the
 * original target intent is forwarded (or safely skipped).
 */
class PushClickReceiverTest {

    private val receiver = PushClickReceiver()

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `onReceive reports click and forwards target intent when campaign id present`() {
        mockkObject(DyplinkPush)
        every { DyplinkPush.reportClick(any()) } returns Unit

        val context = mockk<Context>(relaxed = true)
        val targetIntent = mockk<Intent>(relaxed = true)
        val receivedIntent = mockk<Intent>(relaxed = true)
        every { receivedIntent.getStringExtra(PushClickReceiver.EXTRA_CAMPAIGN_ID) } returns "camp_1"
        every {
            receivedIntent.getParcelableExtra<Intent>(PushClickReceiver.EXTRA_TARGET_INTENT)
        } returns targetIntent

        receiver.onReceive(context, receivedIntent)

        verify(exactly = 1) { DyplinkPush.reportClick("camp_1") }
        verify(exactly = 1) { targetIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
        verify(exactly = 1) { context.startActivity(targetIntent) }
    }

    @Test
    fun `onReceive does not report click when campaign id absent`() {
        mockkObject(DyplinkPush)

        val context = mockk<Context>(relaxed = true)
        val targetIntent = mockk<Intent>(relaxed = true)
        val receivedIntent = mockk<Intent>(relaxed = true)
        every { receivedIntent.getStringExtra(PushClickReceiver.EXTRA_CAMPAIGN_ID) } returns null
        every {
            receivedIntent.getParcelableExtra<Intent>(PushClickReceiver.EXTRA_TARGET_INTENT)
        } returns targetIntent

        receiver.onReceive(context, receivedIntent)

        verify(exactly = 0) { DyplinkPush.reportClick(any()) }
        verify(exactly = 1) { context.startActivity(targetIntent) }
    }

    @Test
    fun `onReceive does not throw when target intent is missing`() {
        mockkObject(DyplinkPush)
        every { DyplinkPush.reportClick(any()) } returns Unit

        val context = mockk<Context>(relaxed = true)
        val receivedIntent = mockk<Intent>(relaxed = true)
        every { receivedIntent.getStringExtra(PushClickReceiver.EXTRA_CAMPAIGN_ID) } returns "camp_1"
        every {
            receivedIntent.getParcelableExtra<Intent>(PushClickReceiver.EXTRA_TARGET_INTENT)
        } returns null

        // Should not throw even without a target to forward to.
        receiver.onReceive(context, receivedIntent)

        verify(exactly = 1) { DyplinkPush.reportClick("camp_1") }
        verify(exactly = 0) { context.startActivity(any()) }
    }
}
