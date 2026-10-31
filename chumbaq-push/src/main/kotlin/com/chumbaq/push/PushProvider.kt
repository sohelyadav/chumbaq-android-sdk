package com.chumbaq.push

/**
 * Delivery channel a push token belongs to, as accepted by
 * `POST /api/push-tokens/register`.
 *
 * A device may hold one token per provider — an FCM token *and* a Mi Push
 * regId, say. The backend keys tokens by provider and picks the vendor
 * channel over FCM when both are present, so it sends exactly one
 * notification. Registering with the wrong value is rejected with a 400,
 * which is why this is an enum rather than a free string.
 */
internal enum class PushProvider(val value: String) {
    FCM("fcm"),
    MI_PUSH("mi_push"),
}
