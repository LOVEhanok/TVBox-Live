package com.github.tvbox.osc.live.ui

import android.app.AlertDialog
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.github.tvbox.osc.R
import com.github.tvbox.osc.base.BaseActivity
import com.github.tvbox.osc.live.data.model.LiveSource
import com.github.tvbox.osc.live.data.model.RefreshResult
import com.github.tvbox.osc.live.data.repository.LiveRepository
import com.github.tvbox.osc.live.worker.DiscoveryWorker
import com.github.tvbox.osc.live.worker.RefreshWorker
import kotlinx.coroutines.*

class LiveSettingActivity : BaseActivity() {

    private lateinit var repository: LiveRepository
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val handler = Handler(Looper.getMainLooper())

    private lateinit var sourceList: RecyclerView
    private lateinit var btnAddSource: Button
    private lateinit var btnRefresh: Button
    private lateinit var btnSpeedTest: Button
    private lateinit var btnDiscover: Button
    private lateinit var tvStatus: TextView
    private lateinit var progressBar: ProgressBar

    private val sources = mutableListOf<LiveSource>()
    private lateinit var adapter: SourceAdapter

    override fun getLayoutResID(): Int = R.layout.activity_live_setting

    override fun init() {
        repository = LiveRepository.getInstance(this)

        sourceList = findViewById(R.id.rvSources)
        btnAddSource = findViewById(R.id.btnAddSource)
        btnRefresh = findViewById(R.id.btnRefresh)
        btnSpeedTest = findViewById(R.id.btnSpeedTest)
        btnDiscover = findViewById(R.id.btnDiscover)
        tvStatus = findViewById(R.id.tvStatus)
        progressBar = findViewById(R.id.progressBar)

        adapter = SourceAdapter()
        sourceList.layoutManager = LinearLayoutManager(this)
        sourceList.adapter = adapter

        btnAddSource.setOnClickListener { showAddSourceDialog() }
        btnRefresh.setOnClickListener { refreshSources() }
        btnSpeedTest.setOnClickListener { runSpeedTest() }
        btnDiscover.setOnClickListener { discoverSources() }

        loadSources()
    }

    private fun loadSources() {
        scope.launch {
            val allSources = repository.getAllSources()
            sources.clear()
            sources.addAll(allSources)
            adapter.notifyDataSetChanged()
            updateStatus("已加载 ${sources.size} 个直播源")
        }
    }

    private fun showAddSourceDialog() {
        val input = EditText(this).apply {
            hint = "输入直播源 URL（M3U/TXT）"
            setPadding(50, 30, 50, 30)
        }

        AlertDialog.Builder(this)
            .setTitle("添加直播源")
            .setView(input)
            .setPositiveButton("添加") { _, _ ->
                val url = input.text.toString().trim()
                if (url.isNotEmpty()) {
                    addSource(url)
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun addSource(url: String) {
        scope.launch {
            try {
                val name = extractNameFromUrl(url)
                repository.addSource(name, url)
                loadSources()
                updateStatus("已添加: $name")
            } catch (e: Exception) {
                updateStatus("添加失败: ${e.message}")
            }
        }
    }

    private fun refreshSources() {
        showLoading(true)
        updateStatus("正在刷新直播源...")

        scope.launch {
            try {
                val result = repository.refreshAllSources()
                showLoading(false)
                updateStatus(
                    "刷新完成: 新增 ${result.newChannels}, " +
                    "更新 ${result.updatedChannels}, " +
                    "总计 ${result.totalChannels} 个频道"
                )
                if (result.errors.isNotEmpty()) {
                    updateStatus("错误: ${result.errors.first()}")
                }
            } catch (e: Exception) {
                showLoading(false)
                updateStatus("刷新失败: ${e.message}")
            }
        }
    }

    private fun runSpeedTest() {
        showLoading(true)
        updateStatus("正在测速...")

        scope.launch {
            try {
                val count = repository.runSpeedTest()
                showLoading(false)
                updateStatus("测速完成: 测试了 $count 个频道")
            } catch (e: Exception) {
                showLoading(false)
                updateStatus("测速失败: ${e.message}")
            }
        }
    }

    private fun discoverSources() {
        showLoading(true)
        updateStatus("正在发现新源...")

        scope.launch {
            try {
                DiscoveryWorker.enqueue(this@LiveSettingActivity)
                showLoading(false)
                updateStatus("发现任务已启动，新源将自动添加")
                delay(2000)
                loadSources()
            } catch (e: Exception) {
                showLoading(false)
                updateStatus("发现失败: ${e.message}")
            }
        }
    }

    private fun showLoading(show: Boolean) {
        progressBar.visibility = if (show) View.VISIBLE else View.GONE
    }

    private fun updateStatus(message: String) {
        tvStatus.text = message
    }

    private fun extractNameFromUrl(url: String): String {
        val parts = url.split("/")
        val lastPart = parts.lastOrNull() ?: return url
        return lastPart
            .removeSuffix(".m3u")
            .removeSuffix(".m3u8")
            .removeSuffix(".txt")
            .replace("_", " ")
            .replace("-", " ")
            .ifEmpty { "自定义源" }
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    inner class SourceAdapter : RecyclerView.Adapter<SourceAdapter.ViewHolder>() {

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val tvName: TextView = view.findViewById(R.id.tvSourceName)
            val tvUrl: TextView = view.findViewById(R.id.tvSourceUrl)
            val tvStatus: TextView = view.findViewById(R.id.tvSourceStatus)
            val switchEnabled: Switch = view.findViewById(R.id.switchEnabled)
            val btnDelete: ImageButton = view.findViewById(R.id.btnDelete)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_live_source, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val source = sources[position]
            holder.tvName.text = source.name
            holder.tvUrl.text = source.url
            holder.tvStatus.text = when {
                source.fetchErrorCount > 0 -> "错误: ${source.fetchErrorCount}"
                source.lastChannelCount > 0 -> "${source.lastChannelCount} 个频道"
                source.lastFetchedAt > 0 -> "已获取"
                else -> "未获取"
            }
            holder.switchEnabled.isChecked = source.enabled
            holder.switchEnabled.setOnCheckedChangeListener { _, isChecked ->
                scope.launch {
                    repository.setSourceEnabled(source.id, isChecked)
                }
            }
            holder.btnDelete.setOnClickListener {
                AlertDialog.Builder(this@LiveSettingActivity)
                    .setTitle("删除直播源")
                    .setMessage("确定删除 ${source.name}？")
                    .setPositiveButton("删除") { _, _ ->
                        scope.launch {
                            repository.removeSource(source.id)
                            loadSources()
                        }
                    }
                    .setNegativeButton("取消", null)
                    .show()
            }
        }

        override fun getItemCount() = sources.size
    }
}
