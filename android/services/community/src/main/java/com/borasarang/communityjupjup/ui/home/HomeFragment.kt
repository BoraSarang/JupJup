package com.borasarang.communityjupjup.ui.home

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.borasarang.communityjupjup.R
import com.borasarang.communityjupjup.databinding.CmFragmentHomeBinding
import com.borasarang.communityjupjup.util.DebugLogger
import com.borasarang.communityjupjup.util.TimeUtils
import kotlinx.coroutines.launch

class HomeFragment : Fragment() {

    private var _binding: CmFragmentHomeBinding? = null
    private val binding get() = _binding!!
    private val viewModel: HomeViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = CmFragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        DebugLogger.i("홈", "홈 화면 진입")

        binding.cmBtnManualCrawl.setOnClickListener { viewModel.triggerManualCrawl() }
        binding.cmBtnToggleCrawl.setOnClickListener { viewModel.toggleCrawl() }
        binding.cmBtnCopyAddress.setOnClickListener { copyAddress() }
        binding.cmBtnOpenPortal.setOnClickListener { openInBrowser() }
        // 주소 탭 → 브라우저로 포털 열기 (복사는 복사 버튼 유지)
        binding.cmServerAddressText.setOnClickListener { openInBrowser() }
        binding.cmServerStatusCard.setOnClickListener { openInBrowser() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { render(it) }
            }
        }
        viewModel.refresh()
    }

    override fun onResume() {
        super.onResume()
        // 복귀마다 상태 갱신 (서버가 뒤늦게 떴거나 네트워크가 바뀐 경우 반영)
        viewModel.refresh()
    }

    private fun render(state: HomeUiState) {
        val context = requireContext()
        binding.cmServerStatusText.text =
            if (state.isServerRunning) "서버 실행 중" else "서버 중지됨"
        binding.cmServerStatusDot.setTextColor(
            ContextCompat.getColor(
                context,
                if (state.isServerRunning) R.color.cm_status_success else R.color.cm_status_error,
            ),
        )
        val address = if (state.localIp != null) {
            "http://${state.localIp}:${state.port}"
        } else {
            "IP 확인 중… (포트 ${state.port})"
        }
        binding.cmServerAddressText.text = address

        setStat(binding.cmStatApps, state.totalApps.toString(), "수집 게시글")
        setStat(binding.cmStatSources, state.activeSources.toString(), "활성 소스")
        setStat(binding.cmStatLastSync, TimeUtils.formatRelative(state.lastCollectedAt), "마지막 수집")

        binding.cmBtnManualCrawl.isEnabled = !state.isCrawling && state.crawlEnabled
        binding.cmBtnManualCrawl.text = when {
            state.isCrawling -> "수집 예약 중…"
            !state.crawlEnabled -> "수집 일시정지됨"
            else -> "지금 수집하기"
        }
        binding.cmBtnToggleCrawl.text = if (state.crawlEnabled) "수집 중지" else "수집 시작"
    }

    private fun setStat(statBinding: com.borasarang.communityjupjup.databinding.CmItemStatCardBinding, value: String, label: String) {
        statBinding.cmStatValue.text = value
        statBinding.cmStatLabel.text = label
    }

    private fun copyAddress() {
        val state = viewModel.uiState.value
        val address = if (state.localIp != null) "http://${state.localIp}:${state.port}" else return
        val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("서버 주소", address))
        Toast.makeText(requireContext(), getString(R.string.cm_toast_address_copied), Toast.LENGTH_SHORT).show()
        DebugLogger.i("홈", "서버 주소 복사")
    }

    private fun openInBrowser() {
        val state = viewModel.uiState.value
        val address = if (state.localIp != null) "http://${state.localIp}:${state.port}" else {
            Toast.makeText(requireContext(), getString(R.string.cm_toast_address_unknown), Toast.LENGTH_SHORT).show()
            return
        }
        DebugLogger.i("홈", "포털 브라우저 열기 $address")
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(address)))
        } catch (e: Exception) {
            DebugLogger.e("홈", "E-AND-UI-0701", "브라우저 열기 실패: ${e.message}", e)
            Toast.makeText(requireContext(), getString(R.string.cm_toast_browser_failed), Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
