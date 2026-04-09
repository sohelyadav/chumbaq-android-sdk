package com.dyplink.sample

import android.app.Application
import com.dyplink.sdk.Dyplink
import com.dyplink.sdk.DyplinkConfig
import com.dyplink.sdk.internal.util.LogLevel

class SampleApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        val config = DyplinkConfig.Builder(
            baseUrl = "https://api.dyplink.com",
            apiKey = "your-api-key-here",
            projectId = "your-project-id",
        )
            .logLevel(LogLevel.DEBUG)
            .deepLinkHosts(listOf("links.dyplink.com"))
            .customScheme("dyplink")
            .flushInterval(15)
            .build()

        Dyplink.init(this, config)
    }
}
