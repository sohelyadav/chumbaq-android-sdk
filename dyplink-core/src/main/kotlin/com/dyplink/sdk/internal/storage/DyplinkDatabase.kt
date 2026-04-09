package com.dyplink.sdk.internal.storage

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Room database backing the Dyplink SDK's offline event queue.
 */
@Database(
    entities = [EventQueueEntity::class],
    version = 1,
    exportSchema = false,
)
internal abstract class DyplinkDatabase : RoomDatabase() {

    abstract fun eventQueueDao(): EventQueueDao

    companion object {

        private const val DB_NAME = "dyplink_sdk.db"

        /**
         * Create (or open) the database.
         *
         * [fallbackToDestructiveMigration] ensures that a schema
         * version bump never crashes the app; data in the queue is
         * transient anyway.
         */
        fun create(context: Context): DyplinkDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                DyplinkDatabase::class.java,
                DB_NAME,
            )
                .fallbackToDestructiveMigration()
                .build()
        }
    }
}
