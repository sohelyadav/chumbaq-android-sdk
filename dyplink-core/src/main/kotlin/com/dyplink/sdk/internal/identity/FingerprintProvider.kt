package com.dyplink.sdk.internal.identity

import com.dyplink.sdk.internal.storage.DyplinkPreferences
import java.util.UUID

/**
 * Generates and persists a device fingerprint and anonymous/identified
 * user identifiers using [DyplinkPreferences] for storage.
 */
internal class FingerprintProvider(private val preferences: DyplinkPreferences) {

    /**
     * A stable UUID representing this device installation.
     * Generated once and persisted across app sessions.
     */
    val fingerprint: String
        get() {
            val existing = preferences.deviceFingerprint
            if (existing != null) return existing
            val generated = UUID.randomUUID().toString()
            preferences.deviceFingerprint = generated
            return generated
        }

    /**
     * A UUID representing the anonymous (pre-identify) user.
     * Persisted across sessions; regenerated only on [reset].
     */
    var anonymousId: String
        get() {
            val existing = preferences.anonymousId
            if (existing != null) return existing
            val generated = UUID.randomUUID().toString()
            preferences.anonymousId = generated
            return generated
        }
        set(value) {
            preferences.anonymousId = value
        }

    /**
     * The identified (logged-in) user id. `null` when the user
     * has not been identified or after [reset].
     */
    var identifiedId: String?
        get() = preferences.identifiedId
        set(value) {
            preferences.identifiedId = value
        }

    /**
     * Returns the best-known user identity: the [identifiedId] if
     * available, otherwise the [anonymousId].
     */
    val distinctId: String
        get() = identifiedId ?: anonymousId

    /**
     * Clears the identified user and generates a fresh anonymous id.
     * The device [fingerprint] is unaffected.
     */
    fun reset() {
        preferences.identifiedId = null
        preferences.anonymousId = UUID.randomUUID().toString()
    }
}
