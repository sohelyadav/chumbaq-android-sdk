package com.chumbaq.sdk.model

/**
 * Sealed hierarchy of errors that the Chumbaq SDK may surface.
 */
sealed class ChumbaqError(message: String, cause: Throwable? = null) : Exception(message, cause) {

    /** The SDK has not been initialised via [com.chumbaq.sdk.Chumbaq.initialize]. */
    class NotInitialized(
        message: String = "Chumbaq SDK not initialized",
    ) : ChumbaqError(message)

    /** A transport-level network failure (no response received). */
    class NetworkError(
        message: String,
        val statusCode: Int? = null,
        cause: Throwable? = null,
    ) : ChumbaqError(message, cause)

    /** The server returned a non-success HTTP status. */
    class ApiError(
        message: String,
        val statusCode: Int,
        val responseBody: String? = null,
    ) : ChumbaqError(message)

    /** The SDK was configured with invalid parameters. */
    class InvalidConfig(
        message: String,
    ) : ChumbaqError(message)
}
