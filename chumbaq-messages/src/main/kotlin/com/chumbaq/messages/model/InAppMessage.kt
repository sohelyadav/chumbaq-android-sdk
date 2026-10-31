package com.chumbaq.messages.model

/**
 * A resolved in-app message ready to be displayed.
 *
 * Mirrors the [DeliveredMessage] shape returned by the platform's
 * `POST /api/messages/check` endpoint.
 */
data class InAppMessage(
    val id: String,
    /** "modal" | "bottom_sheet" | "banner_top" | "banner_bottom" | "fullscreen" */
    val messageType: String,
    val title: String,
    val body: String?,
    val imageUrl: String?,
    /** "top" | "center" | "background" */
    val imagePosition: String,
    val buttons: List<MessageButton>?,
    val theme: MessageTheme?,
    val dismissOnTapOutside: Boolean,
    /** Seconds before auto-dismiss. Null means no auto-dismiss. */
    val autoDismissSeconds: Int?,
    /** Delay in seconds before showing the message after the trigger fires. */
    val triggerDelay: Int,
)
