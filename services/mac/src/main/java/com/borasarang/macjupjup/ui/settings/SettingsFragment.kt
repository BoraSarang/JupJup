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
import com.borasarang.macjupjup.R
import com.borasarang.macjupjup.databinding.MacFragmentSettingsBinding
import com.borasarang.macjupjup.util.DebugLogger
import kotlinx.coroutines.launch

/**
 * 설정 (R46 축소: OS 전용만 — 배터리 예외·자동시작).
 * 서버 설정은 웹 관리(⚙️)로 이관. 동작은 SettingsViewModel이 소유.
 */
class SettingsFragment : Fragment() {

    private var _binding: MacFragmentSettingsBinding? = null
    private val binding get() = _binding!!
    private val viewModel: SettingsViewModel by viewModels()

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

        binding.macSwitchAutoStart.setOnCheckedChangeListener { _, checked ->
            viewModel.saveAutoStart(checked)
        }
        binding.macBtnBatteryRequest.setOnClickListener { requestBatteryExemption() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.settings.collect {
                        if (binding.macSwitchAutoStart.isChecked != it.autoStart) {
                            binding.macSwitchAutoStart.isChecked = it.autoStart
                        }
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

    private fun updateBatteryStatus() {
        val unrestricted = viewModel.isIgnoringBatteryOptimizations()
        DebugLogger.i("설정", "배터리 예외 상태 갱신: $unrestricted")
        binding.macBatteryStatus.text =
            if (unrestricted) "배터리 최적화 예외: 허용됨 (백그라운드 안정 동작)" else "배터리 최적화 예외: 미허용"
        binding.macBatteryStatus.setTextColor(
            ContextCompat.getColor(
                requireContext(),
                if (unrestricted) R.color.mac_status_success else R.color.mac_status_error,
            ),
        )
        binding.macBtnBatteryRequest.visibility = if (unrestricted) View.GONE else View.VISIBLE
        binding.macBatteryDesc.visibility = if (unrestricted) View.GONE else View.VISIBLE
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
            Toast.makeText(requireContext(), getString(R.string.mac_toast_settings_unavailable), Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
