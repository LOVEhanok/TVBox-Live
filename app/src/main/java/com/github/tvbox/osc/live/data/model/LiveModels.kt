package com.github.tvbox.osc.live.data.model

enum class SourceType {
    BUILTIN, GITHUB_REPO, USER_ADDED, DISCOVERED
}

enum class PlaylistFormat {
    TXT, M3U, UNKNOWN
}

data class LiveSource(
    val id: Long = 0,
    val name: String,
    val url: String,
    val type: SourceType = SourceType.USER_ADDED,
    val enabled: Boolean = true,
    val lastFetchedAt: Long = 0,
    val lastChannelCount: Int = 0,
    val fetchErrorCount: Int = 0,
    val etag: String? = null,
    val lastModified: String? = null
)

data class LiveChannel(
    val id: Long = 0,
    val normalizedName: String,
    val displayName: String,
    val groupName: String,
    val urls: List<ChannelUrl> = emptyList(),
    val updatedAt: Long = 0
)

data class ChannelUrl(
    val id: Long = 0,
    val channelId: Long = 0,
    val url: String,
    val sourceName: String = "",
    val sourceId: Long = 0,
    val resolution: Int = 0,
    val speedMbps: Float = 0f,
    val latencyMs: Long = 0,
    val compositeScore: Float = 0f,
    val lastTestedAt: Long = 0,
    val isActive: Boolean = true
)

data class ParsedChannel(
    val name: String,
    val groupName: String,
    val urls: List<String>,
    val sourceName: String
)

data class FailedUrl(
    val url: String,
    val failedAt: Long = 0,
    val failureReason: String = "",
    val retryAfter: Long = 0,
    val failCount: Int = 1
)

data class SpeedTestResult(
    val url: String,
    val latencyMs: Long = 0,
    val speedMbps: Float = 0f,
    val resolution: Int = 0,
    val isAlive: Boolean = false,
    val testedAt: Long = 0
)

data class RefreshResult(
    val newChannels: Int = 0,
    val updatedChannels: Int = 0,
    val removedChannels: Int = 0,
    val totalChannels: Int = 0,
    val errors: List<String> = emptyList()
) {
    val hasChanges: Boolean get() = newChannels > 0 || updatedChannels > 0 || removedChannels > 0
}

data class DiscoveredSource(
    val name: String,
    val url: String,
    val format: PlaylistFormat = PlaylistFormat.UNKNOWN,
    val discoveredFrom: String = ""
)
