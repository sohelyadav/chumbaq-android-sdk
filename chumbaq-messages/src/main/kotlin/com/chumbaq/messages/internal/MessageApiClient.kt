package com.chumbaq.messages.internal

import com.chumbaq.messages.model.InAppMessage
import com.chumbaq.messages.model.MessageButton
import com.chumbaq.messages.model.MessageTheme
import com.chumbaq.sdk.ChumbaqConfigBridge
import com.chumbaq.sdk.ChumbaqDiagnostics
import com.chumbaq.sdk.unwrapChumbaqEnvelope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.util.concurrent.TimeUnit

private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

internal class MessageApiClient {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    /**
     * Calls `POST /api/messages/check` and returns eligible messages.
     *
     * Requires a valid `X-API-Key` header containing the project's API key.
     */
    suspend fun checkMessages(
        projectId: String,
        deviceFingerprint: String,
        distinctId: String?,
        context: MessageCheckContext,
    ): List<InAppMessage> = withContext(Dispatchers.IO) {
        val body = JSONObject().apply {
            put("projectId", projectId)
            put("deviceFingerprint", deviceFingerprint)
            if (distinctId != null) put("distinctId", distinctId)
            put("context", JSONObject().apply {
                put("platform", "android")
                context.screen?.let { put("screen", it) }
                context.event?.let { put("event", it) }
                context.appVersion?.let { put("appVersion", it) }
                context.sessionDuration?.let { put("sessionDuration", it) }
                context.country?.let { put("country", it) }
                context.language?.let { put("language", it) }
            })
        }

        val request = Request.Builder()
            .url("${ChumbaqConfigBridge.baseUrl}/api/messages/check")
            .addHeader("X-API-Key", ChumbaqConfigBridge.apiKey)
            .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()

        // `use` rather than a bare `execute`: the throw below skipped the
        // close, leaking the connection on every failed check.
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                ChumbaqDiagnostics.warn(
                    "MessageApiClient",
                    "checkMessages returned ${response.code}",
                )
                throw RuntimeException("checkMessages failed: ${response.code}")
            }

            parseResponse(response.body?.string())
        }
    }

    /**
     * Calls `POST /api/messages/event` to record a message interaction.
     * Fire-and-forget — errors are swallowed by the caller.
     */
    suspend fun recordEvent(
        messageId: String,
        projectId: String,
        deviceFingerprint: String,
        distinctId: String?,
        eventType: String,
        buttonId: String? = null,
    ) = withContext(Dispatchers.IO) {
        val body = JSONObject().apply {
            put("messageId", messageId)
            put("projectId", projectId)
            put("deviceFingerprint", deviceFingerprint)
            if (distinctId != null) put("distinctId", distinctId)
            put("eventType", eventType)
            if (buttonId != null) put("buttonId", buttonId)
        }

        val request = Request.Builder()
            .url("${ChumbaqConfigBridge.baseUrl}/api/messages/event")
            .addHeader("X-API-Key", ChumbaqConfigBridge.apiKey)
            .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                ChumbaqDiagnostics.warn(
                    "MessageApiClient",
                    "$eventType for $messageId returned ${response.code}",
                )
            }
        }
    }

    // ── Parsing ────────────────────────────────────────────────────

    /**
     * Reads the eligible messages out of a `/api/messages/check` body.
     *
     * The API wraps every response in `{ "data": … }`. Reading `messages`
     * off the root finds nothing and looks exactly like "no messages are
     * eligible" — so for as long as this read the root directly, nothing
     * ever displayed and nothing ever complained.
     *
     * A body that cannot be read yields no messages rather than an
     * exception: there is nothing the host app could do about a malformed
     * check, and a throw here would travel up through a trigger callback.
     */
    internal fun parseResponse(body: String?): List<InAppMessage> {
        val json = try {
            JSONObject(body ?: "{}").unwrapChumbaqEnvelope()
        } catch (_: JSONException) {
            return emptyList()
        }
        return parseMessages(json.optJSONArray("messages") ?: JSONArray())
    }

    private fun parseMessages(array: JSONArray): List<InAppMessage> {
        val result = mutableListOf<InAppMessage>()
        for (i in 0 until array.length()) {
            val message = array.optJSONObject(i) ?: continue
            // One unreadable message costs that message, not the batch.
            result.add(runCatching { parseMessage(message) }.getOrNull() ?: continue)
        }
        return result
    }

    private fun parseMessage(json: JSONObject) = InAppMessage(
        id = json.getString("id"),
        messageType = json.optString("messageType", "modal"),
        title = json.optString("title", ""),
        body = json.optString("body", null).takeIf { !it.isNullOrEmpty() },
        imageUrl = json.optString("imageUrl", null).takeIf { !it.isNullOrEmpty() },
        imagePosition = json.optString("imagePosition", "top"),
        buttons = parseButtons(json.optJSONArray("buttons")),
        theme = parseTheme(json.optJSONObject("theme")),
        dismissOnTapOutside = json.optBoolean("dismissOnTapOutside", true),
        autoDismissSeconds = json.optInt("autoDismissSeconds", -1).takeIf { it >= 0 },
        triggerDelay = json.optInt("triggerDelay", 0),
    )

    private fun parseButtons(array: JSONArray?): List<MessageButton>? {
        if (array == null || array.length() == 0) return null
        val list = mutableListOf<MessageButton>()
        for (i in 0 until array.length()) {
            val b = array.getJSONObject(i)
            list.add(
                MessageButton(
                    id = b.optString("id", ""),
                    text = b.optString("text", ""),
                    action = b.optString("action", "dismiss"),
                    actionUrl = b.optString("actionUrl", null).takeIf { !it.isNullOrEmpty() },
                    actionEvent = b.optString("actionEvent", null).takeIf { !it.isNullOrEmpty() },
                    style = b.optString("style", "primary"),
                )
            )
        }
        return list.ifEmpty { null }
    }

    private fun parseTheme(json: JSONObject?): MessageTheme? {
        if (json == null) return null
        return MessageTheme(
            backgroundColor = json.optString("backgroundColor", null).takeIf { !it.isNullOrEmpty() },
            textColor = json.optString("textColor", null).takeIf { !it.isNullOrEmpty() },
            titleColor = json.optString("titleColor", null).takeIf { !it.isNullOrEmpty() },
            buttonPrimaryColor = json.optString("buttonPrimaryColor", null).takeIf { !it.isNullOrEmpty() },
            buttonSecondaryColor = json.optString("buttonSecondaryColor", null).takeIf { !it.isNullOrEmpty() },
            overlayColor = json.optString("overlayColor", null).takeIf { !it.isNullOrEmpty() },
            borderRadius = json.optInt("borderRadius", -1).takeIf { it >= 0 },
            animation = json.optString("animation", null).takeIf { !it.isNullOrEmpty() },
        )
    }
}

internal data class MessageCheckContext(
    val screen: String? = null,
    val event: String? = null,
    val appVersion: String? = null,
    val sessionDuration: Int? = null,
    val country: String? = null,
    val language: String? = null,
)
