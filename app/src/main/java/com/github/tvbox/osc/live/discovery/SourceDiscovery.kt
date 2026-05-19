package com.github.tvbox.osc.live.discovery

import com.github.tvbox.osc.live.data.model.DiscoveredSource
import com.github.tvbox.osc.live.data.model.PlaylistFormat
import com.github.tvbox.osc.live.util.NetworkUtil

/**
 * 内置直播源 — 国内可直接访问，无需翻墙
 */
object BuiltinSourceDiscovery {

    // 国内可直接访问的直播源（CDN/直连）
    private val builtinSources = listOf(
        // 范明明直播源（国内可访问）
        DiscoveredSource(
            name = "范明明直播源(IPv6)",
            url = "https://live.fanmingming.com/tv/m3u/ipv6.m3u",
            format = PlaylistFormat.M3U,
            discoveredFrom = "builtin"
        ),
        DiscoveredSource(
            name = "范明明直播源(IPv4)",
            url = "https://live.fanmingming.com/tv/m3u/ipv4.m3u",
            format = PlaylistFormat.M3U,
            discoveredFrom = "builtin"
        ),
        // IPTV 直播源（国内镜像）
        DiscoveredSource(
            name = "IPTV中文频道",
            url = "https://iptv-org.github.io/iptv/streams/cn.m3u",
            format = PlaylistFormat.M3U,
            discoveredFrom = "builtin"
        ),
        // 央视卫视源
        DiscoveredSource(
            name = "央视卫视源",
            url = "https://raw.gitmirror.com/iptv-org/iptv/master/streams/cn.m3u",
            format = PlaylistFormat.M3U,
            discoveredFrom = "builtin-mirror"
        )
    )

    fun discover(): List<DiscoveredSource> {
        return builtinSources.filter { source ->
            try {
                val result = NetworkUtil.fetchPlaylist(source.url)
                result != null && result.content.isNotBlank()
            } catch (e: Exception) {
                false
            }
        }
    }
}

/**
 * GitHub 源发现 — 自动使用国内镜像代理
 */
object GitHubSourceDiscovery {

    // GitHub raw 内容代理（国内可访问的镜像）
    private val githubProxies = listOf(
        "",                                    // 直连（有梯子时）
        "https://raw.gitmirror.com/",          // GitMirror
        "https://raw.kkgithub.com/",           // kkgithub
        "https://ghproxy.com/",                // ghproxy
        "https://mirror.ghproxy.com/"          // 镜像 ghproxy
    )

    // 已知的 IPTV 聚合仓库
    private val githubRepos = listOf(
        "iptv-org/iptv/master/streams/cn.m3u",
        "iptv-org/iptv/master/streams/hk.m3u",
        "iptv-org/iptv/master/streams/tw.m3u",
        "HerbertHe/iptv-resources/main/iptv.m3u"
    )

    fun discover(): List<DiscoveredSource> {
        val discovered = mutableListOf<DiscoveredSource>()
        val testedUrls = mutableSetOf<String>()

        for (repo in githubRepos) {
            for (proxy in githubProxies) {
                val url = if (proxy.isEmpty()) {
                    "https://raw.githubusercontent.com/$repo"
                } else {
                    "$proxy$repo"
                }

                if (url in testedUrls) continue
                testedUrls.add(url)

                try {
                    val result = NetworkUtil.fetchPlaylist(url) ?: continue
                    if (result.content.isNotBlank() && result.content.length > 100) {
                        discovered.add(DiscoveredSource(
                            name = "${extractRepoName(repo)} (${extractProxyName(proxy)})",
                            url = url,
                            format = PlaylistParserFactory.detectFormat(result.content),
                            discoveredFrom = "github"
                        ))
                        break // 找到可用的代理就不再尝试其他代理
                    }
                } catch (e: Exception) {
                    // 继续尝试下一个代理
                }
            }
        }

        return discovered
    }

    private fun extractRepoName(repo: String): String {
        val parts = repo.split("/")
        return if (parts.size >= 2) "${parts[0]}/${parts[1]}" else repo
    }

    private fun extractProxyName(proxy: String): String {
        return when {
            proxy.isEmpty() -> "直连"
            proxy.contains("gitmirror") -> "GitMirror"
            proxy.contains("kkgithub") -> "kkgithub"
            proxy.contains("mirror.ghproxy") -> "ghproxy镜像"
            proxy.contains("ghproxy") -> "ghproxy"
            else -> "代理"
        }
    }
}

/**
 * 电信运营商 IPTV 组播源发现
 * 注意：这些地址仅在特定运营商网络下可用
 */
object TelecomSourceDiscovery {

    // 电信/联通/移动 IPTV 组播转单播地址（部分地区可用）
    private val telecomSources = listOf(
        // 电信 ITV 组播源（需电信宽带）
        DiscoveredSource(
            name = "电信ITV组播源",
            url = "http://39.134.66.66/PLTV/88888888/224/3221225466/index.m3u8",
            format = PlaylistFormat.M3U,
            discoveredFrom = "telecom"
        ),
        DiscoveredSource(
            name = "联通IPTV源",
            url = "http://112.47.28.26/PLTV/88888888/224/3221225466/index.m3u8",
            format = PlaylistFormat.M3U,
            discoveredFrom = "unicom"
        )
    )

    fun discover(): List<DiscoveredSource> {
        // 电信源通常在特定网络下才可用，先测试连通性
        return telecomSources.filter { source ->
            try {
                val result = NetworkUtil.fetchPlaylist(source.url)
                result != null && result.content.isNotBlank()
            } catch (e: Exception) {
                false
            }
        }
    }
}

internal object PlaylistParserFactory {
    fun detectFormat(content: String): PlaylistFormat {
        return when {
            content.trimStart().startsWith("#EXTM3U") -> PlaylistFormat.M3U
            content.contains("#genre#") -> PlaylistFormat.TXT
            content.contains("#EXTINF") -> PlaylistFormat.M3U
            else -> PlaylistFormat.TXT
        }
    }
}
