package com.chumbaq.push

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.core.app.NotificationCompat
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.spyk
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for the rich content [PushNotificationHandler] layers onto a
 * notification.
 *
 * They stop at [NotificationCompat.Builder]: `build()` reaches into the
 * framework, which is stubbed out under unit tests, so what is asserted is
 * which builder calls a payload does and — more often — does not produce.
 * That is exactly the property these tests are for, since every rich feature
 * is supposed to leave the plain notification behind when it cannot render.
 */
class PushNotificationHandlerTest {

    private val context = mockk<Context>(relaxed = true)
    private lateinit var builder: NotificationCompat.Builder

    private val carouselJson = """
        [
          {"imageUrl":"https://cdn.test/1.png","caption":"First","deepLinkUrl":"app://one"},
          {"imageUrl":"https://cdn.test/2.png","caption":"Second"},
          {"imageUrl":"https://cdn.test/3.png"}
        ]
    """.trimIndent()

    private fun content(
        campaignId: String? = null,
        imageUrl: String? = null,
        carousel: String? = null,
        timer: String? = null,
        actions: String? = null,
    ) = PushContent(
        title = "Flash sale",
        body = "Half price for an hour",
        deepLinkUrl = "app://sale",
        campaignId = campaignId,
        imageUrl = imageUrl,
        carouselJson = carousel,
        timerJson = timer,
        actionsJson = actions,
    )

    @Before
    fun setUp() {
        builder = spyk(NotificationCompat.Builder(context, "chumbaq_push"))
        // PendingIntent's factories are framework statics that hand back null
        // under unit tests, which would make every button silently unbuildable.
        mockkStatic(PendingIntent::class)
        every { PendingIntent.getActivity(any(), any(), any(), any()) } returns mockk()
        every { PendingIntent.getBroadcast(any(), any(), any(), any()) } returns mockk()
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    // ── dismissal reporting ─────────────────────────────────────────

    /**
     * A swipe was the one notification outcome nothing reported, so the
     * `dismissed` column was permanently zero.
     */
    @Test
    fun `a campaign notification reports its dismissal`() {
        PushNotificationHandler.applyDismissReporting(
            context = context,
            builder = builder,
            content = content(campaignId = "camp_1"),
            notificationId = 1,
        )

        verify(exactly = 1) { builder.setDeleteIntent(any()) }
    }

    @Test
    fun `a non-campaign notification has no dismissal to report`() {
        PushNotificationHandler.applyDismissReporting(
            context = context,
            builder = builder,
            content = content(),
            notificationId = 1,
        )

        verify(exactly = 0) { builder.setDeleteIntent(any()) }
    }

    /**
     * Two notifications on screen at once must not share one delete intent:
     * PendingIntents match on everything but their extras, so the second
     * would report the first's campaign.
     */
    @Test
    fun `each notification gets its own dismissal identity`() {
        assertNotEquals(
            PushNotificationHandler.uniqueAction(1, "dismiss"),
            PushNotificationHandler.uniqueAction(2, "dismiss"),
        )
        // …and a dismissal is distinct from a tap on the same notification.
        assertNotEquals(
            PushNotificationHandler.uniqueAction(1, "dismiss"),
            PushNotificationHandler.uniqueAction(1, "tap"),
        )
    }

    // ── plain payloads ──────────────────────────────────────────────

    @Test
    fun `a payload with no rich keys is left plain`() {
        PushNotificationHandler.decorate(
            context = context,
            builder = builder,
            content = content(),
            notificationId = 1,
            slideIndex = 0,
            image = null,
        )

        verify(exactly = 0) { builder.setStyle(any()) }
        verify(exactly = 0) { builder.setCustomBigContentView(any()) }
        verify(exactly = 0) { builder.setUsesChronometer(any()) }
        // Title and body are the ones baseBuilder already set.
        verify(exactly = 0) { builder.setContentTitle(any()) }
        verify(exactly = 0) { builder.setContentText(any()) }
    }

    // ── single image ────────────────────────────────────────────────

    @Test
    fun `a downloaded image becomes a big picture`() {
        PushNotificationHandler.decorate(
            context = context,
            builder = builder,
            content = content(imageUrl = "https://cdn.test/hero.png"),
            notificationId = 1,
            slideIndex = 0,
            image = mockk<Bitmap>(relaxed = true),
        )

        verify(exactly = 1) { builder.setStyle(any<NotificationCompat.BigPictureStyle>()) }
    }

    @Test
    fun `an image that never downloaded leaves the notification plain`() {
        PushNotificationHandler.decorate(
            context = context,
            builder = builder,
            content = content(imageUrl = "https://cdn.test/hero.png"),
            notificationId = 1,
            slideIndex = 0,
            image = null,
        )

        verify(exactly = 0) { builder.setStyle(any()) }
    }

    @Test
    fun `the image url is the one downloaded when there is no carousel`() {
        val plain = content(imageUrl = "https://cdn.test/hero.png")

        assertEquals("https://cdn.test/hero.png", PushNotificationHandler.imageUrlFor(plain, 0))
    }

    // ── carousel ────────────────────────────────────────────────────

    @Test
    fun `a carousel renders a decorated custom view`() {
        PushNotificationHandler.decorate(
            context = context,
            builder = builder,
            content = content(campaignId = "camp_1", carousel = carouselJson),
            notificationId = 7,
            slideIndex = 1,
            image = mockk<Bitmap>(relaxed = true),
        )

        verify(exactly = 1) { builder.setCustomBigContentView(any()) }
        // Apps targeting Android 12+ get the system template whether they ask
        // for it or not; asking makes the result the same everywhere.
        verify(exactly = 1) {
            builder.setStyle(any<NotificationCompat.DecoratedCustomViewStyle>())
        }
        // Prev/next re-post the same id, and each slide must not buzz.
        verify(exactly = 1) { builder.setOnlyAlertOnce(true) }
    }

    @Test
    fun `each slide downloads its own image`() {
        val carousel = content(carousel = carouselJson)

        assertEquals("https://cdn.test/1.png", PushNotificationHandler.imageUrlFor(carousel, 0))
        assertEquals("https://cdn.test/3.png", PushNotificationHandler.imageUrlFor(carousel, 2))
        // An index from a stale button press is clamped, never thrown on.
        assertEquals("https://cdn.test/3.png", PushNotificationHandler.imageUrlFor(carousel, 99))
    }

    @Test
    fun `malformed carousel json degrades to the plain notification`() {
        PushNotificationHandler.decorate(
            context = context,
            builder = builder,
            content = content(campaignId = "camp_1", carousel = "[[[ not json"),
            notificationId = 1,
            slideIndex = 0,
            image = null,
        )

        verify(exactly = 0) { builder.setStyle(any()) }
        verify(exactly = 0) { builder.setCustomBigContentView(any()) }
    }

    @Test
    fun `a one slide carousel falls back to a big picture`() {
        PushNotificationHandler.decorate(
            context = context,
            builder = builder,
            content = content(carousel = """[{"imageUrl":"https://cdn.test/1.png"}]"""),
            notificationId = 1,
            slideIndex = 0,
            image = mockk<Bitmap>(relaxed = true),
        )

        verify(exactly = 0) { builder.setCustomBigContentView(any()) }
        verify(exactly = 1) { builder.setStyle(any<NotificationCompat.BigPictureStyle>()) }
    }

    @Test
    fun `a slide borrows the campaign copy and link it does not have`() {
        val slides = requireNotNull(PushRichContent.parseCarousel(carouselJson))

        val first = PushNotificationHandler.slideView(slides, 0, "Half price", "app://sale")
        val second = PushNotificationHandler.slideView(slides, 1, "Half price", "app://sale")
        val third = PushNotificationHandler.slideView(slides, 2, "Half price", "app://sale")

        assertEquals("First", first.caption)
        assertEquals("app://one", first.link)
        assertEquals("1/3", first.indicator)
        // Second declares a caption but no link, third declares neither.
        assertEquals("Second", second.caption)
        assertEquals("app://sale", second.link)
        assertEquals("Half price", third.caption)
        assertEquals("app://sale", third.link)
        assertEquals("3/3", third.indicator)
    }

    @Test
    fun `a stale slide index is clamped rather than thrown on`() {
        val slides = requireNotNull(PushRichContent.parseCarousel(carouselJson))

        assertEquals(2, PushNotificationHandler.slideView(slides, 99, "", null).index)
        assertEquals(0, PushNotificationHandler.slideView(slides, -4, "", null).index)
    }

    // ── timer ───────────────────────────────────────────────────────

    @Test
    fun `a running timer sets a counting down chronometer`() {
        val endsAt = requireNotNull(PushRichContent.parseIso8601("2026-09-09T12:00:00.000Z"))

        PushNotificationHandler.decorate(
            context = context,
            builder = builder,
            content = content(timer = """{"endsAt":"2026-09-09T12:00:00.000Z"}"""),
            notificationId = 1,
            slideIndex = 0,
            image = null,
            nowMillis = endsAt - 3_600_000,
            sdkInt = 26,
        )

        verify(exactly = 1) { builder.setWhen(endsAt) }
        verify(exactly = 1) { builder.setUsesChronometer(true) }
        // The count-down direction sits behind a Build.VERSION.SDK_INT guard,
        // which reads 0 under unit tests and cannot be raised from here; that
        // the decision is made at all is covered in PushRichContentTest.
    }

    @Test
    fun `a timer below api 24 sets no chronometer`() {
        val endsAt = requireNotNull(PushRichContent.parseIso8601("2026-09-09T12:00:00.000Z"))

        PushNotificationHandler.decorate(
            context = context,
            builder = builder,
            content = content(timer = """{"endsAt":"2026-09-09T12:00:00.000Z"}"""),
            notificationId = 1,
            slideIndex = 0,
            image = null,
            nowMillis = endsAt - 3_600_000,
            sdkInt = 23,
        )

        verify(exactly = 0) { builder.setUsesChronometer(any()) }
        verify(exactly = 0) { builder.setChronometerCountDown(any()) }
        verify(exactly = 0) { builder.setWhen(any()) }
    }

    @Test
    fun `a timer that has already run out shows the expired copy`() {
        val endsAt = requireNotNull(PushRichContent.parseIso8601("2026-09-09T12:00:00.000Z"))

        PushNotificationHandler.decorate(
            context = context,
            builder = builder,
            content = content(
                timer = """
                    {"endsAt":"2026-09-09T12:00:00.000Z",
                     "expiredTitle":"Sale over","expiredBody":"Back next week"}
                """.trimIndent(),
            ),
            notificationId = 1,
            slideIndex = 0,
            image = null,
            nowMillis = endsAt + 1,
            sdkInt = 34,
        )

        verify(exactly = 1) { builder.setContentTitle("Sale over") }
        verify(exactly = 1) { builder.setContentText("Back next week") }
        verify(exactly = 0) { builder.setUsesChronometer(any()) }
    }

    // ── actions alongside rich content ──────────────────────────────

    @Test
    fun `action buttons survive a carousel`() {
        PushNotificationHandler.decorate(
            context = context,
            builder = builder,
            content = content(
                carousel = carouselJson,
                actions = """[{"id":"buy","title":"Buy now"},{"id":"later","title":"Later"}]""",
            ),
            notificationId = 1,
            slideIndex = 0,
            image = null,
        )

        verify(exactly = 2) { builder.addAction(0, any<String>(), any()) }
    }

    @Test
    fun `malformed actions json costs only the buttons`() {
        PushNotificationHandler.decorate(
            context = context,
            builder = builder,
            content = content(actions = "{}{"),
            notificationId = 1,
            slideIndex = 0,
            image = null,
        )

        verify(exactly = 0) { builder.addAction(any<Int>(), any<String>(), any()) }
    }

    // ── pending intent identity ─────────────────────────────────────

    @Test
    fun `every notification gets its own pending intent identity`() {
        // Two notifications on screen at once must not share one PendingIntent:
        // it matches on everything but extras, so the second would retarget the
        // first's buttons.
        val tap = PushNotificationHandler.uniqueAction(11, "tap")

        assertNotEquals(tap, PushNotificationHandler.uniqueAction(12, "tap"))
        assertNotEquals(tap, PushNotificationHandler.uniqueAction(11, "carousel_next"))
    }
}
