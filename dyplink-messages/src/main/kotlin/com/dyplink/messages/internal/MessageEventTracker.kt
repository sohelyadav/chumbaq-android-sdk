package com.dyplink.messages.internal

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Tracks message events (impression, click, dismiss, etc.) fire-and-forget. */
internal class MessageEventTracker(
    private val apiClient: MessageApiClient,
    private val scope: CoroutineScope,
    private val projectId: String,
    private val deviceFingerprint: String,
    private val distinctId: () -> String?,
) {
    fun track(messageId: String, eventType: String, buttonId: String? = null) {
        scope.launch(Dispatchers.IO) {
            try {
                apiClient.recordEvent(
                    messageId = messageId,
                    projectId = projectId,
                    deviceFingerprint = deviceFingerprint,
                    distinctId = distinctId(),
                    eventType = eventType,
                    buttonId = buttonId,
                )
            } catch (_: Exception) {
                // Fire-and-forget — never crash the app
            }
        }
    }
}
