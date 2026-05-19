package com.github.tvbox.osc.live.fallback

import com.github.tvbox.osc.live.data.db.dao.FailedUrlDao
import com.github.tvbox.osc.live.data.db.entity.FailedUrlEntity

class PlaybackMonitor(
    private val urlSelector: UrlSelector,
    private val failedUrlDao: FailedUrlDao
) {
    private var currentChannelId: Long = -1
    private var currentUrl: String = ""
    private var triedUrls = mutableSetOf<String>()

    fun setCurrentChannel(channelId: Long, url: String) {
        if (channelId != currentChannelId) {
            currentChannelId = channelId
            triedUrls.clear()
        }
        currentUrl = url
        triedUrls.add(url)
    }

    suspend fun onPlayerError(channelId: Long, failedUrl: String, error: String): String? {
        // Mark the URL as failed
        val now = System.currentTimeMillis()
        val existingFailed = failedUrlDao.isCurrentlyFailed(failedUrl, now)
        val failCount = (existingFailed?.failCount ?: 0) + 1
        val retryDelay = calculateRetryDelay(failCount)

        failedUrlDao.insert(FailedUrlEntity(
            url = failedUrl,
            failedAt = now,
            failureReason = error,
            retryAfter = now + retryDelay,
            failCount = failCount
        ))

        // Get next URL that hasn't been tried yet
        val allUrls = urlSelector.getOrderedUrls(channelId)
        for (url in allUrls) {
            if (url.url !in triedUrls) {
                triedUrls.add(url.url)
                currentUrl = url.url
                return url.url
            }
        }

        // All URLs tried, reset and try from beginning (excluding current failed)
        triedUrls.clear()
        return null
    }

    fun reset() {
        currentChannelId = -1
        currentUrl = ""
        triedUrls.clear()
    }

    private fun calculateRetryDelay(failCount: Int): Long {
        // Exponential backoff: 5min, 15min, 45min, 2h, 6h
        return when {
            failCount <= 1 -> 5 * 60 * 1000L
            failCount <= 2 -> 15 * 60 * 1000L
            failCount <= 3 -> 45 * 60 * 1000L
            failCount <= 4 -> 2 * 60 * 60 * 1000L
            else -> 6 * 60 * 60 * 1000L
        }
    }
}
