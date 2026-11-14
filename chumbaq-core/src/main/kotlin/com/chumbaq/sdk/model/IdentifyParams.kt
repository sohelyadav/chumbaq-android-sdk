package com.chumbaq.sdk.model

/**
 * Immutable parameter object for the identify API.
 *
 * Use the [Builder] to construct instances:
 * ```
 * val params = IdentifyParams.Builder()
 *     .externalUserId("user-123")
 *     .userHash(hashFromYourServer)
 *     .firstName("Jane")
 *     .build()
 * ```
 */
class IdentifyParams private constructor(
    val distinctId: String?,
    val externalUserId: String?,
    /**
     * Required with [externalUserId]: HMAC-SHA256 of the external user id under the
     * project's identity secret, as hex, made on your own server (never put the secret in
     * the app). Without it the server refuses the external id.
     */
    val userHash: String?,
    val firstName: String?,
    val lastName: String?,
    val phone: String?,
    val avatar: String?,
    val locale: String?,
    val language: String?,
    val appVersion: String?,
    val appBuild: String?,
    val traits: Map<String, Any>?,
    val utmSource: String?,
    val utmMedium: String?,
    val utmCampaign: String?,
    val utmContent: String?,
    val utmTerm: String?,
    val installSource: String?,
    val installCampaign: String?,
    val emailOptIn: Boolean?,
    val smsOptIn: Boolean?,
    val whatsappOptIn: Boolean?,
    val pushOptIn: Boolean?,
    val gdprConsent: Boolean?,
    val doNotTrack: Boolean?,
) {

    class Builder {
        private var distinctId: String? = null
        private var externalUserId: String? = null
        private var userHash: String? = null
        private var firstName: String? = null
        private var lastName: String? = null
        private var phone: String? = null
        private var avatar: String? = null
        private var locale: String? = null
        private var language: String? = null
        private var appVersion: String? = null
        private var appBuild: String? = null
        private var traits: Map<String, Any>? = null
        private var utmSource: String? = null
        private var utmMedium: String? = null
        private var utmCampaign: String? = null
        private var utmContent: String? = null
        private var utmTerm: String? = null
        private var installSource: String? = null
        private var installCampaign: String? = null
        private var emailOptIn: Boolean? = null
        private var smsOptIn: Boolean? = null
        private var whatsappOptIn: Boolean? = null
        private var pushOptIn: Boolean? = null
        private var gdprConsent: Boolean? = null
        private var doNotTrack: Boolean? = null

        fun distinctId(value: String) = apply { distinctId = value }
        fun externalUserId(value: String) = apply { externalUserId = value }
        fun userHash(value: String) = apply { userHash = value }
        fun firstName(value: String) = apply { firstName = value }
        fun lastName(value: String) = apply { lastName = value }
        fun phone(value: String) = apply { phone = value }
        fun avatar(value: String) = apply { avatar = value }
        fun locale(value: String) = apply { locale = value }
        fun language(value: String) = apply { language = value }
        fun appVersion(value: String) = apply { appVersion = value }
        fun appBuild(value: String) = apply { appBuild = value }
        fun traits(value: Map<String, Any>) = apply { traits = value }
        fun utmSource(value: String) = apply { utmSource = value }
        fun utmMedium(value: String) = apply { utmMedium = value }
        fun utmCampaign(value: String) = apply { utmCampaign = value }
        fun utmContent(value: String) = apply { utmContent = value }
        fun utmTerm(value: String) = apply { utmTerm = value }
        fun installSource(value: String) = apply { installSource = value }
        fun installCampaign(value: String) = apply { installCampaign = value }
        fun emailOptIn(value: Boolean) = apply { emailOptIn = value }
        fun smsOptIn(value: Boolean) = apply { smsOptIn = value }
        fun whatsappOptIn(value: Boolean) = apply { whatsappOptIn = value }
        fun pushOptIn(value: Boolean) = apply { pushOptIn = value }
        fun gdprConsent(value: Boolean) = apply { gdprConsent = value }
        fun doNotTrack(value: Boolean) = apply { doNotTrack = value }

        fun build(): IdentifyParams = IdentifyParams(
            distinctId = distinctId,
            externalUserId = externalUserId,
            userHash = userHash,
            firstName = firstName,
            lastName = lastName,
            phone = phone,
            avatar = avatar,
            locale = locale,
            language = language,
            appVersion = appVersion,
            appBuild = appBuild,
            traits = traits,
            utmSource = utmSource,
            utmMedium = utmMedium,
            utmCampaign = utmCampaign,
            utmContent = utmContent,
            utmTerm = utmTerm,
            installSource = installSource,
            installCampaign = installCampaign,
            emailOptIn = emailOptIn,
            smsOptIn = smsOptIn,
            whatsappOptIn = whatsappOptIn,
            pushOptIn = pushOptIn,
            gdprConsent = gdprConsent,
            doNotTrack = doNotTrack,
        )
    }
}
