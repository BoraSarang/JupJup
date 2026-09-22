package com.borasarang.macjupjup.ui.notif

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.borasarang.macjupjup.MacJupJupRuntime
import com.borasarang.macjupjup.data.repository.NotificationItem
import com.borasarang.macjupjup.data.repository.toItem
import com.borasarang.macjupjup.data.db.entity.NotificationType
import com.borasarang.macjupjup.databinding.MacFragmentNotificationsBinding
import com.borasarang.macjupjup.databinding.MacItemNotificationBinding
import com.borasarang.macjupjup.util.DebugLogger
import com.borasarang.macjupjup.util.TimeUtils
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class NotificationFragment : Fragment() {

    private var _binding: MacFragmentNotificationsBinding? = null
    private val binding get() = _binding!!
    private val app get() = MacJupJupRuntime
    private val repo get() = app.notificationRepository
    private lateinit var adapter: NotifAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = MacFragmentNotificationsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        DebugLogger.i("알림화면", "알림 센터 화면 진입")

        adapter = NotifAdapter(
            onOpen = { item -> openDetail(item) },
            onDelete = { item -> deleteItem(item) },
        )
        binding.macNotifRecycler.layoutManager = LinearLayoutManager(requireContext())
        binding.macNotifRecycler.adapter = adapter

        binding.macNotifReadAll.setOnClickListener { markAllRead() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                load()
            }
        }
    }

    private suspend fun load() {
        try {
            val items = repo.getPaged(null, null, 1, 100).map { it.toItem() }
            val unread = repo.countUnread()
            adapter.submitList(items)
            binding.macNotifEmpty.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
            binding.macNotifCount.text = if (unread > 0) "안 읽음 $unread" else "모두 읽음"
        } catch (e: Exception) {
            DebugLogger.e("알림화면", "E-AND-DB-0404", "알림 로드 실패: ${e.message}", e)
        }
    }

    private fun openDetail(item: NotificationItem) {
        markRead(item.id)
        val detail = parseDetailText(item)
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(typeLabel(item.type))
            .setMessage(item.summary + "\n\n" + detail)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun markRead(id: Long) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                repo.markAsRead(id)
                load()
            } catch (e: Exception) {
                // R6: 사용자 액션 silent 금지
                DebugLogger.e("알림", "E-AND-DB-0402", "읽음 처리 실패 id=$id: ${e.message}", e)
            }
        }
    }

    private fun deleteItem(item: NotificationItem) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                repo.delete(item.id)
                load()
            } catch (e: Exception) {
                // R6: 사용자 액션 silent 금지
                DebugLogger.e("알림", "E-AND-DB-0402", "알림 삭제 실패 id=${item.id}: ${e.message}", e)
            }
        }
    }

    private fun markAllRead() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                repo.markAllAsRead()
                load()
            } catch (e: Exception) {
                // R6: 사용자 액션 silent 금지
                DebugLogger.e("알림", "E-AND-DB-0402", "전체 읽음 처리 실패: ${e.message}", e)
            }
        }
    }

    private fun parseDetailText(item: NotificationItem): String {
        return try {
            val root = Json.parseToJsonElement(item.detailJson).jsonObject
            val sb = StringBuilder()
            // T-090: 수집 시작→완료·소요 (수집완료 타입만, 신규/버전업은 발견 시각)
            val startedAt = root["startedAt"]?.jsonPrimitive?.content?.toLongOrNull()
            val finishedAt = root["finishedAt"]?.jsonPrimitive?.content?.toLongOrNull()
            val duration = TimeUtils.formatDuration(startedAt, finishedAt)
            if (duration != null) {
                sb.append("수집 시작: ${TimeUtils.formatAbsolute(startedAt)}\n")
                sb.append("수집 완료: ${TimeUtils.formatAbsolute(finishedAt)} ($duration)\n")
            } else {
                sb.append("발견 시각: ${TimeUtils.formatAbsolute(item.createdAt)}\n")
            }
            root["totalFound"]?.let { sb.append("전체 발견: ${it.jsonPrimitive.content}\n") }
            root["newApps"]?.let { sb.append("신규: ${it.jsonPrimitive.content}\n") }
            root["updatedApps"]?.let { sb.append("갱신: ${it.jsonPrimitive.content}\n") }
            root["failedCount"]?.let {
                if (it.jsonPrimitive.content != "0") sb.append("실패: ${it.jsonPrimitive.content}\n")
            }
            val bySource = root["bySource"]?.jsonArray.orEmpty()
            if (bySource.isNotEmpty()) {
                sb.append("\n— 출처별 —\n")
                bySource.forEach { el ->
                    val o = el.jsonObject
                    sb.append("${o["sourceName"]?.jsonPrimitive?.content}: ${o["count"]?.jsonPrimitive?.content}\n")
                }
            }
            val byCategory = root["byCategory"]?.jsonArray.orEmpty()
            if (byCategory.isNotEmpty()) {
                sb.append("\n— 카테고리별 —\n")
                byCategory.forEach { el ->
                    val o = el.jsonObject
                    sb.append("${o["category"]?.jsonPrimitive?.content}: ${o["count"]?.jsonPrimitive?.content}\n")
                }
            }
            val nps = root["newAppsDetail"]?.jsonArray.orEmpty()
            if (nps.isNotEmpty()) {
                sb.append("\n— 신규 앱 ${nps.size}건 —\n")
                nps.take(20).forEach { el ->
                    val o = el.jsonObject
                    sb.append("${o["name"]?.jsonPrimitive?.content} (${o["developer"]?.jsonPrimitive?.content})\n")
                }
            }
            val fails = root["failedSources"]?.jsonArray.orEmpty()
            if (fails.isNotEmpty()) {
                sb.append("\n— 실패 소스 —\n")
                fails.forEach { el ->
                    val o = el.jsonObject
                    sb.append("${o["sourceName"]?.jsonPrimitive?.content}: ${o["error"]?.jsonPrimitive?.content}\n")
                }
            }
            sb.toString().ifBlank { "상세 정보 없음" }
        } catch (e: Exception) {
            "상세 정보 없음"
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private class NotifAdapter(
        private val onOpen: (NotificationItem) -> Unit,
        private val onDelete: (NotificationItem) -> Unit,
    ) : ListAdapter<NotificationItem, NotifAdapter.ViewHolder>(DIFF) {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val binding = MacItemNotificationBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return ViewHolder(binding)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bind(getItem(position))
        }

        inner class ViewHolder(private val binding: MacItemNotificationBinding) :
            RecyclerView.ViewHolder(binding.root) {

            fun bind(item: NotificationItem) {
                binding.macNotifSummary.text = item.summary
                binding.macNotifSummary.setTypeface(
                    null,
                    if (item.isRead) android.graphics.Typeface.NORMAL
                    else android.graphics.Typeface.BOLD,
                )
                binding.macNotifTime.text = "${typeLabel(item.type)} · ${TimeUtils.formatRelative(item.createdAt)}"
                binding.macNotifDot.backgroundTintList =
                    android.content.res.ColorStateList.valueOf(
                        if (item.isRead) 0x00000000 else 0xFF0066FF.toInt(),
                    )
                binding.root.setOnClickListener { onOpen(item) }
                binding.macNotifDelete.setOnClickListener { onDelete(item) }
            }
        }

        companion object {
            private val DIFF = object : DiffUtil.ItemCallback<NotificationItem>() {
                override fun areItemsTheSame(old: NotificationItem, new: NotificationItem) =
                    old.id == new.id
                override fun areContentsTheSame(old: NotificationItem, new: NotificationItem) = old == new
            }
        }
    }
}

private fun typeLabel(type: String): String = when (type) {
    NotificationType.CRAWL_COMPLETE -> "수집 완료"
    NotificationType.CRAWL_FAILED -> "수집 실패"
    NotificationType.NEW_APPS_FOUND -> "신규 앱"
    NotificationType.NEWS_FOUND -> "신규 뉴스"
    NotificationType.VERSION_BUMPED -> "버전 업데이트"
    NotificationType.CRAWL_SUMMARY -> "요약"
    NotificationType.CRAWL_FAILED_STREAK -> "수집 실패"
    else -> type
}
