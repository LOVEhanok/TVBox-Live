package com.github.tvbox.osc.live.merge

class ChannelNormalizer {

    private val aliases: Map<String, String> = buildMap {
        // CCTV channels
        for (i in 1..20) {
            val num = i.toString()
            put("cctv$num", "cctv$num")
            put("cctv-$num", "cctv$num")
            put("cctv $num", "cctv$num")
            put("cctv$num hd", "cctv${num}hd")
            put("cctv${num}hd", "cctv${num}hd")
            put("cctv${num}高清", "cctv${num}hd")
        }
        put("cctv1综合", "cctv1")
        put("cctv2财经", "cctv2")
        put("cctv3综艺", "cctv3")
        put("cctv4中文国际", "cctv4")
        put("cctv4欧洲", "cctv4euro")
        put("cctv4美洲", "cctv4america")
        put("cctv5体育", "cctv5")
        put("cctv5+体育赛事", "cctv5plus")
        put("cctv6电影", "cctv6")
        put("cctv7国防军事", "cctv7")
        put("cctv8电视剧", "cctv8")
        put("cctv9纪录", "cctv9")
        put("cctv10科教", "cctv10")
        put("cctv11戏曲", "cctv11")
        put("cctv12社会与法", "cctv12")
        put("cctv13新闻", "cctv13")
        put("cctv14少儿", "cctv14")
        put("cctv15音乐", "cctv15")
        put("cctv16奥林匹克", "cctv16")
        put("cctv17农业农村", "cctv17")
        put("中央一台", "cctv1")
        put("中央二台", "cctv2")
        put("中央三台", "cctv3")
        put("中央四台", "cctv4")
        put("中央五台", "cctv5")
        put("中央六台", "cctv6")
        put("中央七台", "cctv7")
        put("中央八台", "cctv8")
        put("中央综合", "cctv1")
        put("中央新闻", "cctv13")

        // Major provincial satellite channels
        put("湖南卫视", "hunan")
        put("浙江卫视", "zhejiang")
        put("东方卫视", "dongfang")
        put("江苏卫视", "jiangsu")
        put("北京卫视", "beijing")
        put("天津卫视", "tianjin")
        put("山东卫视", "shandong")
        put("辽宁卫视", "liaoning")
        put("湖北卫视", "hubei")
        put("安徽卫视", "anhui")
        put("广东卫视", "guangdong")
        put("深圳卫视", "shenzhen")
        put("广西卫视", "guangxi")
        put("四川卫视", "sichuan")
        put("重庆卫视", "chongqing")
        put("河南卫视", "henan")
        put("河北卫视", "hebei")
        put("黑龙江卫视", "heilongjiang")
        put("吉林卫视", "jilin")
        put("辽宁卫视", "liaoning")
        put("陕西卫视", "shanxi1")
        put("山西卫视", "shanxi2")
        put("甘肃卫视", "gansu")
        put("云南卫视", "yunnan")
        put("贵州卫视", "guizhou")
        put("江西卫视", "jiangxi")
        put("福建卫视", "fujian")
        put("厦门卫视", "xiamen")
        put("海南卫视", "hainan")
        put("新疆卫视", "xinjiang")
        put("西藏卫视", "xizang")
        put("青海卫视", "qinghai")
        put("宁夏卫视", "ningxia")
        put("内蒙古卫视", "neimenggu")
        put("兵团卫视", "bingtuan")
        put("三沙卫视", "sansha")

        // Common alternative names
        put("凤凰中文", "fenghuangcn")
        put("凤凰资讯", "fenghuanginfo")
        put("凤凰卫视中文台", "fenghuangcn")
        put("凤凰卫视资讯台", "fenghuanginfo")
        put("星空卫视", "xingkong")
        put("翡翠台", "feicui")
        put("明珠台", "mingzhu")
        put("viutv", "viutv")
    }

    fun normalize(name: String): String {
        val cleaned = name.trim()
            .lowercase()
            .replace(Regex("[\\s\\-_·]"), "")
            .replace("hd", "")
            .replace("高清", "")
            .replace("频道", "")
            .replace("电视台", "")

        return aliases[cleaned] ?: cleaned
    }

    fun similarity(a: String, b: String): Float {
        val na = normalize(a)
        val nb = normalize(b)
        if (na == nb) return 1.0f

        // Check if one contains the other
        if (na.contains(nb) || nb.contains(na)) return 0.8f

        // Levenshtein-based similarity
        val maxLen = maxOf(na.length, nb.length)
        if (maxLen == 0) return 1.0f
        val distance = levenshtein(na, nb)
        return 1.0f - (distance.toFloat() / maxLen)
    }

    private fun levenshtein(a: String, b: String): Int {
        val dp = Array(a.length + 1) { IntArray(b.length + 1) }
        for (i in 0..a.length) dp[i][0] = i
        for (j in 0..b.length) dp[0][j] = j
        for (i in 1..a.length) {
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                dp[i][j] = minOf(dp[i - 1][j] + 1, dp[i][j - 1] + 1, dp[i - 1][j - 1] + cost)
            }
        }
        return dp[a.length][b.length]
    }
}
