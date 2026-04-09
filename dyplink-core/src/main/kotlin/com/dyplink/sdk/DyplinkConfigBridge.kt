package com.dyplink.sdk

/**
 * Bridge for cross-module, read-only access to SDK configuration values.
 *
 * Populated during [Dyplink.init]. Optional modules (push, banners) read
 * these properties instead of depending on internal config classes.
 *
 * **This is NOT part of the public API.** Application code should not
 * reference this object directly.
 */
object DyplinkConfigBridge {

    @Volatile
    var baseUrl: String = ""

    @Volatile
    var apiKey: String = ""

    @Volatile
    var projectId: String = ""
}
