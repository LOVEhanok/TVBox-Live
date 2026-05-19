package com.github.tvbox.osc.live.data.db

import android.content.Context
import androidx.room.*
import com.github.tvbox.osc.live.data.db.dao.*
import com.github.tvbox.osc.live.data.db.entity.*

@Database(
    entities = [
        LiveSourceEntity::class,
        LiveChannelEntity::class,
        ChannelUrlEntity::class,
        FailedUrlEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class LiveDatabase : RoomDatabase() {
    abstract fun liveSourceDao(): LiveSourceDao
    abstract fun liveChannelDao(): LiveChannelDao
    abstract fun channelUrlDao(): ChannelUrlDao
    abstract fun failedUrlDao(): FailedUrlDao

    companion object {
        @Volatile
        private var INSTANCE: LiveDatabase? = null

        fun getInstance(context: Context): LiveDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    LiveDatabase::class.java,
                    "live_sources.db"
                )
                    .fallbackToDestructiveMigration()
                    .allowMainThreadQueries()
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
