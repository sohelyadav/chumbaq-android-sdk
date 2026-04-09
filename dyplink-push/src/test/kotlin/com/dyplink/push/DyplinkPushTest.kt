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
    private val testDispatcher = StandardTestDispatcher()
    private lateinit var testScope: TestScope

    @Before
    fun setUp() {
        mockkObject(Dyplink)

        tokenManager = mockk<PushTokenManager>(relaxed = true)
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
        DyplinkPush.injectForTesting(tokenManager, testScope)
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
        DyplinkPush.injectForTesting(tokenManager, testScope)

        assertTrue(DyplinkPush.isRegistered)
    }

    @Test
    fun `isRegistered delegates to tokenManager when false`() {
        every { tokenManager.isRegistered } returns false
        DyplinkPush.injectForTesting(tokenManager, testScope)

        assertFalse(DyplinkPush.isRegistered)
    }

    // ── registerToken ───────────────────────────────────────────────

    @Test
    fun `registerToken delegates to tokenManager`() = runTest {
        coEvery { tokenManager.registerToken(any()) } returns Unit
        DyplinkPush.injectForTesting(tokenManager, testScope)

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
        coEvery { tokenManager.unregisterCurrentToken() } returns Unit
        DyplinkPush.injectForTesting(tokenManager, testScope)

        DyplinkPush.unregisterToken()

        coVerify(exactly = 1) { tokenManager.unregisterCurrentToken() }
    }

    @Test(expected = IllegalStateException::class)
    fun `unregisterToken throws when not initialized`() = runTest {
        DyplinkPush.resetForTesting()
        DyplinkPush.unregisterToken()
    }

    // ── onTokenRefresh ──────────────────────────────────────────────

    @Test
    fun `onTokenRefresh registers token when initialized`() = testScope.runTest {
        coEvery { tokenManager.registerToken(any()) } returns Unit
        DyplinkPush.injectForTesting(tokenManager, testScope)

        DyplinkPush.onTokenRefresh("refreshed_token")
        advanceUntilIdle()

        coVerify(exactly = 1) { tokenManager.registerToken("refreshed_token") }
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
        DyplinkPush.injectForTesting(tokenManager, testScope)

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
        DyplinkPush.injectForTesting(tokenManager, testScope)

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
        coEvery { tokenManager.unregisterCurrentToken() } returns Unit
        DyplinkPush.injectForTesting(tokenManager, testScope)

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
}
