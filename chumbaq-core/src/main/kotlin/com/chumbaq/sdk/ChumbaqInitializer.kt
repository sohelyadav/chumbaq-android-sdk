package com.chumbaq.sdk

import android.content.Context
import androidx.startup.Initializer

/**
 * App Startup Initializer for the Chumbaq SDK.
 *
 * This does **not** auto-initialize the SDK — the developer must call
 * [Chumbaq.init] manually with a [ChumbaqConfig]. This initializer
 * simply ensures the SDK library is loaded and any manifest-based
 * setup is complete.
 */
class ChumbaqInitializer : Initializer<Unit> {

    override fun create(context: Context) {
        // No-op: SDK requires explicit init() with config.
    }

    override fun dependencies(): List<Class<out Initializer<*>>> = emptyList()
}
