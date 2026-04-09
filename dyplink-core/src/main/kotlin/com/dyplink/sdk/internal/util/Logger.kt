package com.dyplink.sdk.internal.util

import android.util.Log

/**
 * Configurable log-level filter used throughout the SDK.
 *
 * Values are ordered from least to most verbose so that simple
 * ordinal comparisons control which messages are emitted.
 */
enum class LogLevel {
    NONE,
    ERROR,
    WARN,
    INFO,
    DEBUG,
    VERBOSE,
}

/**
 * Centralised logger for the Dyplink SDK.
 *
 * All SDK logging goes through this object so that it can be
 * silenced or tuned via [logLevel].
 */
internal object Logger {

    private const val TAG = "DyplinkSDK"

    /** Current log level.  Messages above this level are suppressed. */
    @JvmStatic
    var logLevel: LogLevel = LogLevel.NONE

    @JvmStatic
    fun e(message: String, throwable: Throwable? = null) {
        if (logLevel.ordinal >= LogLevel.ERROR.ordinal) {
            if (throwable != null) Log.e(TAG, message, throwable) else Log.e(TAG, message)
        }
    }

    @JvmStatic
    fun w(message: String, throwable: Throwable? = null) {
        if (logLevel.ordinal >= LogLevel.WARN.ordinal) {
            if (throwable != null) Log.w(TAG, message, throwable) else Log.w(TAG, message)
        }
    }

    @JvmStatic
    fun i(message: String) {
        if (logLevel.ordinal >= LogLevel.INFO.ordinal) {
            Log.i(TAG, message)
        }
    }

    @JvmStatic
    fun d(message: String) {
        if (logLevel.ordinal >= LogLevel.DEBUG.ordinal) {
            Log.d(TAG, message)
        }
    }

    @JvmStatic
    fun v(message: String) {
        if (logLevel.ordinal >= LogLevel.VERBOSE.ordinal) {
            Log.v(TAG, message)
        }
    }
}
