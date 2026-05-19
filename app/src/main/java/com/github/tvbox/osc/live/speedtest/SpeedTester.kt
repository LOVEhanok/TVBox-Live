package com.github.tvbox.osc.live.speedtest

import com.github.tvbox.osc.live.data.model.ChannelUrl
import com.github.tvbox.osc.live.data.model.LiveChannel
import com.github.tvbox.osc.live.data.model.SpeedTestResult
import com.github.tvbox.osc.live.util.NetworkUtil
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

data class SpeedTestConfig(
    val concurrency: Int = 10,
    val timeoutMs: Long = 5000,
    val minSpeedMbps: Float = 0.5f,
    val minResolution: Int = 0,
    val sampleBytes: Int = 256 * 1024
)

class SpeedTester(private val config: SpeedTestConfig = SpeedTestConfig()) {

    suspend fun testChannel(channel: LiveChannel): List<ChannelUrl> = withContext(Dispatchers.IO) {
        val semaphore = Semaphore(config.concurrency)
        val results = channel.urls.map { url ->
            async {
                semaphore.withPermit {
                    val result = NetworkUtil.testUrlSpeed(url.url, config.timeoutMs)
                    url.copy(
                        speedMbps = result.speedMbps,
                        latencyMs = result.latencyMs,
                        resolution = result.resolution,
                        compositeScore = calculateScore(result),
                        lastTestedAt = result.testedAt,
                        isActive = result.isAlive && result.speedMbps >= config.minSpeedMbps
                    )
                }
            }
        }
        results.awaitAll()
            .filter { it.isActive }
            .sortedByDescending { it.compositeScore }
    }

    suspend fun testChannels(channels: List<LiveChannel>): List<LiveChannel> = withContext(Dispatchers.IO) {
        channels.map { channel ->
            async {
                channel.copy(urls = testChannel(channel))
            }
        }.awaitAll()
    }

    suspend fun testSingleUrl(url: String): SpeedTestResult = withContext(Dispatchers.IO) {
        NetworkUtil.testUrlSpeed(url, config.timeoutMs)
    }

    private fun calculateScore(result: SpeedTestResult): Float {
        if (!result.isAlive) return 0f
        val speedScore = (result.speedMbps / 10f).coerceIn(0f, 1f)
        val latencyScore = if (result.latencyMs > 0) (1000f / result.latencyMs).coerceIn(0f, 1f) else 0f
        val resolutionScore = when {
            result.resolution >= 1080 -> 1f
            result.resolution >= 720 -> 0.7f
            result.resolution >= 480 -> 0.4f
            else -> 0.3f
        }
        return (speedScore * 0.4f + latencyScore * 0.3f + resolutionScore * 0.3f).coerceIn(0f, 1f)
    }
}
