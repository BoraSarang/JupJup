package com.borasarang.jupjup.ui.dashboard

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
import com.borasarang.jupjup.databinding.FragmentDashboardBinding
import com.borasarang.jupjup.ui.nav.Service
import com.borasarang.macjupjup.util.DebugLogger as MacDebugLogger
import kotlinx.coroutines.launch

/**
 * 줍줍 시리즈 대시보드 — 맥줍줍·요금줍줍 두 서비스를 카드로 병렬 표시.
 *
 * 각 카드: 실행 상태(도트)·접속 주소·통계 3개·[지금 수집][수집 중지/재개][서버 시작/중지].
 */
class DashboardFragment : Fragment() {

    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!

    private val viewModel: DashboardViewModel by viewModels {
        DashboardViewModel.Factory(requireActivity().application)
    }

    /** MainActivity가 주입하는 활성 서비스 */
    var activeService: Service = Service.MAC

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        MacDebugLogger.i("대시보드", "시리즈 대시보드 화면 진입")
        bindActions()
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { render(it) }
            }
        }
        viewModel.refresh()
    }

    /** 탭 복귀·서비스 전환 시 카드 최신화 (활성 서비스 강조 포함) */
    fun refreshData(active: Service = activeService) {
        activeService = active
        applyActiveHighlight()
        if (isAdded) viewModel.refresh()
    }

    /** 맥줍줍 카드 액션 */
    private fun bindActions() {
        binding.dashboardMacBtnCrawl.setOnClickListener {
            if (_binding?.dashboardMacBtnToggle?.isEnabled != true) return@setOnClickListener
            viewModel.triggerCrawl(Service.MAC)
        }
        binding.dashboardMacBtnToggle.setOnClickListener {
            viewModel.toggleCrawl(Service.MAC)
        }
        binding.dashboardMacBtnServer.setOnClickListener {
            viewModel.toggleServer(Service.MAC)
        }

        binding.dashboardPlanBtnCrawl.setOnClickListener {
            if (_binding?.dashboardPlanBtnToggle?.isEnabled != true) return@setOnClickListener
            viewModel.triggerCrawl(Service.PLAN)
        }
        binding.dashboardPlanBtnToggle.setOnClickListener {
            viewModel.toggleCrawl(Service.PLAN)
        }
        binding.dashboardPlanBtnServer.setOnClickListener {
            viewModel.toggleServer(Service.PLAN)
        }
    }

    private fun render(state: DashboardUiState) {
        applyActiveHighlight()
        renderMac(state.mac)
        renderPlan(state.plan)
    }

    /** 활성 서비스 카드 강조: 스트로크 + "현재" 배지 */
    private fun applyActiveHighlight() {
        val b = _binding ?: return
        if (!isAdded) return
        val macActive = activeService == Service.MAC
        val strokePx = (2 * resources.displayMetrics.density).toInt()
        val primary = com.google.android.material.color.MaterialColors.getColor(
            requireContext(),
            com.google.android.material.R.attr.colorPrimary,
            0,
        )
        b.dashboardMacCard.strokeWidth = if (macActive) strokePx else 0
        b.dashboardMacCard.strokeColor = if (macActive) primary else android.graphics.Color.TRANSPARENT
        b.dashboardMacActiveBadge.visibility = if (macActive) View.VISIBLE else View.GONE
        b.dashboardPlanCard.strokeWidth = if (!macActive) strokePx else 0
        b.dashboardPlanCard.strokeColor = if (!macActive) primary else android.graphics.Color.TRANSPARENT
        b.dashboardPlanActiveBadge.visibility = if (!macActive) View.VISIBLE else View.GONE
    }

    private fun renderMac(s: DashboardServiceUi) {
        binding.dashboardMacDot.backgroundTintList = ContextCompat.getColorStateList(
            requireContext(),
            if (s.isServerRunning) com.borasarang.macjupjup.R.color.mac_status_success else com.borasarang.macjupjup.R.color.mac_status_error,
        )
        binding.dashboardMacStatus.text =
            if (s.isServerRunning) getString(R.string.dashboard_status_running)
            else getString(R.string.dashboard_status_stopped)

        binding.dashboardMacAddress.text =
            s.address.ifBlank { getString(R.string.dashboard_address_placeholder) }

        binding.dashboardMacStatValue1.text = s.statValue1.toString()
        binding.dashboardMacStatValue2.text = s.statValue2.toString()
        binding.dashboardMacStatValue3.text =
            s.lastCollectedLabel.ifBlank { getString(R.string.dashboard_stat_zero) }

        binding.dashboardMacBtnCrawl.isEnabled = s.crawlEnabled && !s.isCrawling
        binding.dashboardMacBtnCrawl.text =
            getString(if (s.isCrawling) R.string.dashboard_btn_crawling else R.string.dashboard_btn_crawl_now)

        binding.dashboardMacBtnToggle.text =
            getString(if (s.crawlEnabled) R.string.dashboard_btn_pause else R.string.dashboard_btn_resume)
        binding.dashboardMacBtnToggle.isEnabled = !s.isCrawling

        binding.dashboardMacBtnServer.text =
            getString(if (s.isServerRunning) R.string.dashboard_btn_stop_server else R.string.dashboard_btn_start_server)
    }

    private fun renderPlan(s: DashboardServiceUi) {
        binding.dashboardPlanDot.backgroundTintList = ContextCompat.getColorStateList(
            requireContext(),
            if (s.isServerRunning) com.borasarang.planjupjup.R.color.plan_status_success else com.borasarang.planjupjup.R.color.plan_status_error,
        )
        binding.dashboardPlanStatus.text =
            if (s.isServerRunning) getString(R.string.dashboard_status_running)
            else getString(R.string.dashboard_status_stopped)

        binding.dashboardPlanAddress.text =
            s.address.ifBlank { getString(R.string.dashboard_address_placeholder) }

        binding.dashboardPlanStatValue1.text = s.statValue1.toString()
        binding.dashboardPlanStatValue2.text = s.statValue2.toString()
        binding.dashboardPlanStatValue3.text =
            s.lastCollectedLabel.ifBlank { getString(R.string.dashboard_stat_zero) }

        binding.dashboardPlanBtnCrawl.isEnabled = s.crawlEnabled && !s.isCrawling
        binding.dashboardPlanBtnCrawl.text =
            getString(if (s.isCrawling) R.string.dashboard_btn_crawling else R.string.dashboard_btn_crawl_now)

        binding.dashboardPlanBtnToggle.text =
            getString(if (s.crawlEnabled) R.string.dashboard_btn_pause else R.string.dashboard_btn_resume)
        binding.dashboardPlanBtnToggle.isEnabled = !s.isCrawling

        binding.dashboardPlanBtnServer.text =
            getString(if (s.isServerRunning) R.string.dashboard_btn_stop_server else R.string.dashboard_btn_start_server)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
