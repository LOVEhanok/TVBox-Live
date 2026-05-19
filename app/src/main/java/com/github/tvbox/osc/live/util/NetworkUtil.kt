package com.github.tvbox.osc.live.util

import okhttp3.OkHttpClient
import okhttp3.Request
import java.nio.charset.Charset
import java.util.concurrent.TimeUnit

object NetworkUtil {

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    fun getClient(): OkHttpClient = client

    data class FetchResult(
        val content: String,
        val etag: String? = null,
        val lastModified: String? = null,
        val statusCode: Int = 0
    )

    fun fetchPlaylist(
        url: String,
        etag: String? = null,
        lastModified: String? = null,
        userAgent: String? = null
    ): FetchResult? {
        try {
            val builder = Request.Builder().url(url)
            etag?.let { builder.header("If-None-Match", it) }
            lastModified?.let { builder.header("If-Modified-Since", it) }
            userAgent?.let { builder.header("User-Agent", it) }
                ?: builder.header("User-Agent", "Mozilla/5.0")

            val response = client.newCall(builder.build()).execute()

            if (response.code() == 304) {
                return null // Not modified
            }

            if (!response.isSuccessful) {
                response.close()
                return null
            }

            val bytes = response.body()?.bytes() ?: return null
            val responseEtag = response.header("ETag")
            val responseLastModified = response.header("Last-Modified")
            val contentType = response.header("Content-Type") ?: ""

            val charset = detectCharset(bytes, contentType)
            val content = String(bytes, charset)

            response.close()

            return FetchResult(
                content = content,
                etag = responseEtag,
                lastModified = responseLastModified,
                statusCode = response.code()
            )
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    private fun detectCharset(bytes: ByteArray, contentType: String): Charset {
        // Check Content-Type header
        val charsetMatch = Regex("charset=([\\w-]+)").find(contentType)
        if (charsetMatch != null) {
            try {
                return Charset.forName(charsetMatch.groupValues[1])
            } catch (_: Exception) {}
        }

        // Try to detect using juniversalchardet
        try {
            val detector = org.mozilla.universalchardet.UniversalDetector(null)
            detector.handleData(bytes, 0, bytes.size)
            detector.dataEnd()
            val encoding = detector.detectedCharset
            if (encoding != null) {
                return Charset.forName(encoding)
            }
        } catch (_: Exception) {}

        // Default to UTF-8
        return Charsets.UTF_8
    }

    fun testUrlSpeed(url: String, timeoutMs: Long = 5000): com.github.tvbox.osc.live.data.model.SpeedTestResult {
        val startTime = System.currentTimeMillis()
        try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0")
                .build()

            val clientWithTimeout = client.newBuilder()
                .connectTimeout(timeoutMs.toInt(), TimeUnit.MILLISECONDS)
                .readTimeout(timeoutMs.toInt(), TimeUnit.MILLISECONDS)
                .build()

            val response = clientWithTimeout.newCall(request).execute()
            val latencyMs = System.currentTimeMillis() - startTime

            if (!response.isSuccessful) {
                response.close()
                return com.github.tvbox.osc.live.data.model.SpeedTestResult(
                    url = url,
                    latencyMs = latencyMs,
                    isAlive = false,
                    testedAt = System.currentTimeMillis()
                )
            }

            // Read a sample of data to measure speed
            val buffer = ByteArray(256 * 1024) // 256KB
            val body = response.body()
            var totalBytes = 0
            val readStart = System.currentTimeMillis()

            body?.byteStream()?.use { stream ->
                var bytesRead: Int
                while (stream.read(buffer).also { bytesRead = it } != -1) {
                    totalBytes += bytesRead
                    if (totalBytes >= buffer.size) break
                }
            }
            response.close()

            val readDuration = System.currentTimeMillis() - readStart
            val speedMbps = if (readDuration > 0) {
                (totalBytes * 8.0 / (readDuration / 1000.0) / 1_000_000).toFloat()
            } else 0f

            // Try to detect resolution from URL or content type
            val resolution = detectResolution(url, response.header("Content-Type") ?: "")

            return com.github.tvbox.osc.live.data.model.SpeedTestResult(
                url = url,
                latencyMs = latencyMs,
                speedMbps = speedMbps,
                resolution = resolution,
                isAlive = true,
                testedAt = System.currentTimeMillis()
            )
        } catch (e: Exception) {
            return com.github.tvbox.osc.live.data.model.SpeedTestResult(
                url = url,
                latencyMs = System.currentTimeMillis() - startTime,
                isAlive = false,
                testedAt = System.currentTimeMillis()
            )
        }
    }

    private fun detectResolution(url: String, contentType: String): Int {
        val urlLower = url.lowercase()
        return when {
            "1080" in urlLower || "1080p" in urlLower -> 1080
            "720" in urlLower || "720p" in urlLower -> 720
            "480" in urlLower -> 480
            "4k" in urlLower || "2160" in urlLower -> 2160
            else -> 0
        }
    }
}
