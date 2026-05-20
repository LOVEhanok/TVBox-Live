package com.github.tvbox.osc.live.data.repository

import android.content.Context
import com.github.tvbox.osc.bean.LiveChannelGroup
import com.github.tvbox.osc.bean.LiveChannelItem
import com.github.tvbox.osc.live.data.db.LiveDatabase
import com.github.tvbox.osc.live.data.db.dao.*
import com.github.tvbox.osc.live.data.db.entity.*
import com.github.tvbox.osc.live.data.model.*
import com.github.tvbox.osc.live.merge.ChannelMerger
import com.github.tvbox.osc.live.parser.PlaylistParserFactory
import com.github.tvbox.osc.live.speedtest.SpeedTestConfig
import com.github.tvbox.osc.live.speedtest.SpeedTester
import com.github.tvbox.osc.live.util.NetworkUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class LiveRepository private constructor(private val context: Context) {

    private val db = LiveDatabase.getInstance(context)
    private val sourceDao: LiveSourceDao = db.liveSourceDao()
    private val channelDao: LiveChannelDao = db.liveChannelDao()
    private val urlDao: ChannelUrlDao = db.channelUrlDao()
    private val failedUrlDao: FailedUrlDao = db.failedUrlDao()

    private val merger = ChannelMerger()
    private val speedTester = SpeedTester()

    suspend fun refreshAllSources(): RefreshResult = withContext(Dispatchers.IO) {
        val enabledSources = sourceDao.getEnabled()
        val errors = mutableListOf<String>()
        val allParsed = mutableListOf<List<ParsedChannel>>()

        for (source in enabledSources) {
            try {
                val result = NetworkUtil.fetchPlaylist(
                    url = source.url,
                    etag = source.etag,
                    lastModified = source.lastModified
                )

                if (result == null) {
                    // Not modified or error
                    if (source.etag != null || source.lastModified != null) {
                        // Was a 304 Not Modified - no action needed
                        continue
                    }
                    sourceDao.incrementErrorCount(source.id)
                    errors.add("Failed to fetch: ${source.name}")
                    continue
                }

                val parsed = PlaylistParserFactory.autoDetectAndParse(result.content, source.name)
                allParsed.add(parsed)

                // Update source metadata
                val now = System.currentTimeMillis()
                sourceDao.updateFetchResult(source.id, now, parsed.size)
                sourceDao.updateCacheHeaders(source.id, result.etag, result.lastModified)
            } catch (e: Exception) {
                sourceDao.incrementErrorCount(source.id)
                errors.add("Error fetching ${source.name}: ${e.message}")
            }
        }

        if (allParsed.isEmpty()) {
            return@withContext RefreshResult(errors = errors)
        }

        // Merge all parsed channels
        val mergedChannels = merger.mergeSources(allParsed)

        // Get existing channels from DB
        val existingWithUrls = channelDao.getAllWithUrls()
        val existingChannels = existingWithUrls.map { it.toLiveChannel() }

        // Merge with existing
        val mergeResult = merger.mergeWithExisting(existingChannels, mergedChannels)

        // Save to database
        saveChannels(mergeResult.channels)

        RefreshResult(
            newChannels = mergeResult.newCount,
            updatedChannels = mergeResult.updatedCount,
            removedChannels = mergeResult.removedCount,
            totalChannels = mergeResult.channels.size,
            errors = errors
        )
    }

    suspend fun addSource(name: String, url: String): Long = withContext(Dispatchers.IO) {
        sourceDao.insert(LiveSourceEntity(
            name = name,
            url = url,
            type = SourceType.USER_ADDED.name
        ))
    }

    suspend fun removeSource(id: Long) = withContext(Dispatchers.IO) {
        sourceDao.deleteById(id)
    }

    suspend fun setSourceEnabled(id: Long, enabled: Boolean) = withContext(Dispatchers.IO) {
        sourceDao.setEnabled(id, if (enabled) 1 else 0)
    }

    suspend fun getAllSources(): List<LiveSource> = withContext(Dispatchers.IO) {
        sourceDao.getAll().map { it.toLiveSource() }
    }

    suspend fun getChannelGroups(): List<LiveChannelGroup> = withContext(Dispatchers.IO) {
        val channelsWithUrls = channelDao.getAllWithUrls()
        convertToLegacyGroups(channelsWithUrls)
    }

    suspend fun getBestUrl(channelId: Long): String? = withContext(Dispatchers.IO) {
        val channel = channelDao.getWithUrls(channelId) ?: return@withContext null
        channel.urls
            .filter { it.isActive == 1 }
            .maxByOrNull { it.compositeScore }
            ?.url
    }

    suspend fun recordFailure(url: String, reason: String) = withContext(Dispatchers.IO) {
        recordFailureSync(url, reason)
    }

    /** Non-suspend version for Java callers */
    fun recordFailureSync(url: String, reason: String) {
        val now = System.currentTimeMillis()
        val existing = failedUrlDao.isCurrentlyFailed(url, now)
        val failCount = (existing?.failCount ?: 0) + 1
        val retryDelay = when {
            failCount <= 1 -> 5 * 60 * 1000L
            failCount <= 2 -> 15 * 60 * 1000L
            failCount <= 3 -> 45 * 60 * 1000L
            else -> 2 * 60 * 60 * 1000L
        }

        failedUrlDao.insert(FailedUrlEntity(
            url = url,
            failedAt = now,
            failureReason = reason,
            retryAfter = now + retryDelay,
            failCount = failCount
        ))
    }

    suspend fun revalidateFailedUrls(): Int = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val readyUrls = failedUrlDao.getUrlsReadyForRetry(now)
        var restoredCount = 0

        for (failed in readyUrls) {
            val result = speedTester.testSingleUrl(failed.url)
            if (result.isAlive) {
                failedUrlDao.deleteByUrl(failed.url)
                restoredCount++
            } else {
                // Increase retry delay
                val newFailCount = failed.failCount + 1
                val newDelay = when {
                    newFailCount <= 3 -> 45 * 60 * 1000L
                    newFailCount <= 5 -> 2 * 60 * 60 * 1000L
                    else -> 6 * 60 * 60 * 1000L
                }
                failedUrlDao.insert(failed.copy(
                    failCount = newFailCount,
                    retryAfter = now + newDelay
                ))
            }
        }

        restoredCount
    }

    suspend fun runSpeedTest(): Int = withContext(Dispatchers.IO) {
        val channels = channelDao.getAllWithUrls()
        var testedCount = 0

        for (channelWithUrls in channels) {
            val channel = channelWithUrls.toLiveChannel()
            val testedUrls = speedTester.testChannel(channel)

            for (testedUrl in testedUrls) {
                urlDao.updateTestResult(
                    id = testedUrl.id,
                    score = testedUrl.compositeScore,
                    speed = testedUrl.speedMbps,
                    latency = testedUrl.latencyMs,
                    resolution = testedUrl.resolution,
                    time = testedUrl.lastTestedAt
                )
            }

            // Update best URL
            val bestUrl = testedUrls.firstOrNull()
            channelDao.updateBestUrl(
                id = channelWithUrls.channel.id,
                urlId = bestUrl?.id,
                urlCount = testedUrls.size,
                time = System.currentTimeMillis()
            )
            testedCount++
        }

        testedCount
    }

    private suspend fun saveChannels(channels: List<LiveChannel>) {
        val now = System.currentTimeMillis()
        for (channel in channels) {
            val existing = channelDao.findByNormalizedName(channel.normalizedName)
            val channelId: Long

            if (existing != null) {
                channelId = existing.id
                channelDao.update(existing.copy(
                    displayName = channel.displayName,
                    groupName = channel.groupName,
                    updatedAt = now
                ))
                // Clear old URLs and re-add
                urlDao.deleteForChannel(channelId)
            } else {
                channelId = channelDao.insert(LiveChannelEntity(
                    normalizedName = channel.normalizedName,
                    displayName = channel.displayName,
                    groupName = channel.groupName,
                    updatedAt = now
                ))
            }

            // Insert URLs
            val urlEntities = channel.urls.map { url ->
                ChannelUrlEntity(
                    channelId = channelId,
                    url = url.url,
                    sourceName = url.sourceName,
                    sourceId = url.sourceId,
                    resolution = url.resolution,
                    speedMbps = url.speedMbps,
                    latencyMs = url.latencyMs,
                    compositeScore = url.compositeScore,
                    lastTestedAt = url.lastTestedAt,
                    isActive = if (url.isActive) 1 else 0
                )
            }
            urlDao.insertAll(urlEntities)

            // Update best URL
            val bestUrl = urlEntities.maxByOrNull { it.compositeScore }
            channelDao.updateBestUrl(channelId, bestUrl?.id, urlEntities.size, now)
        }
    }

    /** Public no-arg version for Java callers: fetches channels from DB then converts */
    fun convertToLegacyGroups(): List<LiveChannelGroup> {
        return convertToLegacyGroups(channelDao.getAllWithUrls())
    }

    private fun convertToLegacyGroups(channelsWithUrls: List<LiveChannelWithUrls>): List<LiveChannelGroup> {
        val grouped = channelsWithUrls.groupBy { it.channel.groupName }
        val result = mutableListOf<LiveChannelGroup>()
        var groupIndex = 0

        for ((groupName, channels) in grouped) {
            val group = LiveChannelGroup()
            group.groupIndex = groupIndex++
            group.groupName = groupName
            group.setLiveChannels(ArrayList(channels.mapIndexed { channelIndex, channelWithUrls ->
                val item = LiveChannelItem()
                item.channelIndex = channelIndex
                item.channelNum = channelIndex + 1
                item.channelName = channelWithUrls.channel.displayName
                item.channelUrls = ArrayList(channelWithUrls.urls
                    .sortedByDescending { it.compositeScore }
                    .map { it.url })
                item.channelSourceNames = ArrayList(channelWithUrls.urls
                    .sortedByDescending { it.compositeScore }
                    .map { it.sourceName })
                item.sourceNum = channelWithUrls.urls.size
                item
            }))
            result.add(group)
        }

        return result
    }

    private fun LiveChannelWithUrls.toLiveChannel(): LiveChannel {
        return LiveChannel(
            id = channel.id,
            normalizedName = channel.normalizedName,
            displayName = channel.displayName,
            groupName = channel.groupName,
            urls = urls.map { it.toChannelUrl() },
            updatedAt = channel.updatedAt
        )
    }

    private fun ChannelUrlEntity.toChannelUrl(): ChannelUrl {
        return ChannelUrl(
            id = id,
            channelId = channelId,
            url = url,
            sourceName = sourceName,
            sourceId = sourceId,
            resolution = resolution,
            speedMbps = speedMbps,
            latencyMs = latencyMs,
            compositeScore = compositeScore,
            lastTestedAt = lastTestedAt,
            isActive = isActive == 1
        )
    }

    private fun LiveSourceEntity.toLiveSource(): LiveSource {
        return LiveSource(
            id = id,
            name = name,
            url = url,
            type = SourceType.valueOf(type),
            enabled = enabled == 1,
            lastFetchedAt = lastFetchedAt,
            lastChannelCount = lastChannelCount,
            fetchErrorCount = fetchErrorCount,
            etag = etag,
            lastModified = lastModified
        )
    }

    companion object {
        @Volatile
        private var INSTANCE: LiveRepository? = null

        @JvmStatic
        fun getInstance(context: Context): LiveRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: LiveRepository(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
