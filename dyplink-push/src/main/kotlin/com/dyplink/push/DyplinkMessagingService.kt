package com.dyplink.push

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/**
 * Firebase Cloud Messaging service that integrates with the Dyplink
 * push module.
 *
 * This service is registered in the module's `AndroidManifest.xml`
 * and handles two responsibilities:
 *
 * 1. **Token refresh** — forwards new tokens to [DyplinkPush] so they
 *    are automatically re-registered with the backend.
 * 2. **Message receipt** — reports a `delivered` event for Dyplink
 *    campaign pushes, then delegates to the host app via
 *    [onDyplinkMessage]. If the host does not handle the message, a
 *    default notification is displayed via [PushNotificationHandler].
 *
 * **A host app with its own messaging logic should subclass this service**
 * rather than write its own: override [onDyplinkMessage] to take the
 * message over, and [onDyplinkTokenRefresh] for the token, then declare the
 * subclass in the app's manifest. Both reporting paths keep working, and
 * the subclass wins because the app's manifest entry is merged over the
 * library's.
 *
 * An app that must keep a `FirebaseMessagingService` of its own instead can
 * remove this one at merge time:
 *
 * ```xml
 * <service
 *     android:name="com.dyplink.push.DyplinkMessagingService"
 *     tools:node="remove" />
 * ```
 *
 * That gives up `delivered` reporting and the rich notification rendering,
 * so the subclass above is the supported path.
 */
open class DyplinkMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        DyplinkPush.onTokenRefresh(token)
        onDyplinkTokenRefresh(token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        DyplinkPush.reportDelivered(message.data)
        if (!onDyplinkMessage(message)) {
            // The host app did not handle the message — show a default notification.
            PushNotificationHandler.showNotification(this, message)
        }
    }

    /**
     * Called when a push message is received.
     *
     * Override this to handle messages yourself. Return `true` if you
     * consumed the message and do not want the default notification to
     * be shown; return `false` (the default) to let
     * [PushNotificationHandler] display a notification.
     */
    open fun onDyplinkMessage(message: RemoteMessage): Boolean = false

    /**
     * Called when Firebase issues a new registration token.
     *
     * Override this if the host app needs to perform additional work
     * on token refresh (e.g. sending the token to its own server).
     * The Dyplink backend is already notified automatically.
     */
    open fun onDyplinkTokenRefresh(token: String) {
        // No-op by default.
    }
}
