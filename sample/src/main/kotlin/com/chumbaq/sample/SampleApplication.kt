package com.chumbaq.sample

import android.app.Application
import android.util.Log
import com.chumbaq.push.ChumbaqPush
import com.chumbaq.sdk.Chumbaq
import com.chumbaq.sdk.ChumbaqConfig
import com.chumbaq.sdk.internal.util.LogLevel

class SampleApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        // Debug builds take these from local.properties; see sample/build.gradle.kts.
        val config = ChumbaqConfig.Builder(
            baseUrl = BuildConfig.CHUMBAQ_BASE_URL,
            apiKey = BuildConfig.CHUMBAQ_API_KEY,
            projectId = BuildConfig.CHUMBAQ_PROJECT_ID,
        )
            .logLevel(LogLevel.DEBUG)
            .deepLinkHosts(listOf("links.chumbaq.com"))
            .customScheme("chumbaq")
            .flushInterval(15)
            .build()

        Chumbaq.init(this, config)

        // The Google Services plugin writes google_app_id only when sample/google-services.json
        // exists. Without it Firebase is not set up, so push stays off.
        if (resources.getIdentifier("google_app_id", "string", packageName) != 0) {
            ChumbaqPush.init(this)
        } else {
            Log.i("ChumbaqSample", "No google-services.json in sample/, push is off")
        }
    }
}
