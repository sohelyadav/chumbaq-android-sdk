package com.chumbaq.banners

import com.chumbaq.banners.internal.BannerApiClient
import com.chumbaq.banners.model.BannerCategory

internal class BannerManager {
    private val apiClient = BannerApiClient()
    private val cache = mutableMapOf<String, BannerCategory>()

    suspend fun loadBanners(categoryId: String, forceRefresh: Boolean = false): BannerCategory {
        if (!forceRefresh) {
            cache[categoryId]?.let { return it }
        }
        val category = apiClient.fetchBanners(categoryId)
        cache[categoryId] = category
        return category
    }

    fun clearCache() {
        cache.clear()
    }
}
