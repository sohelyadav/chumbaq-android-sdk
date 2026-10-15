package com.dyplink.push

import com.dyplink.sdk.Dyplink
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [DyplinkPush].
 *
 * The real [PushTokenManager] is replaced with a mockk instance
 * via [DyplinkPush.injectForTesting] so that no Firebase or network
 * calls are made.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DyplinkPushTest {

    private lateinit var tokenManager: PushTokenManager
    private lateinit var eventReporter: PushEventReporter
    private val testDispatcher = StandardTestDispatcher()
    private lateinit var testScope: TestScope

    @Before
    fun setUp() {
        mockkObject(Dyplink)

        tokenManager = mockk<PushTokenManager>(relaxed = true)
        eventReporter = mockk<PushEventReporter>(relaxed = true)
        testScope = TestScope(testDispatcher)
    }

    @After
    fun tearDown() {
        DyplinkPush.resetForTesting()
        unmockkAll()
    }

    // ── init ────────────────────────────────────────────────────────

    @Test(expected = IllegalStateException::class)
    fun `init throws when Dyplink is not initialized`() {
        every { Dyplink.isInitialized } returns false

        val context = mockk<android.content.Context>(relaxed = true)
        DyplinkPush.init(context)
    }

    @Test
    fun `double init is a no-op`() {
        every { Dyplink.isInitialized } returns true

        // First init via injection
        DyplinkPush.injectForTesting(tokenManager, testScope, eventReporter)
        assertTrue("Should be initialized after inject", DyplinkPush.isInitialized)

        // Second init should not throw and should remain initialized
        val context = mockk<android.content.Context>(relaxed = true)
        every { context.applicationContext } returns context
        DyplinkPush.init(context)
        assertTrue("Should still be initialized after double init", DyplinkPush.isInitialized)
    }

    // ── isRegistered ────────────────────────────────────────────────

    @Test
    fun `isRegistered returns false when not initialized`() {
        DyplinkPush.resetForTesting()
        assertFalse(DyplinkPush.isRegistered)
    }

    @Test
    fun `isRegistered delegates to tokenManager when true`() {
        every { tokenManager.isRegistered } returns true
        DyplinkPush.injectForTesting(tokenManager, testScope, eventReporter)

        assertTrue(DyplinkPush.isRegistered)
    }

    @Test
    fun `isRegistered delegates to tokenManager when false`() {
        every { tokenManager.isRegistered } returns false
        DyplinkPush.injectForTesting(tokenManager, testScope, eventReporter)

        assertFalse(DyplinkPush.isRegistered)
    }

    // ── registerToken ───────────────────────────────────────────────

    @Test
    fun `registerToken delegates to tokenManager`() = runTest {
        coEvery { tokenManager.registerToken(any()) } returns Unit
        DyplinkPush.injectForTesting(tokenManager, testScope, eventReporter)

        DyplinkPush.registerToken("test_token")

        coVerify(exactly = 1) { tokenManager.registerToken("test_token") }
    }

    @Test(expected = IllegalStateException::class)
    fun `registerToken throws when not initialized`() = runTest {
        DyplinkPush.resetForTesting()
        DyplinkPush.registerToken("test_token")
    }

    // ── unregisterToken ─────────────────────────────────────────────

    @Test
    fun `unregisterToken delegates to tokenManager`() = runTest {
        coEvery { tokenManager.unregisterAllTokens() } returns Unit
        DyplinkPush.injectForTesting(tokenManager, testScope, eventReporter)

        DyplinkPush.unregisterToken()

        coVerify(exactly = 1) { tokenManager.unregisterAllTokens() }
    }

    @Test(expected = IllegalStateException::class)
    fun `unregisterToken throws when not initialized`() = runTest {
        DyplinkPush.resetForTesting()
        DyplinkPush.unregisterToken()
    }

    /**
     * [DyplinkPush] launches these on [kotlinx.coroutines.Dispatchers.IO],
     * which a [kotlinx.coroutines.test.TestScope] does not own — so
     * `advanceUntilIdle` returns without waiting for them and a bare verify
     * races the IO thread. Waiting is what makes them deterministic.
     */
    // ── onTokenRefresh ──────────────────────────────────────────────

    @Test
    fun `onTokenRefresh registers token when initialized`() = testScope.runTest {
        coEvery { tokenManager.registerToken(any()) } returns Unit
        DyplinkPush.injectForTesting(tokenManager, testScope, eventReporter)

        DyplinkPush.onTokenRefresh("refreshed_token")
        advanceUntilIdle()

        coVerify(timeout = VERIFY_TIMEOUT_MS, exactly = 1) { tokenManager.registerToken("refreshed_token") }
    }

    @Test
    fun `onTokenRefresh is no-op when not initialized`() = testScope.runTest {
        DyplinkPush.resetForTesting()

        // Should not throw
        DyplinkPush.onTokenRefresh("some_token")
        advanceUntilIdle()

        // tokenManager was never set, so no call is made — just verify no crash.
    }

    // ── callback variants ───────────────────────────────────────────

    @Test
    fun `registerToken callback receives success`() = testScope.runTest {
        coEvery { tokenManager.registerToken(any()) } returns Unit
        DyplinkPush.injectForTesting(tokenManager, testScope, eventReporter)

        var callbackResult: Result<Unit>? = null
        DyplinkPush.registerToken("test_token") { result ->
            callbackResult = result
        }
        advanceUntilIdle()

        assertTrue(
            "Callback should receive success",
            callbackResult?.isSuccess == true,
        )
    }

    @Test
    fun `registerToken callback receives failure`() = testScope.runTest {
        coEvery { tokenManager.registerToken(any()) } throws RuntimeException("boom")
        DyplinkPush.injectForTesting(tokenManager, testScope, eventReporter)

        var callbackResult: Result<Unit>? = null
        DyplinkPush.registerToken("test_token") { result ->
            callbackResult = result
        }
        advanceUntilIdle()

        assertTrue(
            "Callback should receive failure",
            callbackResult?.isFailure == true,
        )
        assertTrue(
            "Failure cause should be RuntimeException",
            callbackResult?.exceptionOrNull() is RuntimeException,
        )
    }

    @Test
    fun `unregisterToken callback receives success`() = testScope.runTest {
        coEvery { tokenManager.unregisterAllTokens() } returns Unit
        DyplinkPush.injectForTesting(tokenManager, testScope, eventReporter)

        var callbackResult: Result<Unit>? = null
        DyplinkPush.unregisterToken { result ->
            callbackResult = result
        }
        advanceUntilIdle()

        assertTrue(
            "Callback should receive success",
            callbackResult?.isSuccess == true,
        )
    }

    // ── registerMiPushToken ─────────────────────────────────────────

    @Test
    fun `registerMiPushToken delegates to tokenManager on the mi push channel`() = runTest {
        coEvery { tokenManager.registerToken(any(), any()) } returns Unit
        DyplinkPush.injectForTesting(tokenManager, testScope, eventReporter)

        DyplinkPush.registerMiPushToken("mi_regid_777")

        coVerify(exactly = 1) {
            tokenManager.registerToken("mi_regid_777", PushProvider.MI_PUSH)
        }
    }

    @Test(expected = IllegalStateException::class)
    fun `registerMiPushToken throws when not initialized`() = runTest {
        DyplinkPush.resetForTesting()
        DyplinkPush.registerMiPushToken("mi_regid_777")
    }

    @Test
    fun `registerMiPushToken callback receives success`() = testScope.runTest {
        coEvery { tokenManager.registerToken(any(), any()) } returns Unit
        DyplinkPush.injectForTesting(tokenManager, testScope, eventReporter)

        var callbackResult: Result<Unit>? = null
        DyplinkPush.registerMiPushToken("mi_regid_777") { result ->
            callbackResult = result
        }
        advanceUntilIdle()

        assertTrue(
            "Callback should receive success",
            callbackResult?.isSuccess == true,
        )
        coVerify(exactly = 1) {
            tokenManager.registerToken("mi_regid_777", PushProvider.MI_PUSH)
        }
    }

    @Test
    fun `registerMiPushToken callback receives failure`() = testScope.runTest {
        // A deployment without Mi Push credentials answers 400 — surfaced,
        // not swallowed.
        coEvery {
            tokenManager.registerToken(any(), PushProvider.MI_PUSH)
        } throws RuntimeException("boom")
        DyplinkPush.injectForTesting(tokenManager, testScope, eventReporter)

        var callbackResult: Result<Unit>? = null
        DyplinkPush.registerMiPushToken("mi_regid_777") { result ->
            callbackResult = result
        }
        advanceUntilIdle()

        assertTrue(
            "Callback should receive failure",
            callbackResult?.isFailure == true,
        )
    }

    // ── reportDelivered ─────────────────────────────────────────────

    @Test
    fun `reportDelivered delegates to eventReporter when initialized`() = testScope.runTest {
        coEvery { eventReporter.reportDelivered(any()) } returns Unit
        DyplinkPush.injectForTesting(tokenManager, testScope, eventReporter)

        val data = mapOf("dyplink_campaign_id" to "camp_1")
        DyplinkPush.reportDelivered(data)
        advanceUntilIdle()

        coVerify(timeout = VERIFY_TIMEOUT_MS, exactly = 1) { eventReporter.reportDelivered(data) }
    }

    @Test
    fun `reportDelivered is no-op when not initialized`() = testScope.runTest {
        DyplinkPush.resetForTesting()

        // Should not throw
        DyplinkPush.reportDelivered(mapOf("dyplink_campaign_id" to "camp_1"))
        advanceUntilIdle()

        // eventReporter was never injected, so no call is made — just verify no crash.
    }

    // ── reportImpression ────────────────────────────────────────────

    @Test
    fun `reportImpression delegates to eventReporter when initialized`() = testScope.runTest {
        coEvery { eventReporter.reportImpression(any()) } returns Unit
        DyplinkPush.injectForTesting(tokenManager, testScope, eventReporter)

        DyplinkPush.reportImpression("camp_1")
        advanceUntilIdle()

        coVerify(timeout = VERIFY_TIMEOUT_MS, exactly = 1) { eventReporter.reportImpression("camp_1") }
    }

    @Test
    fun `reportImpression is no-op when not initialized`() = testScope.runTest {
        DyplinkPush.resetForTesting()

        // Should not throw
        DyplinkPush.reportImpression("camp_1")
        advanceUntilIdle()
    }

    // ── reportDismissed ─────────────────────────────────────────────

    @Test
    fun `reportDismissed delegates to eventReporter when initialized`() = testScope.runTest {
        coEvery { eventReporter.reportDismissed(any()) } returns Unit
        DyplinkPush.injectForTesting(tokenManager, testScope, eventReporter)

        DyplinkPush.reportDismissed("camp_1")
        advanceUntilIdle()

        coVerify(timeout = VERIFY_TIMEOUT_MS, exactly = 1) { eventReporter.reportDismissed("camp_1") }
    }

    /**
     * The receiver holds a `goAsync()` result open until this fires. Never
     * finishing it leaves the broadcast pending until the system times it
     * out, so it has to run on the uninitialised path too.
     */
    @Test
    fun `reportDismissed completes even when not initialized`() = testScope.runTest {
        DyplinkPush.resetForTesting()

        var completed = false
        DyplinkPush.reportDismissed("camp_1") { completed = true }
        advanceUntilIdle()

        assertTrue(completed)
    }

    // ── reportClick ─────────────────────────────────────────────────

    @Test
    fun `reportClick delegates to eventReporter when initialized`() = testScope.runTest {
        coEvery { eventReporter.reportClick(any()) } returns Unit
        DyplinkPush.injectForTesting(tokenManager, testScope, eventReporter)

        DyplinkPush.reportClick("camp_1")
        advanceUntilIdle()

        coVerify(timeout = VERIFY_TIMEOUT_MS, exactly = 1) { eventReporter.reportClick("camp_1") }
    }

    @Test
    fun `reportClick is no-op when not initialized`() = testScope.runTest {
        DyplinkPush.resetForTesting()

        // Should not throw
        DyplinkPush.reportClick("camp_1")
        advanceUntilIdle()

        // eventReporter was never injected, so no call is made — just verify no crash.
    }

    private companion object {
        /** Generous: it only ever elapses when something is actually broken. */
        const val VERIFY_TIMEOUT_MS = 5_000L
    }
}
