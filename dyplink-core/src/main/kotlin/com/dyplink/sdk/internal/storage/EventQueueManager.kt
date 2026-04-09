package com.dyplink.sdk.internal.storage

import com.dyplink.sdk.internal.network.RequestExecutor
import com.dyplink.sdk.internal.util.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.min
import kotlin.math.pow

/**
 * Manages the offline event queue.
 *
 * Events are persisted to Room via [EventQueueDao] and flushed to
 * the server in batches by [RequestExecutor].  A periodic timer
 * and a manual [triggerFlush] channel drive the flush loop.
 */
internal class EventQueueManager(
    private val dao: EventQueueDao,
    private val requestExecutor: RequestExecutor,
    private val maxQueueSize: Int,
    private val maxRetries: Int,
    private val flushIntervalSeconds: Int,
) {

    private val flushChannel = Channel<Unit>(Channel.CONFLATED)
    private var periodicJob: Job? = null
    private var flushJob: Job? = null

    /**
     * Start the periodic flush timer and the flush-trigger listener.
     */
    fun start(scope: CoroutineScope) {
        // Periodic timer
        periodicJob = scope.launch {
            while (isActive) {
                delay(flushIntervalSeconds * 1_000L)
                flush()
            }
        }

        // On-demand flush listener
        flushJob = scope.launch {
            for (signal in flushChannel) {
                flush()
            }
        }
    }

    /**
     * Enqueue a new event.  If the queue exceeds [maxQueueSize] the
     * oldest events with exhausted retries are purged first.
     */
    suspend fun enqueue(eventType: String, endpoint: String, payload: String) {
        val entity = EventQueueEntity(
            eventType = eventType,
            endpoint = endpoint,
            payload = payload,
            maxRetries = maxRetries,
        )
        dao.insert(entity)
        Logger.d("EventQueueManager: enqueued $eventType -> $endpoint")

        // Purge if over capacity.
        val count = dao.count()
        if (count > maxQueueSize) {
            dao.purgeExhaustedRetries()
            Logger.d("EventQueueManager: purged exhausted retries (queue was $count)")
        }
    }

    /**
     * Request an immediate flush (coalesced via [Channel.CONFLATED]).
     */
    fun triggerFlush() {
        flushChannel.trySend(Unit)
    }

    /**
     * Process the next batch of ready events.
     *
     * - 2xx / 4xx  ->  delete event (success or permanent failure)
     * - 5xx / IOException  ->  increment retry count with exponential backoff
     */
    suspend fun flush() {
        val now = System.currentTimeMillis()
        val batch = dao.getNextBatch(now)
        if (batch.isEmpty()) return

        Logger.d("EventQueueManager: flushing ${batch.size} events")

        for (event in batch) {
            try {
                val response = requestExecutor.post(event.endpoint, event.payload)
                val code = response.code
                response.close()

                if (code in 200..299 || code in 400..499) {
                    // Success or permanent client error  →  remove.
                    dao.deleteByIds(listOf(event.id))
                    Logger.d("EventQueueManager: event ${event.id} completed (HTTP $code)")
                } else {
                    // Server error  →  schedule retry.
                    scheduleRetry(event)
                }
            } catch (e: Exception) {
                Logger.w("EventQueueManager: event ${event.id} failed: ${e.message}")
                scheduleRetry(event)
            }
        }

        // Clean up events that can no longer be retried.
        dao.purgeExhaustedRetries()
    }

    /**
     * Stop the periodic flush loop and the trigger listener.
     */
    fun stop() {
        periodicJob?.cancel()
        flushJob?.cancel()
        periodicJob = null
        flushJob = null
    }

    /**
     * Number of events still waiting in the queue.
     */
    suspend fun pendingCount(): Int = dao.count()

    // ── Helpers ─────────────────────────────────────────────────────

    private suspend fun scheduleRetry(event: EventQueueEntity) {
        val backoffMs = min(
            (2.0.pow(event.retryCount) * 1_000).toLong(),
            MAX_BACKOFF_MS,
        )
        val nextRetryAt = System.currentTimeMillis() + backoffMs
        dao.incrementRetry(event.id, nextRetryAt)
        Logger.d(
            "EventQueueManager: event ${event.id} retry scheduled " +
                "(retryCount=${event.retryCount + 1}, backoff=${backoffMs}ms)",
        )
    }

    internal companion object {
        /** Maximum single-event backoff: 5 minutes. */
        const val MAX_BACKOFF_MS = 300_000L
    }
}
