package com.dyplink.push

import android.content.Intent
import com.google.firebase.messaging.RemoteMessage

/**
 * Everything [PushNotificationHandler] needs to build one notification,
 * lifted out of the [RemoteMessage] it arrived in.
 *
 * It exists because a carousel has to be rebuilt long after the message is
 * gone: the prev/next buttons fire into [PushCarouselReceiver], which has
 * only the extras it was given. Keeping the fields flat strings means the
 * whole payload travels on that [Intent] unchanged, and the slide the user
 * asked for is built from exactly what built the first one.
 */
internal data class PushContent(
    val title: String,
    val body: String,
    /** Campaign-wide link, used when a slide or button declares none. */
    val deepLinkUrl: String?,
    /** Present only on Dyplink campaign pushes; what click reporting keys on. */
    val campaignId: String?,
    /** Raw `dyplink_image_url` value. */
    val imageUrl: String?,
    /** Raw `dyplink_carousel` JSON, parsed by [PushRichContent.parseCarousel]. */
    val carouselJson: String?,
    /** Raw `dyplink_timer` JSON, parsed by [PushRichContent.resolveTimer]. */
    val timerJson: String?,
    /** Raw `dyplink_actions` JSON, parsed where the buttons are built. */
    val actionsJson: String?,
) {

    /** Nothing to show: no title and no body. */
    val isEmpty: Boolean get() = title.isEmpty() && body.isEmpty()

    /** Writes this payload onto [intent] so [from] can read it back. */
    fun writeTo(intent: Intent): Intent = intent.apply {
        putExtra(EXTRA_TITLE, title)
        putExtra(EXTRA_BODY, body)
        putExtra(EXTRA_DEEP_LINK_URL, deepLinkUrl)
        putExtra(PushClickReceiver.EXTRA_CAMPAIGN_ID, campaignId)
        putExtra(EXTRA_IMAGE_URL, imageUrl)
        putExtra(EXTRA_CAROUSEL, carouselJson)
        putExtra(EXTRA_TIMER, timerJson)
        putExtra(EXTRA_ACTIONS, actionsJson)
    }

    internal companion object {

        private const val EXTRA_TITLE = "com.dyplink.push.EXTRA_TITLE"
        private const val EXTRA_BODY = "com.dyplink.push.EXTRA_BODY"
        private const val EXTRA_DEEP_LINK_URL = "com.dyplink.push.EXTRA_DEEP_LINK_URL"
        private const val EXTRA_IMAGE_URL = "com.dyplink.push.EXTRA_IMAGE_URL"
        private const val EXTRA_CAROUSEL = "com.dyplink.push.EXTRA_CAROUSEL"
        private const val EXTRA_TIMER = "com.dyplink.push.EXTRA_TIMER"
        private const val EXTRA_ACTIONS = "com.dyplink.push.EXTRA_ACTIONS"

        /**
         * Reads an incoming push, preferring the provider's own notification
         * payload over the matching data keys, exactly as the plain handler
         * always has.
         */
        fun from(message: RemoteMessage): PushContent = PushContent(
            title = message.notification?.title ?: message.data["title"] ?: "",
            body = message.notification?.body ?: message.data["body"] ?: "",
            deepLinkUrl = message.data["deep_link_url"] ?: message.data["link"],
            campaignId = message.data[PushEventReporter.CAMPAIGN_ID_DATA_KEY],
            imageUrl = message.data[PushRichContent.IMAGE_URL_DATA_KEY],
            carouselJson = message.data[PushRichContent.CAROUSEL_DATA_KEY],
            timerJson = message.data[PushRichContent.TIMER_DATA_KEY],
            actionsJson = message.data[PushNotificationHandler.ACTIONS_DATA_KEY],
        )

        /** Reads back what [writeTo] wrote. */
        fun from(intent: Intent): PushContent = PushContent(
            title = intent.getStringExtra(EXTRA_TITLE) ?: "",
            body = intent.getStringExtra(EXTRA_BODY) ?: "",
            deepLinkUrl = intent.getStringExtra(EXTRA_DEEP_LINK_URL),
            campaignId = intent.getStringExtra(PushClickReceiver.EXTRA_CAMPAIGN_ID),
            imageUrl = intent.getStringExtra(EXTRA_IMAGE_URL),
            carouselJson = intent.getStringExtra(EXTRA_CAROUSEL),
            timerJson = intent.getStringExtra(EXTRA_TIMER),
            actionsJson = intent.getStringExtra(EXTRA_ACTIONS),
        )
    }
}
