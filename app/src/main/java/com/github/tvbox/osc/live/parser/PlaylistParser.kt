package com.github.tvbox.osc.live.parser

import com.github.tvbox.osc.live.data.model.ParsedChannel
import com.github.tvbox.osc.live.data.model.PlaylistFormat
import java.io.BufferedReader
import java.io.StringReader

interface PlaylistParser {
    fun parse(input: String, sourceName: String): List<ParsedChannel>
}

class TxtPlaylistParser : PlaylistParser {

    override fun parse(input: String, sourceName: String): List<ParsedChannel> {
        val result = mutableListOf<ParsedChannel>()
        val channelUrls = LinkedHashMap<String, MutableList<String>>()
        var currentGroup = "未分组"

        try {
            val reader = BufferedReader(StringReader(input))
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                val trimmed = line!!.trim()
                if (trimmed.isEmpty() || trimmed.startsWith("#") && !trimmed.contains("#genre#")) continue

                val parts = trimmed.split(",", limit = 2)
                if (parts.size < 2) continue

                if (trimmed.contains("#genre#")) {
                    currentGroup = parts[0].trim()
                    continue
                }

                val name = parts[0].trim()
                val urls = parts[1].trim().split("#").map { it.trim() }.filter { isUrl(it) }
                if (urls.isEmpty()) continue

                val key = "$currentGroup|$name"
                val existing = channelUrls.getOrPut(key) { mutableListOf() }
                urls.forEach { url ->
                    if (url !in existing) existing.add(url)
                }
            }
            reader.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        channelUrls.forEach { (key, urls) ->
            val (group, name) = key.split("|", limit = 2)
            result.add(ParsedChannel(name = name, groupName = group, urls = urls, sourceName = sourceName))
        }
        return result
    }

    private fun isUrl(url: String): Boolean {
        return url.startsWith("http") || url.startsWith("rtp") || url.startsWith("rtsp") || url.startsWith("rtmp")
    }
}

class M3uPlaylistParser : PlaylistParser {

    companion object {
        private val NAME_PATTERN = Regex(",(.+?)$")
        private val GROUP_PATTERN = Regex("group-title=\"(.*?)\"")
    }

    override fun parse(input: String, sourceName: String): List<ParsedChannel> {
        val result = mutableListOf<ParsedChannel>()
        val channelUrls = LinkedHashMap<String, MutableList<String>>()

        try {
            val reader = BufferedReader(StringReader(input))
            var line: String?
            var pendingName: String? = null
            var pendingGroup = "未分组"

            while (reader.readLine().also { line = it } != null) {
                val trimmed = line!!.trim()
                if (trimmed.isEmpty()) continue
                if (trimmed.startsWith("#EXTM3U")) continue
                if (isSettingLine(trimmed)) continue

                if (trimmed.contains("#EXTINF")) {
                    pendingName = extractName(trimmed)
                    pendingGroup = extractGroup(trimmed)
                } else if (!trimmed.startsWith("#") && pendingName != null) {
                    if (isUrl(trimmed)) {
                        val key = "$pendingGroup|$pendingName"
                        val existing = channelUrls.getOrPut(key) { mutableListOf() }
                        if (trimmed !in existing) existing.add(trimmed)
                    }
                    pendingName = null
                }
            }
            reader.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        channelUrls.forEach { (key, urls) ->
            val (group, name) = key.split("|", limit = 2)
            result.add(ParsedChannel(name = name, groupName = group, urls = urls, sourceName = sourceName))
        }
        return result
    }

    private fun extractName(line: String): String {
        val match = NAME_PATTERN.find(line)
        return match?.groupValues?.get(1) ?: "未命名"
    }

    private fun extractGroup(line: String): String {
        val match = GROUP_PATTERN.find(line)
        return match?.groupValues?.get(1) ?: "未分组"
    }

    private fun isUrl(url: String): Boolean {
        return url.startsWith("http") || url.startsWith("rtp") || url.startsWith("rtsp") || url.startsWith("rtmp")
    }

    private fun isSettingLine(line: String): Boolean {
        val prefixes = listOf("ua", "parse", "click", "player", "header", "format", "origin", "referer",
            "#EXTHTTP:", "#EXTVLCOPT:", "#KODIPROP:")
        return prefixes.any { line.startsWith(it) }
    }
}

object PlaylistParserFactory {
    fun detectFormat(content: String): PlaylistFormat {
        return when {
            content.trimStart().startsWith("#EXTM3U") -> PlaylistFormat.M3U
            content.contains("#genre#") -> PlaylistFormat.TXT
            content.contains("#EXTINF") -> PlaylistFormat.M3U
            else -> PlaylistFormat.TXT
        }
    }

    fun getParser(format: PlaylistFormat): PlaylistParser {
        return when (format) {
            PlaylistFormat.M3U -> M3uPlaylistParser()
            PlaylistFormat.TXT -> TxtPlaylistParser()
            PlaylistFormat.UNKNOWN -> TxtPlaylistParser()
        }
    }

    fun autoDetectAndParse(content: String, sourceName: String): List<ParsedChannel> {
        val format = detectFormat(content)
        return getParser(format).parse(content, sourceName)
    }
}
