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
import com.borasarang.planjupjup.R
import com.borasarang.planjupjup.databinding.PlanFragmentSettingsBinding
import com.borasarang.planjupjup.util.DebugLogger
import kotlinx.coroutines.launch

class SettingsFragment : Fragment() {

    private var _binding: PlanFragmentSettingsBinding? = null
    private val binding get() = _binding!!
    private val viewModel: SettingsViewModel by viewModels()

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

        binding.planSwitchAutoStart.setOnCheckedChangeListener { _, checked ->
            viewModel.saveAutoStart(checked)
        }
        binding.planBtnBatteryRequest.setOnClickListener { requestBatteryExemption() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.settings.collect {
                        renderAutoStart(it.autoStart)
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

    private fun renderAutoStart(autoStart: Boolean) {
        if (binding.planSwitchAutoStart.isChecked != autoStart) {
            binding.planSwitchAutoStart.isChecked = autoStart
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
}
