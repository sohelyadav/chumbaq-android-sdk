package com.dyplink.banners.internal

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

internal class BannerClickTracker(
    private val apiClient: BannerApiClient,
    private val scope: CoroutineScope,
) {
    fun trackClick(bannerId: String) {
        scope.launch(Dispatchers.IO) {
            try { apiClient.trackClick(bannerId) } catch (_: Exception) {}
        }
    }
}
