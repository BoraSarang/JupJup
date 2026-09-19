package com.borasarang.promptjournaljupjup.ui.notif

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.borasarang.promptjournaljupjup.databinding.PjFragmentNotifBinding
import com.borasarang.promptjournaljupjup.util.DebugLogger

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
        // TODO: 실행 기록 목록 표시 (RecyclerView + ViewModel)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
