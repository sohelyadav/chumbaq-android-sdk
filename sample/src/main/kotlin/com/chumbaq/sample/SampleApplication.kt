package com.chumbaq.sample

import android.app.Application
import com.chumbaq.sdk.Chumbaq
import com.chumbaq.sdk.ChumbaqConfig
import com.chumbaq.sdk.internal.util.LogLevel

class SampleApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        val config = ChumbaqConfig.Builder(
            baseUrl = "https://api.chumbaq.com",
            apiKey = "your-api-key-here",
            projectId = "your-project-id",
        )
            .logLevel(LogLevel.DEBUG)
            .deepLinkHosts(listOf("links.chumbaq.com"))
            .customScheme("chumbaq")
            .flushInterval(15)
            .build()

        Chumbaq.init(this, config)
    }
}
