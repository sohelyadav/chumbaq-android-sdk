package com.chumbaq.sdk

/**
 * Functional callback interface for asynchronous SDK operations.
 *
 * Uses Kotlin's [Result] to convey either a success value or a failure
 * throwable, keeping the public API simple for both Kotlin and Java callers.
 */
fun interface ChumbaqCallback<T> {
    fun onResult(result: Result<T>)
}
