package com.dyplink.sdk.model

/**
 * Sealed hierarchy of errors that the Dyplink SDK may surface.
 */
sealed class DyplinkError(message: String, cause: Throwable? = null) : Exception(message, cause) {

    /** The SDK has not been initialised via [com.dyplink.sdk.Dyplink.initialize]. */
    class NotInitialized(
        message: String = "Dyplink SDK not initialized",
    ) : DyplinkError(message)

    /** A transport-level network failure (no response received). */
    class NetworkError(
        message: String,
        val statusCode: Int? = null,
        cause: Throwable? = null,
    ) : DyplinkError(message, cause)

    /** The server returned a non-success HTTP status. */
    class ApiError(
        message: String,
        val statusCode: Int,
        val responseBody: String? = null,
    ) : DyplinkError(message)

    /** The SDK was configured with invalid parameters. */
    class InvalidConfig(
        message: String,
    ) : DyplinkError(message)
}
