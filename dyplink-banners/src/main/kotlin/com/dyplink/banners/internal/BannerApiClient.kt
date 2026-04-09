package com.dyplink.banners.internal

import com.dyplink.banners.model.Banner
import com.dyplink.banners.model.BannerCategory
import com.dyplink.sdk.DyplinkConfigBridge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

internal class BannerApiClient {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    suspend fun fetchBanners(categoryId: String): BannerCategory = withContext(Dispatchers.IO) {
        val baseUrl = DyplinkConfigBridge.baseUrl
        val request = Request.Builder()
            .url("$baseUrl/api/in-app-banners/serve/$categoryId")
            .get()
            .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            throw RuntimeException("Failed to fetch banners: ${response.code}")
        }

        val json = JSONObject(response.body?.string() ?: "{}")
        // API wraps response in "data" sometimes
        val root = if (json.has("data")) json.getJSONObject("data") else json
        parseBannerCategory(root, categoryId)
    }

    suspend fun trackClick(bannerId: String) = withContext(Dispatchers.IO) {
        val baseUrl = DyplinkConfigBridge.baseUrl
        val request = Request.Builder()
            .url("$baseUrl/api/in-app-banners/$bannerId/click")
            .post(okhttp3.RequestBody.create(null, ByteArray(0)))
            .build()

        try {
            client.newCall(request).execute().close()
        } catch (_: Exception) {
            // Fire-and-forget
        }
    }

    private fun parseBannerCategory(json: JSONObject, categoryId: String): BannerCategory {
        val categoryJson = if (json.has("category")) json.getJSONObject("category") else json
        val bannersArray = if (json.has("banners")) json.getJSONArray("banners")
            else categoryJson.optJSONArray("banners") ?: org.json.JSONArray()

        val banners = mutableListOf<Banner>()
        for (i in 0 until bannersArray.length()) {
            val b = bannersArray.getJSONObject(i)
            banners.add(Banner(
                id = b.optString("id", ""),
                title = b.optString("title", ""),
                imageUrl = b.optString("imageUrl", null),
                clickUrl = b.optString("clickUrl", null),
                ctaText = b.optString("ctaText", null),
                sortOrder = b.optInt("sortOrder", 0),
                isActive = b.optBoolean("isActive", true),
                metadata = parseMetadata(b.optJSONObject("metadata")),
            ))
        }

        return BannerCategory(
            id = categoryJson.optString("id", categoryId),
            name = categoryJson.optString("name", ""),
            layout = categoryJson.optString("layout", "carousel"),
            aspectRatio = categoryJson.optString("aspectRatio", null),
            autoRotate = categoryJson.optBoolean("autoRotate", true),
            rotationInterval = categoryJson.optInt("rotationInterval", 5),
            heading = categoryJson.optString("heading", null),
            backgroundColor = categoryJson.optString("backgroundColor", "#ffffff"),
            padding = categoryJson.optInt("padding", 0),
            banners = banners.filter { it.isActive }.sortedBy { it.sortOrder },
        )
    }

    private fun parseMetadata(json: JSONObject?): Map<String, Any>? {
        if (json == null) return null
        val map = mutableMapOf<String, Any>()
        json.keys().forEach { key -> map[key] = json.get(key) }
        return map.ifEmpty { null }
    }
}
