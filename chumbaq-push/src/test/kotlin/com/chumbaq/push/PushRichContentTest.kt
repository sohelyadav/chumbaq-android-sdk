package com.chumbaq.push

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The parsing half of rich notifications.
 *
 * Everything here arrives as a string in an FCM data map, so the cases that
 * matter are the malformed ones: the rule for all of them is that broken rich
 * content costs the extras and never the notification, which means every
 * failure has to return "nothing to render" rather than throw.
 */
class PushRichContentTest {

    private fun carouselJson(vararg slides: Map<String, String>): String {
        val array = JSONArray()
        slides.forEach { slide ->
            array.put(JSONObject().apply { slide.forEach { (k, v) -> put(k, v) } })
        }
        return array.toString()
    }

    private val slideA = mapOf("imageUrl" to "https://cdn.test/a.png")
    private val slideB = mapOf("imageUrl" to "https://cdn.test/b.png")

    // ── Carousel ──────────────────────────────────────────────────────

    @Test
    fun `parses the slides a campaign carried`() {
        val slides = PushRichContent.parseCarousel(
            carouselJson(
                slideA + mapOf("caption" to "One"),
                slideB + mapOf("deepLinkUrl" to "app://two"),
            ),
        )

        assertEquals(2, slides?.size)
        assertEquals("One", slides!![0].caption)
        assertEquals("app://two", slides[1].deepLinkUrl)
        // Null rather than blank: null means "use the campaign's own link".
        assertNull(slides[0].deepLinkUrl)
    }

    @Test
    fun `treats one slide as not a carousel`() {
        assertNull(PushRichContent.parseCarousel(carouselJson(slideA)))
    }

    @Test
    fun `drops a slide with no image rather than showing a blank frame`() {
        val slides = PushRichContent.parseCarousel(
            carouselJson(slideA, mapOf("caption" to "no image"), slideB),
        )

        assertEquals(2, slides?.size)
    }

    @Test
    fun `ignores malformed json instead of throwing`() {
        // Thrown here, this would take the whole notification with it.
        assertNull(PushRichContent.parseCarousel("not json"))
    }

    @Test
    fun `ignores an absent payload`() {
        assertNull(PushRichContent.parseCarousel(null))
        assertNull(PushRichContent.parseCarousel(""))
    }

    @Test
    fun `caps a carousel at the maximum it can show`() {
        val many = Array(50) { slideA }
        val slides = PushRichContent.parseCarousel(carouselJson(*many))

        assertTrue((slides?.size ?: 0) <= 10)
    }

    // ── Timer ─────────────────────────────────────────────────────────

    private val now = 1_760_000_000_000L
    private val api24 = 24
    private val api23 = 23

    private fun timerJson(vararg fields: Pair<String, String>): String =
        JSONObject().apply { fields.forEach { (k, v) -> put(k, v) } }.toString()

    @Test
    fun `counts down to a deadline in the future`() {
        val render = PushRichContent.resolveTimer(
            timerJson("endsAt" to "2099-01-01T00:00:00Z"),
            now,
            api24,
        )

        assertTrue(render is TimerRender.Countdown)
    }

    @Test
    fun `shows the expired copy instead of a countdown at zero`() {
        val render = PushRichContent.resolveTimer(
            timerJson(
                "endsAt" to "2000-01-01T00:00:00Z",
                "expiredTitle" to "Sale over",
            ),
            now,
            api24,
        )

        // A notification can sit undelivered for longer than its countdown
        // lasts, so this is ordinary rather than exceptional.
        assertEquals("Sale over", (render as TimerRender.Expired).title)
    }

    @Test
    fun `shows no timer below api 24 rather than one counting the wrong way`() {
        val render = PushRichContent.resolveTimer(
            timerJson("endsAt" to "2099-01-01T00:00:00Z"),
            now,
            api23,
        )

        // Chronometers only count down from API 24; the same call below that
        // counts up from the deadline, which is worse than no timer.
        assertEquals(TimerRender.None, render)
    }

    @Test
    fun `ignores a deadline it cannot parse`() {
        // A countdown to an unknown moment is worse than none.
        assertEquals(
            TimerRender.None,
            PushRichContent.resolveTimer(timerJson("endsAt" to "next tuesday"), now, api24),
        )
    }

    @Test
    fun `ignores a timer with no deadline at all`() {
        assertEquals(
            TimerRender.None,
            PushRichContent.resolveTimer(timerJson("expiredTitle" to "Gone"), now, api24),
        )
    }

    @Test
    fun `ignores malformed timer json`() {
        assertEquals(TimerRender.None, PushRichContent.resolveTimer("{{{", now, api24))
    }

    // ── ISO 8601 ──────────────────────────────────────────────────────

    @Test
    fun `reads the timestamp format the backend actually sends`() {
        assertEquals(0L, PushRichContent.parseIso8601("1970-01-01T00:00:00.000Z"))
    }

    @Test
    fun `reads an offset as well as a Z`() {
        val utc = PushRichContent.parseIso8601("2026-06-01T12:00:00Z")
        val offset = PushRichContent.parseIso8601("2026-06-01T14:00:00+02:00")

        assertEquals(utc, offset)
    }

    @Test
    fun `returns null rather than a wrong instant for junk`() {
        assertNull(PushRichContent.parseIso8601("not a date"))
        assertNull(PushRichContent.parseIso8601(""))
    }
}
