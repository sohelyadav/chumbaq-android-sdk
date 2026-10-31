package com.chumbaq.sdk.internal.lifecycle

import com.chumbaq.sdk.internal.events.EventTracker
import com.chumbaq.sdk.internal.storage.ChumbaqPreferences
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SessionTrackerTest {

    private lateinit var eventTracker: EventTracker
    private lateinit var preferences: ChumbaqPreferences
    private lateinit var sessionTracker: SessionTracker

    private val sessionTimeoutSeconds = 300 // 5 minutes

    @Before
    fun setUp() {
        eventTracker = mockk(relaxed = true)
        preferences = mockk(relaxed = true)
    }

    // ── onAppForegrounded ──────────────────────────────────────────

    @Test
    fun `onAppForegrounded after timeout tracks session_start event`() {
        // Last session was 10 minutes ago — well past the 5-minute timeout
        val tenMinutesAgo = System.currentTimeMillis() - (10 * 60 * 1_000L)
        every { preferences.lastSessionTimestamp } returns tenMinutesAgo
        every { preferences.lastSessionTimestamp = any() } just Runs

        sessionTracker = SessionTracker(eventTracker, preferences, sessionTimeoutSeconds)
        sessionTracker.onAppForegrounded()

        verify(exactly = 1) { eventTracker.track("session_start") }
        verify { preferences.lastSessionTimestamp = any() }
        assertTrue(sessionTracker.isInSession)
    }

    @Test
    fun `onAppForegrounded exactly at timeout boundary tracks session_start event`() {
        // Last session was exactly sessionTimeoutSeconds ago
        val exactlyAtTimeout = System.currentTimeMillis() - (sessionTimeoutSeconds * 1_000L)
        every { preferences.lastSessionTimestamp } returns exactlyAtTimeout
        every { preferences.lastSessionTimestamp = any() } just Runs

        sessionTracker = SessionTracker(eventTracker, preferences, sessionTimeoutSeconds)
        sessionTracker.onAppForegrounded()

        // >= comparison means exactly at timeout should fire
        verify(exactly = 1) { eventTracker.track("session_start") }
        assertTrue(sessionTracker.isInSession)
    }

    @Test
    fun `onAppForegrounded within timeout does not track event`() {
        // Last session was 1 minute ago — within the 5-minute timeout
        val oneMinuteAgo = System.currentTimeMillis() - (1 * 60 * 1_000L)
        every { preferences.lastSessionTimestamp } returns oneMinuteAgo
        every { preferences.lastSessionTimestamp = any() } just Runs

        sessionTracker = SessionTracker(eventTracker, preferences, sessionTimeoutSeconds)
        sessionTracker.onAppForegrounded()

        verify(exactly = 0) { eventTracker.track(any(), any()) }
        verify(exactly = 0) { eventTracker.track("session_start") }
        assertTrue(sessionTracker.isInSession)
    }

    @Test
    fun `onAppForegrounded on first launch tracks session_start`() {
        // lastSessionTimestamp is 0 (never set) — should always start session
        every { preferences.lastSessionTimestamp } returns 0L
        every { preferences.lastSessionTimestamp = any() } just Runs

        sessionTracker = SessionTracker(eventTracker, preferences, sessionTimeoutSeconds)
        sessionTracker.onAppForegrounded()

        verify(exactly = 1) { eventTracker.track("session_start") }
        assertTrue(sessionTracker.isInSession)
    }

    @Test
    fun `onAppForegrounded updates lastSessionTimestamp`() {
        val tenMinutesAgo = System.currentTimeMillis() - (10 * 60 * 1_000L)
        every { preferences.lastSessionTimestamp } returns tenMinutesAgo
        every { preferences.lastSessionTimestamp = any() } just Runs

        sessionTracker = SessionTracker(eventTracker, preferences, sessionTimeoutSeconds)

        val before = System.currentTimeMillis()
        sessionTracker.onAppForegrounded()
        val after = System.currentTimeMillis()

        verify { preferences.lastSessionTimestamp = match { it in before..after } }
    }

    // ── onAppBackgrounded ──────────────────────────────────────────

    @Test
    fun `onAppBackgrounded updates lastSessionTimestamp`() {
        every { preferences.lastSessionTimestamp } returns System.currentTimeMillis()
        every { preferences.lastSessionTimestamp = any() } just Runs

        sessionTracker = SessionTracker(eventTracker, preferences, sessionTimeoutSeconds)

        val before = System.currentTimeMillis()
        sessionTracker.onAppBackgrounded()
        val after = System.currentTimeMillis()

        verify { preferences.lastSessionTimestamp = match { it in before..after } }
    }

    @Test
    fun `onAppBackgrounded sets isInSession to false`() {
        every { preferences.lastSessionTimestamp } returns 0L
        every { preferences.lastSessionTimestamp = any() } just Runs

        sessionTracker = SessionTracker(eventTracker, preferences, sessionTimeoutSeconds)

        // First, move to foreground
        sessionTracker.onAppForegrounded()
        assertTrue(sessionTracker.isInSession)

        // Now background
        sessionTracker.onAppBackgrounded()
        assertFalse(sessionTracker.isInSession)
    }

    @Test
    fun `onAppBackgrounded does not track any events`() {
        every { preferences.lastSessionTimestamp } returns System.currentTimeMillis()
        every { preferences.lastSessionTimestamp = any() } just Runs

        sessionTracker = SessionTracker(eventTracker, preferences, sessionTimeoutSeconds)
        sessionTracker.onAppBackgrounded()

        verify(exactly = 0) { eventTracker.track(any(), any()) }
        verify(exactly = 0) { eventTracker.track(any()) }
    }

    // ── isInSession ────────────────────────────────────────────────

    @Test
    fun `isInSession is false initially`() {
        every { preferences.lastSessionTimestamp } returns 0L

        sessionTracker = SessionTracker(eventTracker, preferences, sessionTimeoutSeconds)
        assertFalse(sessionTracker.isInSession)
    }

    // ── Custom timeout ─────────────────────────────────────────────

    @Test
    fun `custom short timeout fires session_start sooner`() {
        val shortTimeout = 10 // 10 seconds
        val fifteenSecondsAgo = System.currentTimeMillis() - (15 * 1_000L)
        every { preferences.lastSessionTimestamp } returns fifteenSecondsAgo
        every { preferences.lastSessionTimestamp = any() } just Runs

        sessionTracker = SessionTracker(eventTracker, preferences, shortTimeout)
        sessionTracker.onAppForegrounded()

        verify(exactly = 1) { eventTracker.track("session_start") }
    }

    @Test
    fun `custom short timeout does not fire within window`() {
        val shortTimeout = 10 // 10 seconds
        val fiveSecondsAgo = System.currentTimeMillis() - (5 * 1_000L)
        every { preferences.lastSessionTimestamp } returns fiveSecondsAgo
        every { preferences.lastSessionTimestamp = any() } just Runs

        sessionTracker = SessionTracker(eventTracker, preferences, shortTimeout)
        sessionTracker.onAppForegrounded()

        verify(exactly = 0) { eventTracker.track("session_start") }
    }
}
