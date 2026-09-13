package com.borasarang.planjupjup.ui.notif

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
import com.borasarang.planjupjup.PlanJupJupRuntime
import com.borasarang.planjupjup.data.repository.NotificationItem
import com.borasarang.planjupjup.data.repository.toItem
import com.borasarang.planjupjup.databinding.PlanFragmentNotificationsBinding
import com.borasarang.planjupjup.databinding.PlanItemNotificationBinding
import com.borasarang.planjupjup.util.DebugLogger
import com.borasarang.planjupjup.util.TimeUtils
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class NotificationFragment : Fragment() {

    private var _binding: PlanFragmentNotificationsBinding? = null
    private val binding get() = _binding!!
    private val app get() = PlanJupJupRuntime
    private lateinit var adapter: NotifAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = PlanFragmentNotificationsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        DebugLogger.i("알림화면", "알림 센터 화면 진입")

        adapter = NotifAdapter(
            onOpen = { item -> openDetail(item) },
            onDelete = { item -> deleteItem(item) },
        )
        binding.planNotifRecycler.layoutManager = LinearLayoutManager(requireContext())
        binding.planNotifRecycler.adapter = adapter

        binding.planNotifReadAll.setOnClickListener { markAllRead() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                load()
            }
        }
    }

    private suspend fun load() {
        try {
            val items = app.notificationRepository.getPaged(null, null, 1, 100)
                .map { it.toItem() }
            val unread = app.notificationRepository.countUnread()
            adapter.submitList(items)
            binding.planNotifEmpty.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
            binding.planNotifCount.text = if (unread > 0) "안 읽음 $unread" else "모두 읽음"
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
                app.notificationRepository.markAsRead(id)
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
                app.notificationRepository.delete(item.id)
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
                app.notificationRepository.markAllAsRead()
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
            root["totalFound"]?.let { sb.append("전체 발견: ${it.jsonPrimitive.content}\n") }
            root["newPlans"]?.let { sb.append("신규: ${it.jsonPrimitive.content}\n") }
            root["updatedPlans"]?.let { sb.append("갱신: ${it.jsonPrimitive.content}\n") }
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
            val nps = root["newPlansDetail"]?.jsonArray.orEmpty()
            if (nps.isNotEmpty()) {
                sb.append("\n— 신규 요금제 ${nps.size}건 —\n")
                nps.take(20).forEach { el ->
                    val o = el.jsonObject
                    sb.append("${o["carrierName"]?.jsonPrimitive?.content} ${o["planName"]?.jsonPrimitive?.content} · ${o["dataAmount"]?.jsonPrimitive?.content}\n")
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
            val binding = PlanItemNotificationBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return ViewHolder(binding)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bind(getItem(position))
        }

        inner class ViewHolder(private val binding: PlanItemNotificationBinding) :
            RecyclerView.ViewHolder(binding.root) {

            fun bind(item: NotificationItem) {
                binding.planNotifSummary.text = item.summary
                binding.planNotifSummary.setTypeface(
                    null,
                    if (item.isRead) android.graphics.Typeface.NORMAL
                    else android.graphics.Typeface.BOLD,
                )
                binding.planNotifTime.text = "${typeLabel(item.type)} · ${TimeUtils.formatRelative(item.createdAt)}"
                binding.planNotifDot.backgroundTintList =
                    android.content.res.ColorStateList.valueOf(
                        if (item.isRead) 0x00000000 else 0xFF0066FF.toInt(),
                    )
                binding.root.setOnClickListener { onOpen(item) }
                binding.planNotifDelete.setOnClickListener { onDelete(item) }
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
    "CRAWL_COMPLETE" -> "수집 완료"
    "CRAWL_FAILED" -> "수집 실패"
    "NEW_PLANS_FOUND" -> "신규 요금제"
    "NEW_PLAN_DETAIL" -> "신규 요금제"
    "CRAWL_SUMMARY" -> "요약"
    "CRAWL_FAILED_STREAK" -> "수집 실패"
    else -> type
}