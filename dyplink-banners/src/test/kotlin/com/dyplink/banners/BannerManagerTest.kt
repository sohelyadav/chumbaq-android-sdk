package com.dyplink.banners

import com.dyplink.banners.internal.BannerApiClient
import com.dyplink.banners.model.Banner
import com.dyplink.banners.model.BannerCategory
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test

class BannerManagerTest {

    private lateinit var apiClient: BannerApiClient
    private lateinit var manager: BannerManager

    private val sampleCategory = BannerCategory(
        id = "cat-1",
        name = "Promo",
        layout = "carousel",
        aspectRatio = "16:9",
        autoRotate = true,
        rotationInterval = 5,
        heading = "Special Offers",
        backgroundColor = "#ffffff",
        padding = 0,
        banners = listOf(
            Banner(
                id = "b-1",
                title = "Banner 1",
                imageUrl = "https://example.com/img1.png",
                clickUrl = "https://example.com/link1",
                ctaText = "Shop Now",
                sortOrder = 1,
                isActive = true,
                metadata = null,
            ),
        ),
    )

    @Before
    fun setUp() {
        apiClient = mockk()
        coEvery { apiClient.fetchBanners(any()) } returns sampleCategory

        // Use reflection to inject the mock apiClient into BannerManager
        manager = BannerManager()
        val field = BannerManager::class.java.getDeclaredField("apiClient")
        field.isAccessible = true
        field.set(manager, apiClient)
    }

    @Test
    fun `loadBanners fetches from API on first call`() = runTest {
        val result = manager.loadBanners("cat-1")

        assertEquals(sampleCategory, result)
        coVerify(exactly = 1) { apiClient.fetchBanners("cat-1") }
    }

    @Test
    fun `loadBanners returns cached result on second call`() = runTest {
        val first = manager.loadBanners("cat-1")
        val second = manager.loadBanners("cat-1")

        assertSame(first, second)
        coVerify(exactly = 1) { apiClient.fetchBanners("cat-1") }
    }

    @Test
    fun `loadBanners with forceRefresh bypasses cache`() = runTest {
        manager.loadBanners("cat-1")
        manager.loadBanners("cat-1", forceRefresh = true)

        coVerify(exactly = 2) { apiClient.fetchBanners("cat-1") }
    }

    @Test
    fun `clearCache empties the cache`() = runTest {
        manager.loadBanners("cat-1")
        coVerify(exactly = 1) { apiClient.fetchBanners("cat-1") }

        manager.clearCache()

        // After clearing cache, loadBanners should fetch from API again
        manager.loadBanners("cat-1")
        coVerify(exactly = 2) { apiClient.fetchBanners("cat-1") }
    }
}
