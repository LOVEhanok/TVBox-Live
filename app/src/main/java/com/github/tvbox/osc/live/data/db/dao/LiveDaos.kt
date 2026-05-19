package com.github.tvbox.osc.live.data.db.dao

import androidx.room.*
import com.github.tvbox.osc.live.data.db.entity.*

@Dao
interface LiveSourceDao {
    @Query("SELECT * FROM live_source ORDER BY name")
    fun getAll(): List<LiveSourceEntity>

    @Query("SELECT * FROM live_source WHERE enabled = 1 ORDER BY name")
    fun getEnabled(): List<LiveSourceEntity>

    @Query("SELECT * FROM live_source WHERE url = :url LIMIT 1")
    fun findByUrl(url: String): LiveSourceEntity?

    @Query("SELECT * FROM live_source WHERE id = :id")
    fun getById(id: Long): LiveSourceEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insert(source: LiveSourceEntity): Long

    @Update
    fun update(source: LiveSourceEntity)

    @Delete
    fun delete(source: LiveSourceEntity)

    @Query("DELETE FROM live_source WHERE id = :id")
    fun deleteById(id: Long)

    @Query("UPDATE live_source SET enabled = :enabled WHERE id = :id")
    fun setEnabled(id: Long, enabled: Int)

    @Query("UPDATE live_source SET last_fetched_at = :time, last_channel_count = :count, fetch_error_count = 0 WHERE id = :id")
    fun updateFetchResult(id: Long, time: Long, count: Int)

    @Query("UPDATE live_source SET fetch_error_count = fetch_error_count + 1 WHERE id = :id")
    fun incrementErrorCount(id: Long)

    @Query("UPDATE live_source SET etag = :etag, last_modified = :lastModified WHERE id = :id")
    fun updateCacheHeaders(id: Long, etag: String?, lastModified: String?)
}

@Dao
interface LiveChannelDao {
    @Query("SELECT * FROM live_channel ORDER BY group_name, display_name")
    fun getAllGrouped(): List<LiveChannelEntity>

    @Query("SELECT * FROM live_channel WHERE normalized_name = :name LIMIT 1")
    fun findByNormalizedName(name: String): LiveChannelEntity?

    @Transaction
    @Query("SELECT * FROM live_channel ORDER BY group_name, display_name")
    fun getAllWithUrls(): List<LiveChannelWithUrls>

    @Transaction
    @Query("SELECT * FROM live_channel WHERE id = :id")
    fun getWithUrls(id: Long): LiveChannelWithUrls?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insert(channel: LiveChannelEntity): Long

    @Update
    fun update(channel: LiveChannelEntity)

    @Query("DELETE FROM live_channel")
    fun deleteAll()

    @Query("UPDATE live_channel SET best_url_id = :urlId, url_count = :urlCount, updated_at = :time WHERE id = :id")
    fun updateBestUrl(id: Long, urlId: Long?, urlCount: Int, time: Long)

    @Query("SELECT COUNT(*) FROM live_channel")
    fun count(): Int
}

@Dao
interface ChannelUrlDao {
    @Query("SELECT * FROM channel_url WHERE channel_id = :channelId AND is_active = 1 ORDER BY composite_score DESC")
    fun getUrlsForChannel(channelId: Long): List<ChannelUrlEntity>

    @Query("SELECT * FROM channel_url WHERE channel_id = :channelId ORDER BY composite_score DESC")
    fun getAllUrlsForChannel(channelId: Long): List<ChannelUrlEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insert(url: ChannelUrlEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertAll(urls: List<ChannelUrlEntity>)

    @Query("DELETE FROM channel_url WHERE channel_id = :channelId")
    fun deleteForChannel(channelId: Long)

    @Query("DELETE FROM channel_url")
    fun deleteAll()

    @Query("UPDATE channel_url SET composite_score = :score, speed_mbps = :speed, latency_ms = :latency, resolution = :resolution, last_tested_at = :time WHERE id = :id")
    fun updateTestResult(id: Long, score: Float, speed: Float, latency: Long, resolution: Int, time: Long)

    @Query("UPDATE channel_url SET is_active = :active WHERE id = :id")
    fun setActive(id: Long, active: Int)

    @Query("SELECT * FROM channel_url WHERE is_active = 1 AND last_tested_at < :staleTime LIMIT :limit")
    fun getStaleUrls(staleTime: Long, limit: Int): List<ChannelUrlEntity>
}

@Dao
interface FailedUrlDao {
    @Query("SELECT * FROM failed_url WHERE url = :url AND retry_after > :now LIMIT 1")
    fun isCurrentlyFailed(url: String, now: Long): FailedUrlEntity?

    @Query("SELECT * FROM failed_url WHERE retry_after <= :now")
    fun getUrlsReadyForRetry(now: Long): List<FailedUrlEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(failedUrl: FailedUrlEntity)

    @Query("DELETE FROM failed_url WHERE url = :url")
    fun deleteByUrl(url: String)

    @Query("DELETE FROM failed_url WHERE retry_after < :time")
    fun cleanupOlderThan(time: Long)
}
