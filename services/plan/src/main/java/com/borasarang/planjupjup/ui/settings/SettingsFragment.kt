package com.borasarang.planjupjup.ui.settings

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
import com.borasarang.planjupjup.R
import com.borasarang.planjupjup.data.repository.RecentLog
import com.borasarang.planjupjup.databinding.PlanFragmentSettingsBinding
import com.borasarang.planjupjup.databinding.PlanItemCrawlLogBinding
import com.borasarang.planjupjup.util.Constants
import com.borasarang.planjupjup.util.DebugLogger
import com.borasarang.planjupjup.util.TimeUtils
import kotlinx.coroutines.launch

class SettingsFragment : Fragment() {

    private var _binding: PlanFragmentSettingsBinding? = null
    private val binding get() = _binding!!
    private val viewModel: SettingsViewModel by viewModels()
    private lateinit var logAdapter: LogAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = PlanFragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        DebugLogger.i("설정", "설정 화면 진입")

        logAdapter = LogAdapter()
        binding.planLogRecycler.layoutManager = LinearLayoutManager(requireContext())
        binding.planLogRecycler.adapter = logAdapter

        binding.planBtnApplyPort.setOnClickListener {
            val port = binding.planEtPort.text.toString().toIntOrNull()
            if (port == null) {
                Toast.makeText(requireContext(), getString(R.string.plan_toast_port_required), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            viewModel.savePort(port)
        }
        binding.planSwitchAutoStart.setOnCheckedChangeListener { _, checked ->
            viewModel.saveAutoStart(checked)
        }
        binding.planRgRetention.setOnCheckedChangeListener { _, checkedId ->
            viewModel.saveRetention(if (checkedId == R.id.plan_rb_90) 90 else 30)
        }
        binding.planBtnCleanup.setOnClickListener { viewModel.cleanupNow() }
        binding.planBtnApplyWatchdog.setOnClickListener {
            val sec = binding.planEtWatchdog.text.toString().toIntOrNull()
            if (sec == null) {
                Toast.makeText(requireContext(), getString(R.string.plan_toast_seconds_required), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            viewModel.saveWatchdog(sec)
        }
        binding.planBtnBatteryRequest.setOnClickListener { requestBatteryExemption() }
        binding.planSwitchNotifCrawl.setOnCheckedChangeListener { _, checked ->
            viewModel.saveNotifCrawlComplete(checked)
        }
        binding.planSwitchNotifNewplan.setOnCheckedChangeListener { _, checked ->
            viewModel.saveNotifNewPlan(checked)
        }
        binding.planSwitchNotifFailure.setOnCheckedChangeListener { _, checked ->
            viewModel.saveNotifFailure(checked)
        }

        val versionName = try {
            requireContext().packageManager
                .getPackageInfo(requireContext().packageName, 0).versionName
        } catch (_: Exception) {
            "?"
        }
        binding.planAppInfo.text = "버전 $versionName · 제작자 BoRaSaRang · leeborasarang@gmail.com"

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.settings.collect {
                        renderSettings(it.port, it.retentionDays, it.autoStart, it.watchdogIntervalSec)
                        renderNotifToggles(it.notifCrawlComplete, it.notifNewPlan, it.notifFailure)
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

    private fun renderSettings(port: Int, retentionDays: Int, autoStart: Boolean, watchdogSec: Int) {
        if (binding.planEtPort.text.toString() != port.toString()) {
            binding.planEtPort.setText(port.toString())
        }
        binding.planRgRetention.check(if (retentionDays == 90) R.id.plan_rb_90 else R.id.plan_rb_30)
        if (binding.planSwitchAutoStart.isChecked != autoStart) {
            binding.planSwitchAutoStart.isChecked = autoStart
        }
        if (binding.planEtWatchdog.text.toString() != watchdogSec.toString()) {
            binding.planEtWatchdog.setText(watchdogSec.toString())
        }
    }

    private fun renderNotifToggles(notifCrawl: Boolean, notifNewPlan: Boolean, notifFailure: Boolean) {
        if (binding.planSwitchNotifCrawl.isChecked != notifCrawl) {
            binding.planSwitchNotifCrawl.isChecked = notifCrawl
        }
        if (binding.planSwitchNotifNewplan.isChecked != notifNewPlan) {
            binding.planSwitchNotifNewplan.isChecked = notifNewPlan
        }
        if (binding.planSwitchNotifFailure.isChecked != notifFailure) {
            binding.planSwitchNotifFailure.isChecked = notifFailure
        }
    }

    private fun updateBatteryStatus() {
        val unrestricted = viewModel.isIgnoringBatteryOptimizations()
        DebugLogger.i("설정", "배터리 예외 상태 갱신: $unrestricted")
        binding.planBatteryStatus.text =
            if (unrestricted) "배터리 최적화 예외: 허용됨" else "배터리 최적화 예외: 미허용"
        binding.planBatteryStatus.setTextColor(
            ContextCompat.getColor(
                requireContext(),
                if (unrestricted) R.color.plan_status_success else R.color.plan_status_error,
            ),
        )
        binding.planBtnBatteryRequest.visibility = if (unrestricted) View.GONE else View.VISIBLE
        binding.planBatteryDesc.visibility = if (unrestricted) View.GONE else View.VISIBLE
        if (unrestricted) {
            binding.planBatteryStatus.text = "배터리 최적화 예외: 허용됨 (백그라운드 안정 동작)"
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
            Toast.makeText(requireContext(), getString(R.string.plan_toast_settings_unavailable), Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private class LogAdapter : ListAdapter<RecentLog, LogAdapter.ViewHolder>(DIFF) {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val binding = PlanItemCrawlLogBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return ViewHolder(binding)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bind(getItem(position))
        }

        inner class ViewHolder(private val binding: PlanItemCrawlLogBinding) :
            RecyclerView.ViewHolder(binding.root) {
            fun bind(item: RecentLog) {
                val color = if (item.status == Constants.STATUS_SUCCESS) "#2E7D32" else "#C62828"
                binding.planLogTitle.text = "${item.sourceName} · ${item.status}"
                binding.planLogTitle.setTextColor(android.graphics.Color.parseColor(color))
                val time = TimeUtils.formatRelative(item.startedAt)
                binding.planLogDetail.text = if (item.status == Constants.STATUS_SUCCESS) {
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
