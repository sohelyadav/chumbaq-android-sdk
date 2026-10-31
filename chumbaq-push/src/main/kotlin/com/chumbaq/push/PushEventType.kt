package com.chumbaq.push

/**
 * Push engagement event types recorded by the Chumbaq backend via
 * `POST /api/push-notifications/events`.
 */
internal enum class PushEventType(val value: String) {
    DELIVERED("delivered"),
    IMPRESSION("impression"),
    CLICK("click"),
    DISMISSED("dismissed"),
}
