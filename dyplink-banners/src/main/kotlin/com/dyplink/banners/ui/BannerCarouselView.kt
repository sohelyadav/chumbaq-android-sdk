package com.dyplink.banners.ui

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.widget.FrameLayout
import android.widget.TextView
import androidx.viewpager2.widget.ViewPager2
import com.dyplink.banners.internal.BannerApiClient
import com.dyplink.banners.internal.BannerClickTracker
import com.dyplink.banners.model.Banner
import com.dyplink.banners.model.BannerCategory
import kotlinx.coroutines.*

class BannerCarouselView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

    private val viewPager: ViewPager2
    private val headingView: TextView
    private val adapter: BannerAdapter
    private val handler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val clickTracker = BannerClickTracker(BannerApiClient(), scope)

    private var autoRotateRunnable: Runnable? = null
    private var rotationIntervalMs = 5000L
    private var onBannerClickListener: ((Banner) -> Unit)? = null

    init {
        // Create views programmatically
        headingView = TextView(context).apply {
            textSize = 18f
            setPadding(16, 16, 16, 8)
            visibility = GONE
        }
        addView(headingView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))

        viewPager = ViewPager2(context)
        val vpParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        vpParams.topMargin = 56  // Below heading
        addView(viewPager, vpParams)

        adapter = BannerAdapter(emptyList()) { banner ->
            clickTracker.trackClick(banner.id)
            onBannerClickListener?.invoke(banner)
        }
        viewPager.adapter = adapter
    }

    fun setCategoryId(categoryId: String) {
        scope.launch {
            try {
                val apiClient = BannerApiClient()
                val category = withContext(Dispatchers.IO) {
                    apiClient.fetchBanners(categoryId)
                }
                setBanners(category)
            } catch (e: Exception) {
                android.util.Log.e("BannerCarousel", "Failed to load banners", e)
            }
        }
    }

    fun setBanners(category: BannerCategory) {
        if (category.heading != null) {
            headingView.text = category.heading
            headingView.visibility = VISIBLE
        }

        adapter.updateBanners(category.banners)
        rotationIntervalMs = category.rotationInterval * 1000L

        if (category.autoRotate && category.banners.size > 1) {
            startAutoRotation()
        }
    }

    fun setOnBannerClickListener(listener: ((Banner) -> Unit)?) {
        onBannerClickListener = listener
    }

    fun startAutoRotation() {
        stopAutoRotation()
        autoRotateRunnable = object : Runnable {
            override fun run() {
                val itemCount = adapter.itemCount
                if (itemCount > 1) {
                    val nextItem = (viewPager.currentItem + 1) % itemCount
                    viewPager.setCurrentItem(nextItem, true)
                }
                handler.postDelayed(this, rotationIntervalMs)
            }
        }
        handler.postDelayed(autoRotateRunnable!!, rotationIntervalMs)
    }

    fun stopAutoRotation() {
        autoRotateRunnable?.let { handler.removeCallbacks(it) }
        autoRotateRunnable = null
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stopAutoRotation()
        scope.cancel()
    }
}
