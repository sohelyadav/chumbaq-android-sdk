package com.chumbaq.sdk

/**
 * Bridge for cross-module, read-only access to SDK configuration values.
 *
 * Populated during [Chumbaq.init]. Optional modules (push, banners) read
 * these properties instead of depending on internal config classes.
 *
 * **This is NOT part of the public API.** Application code should not
 * reference this object directly.
 */
object ChumbaqConfigBridge {

    @Volatile
    var baseUrl: String = ""

    @Volatile
    var apiKey: String = ""

    @Volatile
    var projectId: String = ""
}
