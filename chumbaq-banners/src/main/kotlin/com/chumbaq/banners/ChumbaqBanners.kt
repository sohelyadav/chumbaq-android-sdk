package com.chumbaq.banners

import android.content.Context
import com.chumbaq.banners.model.Banner
import com.chumbaq.banners.model.BannerCategory
import com.chumbaq.banners.ui.BannerCarouselView
import com.chumbaq.sdk.Chumbaq
import com.chumbaq.sdk.ChumbaqCallback
import kotlinx.coroutines.*

object ChumbaqBanners {
    private val bannerManager = BannerManager()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    @JvmStatic
    suspend fun loadBanners(categoryId: String): BannerCategory {
        check(Chumbaq.isInitialized) { "Chumbaq.init() must be called first" }
        return bannerManager.loadBanners(categoryId)
    }

    @JvmStatic
    fun loadBanners(categoryId: String, callback: ChumbaqCallback<BannerCategory>) {
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
