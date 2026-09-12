package com.borasarang.macjupjup.ui.settings

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
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
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.borasarang.macjupjup.R
import com.borasarang.macjupjup.data.repository.RecentLog
import com.borasarang.macjupjup.databinding.MacFragmentSettingsBinding
import com.borasarang.macjupjup.databinding.MacItemCrawlLogBinding
import com.borasarang.macjupjup.util.Constants
import com.borasarang.macjupjup.util.DebugLogger
import com.borasarang.macjupjup.util.TimeUtils
import com.borasarang.macjupjup.util.maskToken
import kotlinx.coroutines.launch

class SettingsFragment : Fragment() {

    private var _binding: MacFragmentSettingsBinding? = null
    private val binding get() = _binding!!
    private val viewModel: SettingsViewModel by viewModels()
    private lateinit var logAdapter: LogAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = MacFragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        DebugLogger.i("설정", "설정 화면 진입")

        logAdapter = LogAdapter()
        binding.macLogRecycler.layoutManager = LinearLayoutManager(requireContext())
        binding.macLogRecycler.adapter = logAdapter

        binding.macBtnSaveToken.setOnClickListener {
            viewModel.saveToken(binding.macEtToken.text.toString())
            binding.macEtToken.text?.clear()
            Toast.makeText(requireContext(), "토큰을 저장했습니다", Toast.LENGTH_SHORT).show()
        }
        binding.macSwitchTranslateKo.setOnCheckedChangeListener { _, checked ->
            viewModel.saveTranslateKo(checked)
        }
        binding.macBtnApplyPort.setOnClickListener {
            val port = binding.macEtPort.text.toString().toIntOrNull()
            if (port == null) {
                Toast.makeText(requireContext(), "포트 번호를 입력하세요", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            viewModel.savePort(port)
        }
        binding.macSwitchAutoStart.setOnCheckedChangeListener { _, checked ->
            viewModel.saveAutoStart(checked)
        }
        binding.macRgRetention.setOnCheckedChangeListener { _, checkedId ->
            viewModel.saveRetention(if (checkedId == R.id.mac_rb_90) 90 else 30)
        }
        binding.macBtnCleanup.setOnClickListener { viewModel.cleanupNow() }
        binding.macBtnApplyWatchdog.setOnClickListener {
            val sec = binding.macEtWatchdog.text.toString().toIntOrNull()
            if (sec == null) {
                Toast.makeText(requireContext(), "초 단위로 입력하세요", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            viewModel.saveWatchdog(sec)
        }
        binding.macBtnBatteryRequest.setOnClickListener { requestBatteryExemption() }
        binding.macSwitchNotifCrawl.setOnCheckedChangeListener { _, checked ->
            viewModel.saveNotifCrawlComplete(checked)
        }
        binding.macSwitchNotifNewplan.setOnCheckedChangeListener { _, checked ->
            viewModel.saveNotifNewApp(checked)
        }
        binding.macSwitchNotifFailure.setOnCheckedChangeListener { _, checked ->
            viewModel.saveNotifFailure(checked)
        }

        val versionName = try {
            requireContext().packageManager
                .getPackageInfo(requireContext().packageName, 0).versionName
        } catch (_: Exception) {
            "?"
        }
        binding.macAppInfo.text = "버전 $versionName · 제작자 BoRaSaRang · leeborasarang@gmail.com"

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.settings.collect {
                        renderSettings(it.port, it.retentionDays, it.autoStart, it.watchdogIntervalSec)
                        renderNotifToggles(it.notifCrawlComplete, it.notifNewApp, it.notifFailure)
                        renderTokenStatus(it.githubToken)
                        if (binding.macSwitchTranslateKo.isChecked != it.translateKo) {
                            binding.macSwitchTranslateKo.isChecked = it.translateKo
                        }
                    }
                }
                launch { viewModel.logs.collect { logAdapter.submitList(it) } }
                launch {
                    viewModel.cleanupResult.collect {
                        if (it != null) Toast.makeText(requireContext(), it, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
        viewModel.refresh()
    }

    override fun onResume() {
        super.onResume()
        updateBatteryStatus()
        viewModel.refresh()
    }

    private fun renderTokenStatus(token: String) {
        binding.macTokenStatus.text =
            if (token.isBlank()) "토큰 미설정" else "토큰 설정됨 (${maskToken(token)})"
    }

    private fun renderSettings(port: Int, retentionDays: Int, autoStart: Boolean, watchdogSec: Int) {
        if (binding.macEtPort.text.toString() != port.toString()) {
            binding.macEtPort.setText(port.toString())
        }
        binding.macRgRetention.check(if (retentionDays == 90) R.id.mac_rb_90 else R.id.mac_rb_30)
        if (binding.macSwitchAutoStart.isChecked != autoStart) {
            binding.macSwitchAutoStart.isChecked = autoStart
        }
        if (binding.macEtWatchdog.text.toString() != watchdogSec.toString()) {
            binding.macEtWatchdog.setText(watchdogSec.toString())
        }
    }

    private fun renderNotifToggles(notifCrawl: Boolean, notifNewApp: Boolean, notifFailure: Boolean) {
        if (binding.macSwitchNotifCrawl.isChecked != notifCrawl) {
            binding.macSwitchNotifCrawl.isChecked = notifCrawl
        }
        if (binding.macSwitchNotifNewplan.isChecked != notifNewApp) {
            binding.macSwitchNotifNewplan.isChecked = notifNewApp
        }
        if (binding.macSwitchNotifFailure.isChecked != notifFailure) {
            binding.macSwitchNotifFailure.isChecked = notifFailure
        }
    }

    private fun updateBatteryStatus() {
        val unrestricted = viewModel.isIgnoringBatteryOptimizations()
        DebugLogger.i("설정", "배터리 예외 상태 갱신: $unrestricted")
        binding.macBatteryStatus.text =
            if (unrestricted) "배터리 최적화 예외: 허용됨" else "배터리 최적화 예외: 미허용"
        binding.macBatteryStatus.setTextColor(
            ContextCompat.getColor(
                requireContext(),
                if (unrestricted) R.color.mac_status_success else R.color.mac_status_error,
            ),
        )
        binding.macBtnBatteryRequest.visibility = if (unrestricted) View.GONE else View.VISIBLE
        binding.macBatteryDesc.visibility = if (unrestricted) View.GONE else View.VISIBLE
        if (unrestricted) {
            binding.macBatteryStatus.text = "배터리 최적화 예외: 허용됨 (백그라운드 안정 동작)"
        }
    }

    private fun requestBatteryExemption() {
        DebugLogger.i("설정", "배터리 예외 요청 인텐트 실행")
        try {
            startActivity(
                Intent(
                    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:${requireContext().packageName}"),
                ),
            )
        } catch (e: Exception) {
            DebugLogger.e("설정", "E-AND-PERM-0602", "배터리 예외 요청 실패: ${e.message}", e)
            Toast.makeText(requireContext(), "설정 화면을 열 수 없습니다", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private class LogAdapter : ListAdapter<RecentLog, LogAdapter.ViewHolder>(DIFF) {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val binding = MacItemCrawlLogBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return ViewHolder(binding)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bind(getItem(position))
        }

        inner class ViewHolder(private val binding: MacItemCrawlLogBinding) :
            RecyclerView.ViewHolder(binding.root) {
            fun bind(item: RecentLog) {
                val color = if (item.status == Constants.STATUS_SUCCESS) "#2E7D32" else "#C62828"
                binding.macLogTitle.text = "${item.sourceName} · ${item.status}"
                binding.macLogTitle.setTextColor(android.graphics.Color.parseColor(color))
                val time = TimeUtils.formatRelative(item.startedAt)
                binding.macLogDetail.text = if (item.status == Constants.STATUS_SUCCESS) {
                    "$time · 발견 ${item.plansFound} · 신규 ${item.plansNew} · 갱신 ${item.plansUpdated}"
                } else {
                    "$time · 오류: ${item.errorMessage ?: "?"}"
                }
            }
        }

        companion object {
            private val DIFF = object : DiffUtil.ItemCallback<RecentLog>() {
                override fun areItemsTheSame(old: RecentLog, new: RecentLog) = old.id == new.id
                override fun areContentsTheSame(old: RecentLog, new: RecentLog) = old == new
            }
        }
    }
}
