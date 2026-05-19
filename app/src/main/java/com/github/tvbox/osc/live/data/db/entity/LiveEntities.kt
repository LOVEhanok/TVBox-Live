package com.github.tvbox.osc.live.data.db.entity

import androidx.room.*

@Entity(tableName = "live_source", indices = [Index(value = ["url"], unique = true)])
data class LiveSourceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val url: String,
    val type: String,
    val enabled: Int = 1,
    @ColumnInfo(name = "last_fetched_at") val lastFetchedAt: Long = 0,
    @ColumnInfo(name = "last_channel_count") val lastChannelCount: Int = 0,
    @ColumnInfo(name = "fetch_error_count") val fetchErrorCount: Int = 0,
    val etag: String? = null,
    @ColumnInfo(name = "last_modified") val lastModified: String? = null
)

@Entity(
    tableName = "live_channel",
    indices = [Index(value = ["normalized_name"])]
)
data class LiveChannelEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "normalized_name") val normalizedName: String,
    @ColumnInfo(name = "display_name") val displayName: String,
    @ColumnInfo(name = "group_name") val groupName: String,
    @ColumnInfo(name = "url_count") val urlCount: Int = 0,
    @ColumnInfo(name = "best_url_id") val bestUrlId: Long? = null,
    @ColumnInfo(name = "updated_at") val updatedAt: Long = 0
)

@Entity(
    tableName = "channel_url",
    indices = [Index(value = ["channel_id"])],
    foreignKeys = [
        ForeignKey(
            entity = LiveChannelEntity::class,
            parentColumns = ["id"],
            childColumns = ["channel_id"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class ChannelUrlEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "channel_id") val channelId: Long,
    val url: String,
    @ColumnInfo(name = "source_name") val sourceName: String,
    @ColumnInfo(name = "source_id") val sourceId: Long = 0,
    val resolution: Int = 0,
    @ColumnInfo(name = "speed_mbps") val speedMbps: Float = 0f,
    @ColumnInfo(name = "latency_ms") val latencyMs: Long = 0,
    @ColumnInfo(name = "composite_score") val compositeScore: Float = 0f,
    @ColumnInfo(name = "last_tested_at") val lastTestedAt: Long = 0,
    @ColumnInfo(name = "is_active") val isActive: Int = 1
)

@Entity(tableName = "failed_url")
data class FailedUrlEntity(
    @PrimaryKey val url: String,
    @ColumnInfo(name = "failed_at") val failedAt: Long,
    @ColumnInfo(name = "failure_reason") val failureReason: String,
    @ColumnInfo(name = "retry_after") val retryAfter: Long,
    @ColumnInfo(name = "fail_count") val failCount: Int = 1
)

data class LiveChannelWithUrls(
    @Embedded val channel: LiveChannelEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "channel_id"
    )
    val urls: List<ChannelUrlEntity>
)
