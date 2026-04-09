package com.dyplink.sdk.internal.identity

import android.content.Context
import android.telephony.TelephonyManager
import java.util.Locale
import java.util.TimeZone

/**
 * Collects device-level information from Android system APIs.
 *
 * All data collected is non-PII hardware/software metadata used to
 * enrich identity and event payloads.
 */
internal class DeviceInfoCollector(private val context: Context) {

    /**
     * Returns a map of device information suitable for merging into
     * an identify or event JSON body.
     */
    fun collect(): Map<String, Any?> {
        val displayMetrics = context.resources.displayMetrics
        return mapOf(
            "os" to "Android",
            "osVersion" to android.os.Build.VERSION.RELEASE,
            "manufacturer" to android.os.Build.MANUFACTURER,
            "brand" to android.os.Build.BRAND,
            "model" to android.os.Build.MODEL,
            "screenWidth" to displayMetrics.widthPixels,
            "screenHeight" to displayMetrics.heightPixels,
            "carrier" to getCarrierName(),
            "locale" to Locale.getDefault().toString(),
            "language" to Locale.getDefault().language,
            "timezone" to TimeZone.getDefault().id,
        )
    }

    private fun getCarrierName(): String {
        return try {
            val telephonyManager =
                context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
            telephonyManager?.networkOperatorName ?: ""
        } catch (_: SecurityException) {
            ""
        } catch (_: Exception) {
            ""
        }
    }
}
