package com.dyplink.push

import android.os.Build
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * One slide of a `dyplink_carousel` payload.
 */
internal data class CarouselSlide(
    /** Image for this slide; a slide without one cannot be shown. */
    val imageUrl: String,
    /** Per-slide text, absent when the campaign body should be used. */
    val caption: String?,
    /** Per-slide link, absent when the campaign deep link should be used. */
    val deepLinkUrl: String?,
)

/**
 * What a `dyplink_timer` payload amounts to on this device, right now.
 */
internal sealed class TimerRender {

    /** No timer, an unusable one, or an Android too old to count down. */
    internal object None : TimerRender()

    /** Count down to [endsAtMillis] in the notification header. */
    internal data class Countdown(val endsAtMillis: Long) : TimerRender()

    /** The deadline has already passed; this copy replaces the countdown. */
    internal data class Expired(val title: String?, val body: String?) : TimerRender()
}

/**
 * Parsing for the rich-content data keys the backend sends alongside a
 * campaign push.
 *
 * No push provider carries a carousel or a countdown natively, so the server
 * ships them as JSON in `data` and they are turned into notification content
 * here. Every parser returns "nothing to render" rather than throwing: the
 * notification these decorate is worth more than any of them.
 */
internal object PushRichContent {

    private const val TAG = "PushRichContent"

    /** Data key carrying the carousel slides, written by the server. */
    internal const val CAROUSEL_DATA_KEY = "dyplink_carousel"

    /** Data key carrying the countdown definition, written by the server. */
    internal const val TIMER_DATA_KEY = "dyplink_timer"

    /** Data key carrying a single hero image URL, written by the server. */
    internal const val IMAGE_URL_DATA_KEY = "dyplink_image_url"

    /** One slide is a picture, not a carousel — it renders better as one. */
    private const val MIN_SLIDES = 2

    /** The server caps campaigns here; a longer array is a payload we do not trust. */
    private const val MAX_SLIDES = 10

    /**
     * Parses the `dyplink_carousel` array into renderable slides.
     *
     * Returns `null` — meaning "not a carousel" — for a missing, malformed or
     * too-short payload. A single usable slide is deliberately included in
     * that: prev/next buttons that cycle one image are worse than the plain
     * big-picture notification the caller falls back to.
     */
    fun parseCarousel(raw: String?): List<CarouselSlide>? {
        if (raw.isNullOrEmpty()) return null

        val array = try {
            JSONArray(raw)
        } catch (e: Exception) {
            Log.w(TAG, "Ignoring malformed $CAROUSEL_DATA_KEY payload", e)
            return null
        }

        val slides = ArrayList<CarouselSlide>(minOf(array.length(), MAX_SLIDES))
        for (index in 0 until minOf(array.length(), MAX_SLIDES)) {
            val slide = array.optJSONObject(index) ?: continue
            val imageUrl = slide.optNonEmpty("imageUrl") ?: continue
            slides += CarouselSlide(
                imageUrl = imageUrl,
                caption = slide.optNonEmpty("caption"),
                deepLinkUrl = slide.optNonEmpty("deepLinkUrl"),
            )
        }

        return slides.takeIf { it.size >= MIN_SLIDES }
    }

    /**
     * Decides how a `dyplink_timer` payload should be shown.
     *
     * [sdkInt] is a parameter rather than a read of [android.os.Build] so the
     * one decision that depends on the platform version stays testable.
     * Chronometers only count down from API 24; below that the same call
     * would count *up* from the deadline, so those devices get no timer at
     * all rather than a wrong one.
     *
     * A deadline already in the past never shows a countdown at zero — it
     * shows the campaign's expired copy, or the original copy when the
     * campaign supplied none.
     */
    fun resolveTimer(raw: String?, nowMillis: Long, sdkInt: Int): TimerRender {
        if (raw.isNullOrEmpty()) return TimerRender.None

        val timer = try {
            JSONObject(raw)
        } catch (e: Exception) {
            Log.w(TAG, "Ignoring malformed $TIMER_DATA_KEY payload", e)
            return TimerRender.None
        }

        val endsAt = timer.optNonEmpty("endsAt")?.let { parseIso8601(it) }
        if (endsAt == null) {
            Log.w(TAG, "Ignoring $TIMER_DATA_KEY payload without a usable endsAt")
            return TimerRender.None
        }

        if (endsAt <= nowMillis) {
            return TimerRender.Expired(
                title = timer.optNonEmpty("expiredTitle"),
                body = timer.optNonEmpty("expiredBody"),
            )
        }

        return if (sdkInt >= Build.VERSION_CODES.N) {
            TimerRender.Countdown(endsAt)
        } else {
            TimerRender.None
        }
    }

    /**
     * Parses an ISO 8601 instant into epoch millis, or `null` if it cannot.
     *
     * Hand-rolled rather than `SimpleDateFormat`'s `X` pattern, which only
     * exists from API 24 while this module supports 21: the zone suffix is
     * normalised to RFC 822 (`+0000`) first so the older `Z` pattern can read
     * it. A timestamp with no zone at all is read as UTC, which is what the
     * backend sends.
     */
    fun parseIso8601(value: String): Long? {
        val normalized = normalizeZone(value.trim())
        for (pattern in ISO_PATTERNS) {
            try {
                val format = SimpleDateFormat(pattern, Locale.US).apply {
                    isLenient = false
                    timeZone = TimeZone.getTimeZone("UTC")
                }
                return format.parse(normalized)?.time ?: continue
            } catch (e: Exception) {
                // Try the next shape before giving up.
            }
        }
        Log.w(TAG, "Unparseable timestamp: $value")
        return null
    }

    private val ISO_PATTERNS = arrayOf(
        "yyyy-MM-dd'T'HH:mm:ss.SSSZ",
        "yyyy-MM-dd'T'HH:mm:ssZ",
    )

    private val OFFSET_WITH_COLON = Regex("([+-]\\d{2}):(\\d{2})$")

    private fun normalizeZone(value: String): String = when {
        value.endsWith("Z") || value.endsWith("z") -> value.dropLast(1) + "+0000"
        OFFSET_WITH_COLON.containsMatchIn(value) -> value.replace(OFFSET_WITH_COLON, "$1$2")
        else -> value + "+0000"
    }

    /** `optString` returns "" for both absent and empty; neither is usable here. */
    private fun JSONObject.optNonEmpty(key: String): String? =
        optString(key).takeIf { it.isNotEmpty() }
}
