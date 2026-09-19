package com.borasarang.promptjournaljupjup.ui.home

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.borasarang.promptjournaljupjup.R
import com.borasarang.promptjournaljupjup.databinding.PjFragmentHomeBinding
import com.borasarang.promptjournaljupjup.util.DebugLogger
import kotlinx.coroutines.launch

class HomeFragment : Fragment() {

    private var _binding: PjFragmentHomeBinding? = null
    private val binding get() = _binding!!
    private val viewModel: HomeViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = PjFragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        DebugLogger.i("홈", "프롬프트 저널 홈 화면 진입")

        binding.pjBtnExecuteNow.setOnClickListener { viewModel.executeNow() }
        binding.pjBtnOpenPortal.setOnClickListener { openInBrowser() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { render(it) }
            }
        }
        viewModel.refresh()
    }

    override fun onResume() {
        super.onResume()
        viewModel.refresh()
    }

    private fun render(state: HomeUiState) {
        binding.pjServerStatusText.text =
            if (state.isServerRunning) "서버 실행 중" else "서버 중지됨"
        val address = if (state.localIp != null) {
            "http://${state.localIp}:${state.port}"
        } else {
            "IP 확인 중… (포트 ${state.port})"
        }
        binding.pjServerAddressText.text = address
        binding.pjLastExecution.text = state.lastExecutionLabel ?: "실행 이력 없음"
        binding.pjExecutionCount.text = "총 ${state.executionCount}회 실행"
        binding.pjPromptCount.text = "등록 ${state.promptCount}개 · 활성 ${state.activePromptCount}개"
        binding.pjBtnExecuteNow.isEnabled = !state.isExecuting
        binding.pjBtnExecuteNow.text = if (state.isExecuting) "실행 중…" else "지금 실행하기"
    }

    private fun openInBrowser() {
        val state = viewModel.uiState.value
        val address = if (state.localIp != null) "http://${state.localIp}:${state.port}" else {
            Toast.makeText(requireContext(), "IP 확인 중…", Toast.LENGTH_SHORT).show()
            return
        }
        DebugLogger.i("홈", "포털 브라우저 열기 $address")
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(address)))
        } catch (e: Exception) {
            DebugLogger.e("홈", "E-AND-UI-0701", "브라우저 열기 실패: ${e.message}", e)
            Toast.makeText(requireContext(), "브라우저를 열 수 없습니다", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
