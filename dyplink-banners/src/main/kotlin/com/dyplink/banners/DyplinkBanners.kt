package com.dyplink.banners

import android.content.Context
import com.dyplink.banners.model.Banner
import com.dyplink.banners.model.BannerCategory
import com.dyplink.banners.ui.BannerCarouselView
import com.dyplink.sdk.Dyplink
import com.dyplink.sdk.DyplinkCallback
import kotlinx.coroutines.*

object DyplinkBanners {
    private val bannerManager = BannerManager()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    @JvmStatic
    suspend fun loadBanners(categoryId: String): BannerCategory {
        check(Dyplink.isInitialized) { "Dyplink.init() must be called first" }
        return bannerManager.loadBanners(categoryId)
    }

    @JvmStatic
    fun loadBanners(categoryId: String, callback: DyplinkCallback<BannerCategory>) {
        scope.launch {
            callback.onResult(runCatching { loadBanners(categoryId) })
        }
    }

    @JvmStatic
    fun createCarousel(
        context: Context,
        categoryId: String,
        onBannerClick: ((Banner) -> Unit)? = null,
    ): BannerCarouselView {
        val view = BannerCarouselView(context)
        view.setOnBannerClickListener(onBannerClick)
        view.setCategoryId(categoryId)
        return view
    }

    @JvmStatic
    fun clearCache() {
        bannerManager.clearCache()
    }
}
