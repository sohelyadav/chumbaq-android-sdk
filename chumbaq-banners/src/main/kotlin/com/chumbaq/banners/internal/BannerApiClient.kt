package com.chumbaq.banners.internal

import com.chumbaq.banners.model.Banner
import com.chumbaq.banners.model.BannerCategory
import com.chumbaq.sdk.ChumbaqConfigBridge
import com.chumbaq.sdk.ChumbaqDiagnostics
import com.chumbaq.sdk.unwrapChumbaqEnvelope
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

    /**
     * Calls `GET /api/in-app-banners/serve/:categoryId`.
     *
     * Requires a valid `X-API-Key` header: the serve route is behind
     * `ApiKeyGuard`, which scopes the widget to the key's project. Without
     * the header every call answered 401, and because the carousel treats a
     * failed load as "no banners" the module looked idle rather than broken.
     */
    suspend fun fetchBanners(categoryId: String): BannerCategory = withContext(Dispatchers.IO) {
        val baseUrl = ChumbaqConfigBridge.baseUrl
        val request = Request.Builder()
            .url("$baseUrl/api/in-app-banners/serve/$categoryId")
            .addHeader("X-API-Key", ChumbaqConfigBridge.apiKey)
            .get()
            .build()

        // `use` rather than a bare `execute`: the throw below skipped the
        // close, and OkHttp held the connection until the GC noticed —
        // one leaked connection per failed load.
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                ChumbaqDiagnostics.warn(
                    "BannerApiClient",
                    "serve $categoryId returned ${response.code}",
                )
                throw RuntimeException("Failed to fetch banners: ${response.code}")
            }

            val json = JSONObject(response.body?.string() ?: "{}")
            // Every API response is wrapped in `{ "data": … }`; falling back to
            // the root keeps an unwrapped body working.
            val root = json.unwrapChumbaqEnvelope()
            parseBannerCategory(root, categoryId)
        }
    }

    /**
     * Calls `POST /api/in-app-banners/:bannerId/click`.
     *
     * Behind the same `ApiKeyGuard` as the serve route, so it carries the
     * same header; fire-and-forget otherwise.
     */
    suspend fun trackClick(bannerId: String) = withContext(Dispatchers.IO) {
        val baseUrl = ChumbaqConfigBridge.baseUrl
        val request = Request.Builder()
            .url("$baseUrl/api/in-app-banners/$bannerId/click")
            .addHeader("X-API-Key", ChumbaqConfigBridge.apiKey)
            .post(okhttp3.RequestBody.create(null, ByteArray(0)))
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    ChumbaqDiagnostics.warn(
                        "BannerApiClient",
                        "click $bannerId returned ${response.code}",
                    )
                }
            }
        } catch (e: Exception) {
            // Fire-and-forget
            ChumbaqDiagnostics.warn("BannerApiClient", "click $bannerId failed", e)
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
