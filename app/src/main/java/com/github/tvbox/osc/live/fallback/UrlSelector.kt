package com.github.tvbox.osc.live.fallback

import com.github.tvbox.osc.live.data.db.dao.ChannelUrlDao
import com.github.tvbox.osc.live.data.db.dao.FailedUrlDao
import com.github.tvbox.osc.live.data.db.entity.ChannelUrlEntity

class UrlSelector(
    private val urlDao: ChannelUrlDao,
    private val failedUrlDao: FailedUrlDao
) {
    suspend fun selectBest(channelId: Long): String? {
        val urls = urlDao.getUrlsForChannel(channelId)
        val now = System.currentTimeMillis()
        for (url in urls) {
            val failed = failedUrlDao.isCurrentlyFailed(url.url, now)
            if (failed == null) return url.url
        }
        // If all are in cooldown, return the best one anyway
        return urls.firstOrNull()?.url
    }

    suspend fun getOrderedUrls(channelId: Long): List<ChannelUrlEntity> {
        val urls = urlDao.getUrlsForChannel(channelId)
        val now = System.currentTimeMillis()
        val available = mutableListOf<ChannelUrlEntity>()
        val cooldown = mutableListOf<ChannelUrlEntity>()

        for (url in urls) {
            val failed = failedUrlDao.isCurrentlyFailed(url.url, now)
            if (failed == null) available.add(url) else cooldown.add(url)
        }

        return available + cooldown
    }

    suspend fun getNextUrl(channelId: Long, currentUrl: String): String? {
        val urls = getOrderedUrls(channelId)
        val currentIndex = urls.indexOfFirst { it.url == currentUrl }
        return if (currentIndex >= 0 && currentIndex < urls.size - 1) {
            urls[currentIndex + 1].url
        } else if (urls.isNotEmpty()) {
            urls.first().url
        } else {
            null
        }
    }
}
