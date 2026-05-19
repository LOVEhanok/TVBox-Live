package com.github.tvbox.osc.live.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.*
import com.github.tvbox.osc.R
import com.github.tvbox.osc.live.data.repository.LiveRepository
import com.github.tvbox.osc.live.discovery.BuiltinSourceDiscovery
import com.github.tvbox.osc.live.discovery.GitHubSourceDiscovery
import com.github.tvbox.osc.live.discovery.TelecomSourceDiscovery
import java.util.concurrent.TimeUnit

/**
 * 定期刷新直播源（每 6 小时）
 */
class RefreshWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val repository = LiveRepository.getInstance(applicationContext)
            val result = repository.refreshAllSources()

            if (result.hasChanges) {
                showNotification(
                    "直播源已更新",
                    "新增 ${result.newChannels} 个频道，更新 ${result.updatedChannels} 个频道"
                )
            }

            // 同时重试失效 URL
            repository.revalidateFailedUrls()

            Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            Result.retry()
        }
    }

    private fun showNotification(title: String, message: String) {
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "直播源更新", NotificationManager.IMPORTANCE_LOW)
            manager.createNotificationChannel(channel)
        }
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.app_banner)
            .setContentTitle(title)
            .setContentText(message)
            .setAutoCancel(true)
            .build()
        manager.notify(NOTIFICATION_ID, notification)
    }

    companion object {
        private const val CHANNEL_ID = "live_refresh"
        private const val NOTIFICATION_ID = 1001

        /** 注册周期性任务（每 6 小时） */
        @JvmStatic
        fun enqueue(context: Context) {
            val request = PeriodicWorkRequestBuilder<RefreshWorker>(6, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "live_refresh", ExistingPeriodicWorkPolicy.KEEP, request
            )
        }

        /** 立即执行一次刷新（应用启动时调用） */
        @JvmStatic
        fun enqueueOnce(context: Context) {
            val request = OneTimeWorkRequestBuilder<RefreshWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 5, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context).enqueue(request)
        }
    }
}

/**
 * 自动发现新源（每 24 小时）
 * 优先使用国内可访问的渠道：内置源 → 电信运营商源 → GitHub 镜像
 */
class DiscoveryWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val repository = LiveRepository.getInstance(applicationContext)
            val existingUrls = repository.getAllSources().map { it.url }.toSet()

            // 1. 内置源（国内直连，最可靠）
            val builtinSources = BuiltinSourceDiscovery.discover()
            for (source in builtinSources) {
                if (source.url !in existingUrls) {
                    repository.addSource(source.name, source.url)
                }
            }

            // 2. 电信运营商源（特定网络下可用）
            val telecomSources = TelecomSourceDiscovery.discover()
            for (source in telecomSources) {
                if (source.url !in existingUrls) {
                    repository.addSource(source.name, source.url)
                }
            }

            // 3. GitHub 镜像源（使用国内代理，最后尝试）
            val githubSources = GitHubSourceDiscovery.discover()
            for (source in githubSources) {
                if (source.url !in existingUrls) {
                    repository.addSource(source.name, source.url)
                }
            }

            Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            Result.retry()
        }
    }

    companion object {
        /** 注册周期性任务（每 24 小时） */
        @JvmStatic
        fun enqueue(context: Context) {
            val request = PeriodicWorkRequestBuilder<DiscoveryWorker>(24, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "live_discovery", ExistingPeriodicWorkPolicy.KEEP, request
            )
        }

        /** 立即执行一次发现（应用启动时调用） */
        @JvmStatic
        fun enqueueOnce(context: Context) {
            val request = OneTimeWorkRequestBuilder<DiscoveryWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setInitialDelay(10, TimeUnit.SECONDS) // 延迟 10 秒，避免启动时卡顿
                .build()
            WorkManager.getInstance(context).enqueue(request)
        }
    }
}

/**
 * 重试失效 URL（每 2 小时）
 */
class RevalidationWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val repository = LiveRepository.getInstance(applicationContext)
            val restored = repository.revalidateFailedUrls()

            if (restored > 0) {
                val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val channel = NotificationChannel("live_revalidate", "直播源恢复", NotificationManager.IMPORTANCE_LOW)
                    manager.createNotificationChannel(channel)
                }
                val notification = NotificationCompat.Builder(applicationContext, "live_revalidate")
                    .setSmallIcon(R.drawable.app_banner)
                    .setContentTitle("直播源恢复")
                    .setContentText("$restored 个失效源已恢复")
                    .setAutoCancel(true)
                    .build()
                manager.notify(1002, notification)
            }

            Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            Result.retry()
        }
    }

    companion object {
        @JvmStatic
        fun enqueue(context: Context) {
            val request = PeriodicWorkRequestBuilder<RevalidationWorker>(2, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "live_revalidation", ExistingPeriodicWorkPolicy.KEEP, request
            )
        }
    }
}
