package com.dyplink.messages

import android.content.Context
import com.dyplink.messages.internal.MessageApiClient
import com.dyplink.messages.internal.MessageCheckContext
import com.dyplink.sdk.Dyplink
import com.dyplink.sdk.DyplinkConfigBridge
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Entry point for Dyplink in-app messages.
 *
 * Requires [Dyplink.init] to have been called first.
 *
 * **Basic usage:**
 * ```kotlin
 * // On app open — checks and displays on_app_open messages
 * DyplinkMessages.onAppOpen(this)
 *
 * // On screen change
 * DyplinkMessages.onScreenView(this, screen = "HomeScreen")
 *
 * // On custom event
 * DyplinkMessages.onEvent(this, event = "purchase_complete")
 * ```
 */
object DyplinkMessages {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val apiClient = MessageApiClient()

    private val manager: MessageManager by lazy {
        MessageManager(
            apiClient = apiClient,
            scope = scope,
            projectId = DyplinkConfigBridge.projectId,
            deviceFingerprint = Dyplink.deviceFingerprint,
            distinctId = { Dyplink.distinctId },
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
        check(Dyplink.isInitialized) { "Dyplink.init() must be called before using DyplinkMessages" }
        scope.launch(Dispatchers.IO) {
            try {
                manager.checkAndShow(context, checkContext)
            } catch (_: Exception) {
                // Never crash the host app
            }
        }
    }
}
