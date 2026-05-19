package com.github.tvbox.osc.live.merge

import com.github.tvbox.osc.live.data.model.LiveChannel
import com.github.tvbox.osc.live.data.model.ChannelUrl
import com.github.tvbox.osc.live.data.model.ParsedChannel

class ChannelMerger(private val normalizer: ChannelNormalizer = ChannelNormalizer()) {

    data class MergeResult(
        val channels: List<LiveChannel>,
        val newCount: Int,
        val updatedCount: Int,
        val removedCount: Int
    )

    fun mergeSources(allParsed: List<List<ParsedChannel>>): List<LiveChannel> {
        val channelMap = LinkedHashMap<String, MutableList<ChannelUrl>>()
        val displayNames = LinkedHashMap<String, String>()
        val groupNames = LinkedHashMap<String, String>()

        for (parsedList in allParsed) {
            for (parsed in parsedList) {
                val normalizedName = normalizer.normalize(parsed.name)
                displayNames.putIfAbsent(normalizedName, parsed.name)
                groupNames.putIfAbsent(normalizedName, parsed.groupName)

                val urls = channelMap.getOrPut(normalizedName) { mutableListOf() }
                for (url in parsed.urls) {
                    if (urls.none { it.url == url }) {
                        urls.add(ChannelUrl(
                            url = url,
                            sourceName = parsed.sourceName
                        ))
                    }
                }
            }
        }

        return channelMap.map { (normalizedName, urls) ->
            LiveChannel(
                normalizedName = normalizedName,
                displayName = displayNames[normalizedName] ?: normalizedName,
                groupName = groupNames[normalizedName] ?: "未分组",
                urls = urls
            )
        }
    }

    fun mergeWithExisting(
        existing: List<LiveChannel>,
        incoming: List<LiveChannel>
    ): MergeResult {
        val existingMap = existing.associateBy { it.normalizedName }
        val incomingMap = incoming.associateBy { it.normalizedName }

        val resultChannels = mutableListOf<LiveChannel>()
        var newCount = 0
        var updatedCount = 0
        val now = System.currentTimeMillis()

        // Process incoming channels
        for ((name, incomingChannel) in incomingMap) {
            val existingChannel = existingMap[name]
            if (existingChannel == null) {
                // New channel
                newCount++
                resultChannels.add(incomingChannel.copy(updatedAt = now))
            } else {
                // Merge URLs
                val existingUrls = existingChannel.urls.map { it.url }.toSet()
                val newUrls = incomingChannel.urls.filter { it.url !in existingUrls }
                if (newUrls.isNotEmpty()) {
                    updatedCount++
                    val mergedUrls = existingChannel.urls + newUrls
                    resultChannels.add(existingChannel.copy(
                        urls = mergedUrls,
                        updatedAt = now
                    ))
                } else {
                    resultChannels.add(existingChannel)
                }
            }
        }

        // Keep existing channels that weren't in incoming (they might come from other sources)
        for ((name, existingChannel) in existingMap) {
            if (name !in incomingMap) {
                resultChannels.add(existingChannel)
            }
        }

        val removedCount = existing.size - resultChannels.count { it.normalizedName in existingMap }

        return MergeResult(
            channels = resultChannels,
            newCount = newCount,
            updatedCount = updatedCount,
            removedCount = removedCount
        )
    }
}
