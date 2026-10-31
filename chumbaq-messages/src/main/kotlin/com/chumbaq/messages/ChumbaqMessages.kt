package com.chumbaq.messages

import android.content.Context
import com.chumbaq.messages.internal.ActivityTracker
import com.chumbaq.messages.internal.MessageApiClient
import com.chumbaq.messages.internal.MessageCheckContext
import com.chumbaq.sdk.Chumbaq
import com.chumbaq.sdk.ChumbaqConfigBridge
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Entry point for Chumbaq in-app messages.
 *
 * Requires [Chumbaq.init] to have been called first.
 *
 * **Basic usage:**
 * ```kotlin
 * // On app open — checks and displays on_app_open messages
 * ChumbaqMessages.onAppOpen(this)
 *
 * // On screen change
 * ChumbaqMessages.onScreenView(this, screen = "HomeScreen")
 *
 * // On custom event
 * ChumbaqMessages.onEvent(this, event = "purchase_complete")
 * ```
 */
object ChumbaqMessages {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val apiClient = MessageApiClient()

    private val manager: MessageManager by lazy {
        MessageManager(
            apiClient = apiClient,
            scope = scope,
            projectId = ChumbaqConfigBridge.projectId,
            deviceFingerprint = Chumbaq.deviceFingerprint,
            distinctId = { Chumbaq.distinctId },
        )
    }

    // ── Trigger points ─────────────────────────────────────────────

    /**
     * Call from `Activity.onResume()` or `Application.onCreate()`.
     * Checks for `on_app_open` and `scheduled` messages.
     */
    @JvmStatic
    fun onAppOpen(context: Context) {
        checkAndShow(context, MessageCheckContext())
    }

    /**
     * Call when the user navigates to a new screen.
     * Checks for `on_screen` messages targeting [screen].
     */
    @JvmStatic
    fun onScreenView(context: Context, screen: String) {
        checkAndShow(context, MessageCheckContext(screen = screen))
    }

    /**
     * Call when a custom event fires (e.g. "purchase_complete").
     * Checks for `on_event` messages targeting [event].
     */
    @JvmStatic
    fun onEvent(context: Context, event: String) {
        checkAndShow(context, MessageCheckContext(event = event))
    }

    // ── Listener ───────────────────────────────────────────────────

    /**
     * Sets (or clears) the listener that receives message interaction callbacks.
     */
    @JvmStatic
    fun setMessageListener(listener: MessageListener?) {
        manager.messageListener = listener
    }

    // ── Internal ───────────────────────────────────────────────────

    private fun checkAndShow(context: Context, checkContext: MessageCheckContext) {
        check(Chumbaq.isInitialized) { "Chumbaq.init() must be called before using ChumbaqMessages" }

        // The only thing [context] is for. A message is displayed on
        // whichever Activity is in the foreground when its trigger delay
        // elapses, not on the one that happened to make the check — see
        // [ActivityTracker].
        ActivityTracker.register(context)

        scope.launch(Dispatchers.IO) {
            try {
                manager.checkAndShow(checkContext)
            } catch (_: Exception) {
                // Never crash the host app
            }
        }
    }
}
