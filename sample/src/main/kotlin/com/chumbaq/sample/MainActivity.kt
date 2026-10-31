package com.chumbaq.sample

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.chumbaq.sdk.Chumbaq
import com.chumbaq.sdk.deeplink.DeepLinkListener
import com.chumbaq.sdk.model.DeepLinkResult
import com.chumbaq.sdk.model.IdentifyParams
import com.chumbaq.sdk.model.TrackConversionParams
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val statusText = findViewById<TextView>(R.id.text_status)

        // Set deep link listener
        Chumbaq.setDeepLinkListener(DeepLinkListener { result ->
            statusText.text = "Deep link: ${result.shortCode}\nParams: ${result.params}"
            if (result.shortCode != null) {
                startActivity(Intent(this, ProductActivity::class.java).apply {
                    putExtra("short_code", result.shortCode)
                    putExtra("params", result.params?.toString())
                })
            }
        })

        // Handle deep link from intent
        Chumbaq.handleDeepLink(intent)

        // Check for deferred deep link
        lifecycleScope.launch {
            val result = Chumbaq.matchDeferredDeepLink()
            if (result.matched) {
                statusText.text = "Deferred match!\nShortCode: ${result.shortCode}\nParams: ${result.params}"
            }
        }

        // Track event button
        findViewById<Button>(R.id.btn_track_event).setOnClickListener {
            Chumbaq.track("button_clicked", mapOf("screen" to "main", "button" to "track"))
            Toast.makeText(this, "Event tracked!", Toast.LENGTH_SHORT).show()
        }

        // Identify button
        findViewById<Button>(R.id.btn_identify).setOnClickListener {
            lifecycleScope.launch {
                try {
                    val result = Chumbaq.identify(
                        IdentifyParams.Builder()
                            .externalUserId("user-123")
                            .firstName("John")
                            .lastName("Doe")
                            .build()
                    )
                    statusText.text = "Identified: ${result.distinctId}"
                } catch (e: Exception) {
                    statusText.text = "Identify failed: ${e.message}"
                }
            }
        }

        // Track conversion button
        findViewById<Button>(R.id.btn_track_conversion).setOnClickListener {
            Chumbaq.trackConversion(TrackConversionParams(
                eventType = "signup",
                externalUserId = "user-123",
                metadata = mapOf("plan" to "free"),
            ))
            Toast.makeText(this, "Conversion tracked!", Toast.LENGTH_SHORT).show()
        }

        // Banners button
        findViewById<Button>(R.id.btn_banners).setOnClickListener {
            startActivity(Intent(this, BannerDemoActivity::class.java))
        }

        // Reset button
        findViewById<Button>(R.id.btn_reset).setOnClickListener {
            Chumbaq.reset()
            statusText.text = "Identity reset. New ID: ${Chumbaq.distinctId}"
        }

        // Display current IDs
        statusText.text = "DistinctID: ${Chumbaq.distinctId}\nFingerprint: ${Chumbaq.deviceFingerprint}"
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        Chumbaq.handleDeepLink(intent)
    }
}
