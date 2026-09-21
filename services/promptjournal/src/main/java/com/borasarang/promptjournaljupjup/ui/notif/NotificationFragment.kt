package com.borasarang.promptjournaljupjup.ui.notif

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.borasarang.promptjournaljupjup.PromptJournalRuntime
import com.borasarang.promptjournaljupjup.databinding.PjFragmentNotifBinding
import com.borasarang.promptjournaljupjup.util.DebugLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class NotificationFragment : Fragment() {

    private var _binding: PjFragmentNotifBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = PjFragmentNotifBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        DebugLogger.i("알림", "알림 화면 진입")
        refreshRecent()
    }

    override fun onResume() {
        super.onResume()
        refreshRecent()
    }

    private fun refreshRecent() {
        viewLifecycleOwner.lifecycleScope.launch {
            val lines = try {
                withContext(Dispatchers.IO) {
                    if (!PromptJournalRuntime.isInitialized) return@withContext null
                    PromptJournalRuntime.promptExecutionRepository.getRecent(20)
                }
            } catch (e: Exception) {
                DebugLogger.e("알림", "E-AND-DB-0404", "실행 기록 조회 실패: ${e.message}", e)
                null
            } ?: run {
                _binding?.pjNotifList?.text = "기록을 불러오지 못했습니다"
                return@launch
            }
            if (lines.isEmpty()) {
                _binding?.pjNotifList?.text = "실행 기록 없음"
                return@launch
            }
            val dateFormat = java.text.SimpleDateFormat("MM/dd HH:mm", java.util.Locale.getDefault())
            _binding?.pjNotifList?.text = lines.joinToString("\n") {
                "${dateFormat.format(java.util.Date(it.executedAt))} · ${it.status} · ${it.modelId}"
            }
            DebugLogger.i("알림", "최근 실행 표시 count=${lines.size}")
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
