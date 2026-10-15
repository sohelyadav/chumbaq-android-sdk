package com.dyplink.sdk

import android.util.Log

/**
 * Opt-in channel for the failures the SDK otherwise swallows.
 *
 * Almost every network call in the optional modules is fire-and-forget:
 * a banner that will not load, a conversion the server rejects, an
 * engagement event that never arrives. Each one is caught and dropped so
 * it can never reach the host app — which is right, and is also why a
 * banner module that could not make a single authenticated call looked
 * perfectly healthy for as long as it shipped.
 *
 * Enabling this turns those drops into warnings. It is off by default and
 * is set from [DyplinkConfig.Builder.diagnostics]; nothing here runs in a
 * normal integration.
 *
 * **Never pass a credential to [warn].** The API key and push tokens travel
 * through most of the paths that report here, and a diagnostic log is the
 * one place an integrator is likely to paste in a bug report.
 *
 * Lives beside [DyplinkConfigBridge], and for the same reason: the optional
 * modules need it and `internal` does not cross a Gradle module boundary.
 *
 * **This is NOT part of the public API.** Application code should not
 * reference it directly.
 */
object DyplinkDiagnostics {

    private const val TAG = "DyplinkSDK"

    /** Set from [DyplinkConfig.diagnosticsEnabled] during [Dyplink.init]. */
    @Volatile
    var enabled: Boolean = false

    /**
     * Reports a failure the SDK has otherwise absorbed.
     *
     * [area] names the component so one grep finds every drop from it —
     * `"BannerApiClient"`, `"ConversionTracker"`.
     */
    @JvmStatic
    fun warn(area: String, message: String, throwable: Throwable? = null) {
        if (!enabled) return
        val line = "[$area] $message"
        if (throwable != null) Log.w(TAG, line, throwable) else Log.w(TAG, line)
    }
}
