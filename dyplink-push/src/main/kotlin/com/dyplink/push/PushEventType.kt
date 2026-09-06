package com.dyplink.push

/**
 * Push engagement event types recorded by the Dyplink backend via
 * `POST /api/push-notifications/events`.
 */
internal enum class PushEventType(val value: String) {
    DELIVERED("delivered"),
    IMPRESSION("impression"),
    CLICK("click"),
    DISMISSED("dismissed"),
}
