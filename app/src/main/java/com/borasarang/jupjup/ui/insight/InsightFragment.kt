package com.borasarang.jupjup.ui.insight

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.borasarang.jupjup.R
import com.borasarang.jupjup.databinding.FragmentInsightBinding
import com.borasarang.macjupjup.util.DebugLogger as MacDebugLogger
import kotlinx.coroutines.launch

/**
 * 줍줍 시리즈 인사이트 — 맥줍줍·요금줍줍 두 서비스를 카드로 병렬 표시.
 *
 * 각 카드: 실행 상태(도트)·접속 주소·통계 3개·[지금 수집][수집 중지/재개][서버 시작/중지].
 */
class InsightFragment : Fragment() {

    private var _binding: FragmentInsightBinding? = null
    private val binding get() = _binding!!

    private val viewModel: InsightViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentInsightBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        MacDebugLogger.i("인사이트", "시리즈 인사이트 화면 진입")
        bindActions()
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { render(it) }
            }
        }
        viewModel.refresh()
    }

    /** 탭 복귀·서비스 전환 시 카드 최신화 */
    fun refreshData() {
        if (isAdded) viewModel.refresh()
    }

    /** 맥줍줍 카드 액션 */
    private fun bindActions() {
        binding.insightMacBtnCrawl.setOnClickListener {
            if (_binding?.insightMacBtnToggle?.isEnabled != true) return@setOnClickListener
            viewModel.triggerMacCrawl()
        }
        binding.insightMacBtnToggle.setOnClickListener {
            viewModel.toggleMacCrawl()
        }
        binding.insightMacBtnServer.setOnClickListener {
            viewModel.toggleMacServer()
        }

        binding.insightPlanBtnCrawl.setOnClickListener {
            if (_binding?.insightPlanBtnToggle?.isEnabled != true) return@setOnClickListener
            viewModel.triggerPlanCrawl()
        }
        binding.insightPlanBtnToggle.setOnClickListener {
            viewModel.togglePlanCrawl()
        }
        binding.insightPlanBtnServer.setOnClickListener {
            viewModel.togglePlanServer()
        }
    }

    private fun render(state: InsightUiState) {
        renderMac(state.mac)
        renderPlan(state.plan)
    }

    private fun renderMac(s: InsightServiceUi) {
        binding.insightMacDot.backgroundTintList = ContextCompat.getColorStateList(
            requireContext(),
            if (s.isServerRunning) com.borasarang.macjupjup.R.color.mac_status_success else com.borasarang.macjupjup.R.color.mac_status_error,
        )
        binding.insightMacStatus.text =
            if (s.isServerRunning) getString(R.string.insight_status_running)
            else getString(R.string.insight_status_stopped)

        binding.insightMacAddress.text =
            s.address.ifBlank { getString(R.string.insight_address_placeholder) }

        binding.insightMacStatValue1.text = s.statValue1.toString()
        binding.insightMacStatValue2.text = s.statValue2.toString()
        binding.insightMacStatValue3.text =
            s.lastCollectedLabel.ifBlank { getString(R.string.insight_stat_zero) }

        binding.insightMacBtnCrawl.isEnabled = s.crawlEnabled && !s.isCrawling
        binding.insightMacBtnCrawl.text =
            getString(if (s.isCrawling) R.string.insight_btn_crawling else R.string.insight_btn_crawl_now)

        binding.insightMacBtnToggle.text =
            getString(if (s.crawlEnabled) R.string.insight_btn_pause else R.string.insight_btn_resume)
        binding.insightMacBtnToggle.isEnabled = !s.isCrawling

        binding.insightMacBtnServer.text =
            getString(if (s.isServerRunning) R.string.insight_btn_stop_server else R.string.insight_btn_start_server)
    }

    private fun renderPlan(s: InsightServiceUi) {
        binding.insightPlanDot.backgroundTintList = ContextCompat.getColorStateList(
            requireContext(),
            if (s.isServerRunning) com.borasarang.planjupjup.R.color.plan_status_success else com.borasarang.planjupjup.R.color.plan_status_error,
        )
        binding.insightPlanStatus.text =
            if (s.isServerRunning) getString(R.string.insight_status_running)
            else getString(R.string.insight_status_stopped)

        binding.insightPlanAddress.text =
            s.address.ifBlank { getString(R.string.insight_address_placeholder) }

        binding.insightPlanStatValue1.text = s.statValue1.toString()
        binding.insightPlanStatValue2.text = s.statValue2.toString()
        binding.insightPlanStatValue3.text =
            s.lastCollectedLabel.ifBlank { getString(R.string.insight_stat_zero) }

        binding.insightPlanBtnCrawl.isEnabled = s.crawlEnabled && !s.isCrawling
        binding.insightPlanBtnCrawl.text =
            getString(if (s.isCrawling) R.string.insight_btn_crawling else R.string.insight_btn_crawl_now)

        binding.insightPlanBtnToggle.text =
            getString(if (s.crawlEnabled) R.string.insight_btn_pause else R.string.insight_btn_resume)
        binding.insightPlanBtnToggle.isEnabled = !s.isCrawling

        binding.insightPlanBtnServer.text =
            getString(if (s.isServerRunning) R.string.insight_btn_stop_server else R.string.insight_btn_start_server)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
