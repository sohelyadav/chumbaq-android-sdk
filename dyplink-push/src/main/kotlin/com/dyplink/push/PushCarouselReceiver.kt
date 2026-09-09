package com.dyplink.push

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Moves a carousel notification between its slides.
 *
 * The prev/next buttons in the custom view are [PendingIntent]s into this
 * receiver. It re-posts the notification under the same id, so the one on
 * screen changes slide instead of a second one appearing beneath it.
 *
 * Registered (non-exported) in the module's `AndroidManifest.xml`; only
 * reachable via the [PendingIntent]s created for the notification, never by
 * another app.
 */
internal class PushCarouselReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val navigation = resolveNavigation(intent) ?: return

        // Re-posting downloads the new slide's image, which cannot happen on
        // the main thread this receiver runs on. goAsync holds the broadcast
        // open for the ~10s the system allows — the image budget fits inside
        // it — and the work itself runs on the IO dispatcher.
        val pendingResult = goAsync()
        val appContext = context.applicationContext
        scope.launch {
            try {
                PushNotificationHandler.post(
                    appContext,
                    navigation.content,
                    navigation.notificationId,
                    navigation.slideIndex,
                )
            } catch (e: Exception) {
                // The notification the user is looking at simply stays where
                // it is; there is nothing better to do from here.
                Log.w(TAG, "Failed to show carousel slide ${navigation.slideIndex}", e)
            } finally {
                pendingResult?.finish()
            }
        }
    }

    /** The slide a prev/next press resolves to. */
    internal data class Navigation(
        val content: PushContent,
        val notificationId: Int,
        val slideIndex: Int,
    )

    internal companion object {

        private const val TAG = "PushCarouselReceiver"

        internal const val EXTRA_NOTIFICATION_ID = "com.dyplink.push.EXTRA_NOTIFICATION_ID"

        /** Slide currently on screen, not the one being navigated to. */
        internal const val EXTRA_SLIDE_INDEX = "com.dyplink.push.EXTRA_SLIDE_INDEX"

        /** `+1` for next, `-1` for previous. */
        internal const val EXTRA_DELTA = "com.dyplink.push.EXTRA_DELTA"

        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        /**
         * Reads a prev/next press back into the slide it asks for, or `null`
         * when the intent no longer describes a carousel — a payload that has
         * stopped parsing cannot be re-rendered, and re-posting a half-built
         * notification over the one on screen would be worse than ignoring
         * the press.
         */
        internal fun resolveNavigation(intent: Intent): Navigation? {
            val content = PushContent.from(intent)
            val slides = PushRichContent.parseCarousel(content.carouselJson) ?: return null

            return Navigation(
                content = content,
                notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, 0),
                slideIndex = advanceIndex(
                    current = intent.getIntExtra(EXTRA_SLIDE_INDEX, 0),
                    size = slides.size,
                    delta = intent.getIntExtra(EXTRA_DELTA, 0),
                ),
            )
        }

        /**
         * Steps [delta] slides from [current], wrapping at both ends: next on
         * the last slide returns to the first, previous on the first goes to
         * the last. Kotlin's `%` keeps the sign of its left operand, hence the
         * second one.
         */
        internal fun advanceIndex(current: Int, size: Int, delta: Int): Int =
            if (size <= 0) 0 else ((current + delta) % size + size) % size

        /**
         * The [PendingIntent] behind one prev/next button.
         *
         * The whole payload rides along as extras because the receiver has no
         * other way back to it. The action string keeps this instance distinct
         * per notification and direction — [PendingIntent]s match on
         * everything but their extras, so without it a second carousel would
         * take over the first one's buttons.
         */
        internal fun navigationIntent(
            context: Context,
            content: PushContent,
            notificationId: Int,
            currentIndex: Int,
            delta: Int,
        ): PendingIntent? {
            val intent = content
                .writeTo(Intent(context, PushCarouselReceiver::class.java))
                .setAction(
                    PushNotificationHandler.uniqueAction(
                        notificationId,
                        if (delta < 0) "carousel_prev" else "carousel_next",
                    ),
                )
                .putExtra(EXTRA_NOTIFICATION_ID, notificationId)
                .putExtra(EXTRA_SLIDE_INDEX, currentIndex)
                .putExtra(EXTRA_DELTA, delta)

            return PendingIntent.getBroadcast(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }
    }
}
