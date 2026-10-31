package com.chumbaq.sample

import android.os.Bundle
import android.widget.FrameLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.chumbaq.banners.ChumbaqBanners

class BannerDemoActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val container = FrameLayout(this)
        setContentView(container)

        val carousel = ChumbaqBanners.createCarousel(
            context = this,
            categoryId = "your-category-id",
        ) { banner ->
            Toast.makeText(this, "Clicked: ${banner.title}", Toast.LENGTH_SHORT).show()
        }

        container.addView(carousel)
    }
}
