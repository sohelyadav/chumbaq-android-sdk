package com.dyplink.messages

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import com.dyplink.messages.internal.MessageApiClient
import com.dyplink.messages.internal.MessageCheckContext
import com.dyplink.messages.internal.MessageEventTracker
import com.dyplink.messages.model.InAppMessage
import com.dyplink.messages.model.MessageButton
import com.dyplink.messages.ui.InAppMessageDialog
import com.dyplink.sdk.DyplinkConfigBridge
import kotlinx.coroutines.CoroutineScope

internal class MessageManager(
    private val apiClient: MessageApiClient,
    private val scope: CoroutineScope,
    private val projectId: String,
    private val deviceFingerprint: String,
    private val distinctId: () -> String?,
) {
    private val eventTracker = MessageEventTracker(
        apiClient, scope, projectId, deviceFingerprint, distinctId,
    )

    var messageListener: MessageListener? = null

    suspend fun checkAndShow(context: Context, checkContext: MessageCheckContext) {
        val messages = apiClient.checkMessages(
            projectId = projectId,
            deviceFingerprint = deviceFingerprint,
            distinctId = distinctId(),
            context = checkContext,
        )
        if (messages.isEmpty()) return

        // Show on main thread, one at a time (top priority first)
        Handler(Looper.getMainLooper()).post {
            messages.forEach { message ->
                val delayMs = message.triggerDelay * 1000L
                Handler(Looper.getMainLooper()).postDelayed({
                    displayMessage(context, message)
                }, delayMs)
            }
        }
    }

    private fun displayMessage(context: Context, message: InAppMessage) {
        InAppMessageDialog(
            context = context,
            message = message,
            onEvent = { eventType, buttonId ->
                eventTracker.track(message.id, eventType, buttonId)
                messageListener?.onMessageEvent(message, eventType, buttonId)
            },
            onButtonAction = { button ->
                eventTracker.track(message.id, "cta_click", button.id)
                messageListener?.onButtonAction(message, button)
                handleButtonAction(context, button, message)
            },
        ).show()
    }

    private fun handleButtonAction(context: Context, button: MessageButton, message: InAppMessage) {
        when (button.action) {
            "deep_link", "url" -> {
                val url = button.actionUrl ?: return
                try {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    })
                } catch (_: Exception) {}
            }
            "custom_event" -> {
                button.actionEvent?.let { eventName ->
                    messageListener?.onCustomEvent(message, eventName)
                }
            }
            // Nothing to do beyond closing, which every tapped button does.
            "dismiss" -> {}
        }
    }
}

/** Listener for in-app message interactions. */
interface MessageListener {
    /** Called when a message event fires (impression, click, dismiss, etc.). */
    fun onMessageEvent(message: InAppMessage, eventType: String, buttonId: String?) {}
    /** Called when a CTA button is tapped. */
    fun onButtonAction(message: InAppMessage, button: MessageButton) {}
    /** Called when a button with action="custom_event" is tapped. */
    fun onCustomEvent(message: InAppMessage, eventName: String) {}
}
