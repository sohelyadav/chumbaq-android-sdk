package com.chumbaq.sample

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.chumbaq.sdk.Chumbaq

class ProductActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val textView = TextView(this).apply {
            textSize = 20f
            setPadding(32, 32, 32, 32)
        }
        setContentView(textView)

        val shortCode = intent.getStringExtra("short_code") ?: "unknown"
        val params = intent.getStringExtra("params") ?: "{}"

        textView.text = "Product Screen\n\nShort Code: $shortCode\nParams: $params"

        Chumbaq.track("product_viewed", mapOf("short_code" to shortCode))
    }
}
