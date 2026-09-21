package com.borasarang.communityjupjup.ui.settings

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
import com.borasarang.communityjupjup.R
import com.borasarang.communityjupjup.data.repository.RecentLog
import com.borasarang.communityjupjup.databinding.CmFragmentSettingsBinding
import com.borasarang.communityjupjup.databinding.CmItemCrawlLogBinding
import com.borasarang.communityjupjup.util.Constants
import com.borasarang.communityjupjup.util.DebugLogger
import com.borasarang.communityjupjup.util.TimeUtils
import kotlinx.coroutines.launch

class SettingsFragment : Fragment() {

    private var _binding: CmFragmentSettingsBinding? = null
    private val binding get() = _binding!!
    private val viewModel: SettingsViewModel by viewModels()
    private lateinit var logAdapter: LogAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = CmFragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        DebugLogger.i("설정", "설정 화면 진입")

        logAdapter = LogAdapter()
        binding.cmLogRecycler.layoutManager = LinearLayoutManager(requireContext())
        binding.cmLogRecycler.adapter = logAdapter

        binding.cmBtnApplyPort.setOnClickListener {
            val port = binding.cmEtPort.text.toString().toIntOrNull()
            if (port == null) {
                Toast.makeText(requireContext(), getString(R.string.cm_toast_port_required), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            viewModel.savePort(port)
        }
        binding.cmSwitchAutoStart.setOnCheckedChangeListener { _, checked ->
            viewModel.saveAutoStart(checked)
        }
        binding.cmRgRetention.setOnCheckedChangeListener { _, checkedId ->
            val days = when (checkedId) {
                R.id.cm_rb_7 -> 7
                R.id.cm_rb_14 -> 14
                R.id.cm_rb_30 -> 30
                R.id.cm_rb_90 -> 90
                else -> 3
            }
            viewModel.saveRetention(days)
        }
        binding.cmBtnCleanup.setOnClickListener { viewModel.cleanupNow() }
        binding.cmBtnApplyWatchdog.setOnClickListener {
            val sec = binding.cmEtWatchdog.text.toString().toIntOrNull()
            if (sec == null) {
                Toast.makeText(requireContext(), getString(R.string.cm_toast_seconds_required), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            viewModel.saveWatchdog(sec)
        }
        binding.cmBtnBatteryRequest.setOnClickListener { requestBatteryExemption() }
        binding.cmSwitchNotifCrawl.setOnCheckedChangeListener { _, checked ->
            viewModel.saveNotifCrawlComplete(checked)
        }
        binding.cmSwitchNotifNewplan.setOnCheckedChangeListener { _, checked ->
            viewModel.saveNotifNewApp(checked)
        }
        binding.cmSwitchNotifFailure.setOnCheckedChangeListener { _, checked ->
            viewModel.saveNotifFailure(checked)
        }

        val versionName = try {
            requireContext().packageManager
                .getPackageInfo(requireContext().packageName, 0).versionName
        } catch (_: Exception) {
            "?"
        }
        binding.cmAppInfo.text = "버전 $versionName · 제작자 BoRaSaRang · leeborasarang@gmail.com"

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.settings.collect {
                        renderSettings(it.port, it.retentionDays, it.autoStart, it.watchdogIntervalSec)
                        renderNotifToggles(it.notifCrawlComplete, it.notifNewPost, it.notifFailure)
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
        if (binding.cmEtPort.text.toString() != port.toString()) {
            binding.cmEtPort.setText(port.toString())
        }
        binding.cmRgRetention.check(
            when (retentionDays) {
                7 -> R.id.cm_rb_7
                14 -> R.id.cm_rb_14
                30 -> R.id.cm_rb_30
                90 -> R.id.cm_rb_90
                else -> R.id.cm_rb_3
            }
        )
        if (binding.cmSwitchAutoStart.isChecked != autoStart) {
            binding.cmSwitchAutoStart.isChecked = autoStart
        }
        if (binding.cmEtWatchdog.text.toString() != watchdogSec.toString()) {
            binding.cmEtWatchdog.setText(watchdogSec.toString())
        }
    }

    private fun renderNotifToggles(notifCrawl: Boolean, notifNewApp: Boolean, notifFailure: Boolean) {
        if (binding.cmSwitchNotifCrawl.isChecked != notifCrawl) {
            binding.cmSwitchNotifCrawl.isChecked = notifCrawl
        }
        if (binding.cmSwitchNotifNewplan.isChecked != notifNewApp) {
            binding.cmSwitchNotifNewplan.isChecked = notifNewApp
        }
        if (binding.cmSwitchNotifFailure.isChecked != notifFailure) {
            binding.cmSwitchNotifFailure.isChecked = notifFailure
        }
    }

    private fun updateBatteryStatus() {
        val unrestricted = viewModel.isIgnoringBatteryOptimizations()
        DebugLogger.i("설정", "배터리 예외 상태 갱신: $unrestricted")
        binding.cmBatteryStatus.text =
            if (unrestricted) "배터리 최적화 예외: 허용됨" else "배터리 최적화 예외: 미허용"
        binding.cmBatteryStatus.setTextColor(
            ContextCompat.getColor(
                requireContext(),
                if (unrestricted) R.color.cm_status_success else R.color.cm_status_error,
            ),
        )
        binding.cmBtnBatteryRequest.visibility = if (unrestricted) View.GONE else View.VISIBLE
        binding.cmBatteryDesc.visibility = if (unrestricted) View.GONE else View.VISIBLE
        if (unrestricted) {
            binding.cmBatteryStatus.text = "배터리 최적화 예외: 허용됨 (백그라운드 안정 동작)"
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
            Toast.makeText(requireContext(), getString(R.string.cm_toast_settings_unavailable), Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private class LogAdapter : ListAdapter<RecentLog, LogAdapter.ViewHolder>(DIFF) {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val binding = CmItemCrawlLogBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return ViewHolder(binding)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bind(getItem(position))
        }

        inner class ViewHolder(private val binding: CmItemCrawlLogBinding) :
            RecyclerView.ViewHolder(binding.root) {
            fun bind(item: RecentLog) {
                val color = if (item.status == Constants.STATUS_SUCCESS) "#2E7D32" else "#C62828"
                binding.cmLogTitle.text = "${item.sourceName} · ${item.status}"
                binding.cmLogTitle.setTextColor(android.graphics.Color.parseColor(color))
                val time = TimeUtils.formatRelative(item.startedAt)
                binding.cmLogDetail.text = if (item.status == Constants.STATUS_SUCCESS) {
                    "$time · 발견 ${item.postsFound} · 신규 ${item.postsNew} · 갱신 ${item.postsUpdated}"
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
