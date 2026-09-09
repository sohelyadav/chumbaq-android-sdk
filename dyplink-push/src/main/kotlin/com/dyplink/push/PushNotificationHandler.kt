package com.dyplink.push

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.util.Log
import android.view.View
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.runBlocking
import org.json.JSONArray

/**
 * Default notification display helper for incoming push messages.
 *
 * Creates an Android notification from a [RemoteMessage], handling
 * both the FCM `notification` payload and custom `data` keys
 * (`title`, `body`, `deep_link_url` / `link`).
 *
 * Campaigns can enrich that notification through further data keys —
 * `dyplink_image_url`, `dyplink_carousel` and `dyplink_timer` — because no
 * push provider carries rich content natively. Each is optional and each
 * degrades on its own: whatever cannot be parsed, downloaded or rendered is
 * dropped and the plain notification is posted regardless.
 *
 * Host apps can customise the notification channel or small icon by
 * handling messages in [DyplinkMessagingService.onDyplinkMessage]
 * instead of relying on this default handler.
 */
internal object PushNotificationHandler {

    private const val CHANNEL_ID = "dyplink_push"

    private const val TAG = "PushNotificationHandler"

    /** Data key carrying campaign action buttons, written by the server. */
    internal const val ACTIONS_DATA_KEY = "dyplink_actions"

    /** Android only renders three; more are silently dropped by the system. */
    private const val MAX_ACTIONS = 3
    private const val CHANNEL_NAME = "Push Notifications"

    /**
     * Total time one notification may spend fetching its image.
     *
     * FCM allows roughly 20 seconds inside `onMessageReceived` before it
     * stops the service, and a broadcast receiver gets about 10 through
     * `goAsync()`; five seconds sits inside both with room to spare for the
     * delivery report that runs alongside. A picture is not worth a
     * notification that arrives late, or after the system has given up on
     * the process — past the budget the image is simply left out.
     */
    internal const val IMAGE_BUDGET_MS = 5_000L

    /**
     * Build and post a notification from the given [RemoteMessage].
     *
     * If the message contains a `deep_link_url` (or `link`) data key
     * the notification tap will open that URL via [Intent.ACTION_VIEW].
     * Otherwise the app's launcher activity is opened.
     */
    fun showNotification(context: Context, message: RemoteMessage) {
        val content = PushContent.from(message)

        // Skip entirely empty notifications
        if (content.isEmpty) return

        // Use current millis truncated to Int for a quasi-unique notification ID
        val notificationId = (System.currentTimeMillis() % Int.MAX_VALUE).toInt()

        // FCM dispatches onMessageReceived on a background thread, so waiting
        // here for the image costs nothing the user can see; the download
        // itself still happens on Dispatchers.IO and gives up on its own.
        runBlocking { post(context, content, notificationId, slideIndex = 0) }
    }

    /**
     * Posts [content] as notification [notificationId], showing carousel
     * slide [slideIndex] when the payload carries one.
     *
     * Re-posting with an id that is already showing replaces it in place,
     * which is how [PushCarouselReceiver] moves between slides.
     */
    internal suspend fun post(
        context: Context,
        content: PushContent,
        notificationId: Int,
        slideIndex: Int,
    ) {
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Create notification channel on Android O+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT,
            )
            notificationManager.createNotificationChannel(channel)
        }

        val image = PushImageLoader.load(imageUrlFor(content, slideIndex), IMAGE_BUDGET_MS)

        val builder = baseBuilder(context, content, notificationId)
        decorate(context, builder, content, notificationId, slideIndex, image)

        try {
            notificationManager.notify(notificationId, builder.build())
        } catch (e: Exception) {
            // Something about the rich content the builder could not swallow —
            // an unusable bitmap, a custom view the system refuses. The plain
            // notification is still owed to the user.
            Log.w(TAG, "Rich notification failed to post, retrying plain", e)
            try {
                notificationManager.notify(
                    notificationId,
                    baseBuilder(context, content, notificationId).build(),
                )
            } catch (e: Exception) {
                Log.e(TAG, "Notification failed to post", e)
            }
        }
    }

    /**
     * Which image this render needs: the current slide's when the payload is
     * a carousel, the campaign's hero image otherwise.
     */
    internal fun imageUrlFor(content: PushContent, slideIndex: Int): String? {
        val slides = PushRichContent.parseCarousel(content.carouselJson)
            ?: return content.imageUrl
        return slides.getOrNull(slideIndex.coerceIn(0, slides.size - 1))?.imageUrl
    }

    /** The notification every payload gets, before any rich content. */
    private fun baseBuilder(
        context: Context,
        content: PushContent,
        notificationId: Int,
    ): NotificationCompat.Builder = NotificationCompat.Builder(context, CHANNEL_ID)
        .setContentTitle(content.title)
        .setContentText(content.body)
        .setSmallIcon(android.R.drawable.ic_dialog_info)
        .setAutoCancel(true)
        .apply {
            val tap = clickPendingIntent(
                context = context,
                campaignId = content.campaignId,
                link = content.deepLinkUrl,
                notificationId = notificationId,
                actionId = null,
            )
            if (tap != null) setContentIntent(tap)
        }

    /**
     * Layers the campaign's rich content onto [builder]: a countdown, a
     * carousel or a single image, and the action buttons.
     *
     * Nothing here can cost the caller its notification. Each piece parses
     * defensively and is skipped when it cannot be built, so the worst
     * outcome is the plain notification that [baseBuilder] already produced.
     *
     * [nowMillis] and [sdkInt] are parameters so the time- and
     * version-dependent decisions can be tested without either.
     */
    internal fun decorate(
        context: Context,
        builder: NotificationCompat.Builder,
        content: PushContent,
        notificationId: Int,
        slideIndex: Int,
        image: Bitmap?,
        nowMillis: Long = System.currentTimeMillis(),
        sdkInt: Int = Build.VERSION.SDK_INT,
    ) {
        applyTimer(builder, PushRichContent.resolveTimer(content.timerJson, nowMillis, sdkInt))

        val slides = PushRichContent.parseCarousel(content.carouselJson)
        val carouselShown = slides != null &&
            applyCarousel(context, builder, content, slides, notificationId, slideIndex, image)

        // A carousel that could not be rendered still has a picture to show.
        if (!carouselShown && image != null) {
            builder.setStyle(NotificationCompat.BigPictureStyle().bigPicture(image))
        }

        addActions(context, builder, content, notificationId)
    }

    /**
     * Turns a resolved [TimerRender] into builder calls.
     *
     * The countdown is the platform's own chronometer rather than a
     * self-updating text: the system redraws it, so the notification needs no
     * alarm, no wakeups and no second delivery to stay correct.
     */
    private fun applyTimer(builder: NotificationCompat.Builder, timer: TimerRender) {
        when (timer) {
            is TimerRender.None -> Unit
            is TimerRender.Countdown -> {
                builder.setWhen(timer.endsAtMillis)
                    .setShowWhen(true)
                    .setUsesChronometer(true)
                    .setChronometerCountDown(true)
            }
            is TimerRender.Expired -> {
                timer.title?.let { builder.setContentTitle(it) }
                timer.body?.let { builder.setContentText(it) }
            }
        }
    }

    /**
     * Renders one slide of a carousel into a custom view, with buttons that
     * cycle to its neighbours.
     *
     * Apps targeting Android 12 cannot post a fully custom notification any
     * more — the system re-wraps the view in its own template whatever the
     * app asks for. Asking for [NotificationCompat.DecoratedCustomViewStyle]
     * explicitly means one appearance on every version instead of two, and
     * the standard header keeps the app name, timestamp and countdown that
     * the custom view does not draw.
     *
     * Only the expanded view is customised. The collapsed one stays the
     * platform template, which is the only thing that fits in its height and
     * the only thing that still reads correctly if this view is ever refused.
     *
     * @return `true` when the slide was rendered, `false` to fall back.
     */
    private fun applyCarousel(
        context: Context,
        builder: NotificationCompat.Builder,
        content: PushContent,
        slides: List<CarouselSlide>,
        notificationId: Int,
        slideIndex: Int,
        image: Bitmap?,
    ): Boolean {
        val index = slideIndex.coerceIn(0, slides.size - 1)
        val slide = slides[index]

        return try {
            val views = RemoteViews(context.packageName, R.layout.dyplink_notification_carousel)
            views.setTextViewText(R.id.dyplink_carousel_title, content.title)

            val caption = slide.caption ?: content.body
            views.setTextViewText(R.id.dyplink_carousel_caption, caption)
            views.setViewVisibility(
                R.id.dyplink_carousel_caption,
                if (caption.isEmpty()) View.GONE else View.VISIBLE,
            )

            if (image != null) {
                views.setImageViewBitmap(R.id.dyplink_carousel_image, image)
            } else {
                // The slide's image never arrived; its caption and buttons
                // are still worth showing.
                views.setViewVisibility(R.id.dyplink_carousel_image, View.GONE)
            }

            views.setTextViewText(R.id.dyplink_carousel_indicator, "${index + 1}/${slides.size}")
            views.setOnClickPendingIntent(
                R.id.dyplink_carousel_prev,
                PushCarouselReceiver.navigationIntent(context, content, notificationId, index, -1),
            )
            views.setOnClickPendingIntent(
                R.id.dyplink_carousel_next,
                PushCarouselReceiver.navigationIntent(context, content, notificationId, index, 1),
            )

            builder.setCustomBigContentView(views)
                .setStyle(NotificationCompat.DecoratedCustomViewStyle())
                // Prev/next re-post this same id; without this every slide
                // change would buzz the phone again.
                .setOnlyAlertOnce(true)

            // The slide's own link, falling back to the campaign's — the same
            // order the action buttons use — and still routed through
            // PushClickReceiver so the tap is reported like any other.
            clickPendingIntent(
                context = context,
                campaignId = content.campaignId,
                link = slide.deepLinkUrl ?: content.deepLinkUrl,
                notificationId = notificationId,
                actionId = null,
            )?.let { builder.setContentIntent(it) }

            true
        } catch (e: Exception) {
            Log.w(TAG, "Carousel slide $index could not be rendered", e)
            false
        }
    }

    /**
     * Adds buttons declared by the campaign under `dyplink_actions`.
     *
     * Android has no payload-level notion of notification actions, so the
     * server sends their definitions as data and they are built here. Each
     * button routes through [PushClickReceiver] like the body tap does, so a
     * press is reported before the app opens — carrying its own action id, so
     * "tapped the notification" can be told apart from "pressed Buy now".
     *
     * A malformed or missing list is ignored: a notification with no buttons
     * is a smaller failure than no notification at all.
     */
    private fun addActions(
        context: Context,
        builder: NotificationCompat.Builder,
        content: PushContent,
        notificationId: Int,
    ) {
        val raw = content.actionsJson ?: return

        val actions = try {
            JSONArray(raw)
        } catch (e: Exception) {
            Log.w(TAG, "Ignoring malformed $ACTIONS_DATA_KEY payload", e)
            return
        }

        for (index in 0 until minOf(actions.length(), MAX_ACTIONS)) {
            val action = actions.optJSONObject(index) ?: continue
            val id = action.optString("id").takeIf { it.isNotEmpty() } ?: continue
            val actionTitle = action.optString("title").takeIf { it.isNotEmpty() } ?: continue
            val link = action.optString("deepLinkUrl").takeIf { it.isNotEmpty() }
                ?: content.deepLinkUrl

            val pending = clickPendingIntent(
                context = context,
                campaignId = content.campaignId,
                link = link,
                notificationId = notificationId,
                actionId = id,
            ) ?: continue

            builder.addAction(0, actionTitle, pending)
        }
    }

    /**
     * Builds the [PendingIntent] behind a tap: on the notification body, on
     * an action button, or on a carousel slide.
     *
     * For Dyplink campaign pushes the tap is routed through
     * [PushClickReceiver] so a `click` event is reported before the original
     * intent is forwarded. Non-campaign pushes keep a direct activity
     * [PendingIntent] untouched.
     *
     * @return `null` when there is nothing to open — no link and no launcher.
     */
    private fun clickPendingIntent(
        context: Context,
        campaignId: String?,
        link: String?,
        notificationId: Int,
        actionId: String?,
    ): PendingIntent? {
        val target: Intent = if (link != null) {
            Intent(Intent.ACTION_VIEW, Uri.parse(link))
        } else {
            context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return null
        }

        if (campaignId == null) {
            return PendingIntent.getActivity(
                context,
                0,
                target,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }

        return PendingIntent.getBroadcast(
            context,
            0,
            Intent(context, PushClickReceiver::class.java)
                // PendingIntents match on everything but their extras, so two
                // notifications on screen at once would otherwise share one
                // instance and the second would silently retarget the first.
                .setAction(uniqueAction(notificationId, actionId ?: "tap"))
                .putExtra(PushClickReceiver.EXTRA_CAMPAIGN_ID, campaignId)
                .putExtra(PushClickReceiver.EXTRA_ACTION_ID, actionId)
                .putExtra(PushClickReceiver.EXTRA_TARGET_INTENT, target),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /** An intent action no other notification of ours can collide with. */
    internal fun uniqueAction(notificationId: Int, suffix: String): String =
        "com.dyplink.push.N$notificationId.$suffix"
}
