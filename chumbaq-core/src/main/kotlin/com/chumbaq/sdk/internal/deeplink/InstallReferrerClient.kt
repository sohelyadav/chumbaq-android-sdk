package com.chumbaq.sdk.internal.deeplink

import android.content.Context
import android.net.Uri
import com.android.installreferrer.api.InstallReferrerClient as GoogleReferrerClient
import com.android.installreferrer.api.InstallReferrerStateListener
import com.chumbaq.sdk.internal.util.Logger
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Result of querying the Google Play Install Referrer API.
 *
 * @property referrer          The raw referrer string (URL-encoded parameters).
 * @property shortCode         Value of `utm_content` if it looks like a Chumbaq short code.
 * @property clickTimestamp    Timestamp (seconds since epoch) of the referrer click.
 * @property installTimestamp  Timestamp (seconds since epoch) of the app install.
 */
internal data class InstallReferrerResult(
    val referrer: String,
    val shortCode: String?,
    val clickTimestamp: Long,
    val installTimestamp: Long,
)

/**
 * Wraps the Google Play Install Referrer API behind a suspending
 * function so callers can retrieve install attribution data
 * without dealing with listeners directly.
 *
 * @param context Application or activity context.
 */
internal class InstallReferrerClient(
    private val context: Context,
) {

    /**
     * Connects to the Play Store, retrieves the install referrer,
     * and returns a parsed [InstallReferrerResult].
     *
     * @return the referrer data, or `null` when the API is
     *         unavailable or the connection fails.
     */
    suspend fun getReferrer(): InstallReferrerResult? = suspendCancellableCoroutine { cont ->
        val client: GoogleReferrerClient = GoogleReferrerClient.newBuilder(context).build()

        cont.invokeOnCancellation {
            try {
                client.endConnection()
            } catch (_: Exception) {
                // Best-effort cleanup.
            }
        }

        client.startConnection(object : InstallReferrerStateListener {

            override fun onInstallReferrerSetupFinished(responseCode: Int) {
                try {
                    if (responseCode != GoogleReferrerClient.InstallReferrerResponse.OK) {
                        Logger.d(
                            "InstallReferrerClient: setup finished with " +
                                "responseCode=$responseCode",
                        )
                        safeResume(cont, null)
                        return
                    }

                    val details = client.installReferrer
                    val rawReferrer = details.installReferrer.orEmpty()
                    val clickTs = details.referrerClickTimestampSeconds
                    val installTs = details.installBeginTimestampSeconds

                    val shortCode = extractShortCode(rawReferrer)

                    safeResume(
                        cont,
                        InstallReferrerResult(
                            referrer = rawReferrer,
                            shortCode = shortCode,
                            clickTimestamp = clickTs,
                            installTimestamp = installTs,
                        ),
                    )
                } catch (e: Exception) {
                    Logger.e("InstallReferrerClient: error reading referrer", e)
                    safeResume(cont, null)
                } finally {
                    try {
                        client.endConnection()
                    } catch (_: Exception) {
                        // Best-effort cleanup.
                    }
                }
            }

            override fun onInstallReferrerServiceDisconnected() {
                Logger.d("InstallReferrerClient: service disconnected")
                safeResume(cont, null)
            }
        })
    }

    // ── Helpers ─────────────────────────────────────────────────────

    /**
     * Parses the URL-encoded referrer string and returns the value
     * of `utm_content` if it matches the short-code pattern.
     */
    private fun extractShortCode(referrer: String): String? {
        if (referrer.isBlank()) return null

        return try {
            // Wrap referrer in a dummy URI so we can use Android's query parser.
            val uri = Uri.parse("https://dummy?$referrer")
            val utmContent = uri.getQueryParameter(PARAM_UTM_CONTENT)
            if (utmContent != null && IntentParser.SHORT_CODE_REGEX.matches(utmContent)) {
                utmContent
            } else {
                null
            }
        } catch (e: Exception) {
            Logger.e("InstallReferrerClient: failed to parse referrer", e)
            null
        }
    }

    /**
     * Safely resumes [cont] only if it has not already been resumed
     * or cancelled.
     */
    private fun <T> safeResume(
        cont: kotlinx.coroutines.CancellableContinuation<T>,
        value: T,
    ) {
        if (cont.isActive) cont.resume(value)
    }

    internal companion object {
        private const val PARAM_UTM_CONTENT = "utm_content"
    }
}
