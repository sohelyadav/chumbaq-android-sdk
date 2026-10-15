package com.dyplink.sdk

import org.json.JSONObject

/**
 * Steps inside the envelope the backend wraps every response in.
 *
 * `TransformInterceptor` on the API returns `{ "data": … }` around every
 * endpoint, so a client reading a field at the top level finds nothing
 * there. Where that read has a default standing for an empty answer — no
 * deferred match, no eligible messages — the failure is completely silent,
 * which is how in-app messages and deferred matching could both be dead on
 * every device without a single error anywhere.
 *
 * Returns the receiver unchanged when there is no `data` object, so the same
 * parsers keep working on a body the SDK cached itself, or on an endpoint
 * that answers unwrapped.
 *
 * Lives beside [DyplinkConfigBridge], and for the same reason: the optional
 * modules need it and `internal` does not cross a Gradle module boundary.
 *
 * **This is NOT part of the public API.** Application code should not
 * reference it directly.
 */
fun JSONObject.unwrapDyplinkEnvelope(): JSONObject = optJSONObject("data") ?: this
