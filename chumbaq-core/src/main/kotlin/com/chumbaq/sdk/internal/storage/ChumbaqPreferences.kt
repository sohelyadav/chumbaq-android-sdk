package com.chumbaq.sdk.internal.storage

import android.content.Context
import android.content.SharedPreferences

/**
 * Thin wrapper around [SharedPreferences] that stores small
 * SDK state values (IDs, flags, timestamps).
 */
internal class ChumbaqPreferences(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // ── Anonymous / Device / Identified IDs ─────────────────────────

    var anonymousId: String?
        get() = prefs.getString(KEY_ANONYMOUS_ID, null)
        set(value) = prefs.edit().putString(KEY_ANONYMOUS_ID, value).apply()

    var deviceFingerprint: String?
        get() = prefs.getString(KEY_DEVICE_FINGERPRINT, null)
        set(value) = prefs.edit().putString(KEY_DEVICE_FINGERPRINT, value).apply()

    var identifiedId: String?
        get() = prefs.getString(KEY_IDENTIFIED_ID, null)
        set(value) = prefs.edit().putString(KEY_IDENTIFIED_ID, value).apply()

    // ── Deferred deep-link matching ─────────────────────────────────

    var deferredMatchAttempted: Boolean
        get() = prefs.getBoolean(KEY_DEFERRED_MATCH_ATTEMPTED, false)
        set(value) = prefs.edit().putBoolean(KEY_DEFERRED_MATCH_ATTEMPTED, value).apply()

    var deferredMatchResult: String?
        get() = prefs.getString(KEY_DEFERRED_MATCH_RESULT, null)
        set(value) = prefs.edit().putString(KEY_DEFERRED_MATCH_RESULT, value).apply()

    // ── Attribution ─────────────────────────────────────────────────

    var attributedShortCode: String?
        get() = prefs.getString(KEY_ATTRIBUTED_SHORT_CODE, null)
        set(value) = prefs.edit().putString(KEY_ATTRIBUTED_SHORT_CODE, value).apply()

    var attributedLinkId: String?
        get() = prefs.getString(KEY_ATTRIBUTED_LINK_ID, null)
        set(value) = prefs.edit().putString(KEY_ATTRIBUTED_LINK_ID, value).apply()

    // ── Session ─────────────────────────────────────────────────────

    var lastSessionTimestamp: Long
        get() = prefs.getLong(KEY_LAST_SESSION_TIMESTAMP, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_SESSION_TIMESTAMP, value).apply()

    // ── Housekeeping ────────────────────────────────────────────────

    /**
     * Remove **all** SDK preferences.
     */
    fun clear() {
        prefs.edit().clear().apply()
    }

    // ── Key constants ───────────────────────────────────────────────

    internal companion object {
        const val PREFS_NAME = "chumbaq_sdk_prefs"

        private const val KEY_ANONYMOUS_ID = "chumbaq_anonymous_id"
        private const val KEY_DEVICE_FINGERPRINT = "chumbaq_device_fingerprint"
        private const val KEY_IDENTIFIED_ID = "chumbaq_identified_id"
        private const val KEY_DEFERRED_MATCH_ATTEMPTED = "chumbaq_deferred_match_attempted"
        private const val KEY_DEFERRED_MATCH_RESULT = "chumbaq_deferred_match_result"
        private const val KEY_ATTRIBUTED_SHORT_CODE = "chumbaq_attributed_short_code"
        private const val KEY_ATTRIBUTED_LINK_ID = "chumbaq_attributed_link_id"
        private const val KEY_LAST_SESSION_TIMESTAMP = "chumbaq_last_session_timestamp"
    }
}
