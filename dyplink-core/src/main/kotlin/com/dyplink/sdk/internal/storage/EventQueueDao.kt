package com.dyplink.sdk.internal.storage

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/**
 * Data-access object for the offline event queue.
 */
@Dao
internal interface EventQueueDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(event: EventQueueEntity): Long

    /**
     * Return the next batch of events whose [EventQueueEntity.nextRetryAt]
     * has elapsed, ordered oldest-first.
     */
    @Query(
        """
        SELECT * FROM dyplink_event_queue
        WHERE next_retry_at <= :now
        ORDER BY created_at ASC
        LIMIT :batchSize
        """,
    )
    suspend fun getNextBatch(now: Long, batchSize: Int = 50): List<EventQueueEntity>

    @Query("SELECT COUNT(*) FROM dyplink_event_queue")
    suspend fun count(): Int

    @Query("DELETE FROM dyplink_event_queue WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    /**
     * Bump the retry counter and push the next-retry timestamp.
     */
    @Query(
        """
        UPDATE dyplink_event_queue
        SET retry_count = retry_count + 1,
            next_retry_at = :nextRetryAt
        WHERE id = :id
        """,
    )
    suspend fun incrementRetry(id: Long, nextRetryAt: Long)

    /**
     * Remove events that have exhausted their retry budget.
     */
    @Query("DELETE FROM dyplink_event_queue WHERE retry_count >= max_retries")
    suspend fun purgeExhaustedRetries()

    /**
     * Remove events older than [cutoff] (epoch millis).
     */
    @Query("DELETE FROM dyplink_event_queue WHERE created_at < :cutoff")
    suspend fun purgeOlderThan(cutoff: Long)

    @Query("DELETE FROM dyplink_event_queue")
    suspend fun deleteAll()
}
