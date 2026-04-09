package com.dyplink.sdk.internal.lifecycle

import com.dyplink.sdk.internal.events.EventTracker
import com.dyplink.sdk.internal.storage.DyplinkPreferences
import com.dyplink.sdk.internal.util.Logger

/**
 * Tracks app sessions based on foreground/background transitions.
 *
 * A new session is started whenever the app comes to the foreground
 * and the elapsed time since the last background exceeds
 * [sessionTimeoutSeconds].
 */
internal class SessionTracker(
    private val eventTracker: EventTracker,
    private val preferences: DyplinkPreferences,
    private val sessionTimeoutSeconds: Int,
) {

    /**
     * `true` when the app is currently in a foreground session.
     */
    @Volatile
    var isInSession: Boolean = false
        private set

    /**
     * Called when the app enters the foreground.
     *
     * If the time elapsed since the last [onAppBackgrounded] call
     * exceeds [sessionTimeoutSeconds], a `session_start` event is
     * tracked and the session timestamp is refreshed.
     */
    fun onAppForegrounded() {
        val now = System.currentTimeMillis()
        val lastTimestamp = preferences.lastSessionTimestamp
        val elapsedSeconds = if (lastTimestamp > 0L) {
            (now - lastTimestamp) / 1_000L
        } else {
            // First launch — always start a session.
            Long.MAX_VALUE
        }

        if (elapsedSeconds >= sessionTimeoutSeconds) {
            Logger.d("SessionTracker: session timeout exceeded — starting new session")
            eventTracker.track("session_start")
        }

        preferences.lastSessionTimestamp = now
        isInSession = true
    }

    /**
     * Called when the app enters the background.
     *
     * Records the current timestamp so that the next foreground
     * transition can determine whether the session has expired.
     */
    fun onAppBackgrounded() {
        preferences.lastSessionTimestamp = System.currentTimeMillis()
        isInSession = false
    }
}
