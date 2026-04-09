package com.dyplink.sdk

import android.content.Context
import androidx.startup.Initializer

/**
 * App Startup Initializer for the Dyplink SDK.
 *
 * This does **not** auto-initialize the SDK — the developer must call
 * [Dyplink.init] manually with a [DyplinkConfig]. This initializer
 * simply ensures the SDK library is loaded and any manifest-based
 * setup is complete.
 */
class DyplinkInitializer : Initializer<Unit> {

    override fun create(context: Context) {
        // No-op: SDK requires explicit init() with config.
    }

    override fun dependencies(): List<Class<out Initializer<*>>> = emptyList()
}
