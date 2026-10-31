package com.chumbaq.messages

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import com.chumbaq.messages.internal.ActivityTracker
import com.chumbaq.messages.internal.MessageApiClient
import com.chumbaq.messages.internal.MessageCheckContext
import com.chumbaq.messages.internal.MessageEventTracker
import com.chumbaq.messages.model.InAppMessage
import com.chumbaq.messages.model.MessageButton
import com.chumbaq.messages.ui.InAppMessageDialog
import com.chumbaq.sdk.ChumbaqDiagnostics
import kotlinx.coroutines.CoroutineScope

internal class MessageManager(
    private val apiClient: MessageApiClient,
    private val scope: CoroutineScope,
    private val projectId: String,
    private val deviceFingerprint: String,
    private val distinctId: () -> String?,
    /**
     * The window a message attaches to, asked for at display time.
     *
     * Not the Activity that made the check: a message can be up to
     * `triggerDelay` seconds behind it, and that Activity may be gone.
     */
    private val displayActivity: () -> Activity? = { ActivityTracker.displayActivity },
    /** Overridable so a test can assert on reports without waiting for IO. */
    private val eventTracker: MessageEventTracker = MessageEventTracker(
        apiClient, scope, projectId, deviceFingerprint, distinctId,
    ),
) {
    private val handler = Handler(Looper.getMainLooper())

    /**
     * Messages waiting for the screen, in the order the server returned
     * them — which is priority descending, since `DeliveredMessage` carries
     * no priority field of its own for the SDK to re-sort by.
     */
    private val pending = ArrayDeque<InAppMessage>()

    /** The message holding the screen, including its trigger delay. */
    private var showing: InAppMessage? = null

    var messageListener: MessageListener? = null

    /**
     * Checks for eligible messages and queues whatever came back.
     *
     * The server returns up to three. They are shown one at a time, highest
     * priority first: posting all three at once stacked them, left the
     * *lowest* priority one on top because it was drawn last, and reported
     * three impressions for one check. Two triggers in a row — `onAppOpen`
     * then `onScreenView` — stacked further.
     *
     * Takes no [Context] on purpose. The caller's Activity used to be
     * carried from here all the way to the `show()` that happened seconds
     * later; the display one is resolved at that point instead, through
     * [displayActivity].
     */
    suspend fun checkAndShow(checkContext: MessageCheckContext) {
        val messages = apiClient.checkMessages(
            projectId = projectId,
            deviceFingerprint = deviceFingerprint,
            distinctId = distinctId(),
            context = checkContext,
        )
        if (messages.isEmpty()) return

        // The queue and its timers belong to the main thread; the check runs
        // on IO.
        handler.post { enqueue(messages) }
    }

    // ── Queue ──────────────────────────────────────────────────────

    /**
     * Appends [messages] and starts the queue if nothing holds the screen.
     *
     * A message already queued or on screen is skipped: overlapping triggers
     * return the same eligible set, and the same message twice in a row is
     * never what the campaign asked for. Only the live queue is consulted —
     * this is not a history, so a message can be shown again later in the
     * session exactly as before.
     */
    private fun enqueue(messages: List<InAppMessage>) {
        for (message in messages) {
            if (showing?.id == message.id || pending.any { it.id == message.id }) continue
            pending.addLast(message)
            // `delivered` is the honest top of the funnel: the message
            // reached the device. The `impression` below it is reported by
            // the dialog, so only the message that reached the screen counts
            // — which is what made the funnel read inverted while two of the
            // three queued messages were never shown but all three reported.
            eventTracker.track(message.id, "delivered")
        }
        showNext()
    }

    /** Takes the next message, unless one still holds the screen. */
    private fun showNext() {
        if (showing != null) return
        val message = pending.removeFirstOrNull() ?: return
        showing = message
        handler.postDelayed({ display(message) }, message.triggerDelay * 1_000L)
    }

    /**
     * Releases the screen and moves on.
     *
     * Keyed on the message so a second close for one already finished with —
     * a dialog dismissed twice, a drop that races its own dismissal — cannot
     * skip the message after it.
     */
    private fun finish(message: InAppMessage) {
        if (showing?.id != message.id) return
        showing = null
        showNext()
    }

    // ── Display ────────────────────────────────────────────────────

    /**
     * Shows [message] on whichever Activity is in the foreground now, or
     * drops it if there is none.
     *
     * Dropping is the whole point of resolving late: `show()` on a dead
     * Activity throws `BadTokenException`, and nothing above here catches it
     * — `ChumbaqMessages` wraps the check, which only posts this work.
     */
    private fun display(message: InAppMessage) {
        val activity = displayActivity()
        if (activity == null) {
            ChumbaqDiagnostics.warn(
                "MessageManager",
                "dropped ${message.id}: no activity in the foreground",
            )
            finish(message)
            return
        }

        try {
            displayMessage(activity, message)
        } catch (e: Exception) {
            // The window went away between the check above and the show.
            ChumbaqDiagnostics.warn("MessageManager", "could not show ${message.id}", e)
            finish(message)
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
            onClosed = { finish(message) },
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
