package com.dyplink.push

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.concurrent.TimeUnit

/**
 * Downloads and decodes the images a rich notification needs.
 *
 * Every failure — a dead host, a 404, an oversized file, bytes that are not
 * an image — comes back as `null` so the caller can post the notification
 * without a picture instead of not posting one at all.
 */
internal object PushImageLoader {

    private const val TAG = "PushImageLoader"

    /**
     * A notification bitmap crosses a Binder transaction, and the buffer for
     * one is about 1 MB; a huge JPEG would either be refused there or push a
     * background process into a heap it cannot afford. Downloads stop at this
     * many bytes and decoding scales the result down to [MAX_EDGE_PX].
     */
    private const val MAX_BYTES = 2 * 1024 * 1024

    /** Wider than any notification is ever drawn; more pixels are wasted memory. */
    private const val MAX_EDGE_PX = 1024

    /**
     * The client's own call timeout, deliberately equal to the caller's
     * budget: [withTimeoutOrNull] cannot interrupt a socket read that OkHttp
     * is blocked in, so the hard stop has to come from OkHttp itself.
     */
    private const val CALL_TIMEOUT_SECONDS = 5L

    private val DEFAULT_CLIENT: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(3, TimeUnit.SECONDS)
        .callTimeout(CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()

    /**
     * Downloads [url] and decodes it, giving up after [budgetMs].
     *
     * Always runs on [Dispatchers.IO]: callers reach this from an FCM
     * callback or a broadcast receiver, and neither may decode a bitmap on
     * the thread it was handed.
     *
     * @param url    image to fetch; `null` or empty yields `null` immediately.
     * @param client overridable for testing with MockWebServer.
     */
    suspend fun load(
        url: String?,
        budgetMs: Long,
        client: OkHttpClient = DEFAULT_CLIENT,
    ): Bitmap? {
        if (url.isNullOrEmpty()) return null
        return withTimeoutOrNull(budgetMs) {
            withContext(Dispatchers.IO) { fetch(url, client) }
        }
    }

    private fun fetch(url: String, client: OkHttpClient): Bitmap? = try {
        client.newCall(Request.Builder().url(url).build()).execute().use { response ->
            val body = response.body
            when {
                !response.isSuccessful -> {
                    Log.w(TAG, "Image download returned ${response.code} for $url")
                    null
                }
                body == null -> null
                else -> body.byteStream().readAtMost(MAX_BYTES)?.let { decodeScaled(it) }
            }
        }
    } catch (e: Exception) {
        Log.w(TAG, "Image download failed for $url", e)
        null
    }

    /** Reads the stream, or `null` as soon as it proves longer than [limit]. */
    private fun InputStream.readAtMost(limit: Int): ByteArray? {
        val buffer = ByteArray(16 * 1024)
        val out = ByteArrayOutputStream()
        while (true) {
            val read = read(buffer)
            if (read == -1) return out.toByteArray()
            if (out.size() + read > limit) {
                Log.w(TAG, "Image exceeds ${limit}B, skipping")
                return null
            }
            out.write(buffer, 0, read)
        }
    }

    /**
     * Decodes [bytes], subsampling anything larger than [MAX_EDGE_PX] on its
     * longest edge so an oversized source image cannot blow up the heap of a
     * process that is only meant to post a notification.
     */
    private fun decodeScaled(bytes: ByteArray): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)

        val options = BitmapFactory.Options()
        var sample = 1
        val longestEdge = maxOf(bounds.outWidth, bounds.outHeight)
        while (longestEdge / sample > MAX_EDGE_PX) sample *= 2
        options.inSampleSize = sample

        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
    }
}
